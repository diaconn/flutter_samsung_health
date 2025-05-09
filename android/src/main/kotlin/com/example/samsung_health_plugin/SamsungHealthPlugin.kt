package com.example.samsung_health_plugin

import android.content.Context
import android.app.Activity
import android.util.Log
import androidx.annotation.NonNull

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import com.samsung.android.sdk.healthdata.HealthConnectionErrorResult
import com.samsung.android.sdk.healthdata.HealthConstants
import com.samsung.android.sdk.healthdata.HealthConstants.Exercise
import com.samsung.android.sdk.healthdata.HealthConstants.Exercise.CALORIE
import com.samsung.android.sdk.healthdata.HealthConstants.Exercise.END_TIME
import com.samsung.android.sdk.healthdata.HealthConstants.Exercise.EXERCISE_TYPE
import com.samsung.android.sdk.healthdata.HealthConstants.Exercise.START_TIME
import com.samsung.android.sdk.healthdata.HealthConstants.HeartRate
import com.samsung.android.sdk.healthdata.HealthConstants.HeartRate.END_TIME
import com.samsung.android.sdk.healthdata.HealthConstants.HeartRate.HEART_RATE
import com.samsung.android.sdk.healthdata.HealthConstants.HeartRate.START_TIME
import com.samsung.android.sdk.healthdata.HealthConstants.StepCount
import com.samsung.android.sdk.healthdata.HealthConstants.Sleep
import com.samsung.android.sdk.healthdata.HealthDataResolver
import com.samsung.android.sdk.healthdata.HealthDataResolver.ReadRequest
import com.samsung.android.sdk.healthdata.HealthDataStore
import com.samsung.android.sdk.healthdata.HealthPermissionManager
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionKey
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionType
import java.util.concurrent.TimeUnit

