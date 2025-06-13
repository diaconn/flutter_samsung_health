package com.example.flutter_samsung_health

import android.app.Activity
import android.content.Context
import android.util.Log
import com.samsung.android.sdk.healthdata.HealthConnectionErrorResult
import com.samsung.android.sdk.healthdata.HealthConstants
import com.samsung.android.sdk.healthdata.HealthConstants.Exercise
import com.samsung.android.sdk.healthdata.HealthConstants.HeartRate
import com.samsung.android.sdk.healthdata.HealthConstants.Sleep
import com.samsung.android.sdk.healthdata.HealthConstants.SleepStage
import com.samsung.android.sdk.healthdata.HealthConstants.StepCount
import com.samsung.android.sdk.healthdata.HealthConstants.Nutrition
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

/** FlutterSamsungHealth */
class FlutterSamsungHealth: FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private lateinit var context: Context
  private val APP_TAG : String = "FlutterSamsungHealth"
  private lateinit var mStore : HealthDataStore
  private val permissions = setOf(
    PermissionKey(Exercise.HEALTH_DATA_TYPE, PermissionType.READ),
    PermissionKey(StepCount.HEALTH_DATA_TYPE, PermissionType.READ),
    PermissionKey(HeartRate.HEALTH_DATA_TYPE, PermissionType.READ),
    PermissionKey(Sleep.HEALTH_DATA_TYPE, PermissionType.READ),
    PermissionKey(SleepStage.HEALTH_DATA_TYPE, PermissionType.READ),
    PermissionKey(Nutrition.HEALTH_DATA_TYPE, PermissionType.READ)
  )
  private var activity: Activity? = null

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_samsung_health")
    channel.setMethodCallHandler(this)
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {

      "connect" -> {
        connectSamsungHealth(result)
      }
      "getHeartRate5minSeries" -> {
        val start = call.argument<Long>("start")!!
        val end = call.argument<Long>("end")!!
        val permission = PermissionKey(HeartRate.HEALTH_DATA_TYPE, PermissionType.READ)
        checkPermissionAndExecute(permission,
          onGranted = {
            getHeartRate5minSeries(start, end, result)
          },
          onDenied = {
            requestPermission(result)
          }
        )
      }
      "getHeartRateData" -> {
        val start = call.argument<Long>("start")!!
        val end = call.argument<Long>("end")!!
        val permission = PermissionKey(HeartRate.HEALTH_DATA_TYPE, PermissionType.READ)
        checkPermissionAndExecute(permission,
          onGranted = {
            getHeartRateData(start, end, result)
          },
          onDenied = {
            requestPermission(result)
          }
        )
      }
      "getExerciseSessions" -> {
        val start = call.argument<Long>("start")!!
        val end = call.argument<Long>("end")!!
        val permission = PermissionKey(Exercise.HEALTH_DATA_TYPE, PermissionType.READ)
        checkPermissionAndExecute(permission,
          onGranted = {
            getExerciseData(start, end, result)
          },
          onDenied = {
            requestPermission(result)
          }
        )
      }
      "getStepCountSeries" -> {
        val start = call.argument<Long>("start")!!
        val end = call.argument<Long>("end")!!
        val permission = PermissionKey(StepCount.HEALTH_DATA_TYPE, PermissionType.READ)
        checkPermissionAndExecute(permission,
          onGranted = {
            getStepCountData(start, end, result)
          },
          onDenied = {
            requestPermission(result)
          }
        )
      }
      "getSleepData" -> {
        val start = call.argument<Long>("start")!!
        val end = call.argument<Long>("end")!!
        val permission = PermissionKey(Sleep.HEALTH_DATA_TYPE, PermissionType.READ)
        checkPermissionAndExecute(permission,
          onGranted = {
            getSleepData(start, end, result)
          },
          onDenied = {
            requestPermission(result)
            //result.error("PERMISSION_DENIED", "수면 권한이 없습니다.", null)
          }
        )
      }
      "getNutritionData" -> {
        val start = call.argument<Long>("start")!!
        val end = call.argument<Long>("end")!!
        val permission = PermissionKey(Nutrition.HEALTH_DATA_TYPE, PermissionType.READ)
        checkPermissionAndExecute(permission,
          onGranted = {
            getNutritionData(start, end, result)
          },
          onDenied = {
            requestPermission(result)
//            result.error("PERMISSION_DENIED", "심박수 권한이 없습니다.", null)
          }
        )
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
//        if (!isPermissionAcquired(result)) {
//          requestPermission(result)
//        }
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
    val request: AggregateRequest = AggregateRequest.Builder()
      .setDataType(HealthConstants.HeartRate.HEALTH_DATA_TYPE)
      .addFunction(AggregateFunction.AVG, HealthConstants.HeartRate.HEART_RATE, "avg_hr")
      .setTimeGroup(AggregateRequest.TimeGroupUnit.MINUTELY, 5, HealthConstants.HeartRate.START_TIME, HealthConstants.HeartRate.TIME_OFFSET, "minute")
      .setLocalTimeRange(HealthConstants.HeartRate.START_TIME, HealthConstants.HeartRate.TIME_OFFSET, start, end)
      .setSort(HealthConstants.HeartRate.START_TIME, HealthDataResolver.SortOrder.DESC)
      .build()

    resolver.aggregate(request).setResultListener { readResult ->
      Log.d(APP_TAG, "5분 단위 집계 결과 수: ${readResult.count}")
      val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
      sdf.timeZone = TimeZone.getDefault() // 또는 "UTC"로 설정
      val heartRateList = mutableListOf<Map<String, Any>>()  // <timestamp, avg_hr>
      for (data in readResult) {
        val avgHr = data.getFloat("avg_hr").toDouble()
        val timeStr = data.getString("minute") ?: continue
        val timestamp = try {
          sdf.parse(timeStr)?.time ?: continue
        } catch (e: Exception) {
          Log.e(APP_TAG, "날짜 파싱 오류: $timeStr", e)
          continue
        }
        Log.d(APP_TAG, "5분 데이터 → 시간: $timestamp ($timeStr), 평균 심박수: $avgHr")
        heartRateList.add(mapOf("timestamp" to timestamp, "time_str" to timeStr, "avg_hr" to avgHr))
      }
      result.success(heartRateList)
    }
  }

  /**
   * 심박수 일반 요청
   */
  private fun getHeartRateData(start: Long, end: Long, result: MethodChannel.Result) {
    Log.d(APP_TAG, "getHeartRateData 호출")
    val heartRateRequest = ReadRequest.Builder()
      .setDataType(HealthConstants.HeartRate.HEALTH_DATA_TYPE)
      .setLocalTimeRange(HealthConstants.HeartRate.START_TIME, HealthConstants.HeartRate.TIME_OFFSET, start, end)
      .setSort(HealthConstants.HeartRate.START_TIME, HealthDataResolver.SortOrder.DESC)
      .setProperties(
        arrayOf(
          HealthConstants.HeartRate.START_TIME,
          HealthConstants.HeartRate.END_TIME,
          HealthConstants.HeartRate.TIME_OFFSET,
          HealthConstants.HeartRate.HEART_RATE,
          HealthConstants.HeartRate.HEART_BEAT_COUNT,
          HealthConstants.HeartRate.MIN,
          HealthConstants.HeartRate.MAX,
          HealthConstants.HeartRate.BINNING_DATA
        ))
      .build()

    val resolver = HealthDataResolver(mStore, null)
    resolver.read(heartRateRequest).setResultListener { readResult ->
      Log.d(APP_TAG, "심박 요청 결과 수: ${readResult.count}")
      val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
      sdf.timeZone = TimeZone.getDefault() // 또는 "UTC"로 설정
      val heartRateList = mutableListOf<Map<String, Any>>()  // <timestamp, avg_hr>
      for (data in readResult) {
        heartRateList.add(
          mapOf(
            "start_time" to data.getLong(HealthConstants.HeartRate.START_TIME),
            "end_time" to data.getLong(HealthConstants.HeartRate.END_TIME),
            "time_offset" to data.getLong(HealthConstants.HeartRate.TIME_OFFSET),
            "heart_rate" to data.getFloat(HealthConstants.HeartRate.HEART_RATE),
            "heart_beat_count" to data.getLong(HealthConstants.HeartRate.HEART_BEAT_COUNT),
            "min" to data.getFloat(HealthConstants.HeartRate.MIN),
            "max" to data.getFloat(HealthConstants.HeartRate.MAX)
          )
        )
      }
      result.success(heartRateList)
    }
  }

  /**
   * 운동조회
   */
  private fun getExerciseData(start: Long, end: Long, result: MethodChannel.Result) {
    val exerciseRequest = ReadRequest.Builder()
      .setDataType(HealthConstants.Exercise.HEALTH_DATA_TYPE)
      .setLocalTimeRange(HealthConstants.Exercise.START_TIME, HealthConstants.Exercise.TIME_OFFSET, start, end)
      .setSort(HealthConstants.Exercise.START_TIME, HealthDataResolver.SortOrder.DESC)
      .setProperties(
        arrayOf(
          HealthConstants.Exercise.EXERCISE_TYPE,
          HealthConstants.Exercise.START_TIME,
          HealthConstants.Exercise.END_TIME,
          HealthConstants.Exercise.DURATION,
          HealthConstants.Exercise.DISTANCE,
          HealthConstants.Exercise.CALORIE,
          HealthConstants.Exercise.MAX_HEART_RATE,
          HealthConstants.Exercise.MEAN_HEART_RATE,
          HealthConstants.Exercise.MIN_HEART_RATE,
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
            "exercise_type" to ExerciseTypeMapper.getName(it.getInt(HealthConstants.Exercise.EXERCISE_TYPE)),
            "exercise_type_name" to ExerciseTypeMapper.getName(it.getInt(HealthConstants.Exercise.EXERCISE_TYPE)),
            "start_time" to it.getLong(HealthConstants.Exercise.START_TIME),
            "end_time" to it.getLong(HealthConstants.Exercise.END_TIME),
            "duration" to it.getLong(HealthConstants.Exercise.DURATION),
            "distance" to it.getFloat(HealthConstants.Exercise.DISTANCE),
            "calories" to it.getFloat(HealthConstants.Exercise.CALORIE),
            "max_heart_rate" to it.getFloat(HealthConstants.Exercise.MAX_HEART_RATE),
            "mean_heart_rate" to it.getFloat(HealthConstants.Exercise.MEAN_HEART_RATE),
            "min_heart_rate" to it.getFloat(HealthConstants.Exercise.MIN_HEART_RATE),
            "liveData" to it.getString(HealthConstants.Exercise.LIVE_DATA)
          )
        )
      }
      Log.d(APP_TAG, "운동조회 결과. $exercisesList")
      result.success(exercisesList)
    }
  }

  /**
   * 걷기정보 집계조회
   */
  private fun getStepCountData(start: Long, end: Long, result: MethodChannel.Result) {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    sdf.timeZone = TimeZone.getDefault() // 또는 "UTC"로 설정
    val request: AggregateRequest = AggregateRequest.Builder()
      .setDataType(StepCount.HEALTH_DATA_TYPE)
      .addFunction(AggregateFunction.SUM, StepCount.COUNT, "total_step")
      .addFunction(AggregateFunction.SUM, StepCount.CALORIE, "total_calorie")
      .addFunction(AggregateFunction.SUM, StepCount.DISTANCE, "total_distance")
      .addFunction(AggregateFunction.AVG, StepCount.SPEED, "avg_speed")
      .setLocalTimeRange(StepCount.START_TIME, StepCount.TIME_OFFSET, start, end)
      .setTimeGroup(AggregateRequest.TimeGroupUnit.MINUTELY, 5, HealthConstants.StepCount.START_TIME, StepCount.TIME_OFFSET, "minute")
      .setSort(HealthConstants.StepCount.START_TIME, HealthDataResolver.SortOrder.DESC)
      .build()
    val resolver = HealthDataResolver(mStore, null)
    val hourlyStepList = mutableListOf<Map<String, Any>>()
    resolver.aggregate(request).setResultListener { dataResult ->
      for (data in dataResult) {
        val timeStr = data.getString("minute") ?: continue// "yyyy-MM-dd HH:min" 형식의 문자열
        val steps = data.getInt("total_step")
        val calorie = data.getFloat("total_calorie")
        val distance = data.getFloat("total_distance")
        val speed = data.getFloat("avg_speed")
        val timestamp = try {
          sdf.parse(timeStr)?.time ?: continue
        } catch (e: Exception) {
          Log.e(APP_TAG, "날짜 파싱 오류: $timeStr", e)
          continue
        }
        // 수정된 방식 (Flutter에서 이해 가능한 구조로 변환)
        Log.d(APP_TAG, "5분 누적 데이터 → 시간: $timestamp ($timeStr), 누적 걸음수: $steps")
        hourlyStepList.add(mapOf("timestamp" to timestamp, "time_str" to timeStr, "steps" to steps, "calorie" to calorie, "distance" to distance, "speed" to speed))
      }
      result.success(hourlyStepList)
    }
  }

  /**
   * 수면단계정보 조회
   */
  private fun getSleepStageData(start: Long, end: Long, result: MethodChannel.Result) {
    val request = ReadRequest.Builder()
      .setDataType(SleepStage.HEALTH_DATA_TYPE)
      .setLocalTimeRange(SleepStage.START_TIME, SleepStage.TIME_OFFSET, start, end)
      .setProperties(
        arrayOf(
          HealthConstants.SleepStage.START_TIME,
          HealthConstants.SleepStage.END_TIME,
          HealthConstants.SleepStage.TIME_OFFSET,
          HealthConstants.SleepStage.SLEEP_ID,
          HealthConstants.SleepStage.STAGE
        ))
      .setSort(HealthConstants.SleepStage.START_TIME, HealthDataResolver.SortOrder.DESC)
      .build()
    val resolver = HealthDataResolver(mStore, null)
    val sleepList = mutableListOf<Map<String, Any>>()
    resolver.read(request).setResultListener { dataResult ->
      for (data in dataResult) {
        sleepList.add(
          mapOf(
            "start_time" to data.getLong(HealthConstants.SleepStage.START_TIME),
            "end_time" to data.getLong(HealthConstants.SleepStage.END_TIME),
            "time_offset" to data.getLong(HealthConstants.SleepStage.TIME_OFFSET),
            "sleep_id" to data.getString(HealthConstants.SleepStage.SLEEP_ID),
            "stage" to data.getInt(HealthConstants.SleepStage.STAGE),
            "stage_type_name" to SleepTypeMapper.getName(data.getInt(HealthConstants.SleepStage.STAGE))
          )
        )
      }
      result.success(sleepList)
    }
  }

  /**
   * 수면정보 조회
   */
  private fun getSleepData(start: Long, end: Long, result: MethodChannel.Result) {
    val request = ReadRequest.Builder()
      .setDataType(Sleep.HEALTH_DATA_TYPE)
      .setLocalTimeRange(Sleep.START_TIME, Sleep.TIME_OFFSET, start, end)
      .setProperties(
        arrayOf(
          HealthConstants.Sleep.UUID,
          HealthConstants.Sleep.START_TIME,
          HealthConstants.Sleep.END_TIME,
          HealthConstants.Sleep.TIME_OFFSET,
        ))
      .setSort(HealthConstants.Sleep.START_TIME, HealthDataResolver.SortOrder.DESC)
      .build()
    val resolver = HealthDataResolver(mStore, null)
    val sleepList = mutableListOf<Map<String, Any>>()
    resolver.read(request).setResultListener { dataResult ->
      for (data in dataResult) {
        sleepList.add(
          mapOf(
            "id" to data.getLong(HealthConstants.Sleep.UUID),
            "start_time" to data.getLong(HealthConstants.Sleep.START_TIME),
            "end_time" to data.getLong(HealthConstants.Sleep.END_TIME),
            "time_offset" to data.getLong(HealthConstants.Sleep.TIME_OFFSET),
          )
        )
      }
      result.success(sleepList)
    }
  }

  /**
   * 식사 영양소 정보 조회
   */
  private fun getNutritionData(start: Long, end: Long, result: MethodChannel.Result) {
    val request = ReadRequest.Builder()
      .setDataType(Nutrition.HEALTH_DATA_TYPE)
      .setProperties(
        arrayOf(
          HealthConstants.Nutrition.START_TIME,
          HealthConstants.Nutrition.TIME_OFFSET,
          HealthConstants.Nutrition.MEAL_TYPE,
          HealthConstants.Nutrition.CALORIE,
          HealthConstants.Nutrition.TITLE,
          HealthConstants.Nutrition.TOTAL_FAT,
          HealthConstants.Nutrition.SATURATED_FAT,
          HealthConstants.Nutrition.POLYSATURATED_FAT,
          HealthConstants.Nutrition.MONOSATURATED_FAT,
          HealthConstants.Nutrition.TRANS_FAT,
          HealthConstants.Nutrition.CARBOHYDRATE,
          HealthConstants.Nutrition.DIETARY_FIBER,
          HealthConstants.Nutrition.SUGAR,
          HealthConstants.Nutrition.PROTEIN,
          HealthConstants.Nutrition.CHOLESTEROL,
          HealthConstants.Nutrition.SODIUM,
          HealthConstants.Nutrition.POTASSIUM,
          HealthConstants.Nutrition.VITAMIN_A,
          HealthConstants.Nutrition.VITAMIN_C,
          HealthConstants.Nutrition.CALCIUM,
          HealthConstants.Nutrition.IRON
        ))
      .setSort(HealthConstants.Nutrition.START_TIME, HealthDataResolver.SortOrder.DESC)
      .build()

    val resolver = HealthDataResolver(mStore, null)
    val nutritionList = mutableListOf<Map<String, Any>>()

    resolver.read(request).setResultListener { dataResult ->
      for (data in dataResult) {
        nutritionList.add(
          mapOf(
            "start_time" to data.getLong(HealthConstants.Nutrition.START_TIME),
            "time_offset" to data.getLong(HealthConstants.Nutrition.TIME_OFFSET),
            "meal_type" to data.getString(HealthConstants.Nutrition.MEAL_TYPE),
            "meal_type_name" to MealTypeMapper.getName(data.getInt(HealthConstants.Nutrition.MEAL_TYPE)),
            "calorie" to data.getString(HealthConstants.Nutrition.CALORIE),
            "title" to data.getString(HealthConstants.Nutrition.TITLE),
            "total_fat" to data.getString(HealthConstants.Nutrition.TOTAL_FAT),
            "saturated_fat" to data.getString(HealthConstants.Nutrition.SATURATED_FAT),
            "polysaturated_fat" to data.getString(HealthConstants.Nutrition.POLYSATURATED_FAT),
            "monosaturated_fat" to data.getString(HealthConstants.Nutrition.MONOSATURATED_FAT),
            "trans_fat" to data.getString(HealthConstants.Nutrition.TRANS_FAT),
            "carbohydrate" to data.getString(HealthConstants.Nutrition.CARBOHYDRATE),
            "dietary_fiber" to data.getString(HealthConstants.Nutrition.DIETARY_FIBER),
            "sugar" to data.getString(HealthConstants.Nutrition.SUGAR),
            "protein" to data.getString(HealthConstants.Nutrition.PROTEIN),
            "cholesterol" to data.getString(HealthConstants.Nutrition.CHOLESTEROL),
            "sodium" to data.getString(HealthConstants.Nutrition.SODIUM),
            "potassium" to data.getString(HealthConstants.Nutrition.POTASSIUM),
            "vitamin_a" to data.getString(HealthConstants.Nutrition.VITAMIN_A),
            "vitamin_c" to data.getString(HealthConstants.Nutrition.VITAMIN_C),
            "calcium" to data.getString(HealthConstants.Nutrition.CALCIUM),
            "iron" to data.getString(HealthConstants.Nutrition.IRON)
            )
          )
      }
      result.success(nutritionList)
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

  private fun checkPermissionAndExecute(
    permissionKey: PermissionKey,
    onGranted: () -> Unit,
    onDenied: () -> Unit
  ) {
    val permissionManager = HealthPermissionManager(mStore)
    try {
      val resultMap = permissionManager.isPermissionAcquired(setOf(permissionKey))
      if (resultMap[permissionKey] == true) {
        onGranted()
      } else {
        onDenied()
      }
    } catch (e: Exception) {
      Log.e(APP_TAG, "권한 확인 중 오류", e)
      onDenied()
    }
  }

}

object ExerciseTypeMapper {
    private val typeMap = mapOf(
        1001 to "Walking",
        1002 to "Running",
        1003 to "Cycling",
        1004 to "Hiking",
        1005 to "Mountain Biking",
        1006 to "Treadmill",
        1007 to "Elliptical Trainer",
        1008 to "Pilates",
        1009 to "Rowing Machine",
        1010 to "Spinning",
        1011 to "Stair Climber",
        1012 to "Yoga",
        1013 to "Aerobics",
        1014 to "Dancing",
        1015 to "Strength Training",
        1016 to "Swimming",
        1017 to "Circuit Training",
        1018 to "Other"
    )

    fun getName(type: Int): String {
        return typeMap[type] ?: "Unknown"
    }
}

object SleepTypeMapper {
  private val map = mapOf(
    40001 to "Awake",
    40002 to "Light",
    40003 to "Deep",
    40004 to "REM"
  )

  fun getName(type: Int): String {
    return map[type] ?: "Unknown"
  }
}

object MealTypeMapper {
  private val map = mapOf(
    100001 to "Breakfast",
    100002 to "Lunch",
    100003 to "Dinner",
    100004 to "Morning Snack",
    100005 to "Afternoon Snack",
    100006 to "Evening Snack"
  )

  fun getName(type: Int): String {
    return map[type] ?: "Unknown"
  }
}