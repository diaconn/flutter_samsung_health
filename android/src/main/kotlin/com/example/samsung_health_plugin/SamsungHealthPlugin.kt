package com.example.samsung_health_plugin

import android.app.Activity
import android.content.Context
import android.util.Log
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
import com.samsung.android.sdk.healthdata.HealthConstants.Sleep
import com.samsung.android.sdk.healthdata.HealthConstants.StepCount
import com.samsung.android.sdk.healthdata.HealthDataResolver
import com.samsung.android.sdk.healthdata.HealthDataResolver.ReadRequest
import com.samsung.android.sdk.healthdata.HealthDataResolver.AggregateRequest
import com.samsung.android.sdk.healthdata.HealthDataResolver.AggregateRequest.AggregateFunction
import com.samsung.android.sdk.healthdata.HealthDataStore
import com.samsung.android.sdk.healthdata.HealthPermissionManager
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionKey
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionType
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.Date

/** SamsungHealthPlugin */
class SamsungHealthPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private lateinit var context: Context
  private val APP_TAG : String = "SamsungHealthPlugin"
  private val ONE_DAY_IN_MILLIS: Long = 24 * 60 * 60 * 1000L
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

  /**
   * 심박수 집계
   */
  private fun getHeartRate5minSeries(start: Long, end: Long, result: MethodChannel.Result) {
    Log.d(APP_TAG, "getHeartRate5minSeries 호출")

    val resolver = HealthDataResolver(mStore, null)
    val oneMinuteResults = mutableListOf<Pair<Long, Double>>()  // <timestamp, avg_hr>

    val request: AggregateRequest = AggregateRequest.Builder()
      .setDataType(HealthConstants.HeartRate.HEALTH_DATA_TYPE)
      .addFunction(AggregateFunction.AVG, HealthConstants.HeartRate.HEART_RATE, "avg_hr")
      .setTimeGroup(AggregateRequest.TimeGroupUnit.MINUTELY, 1, HealthConstants.HeartRate.START_TIME, "minute")
      .setLocalTimeRange(
        HealthConstants.HeartRate.START_TIME,
        HealthConstants.HeartRate.TIME_OFFSET,
        start, end
      )
      .build()

    resolver.aggregate(request).setResultListener { readResult ->
      Log.d(APP_TAG, "1분 단위 집계 결과 수: ${readResult.count}")
      val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
      sdf.timeZone = TimeZone.getDefault() // 또는 "UTC"로 설정

      // 1분 데이터: timestamp -> avg_hr
      val dataMap = mutableMapOf<Long, Double>()
      for (data in readResult) {
        val avgHr = data.getFloat("avg_hr").toDouble()
        val timeStr = data.getString("minute") ?: continue
        val timestamp = try {
          sdf.parse(timeStr)?.time ?: continue
        } catch (e: Exception) {
          Log.e(APP_TAG, "날짜 파싱 오류: $timeStr", e)
          continue
        }
        Log.d(APP_TAG, "1분 데이터 → 시간: $timestamp ($timeStr), 평균 심박수: $avgHr")
        dataMap[timestamp] = avgHr
      }

      // 1분 단위로 결과 채우기 (누락시 Double.NaN)
      var cursor = start
      while (cursor < end) {
        val value = dataMap[cursor]
        oneMinuteResults.add(cursor to (value ?: Double.NaN))
        cursor += 60_000L
      }

      val groupedData = mutableListOf<Map<String, Any>>()
      // 1분 단위 데이터를 5분 단위로 그룹화, NaN 무시
      oneMinuteResults.sortedBy { it.first }
        .chunked(5) { chunk ->
          val validValues = chunk.mapNotNull { if (it.second.isNaN()) null else it.second }
          if (validValues.isNotEmpty()) {
            val avg = validValues.average()
            val startTime = chunk.first().first
            val endTime = chunk.last().first + 60_000
            Log.d(APP_TAG, "5분 그룹 생성 → 시작: $startTime, 끝: $endTime, 평균: $avg")
            groupedData.add(
              mapOf(
                "start_time" to startTime,
                "end_time" to endTime,
                "avg_heart_rate" to avg,
                "sample_count" to validValues.size
              )
            )
          } else {
            Log.d(APP_TAG, "5분 그룹 모두 비어 있음 (skip)")
          }
        }
      Log.d(APP_TAG, "최종 그룹 수: ${groupedData.size}")
      result.success(groupedData)
    }
  }

  private fun getExerciseData(start: Long, end: Long, result: MethodChannel.Result) {
    val exerciseRequest = ReadRequest.Builder()
      .setDataType(HealthConstants.Exercise.HEALTH_DATA_TYPE)
      .setLocalTimeRange(HealthConstants.Exercise.START_TIME, HealthConstants.Exercise.TIME_OFFSET, start, end)
      .setSort(HealthConstants.Exercise.START_TIME, HealthDataResolver.SortOrder.DESC)
      .setProperties(
        arrayOf(
          HealthConstants.Exercise.EXERCISE_TYPE,
          HealthConstants.Exercise.CALORIE,
          HealthConstants.Exercise.START_TIME,
          HealthConstants.Exercise.END_TIME,
          HealthConstants.Exercise.LIVE_DATA
        )
      ).build()

    val resolver = HealthDataResolver(mStore, null)
    val exercisesList = mutableListOf<Map<String, Any>>()

    resolver.read(exerciseRequest).setResultListener { exResult ->
      var isExercising = false
      exResult.forEach {
        val liveData = it.getString(HealthConstants.Exercise.LIVE_DATA)
        Log.d(APP_TAG, "운동 종료시간 확인 :  $end, 현재시간 : ${System.currentTimeMillis()}")
        // 라이브데이터가 널이면 운동중으로 판단
        if (liveData == null ) {
          isExercising = true
        }
        exercisesList.add(
          mapOf(
            "exerciseType" to it.getInt(HealthConstants.Exercise.EXERCISE_TYPE),
            "calories" to it.getFloat(HealthConstants.Exercise.CALORIE),
            "startTime" to it.getLong(HealthConstants.Exercise.START_TIME),
            "endTime" to it.getLong(HealthConstants.Exercise.END_TIME),
            "liveData" to it.getString(HealthConstants.Exercise.LIVE_DATA)
          )
        )
      }
      Log.d(APP_TAG, "운동조회 결과. $exercisesList")
      result.success(exercisesList)
    }
  }

  private fun getStepCountData(start: Long, end: Long, result: MethodChannel.Result) {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH", Locale.getDefault())
    sdf.timeZone = TimeZone.getDefault() // 또는 "UTC"로 설정

    val request: AggregateRequest = AggregateRequest.Builder()
      .setDataType(HealthConstants.StepCount.HEALTH_DATA_TYPE)
      .addFunction(AggregateFunction.SUM, HealthConstants.StepCount.COUNT, "sum_steps")
      .setTimeGroup(AggregateRequest.TimeGroupUnit.HOURLY, 1, HealthConstants.StepCount.START_TIME, "hour")
      .setLocalTimeRange(HealthConstants.StepCount.START_TIME, HealthConstants.StepCount.TIME_OFFSET, start, end)
      .build()

    val resolver = HealthDataResolver(mStore, null)
    val hourlyStepList = mutableListOf<Pair<Long, Int>>()  // <timestamp, avg_hr>

    resolver.aggregate(request).setResultListener { dataResult ->
      for (data in dataResult) {
        val timeStr = data.getString("hour") ?: continue// "yyyy-MM-dd HH" 형식의 문자열
        val steps = data.getInt("sum_steps")
        val timestamp = try {
          sdf.parse(timeStr)?.time ?: continue
        } catch (e: Exception) {
          Log.e(APP_TAG, "날짜 파싱 오류: $timeStr", e)
          continue
        }

        Log.d(APP_TAG, "1시간 데이터 → 시간: $timestamp ($timeStr), 누적 걸음수: $steps")
        hourlyStepList.add(timestamp to steps)
      }
      result.success(hourlyStepList)
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