/** SamsungHealthPlugin */
class SamsungHealthPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private lateinit var context: Context
  private val APP_TAG : String = "SamsungHealthPlugin"
  private lateinit var mStore : HealthDataStore
  private val permissions = setOf(
    PermissionKey(Exercise.HEALTH_DATA_TYPE, PermissionType.READ),
    PermissionKey(StepCount.HEALTH_DATA_TYPE, PermissionType.READ),
    PermissionKey(HeartRate.HEALTH_DATA_TYPE, PermissionType.READ),
    PermissionKey(Sleep.HEALTH_DATA_TYPE, PermissionType.READ),
  )
  private var activity: Activity? = null

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "samsung_health_plugin")
    channel.setMethodCallHandler(this)
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      "getPlatformVersion" -> {
        result.success("Android ${android.os.Build.VERSION.RELEASE}")
      }
      "connect" -> {
        connectSamsungHealth(result)
      }
      "getHeartRate5minSeries" -> {
        val startMillis = call.argument<Long>("startMillis")!!
        val endMillis = call.argument<Long>("endMillis")!!
        getHeartRate5minSeries(startMillis, endMillis, result)
      }
      "getExerciseSessions" -> {
        val startMillis = call.argument<Long>("startMillis")!!
        val endMillis = call.argument<Long>("endMillis")!!
        getExerciseData(startMillis, endMillis, result)
      }
      "getStepCountSeries" -> {
        val startMillis = call.argument<Long>("startMillis")!!
        val endMillis = call.argument<Long>("endMillis")!!
        getStepCountData(startMillis, endMillis, result)
      }
      "getSleepData" -> {
        val startMillis = call.argument<Long>("startMillis")!!
        val endMillis = call.argument<Long>("endMillis")!!
        getSleepData(startMillis, endMillis, result)
      }
      else -> result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  /**
   * 삼성헬스 연계 관련 함수 추가.
   */
  private fun connectSamsungHealth(result: MethodChannel.Result) {
    Log.d(APP_TAG, "connectSamsungHealth() 호출")
    val resultMap: MutableMap<String, Any> = mutableMapOf()
    mStore = HealthDataStore(context, object : HealthDataStore.ConnectionListener {
      override fun onConnected() {
        Log.d(APP_TAG, "삼성 헬스 연결 성공")
        if (!isPermissionAcquired(result)) {
          requestPermission(result)
        }
        resultMap.put("isConnect", true)
        result.success(resultMap)
      }

      override fun onConnectionFailed(error: HealthConnectionErrorResult?) {
        Log.e(APP_TAG, "연결 실패: $error")
        resultMap.put("isConnect", false)
        result.success(resultMap)
      }

      override fun onDisconnected() {
        Log.w(APP_TAG, "삼성 헬스 연결 종료")
        resultMap.put("isConnect", false)
        result.success(resultMap)
      }
    })

    mStore.connectService()
  }

  private fun isPermissionAcquired(result: MethodChannel.Result): Boolean {
    val pmsManager = HealthPermissionManager(mStore)
    return try {
      // Check whether the permissions that this application needs are acquired
      Log.d(APP_TAG, "permissions. $permissions")
      val resultMap: Map<PermissionKey, Boolean> = pmsManager.isPermissionAcquired(permissions)
      Log.d(APP_TAG, "isPermissionAcquired. $resultMap")

      !resultMap.containsValue(false)

    } catch (e: Exception) {
      Log.d(APP_TAG, "Permission request fails. $e")
      false
    }
  }

  private fun requestPermission(result: MethodChannel.Result) {
    val pmsManager = HealthPermissionManager(mStore)
    try {
      Log.d(APP_TAG, "삼성헬스 권한요청 시작 ")
      // Show user permission UI for allowing user to change options
      pmsManager.requestPermissions(permissions, activity!!).setResultListener({ res ->
        val resultMap: Map<PermissionKey, Boolean> = res.resultMap
        if (resultMap.containsValue(false)) {
          Log.d(APP_TAG, "일부 권한 거부됨: $resultMap")

        } else {
          // Get the current step count and display it
          Log.d(APP_TAG, "모든 권한 획득 완료!")

        }
      })
    } catch (e: Exception) {
      showPermissionAlarmDialog()
      Log.d(APP_TAG, "Permission setting fails. $e")
    }
  }

  private fun showPermissionAlarmDialog() {
    Log.d(APP_TAG, "showPermissionAlarmDialog 호출")
  }

  private fun getHeartRate5minSeries(start: Long, end: Long, result: MethodChannel.Result) {
    val intervalMillis = 5 * 60 * 1000  // 5분
    val resolver = HealthDataResolver(mStore, null)
    val dataList = mutableListOf<Map<String, Any>>()

    var currentStart = start

    fun fetchNextChunk() {
      if (currentStart >= end) {
        result.success(dataList)
        return
      }

      val currentEnd = (currentStart + intervalMillis).coerceAtMost(end)

      val request = ReadRequest.Builder()
        .setDataType(HealthConstants.HeartRate.HEALTH_DATA_TYPE)
        .setProperties(arrayOf("heart_rate", "start_time", "end_time"))
        .setLocalTimeRange(
          HealthConstants.HeartRate.START_TIME, HealthConstants.HeartRate.TIME_OFFSET,
          currentStart, currentEnd
        )
        .build()

      resolver.read(request).setResultListener { readResult ->
        val heartRates = mutableListOf<Double>()

        for (data in readResult) {
          val rate = data.getFloat("heart_rate")
          heartRates.add(rate.toDouble())
        }

        // 평균값 예시로 전달 (최대/최소/개수도 포함 가능)
        val avg = if (heartRates.isNotEmpty()) heartRates.average() else 0.0

        dataList.add(
          mapOf(
            "start_time" to currentStart,
            "end_time" to currentEnd,
            "avg_heart_rate" to avg,
            "sample_count" to heartRates.size
          )
        )

        currentStart = currentEnd
        fetchNextChunk()
      }
    }

    fetchNextChunk()
  }

  private fun getExerciseData(start: Long, end: Long, result: MethodChannel.Result) {
    val request = ReadRequest.Builder()
      .setDataType(HealthConstants.Exercise.HEALTH_DATA_TYPE)
      .setProperties(
        arrayOf(
          HealthConstants.Exercise.EXERCISE_TYPE,
          HealthConstants.Exercise.DURATION,
          HealthConstants.Exercise.START_TIME,
          HealthConstants.Exercise.END_TIME,
          HealthConstants.Exercise.CALORIE,
          HealthConstants.Exercise.DISTANCE,
          HealthConstants.Exercise.LIVE_DATA,
        )
      )
      .setLocalTimeRange(HealthConstants.Exercise.START_TIME, HealthConstants.Exercise.TIME_OFFSET, start, end)
      .setSort(HealthConstants.Exercise.START_TIME, HealthDataResolver.SortOrder.DESC)
      .build()

    val resolver = HealthDataResolver(mStore, null)
    val resultList = mutableListOf<Map<String, Any>>()

    resolver.read(request).setResultListener { dataResult ->
      for (data in dataResult) {
        resultList.add(
          mapOf(
            "type" to data.getInt("exercise_type"),
            "duration" to data.getLong("duration"),
            "start_time" to data.getLong("start_time"),
            "end_time" to data.getLong("end_time"),
            "calorie" to data.getFloat("calorie"),
            "distance" to data.getFloat("distance"),
            "live_data" to data.getLong("live_data")
          )
        )
      }
      result.success(resultList)
    }
  }

  private fun getStepCountData(start: Long, end: Long, result: MethodChannel.Result) {
    val request = ReadRequest.Builder()
      .setDataType("com.samsung.health.step_count")
      .setProperties(arrayOf("count", "start_time", "end_time"))
      .setLocalTimeRange(HealthConstants.StepCount.START_TIME, HealthConstants.StepCount.TIME_OFFSET, start, end)
      .setSort(HealthConstants.StepCount.START_TIME, HealthDataResolver.SortOrder.DESC)
      .build()

    val resolver = HealthDataResolver(mStore, null)
    val stepList = mutableListOf<Map<String, Any>>()

    resolver.read(request).setResultListener { dataResult ->
      for (data in dataResult) {
        stepList.add(
          mapOf(
            "count" to data.getInt("count"),
            "start_time" to data.getLong("start_time"),
            "end_time" to data.getLong("end_time")
          )
        )
      }
      result.success(stepList)
    }
  }

  private fun getSleepData(start: Long, end: Long, result: MethodChannel.Result) {
    val request = ReadRequest.Builder()
      .setDataType("com.samsung.health.sleep")
      .setProperties(arrayOf("start_time", "end_time", "sleep_type", "efficiency"))
      .setLocalTimeRange(HealthConstants.StepCount.START_TIME, HealthConstants.StepCount.TIME_OFFSET, start, end)
      .build()

    val resolver = HealthDataResolver(mStore, null)
    val sleepList = mutableListOf<Map<String, Any>>()

    resolver.read(request).setResultListener { dataResult ->
      for (data in dataResult) {
        sleepList.add(
          mapOf(
            "type" to data.getInt("sleep_type"),
            "start_time" to data.getLong("start_time"),
            "end_time" to data.getLong("end_time"),
            "efficiency" to data.getFloat("efficiency")
          )
        )
      }
      result.success(sleepList)
    }
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }
}
