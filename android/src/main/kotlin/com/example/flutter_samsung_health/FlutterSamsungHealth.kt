package com.example.flutter_samsung_health

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.error.HealthDataException
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** FlutterSamsungHealth - Samsung Health Data SDK 1.0.0 */
class FlutterSamsungHealth : FlutterPlugin, MethodCallHandler, ActivityAware, EventChannel.StreamHandler {

    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var healthDataStore: HealthDataStore? = null
    private var activity: Activity? = null
    private val eventSinks = mutableListOf<EventChannel.EventSink>()

    private val APP_TAG: String = "FlutterSamsungHealth"

    // 권한 매핑 - DataTypes 사용
    private val allPermissions = setOf(
        Permission.of(DataTypes.EXERCISE, AccessType.READ),
        Permission.of(DataTypes.HEART_RATE, AccessType.READ),
        Permission.of(DataTypes.SLEEP, AccessType.READ),
        Permission.of(DataTypes.STEPS, AccessType.READ),
        Permission.of(DataTypes.NUTRITION, AccessType.READ),
        Permission.of(DataTypes.BODY_COMPOSITION, AccessType.READ),
        Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.READ),
        Permission.of(DataTypes.BODY_TEMPERATURE, AccessType.READ),
        Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ),
    )

    // DataType 문자열 → Permission 매핑
    private fun getPermissionForType(type: String): Permission? {
        return when (type) {
            "exercise" -> Permission.of(DataTypes.EXERCISE, AccessType.READ)
            "heart_rate" -> Permission.of(DataTypes.HEART_RATE, AccessType.READ)
            "sleep" -> Permission.of(DataTypes.SLEEP, AccessType.READ)
            "steps" -> Permission.of(DataTypes.STEPS, AccessType.READ)
            "nutrition" -> Permission.of(DataTypes.NUTRITION, AccessType.READ)
            "body_composition" -> Permission.of(DataTypes.BODY_COMPOSITION, AccessType.READ)
            "blood_oxygen" -> Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.READ)
            "body_temperature" -> Permission.of(DataTypes.BODY_TEMPERATURE, AccessType.READ)
            "blood_glucose" -> Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ)
            else -> null
        }
    }

    // Permission → 문자열 타입명 변환
    private fun getDataTypeNameForPermission(permission: Permission): String {
        return when (permission.dataType) {
            DataTypes.EXERCISE -> "exercise"
            DataTypes.HEART_RATE -> "heart_rate"
            DataTypes.SLEEP -> "sleep"
            DataTypes.STEPS -> "steps"
            DataTypes.NUTRITION -> "nutrition"
            DataTypes.BODY_COMPOSITION -> "body_composition"
            DataTypes.BLOOD_OXYGEN -> "blood_oxygen"
            DataTypes.BODY_TEMPERATURE -> "body_temperature"
            DataTypes.BLOOD_GLUCOSE -> "blood_glucose"
            else -> permission.dataType.toString()
        }
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_samsung_health")
        channel.setMethodCallHandler(this)

        val eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "flutter_samsung_health_event")
        eventChannel.setStreamHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        val wrapper = ResultWrapper(result)

        when (call.method) {
            "isSamsungHealthInstalled" -> isSamsungHealthInstalled(wrapper)
            "openSamsungHealth" -> openSamsungHealth(wrapper)
            "connect" -> connect(wrapper)
            "disconnect" -> disconnect(wrapper)
            "requestPermissions" -> {
                val types = call.argument<List<String>>("types") ?: emptyList()
                requestPermissions(types, wrapper)
            }
            "getGrantedPermissions" -> getGrantedPermissions(wrapper)
            "getTotalData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                getTotalData(start, end, wrapper)
            }
            "getExerciseData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                getExerciseData(start, end, wrapper)
            }
            "getHeartRateData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                getHeartRateData(start, end, wrapper)
            }
            "getSleepData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                getSleepData(start, end, wrapper)
            }
            "getStepData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                getStepData(start, end, wrapper)
            }
            "getNutritionData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                getNutritionData(start, end, wrapper)
            }
            "getWeightData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                getWeightData(start, end, wrapper)
            }
            "getOxygenSaturationData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                getOxygenSaturationData(start, end, wrapper)
            }
            "getBodyTemperatureData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                getBodyTemperatureData(start, end, wrapper)
            }
            "getBloodGlucoseData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                getBloodGlucoseData(start, end, wrapper)
            }
            else -> wrapper.notImplemented()
        }
    }

    private fun isSamsungHealthInstalled(wrapper: ResultWrapper) {
        Log.d(APP_TAG, "isSamsungHealthInstalled() 호출")
        val resultMap = mutableMapOf<String, Any>()
        try {
            context.packageManager.getPackageInfo("com.sec.android.app.shealth", 0)
            resultMap["isInstalled"] = true
        } catch (e: Exception) {
            resultMap["isInstalled"] = false
        }
        wrapper.success(resultMap)
    }

    private fun openSamsungHealth(wrapper: ResultWrapper) {
        Log.d(APP_TAG, "openSamsungHealth() 호출")
        val resultMap = mutableMapOf<String, Any>()
        val packageName = "com.sec.android.app.shealth"

        try {
            val packageManager = context.packageManager
            packageManager.getPackageInfo(packageName, 0)

            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                resultMap["action"] = "launched"
            } else {
                resultMap["action"] = "launch_failed"
            }
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=$packageName")
                    setPackage("com.android.vending")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                resultMap["action"] = "move_to_store"
            } catch (e: Exception) {
                resultMap["action"] = "store_failed"
            }
        }
        wrapper.success(resultMap)
    }

    private fun connect(wrapper: ResultWrapper) {
        Log.d(APP_TAG, "connect() 호출")
        val resultMap = mutableMapOf<String, Any>()

        try {
            healthDataStore = HealthDataService.getStore(context)
            Log.d(APP_TAG, "Samsung Health 연결 성공")
            resultMap["isConnect"] = true
            wrapper.success(resultMap)
        } catch (e: ResolvablePlatformException) {
            Log.e(APP_TAG, "연결 실패 (해결 가능): ${e.message}")
            if (e.hasResolution && activity != null) {
                e.resolve(activity!!)
            }
            resultMap["isConnect"] = false
            resultMap["error"] = "resolvable"
            resultMap["message"] = e.message ?: "Unknown error"
            wrapper.success(resultMap)
        } catch (e: HealthDataException) {
            Log.e(APP_TAG, "연결 실패: ${e.message}")
            resultMap["isConnect"] = false
            resultMap["error"] = "health_data_exception"
            resultMap["message"] = e.message ?: "Unknown error"
            wrapper.success(resultMap)
        } catch (e: Exception) {
            Log.e(APP_TAG, "연결 실패: ${e.message}")
            resultMap["isConnect"] = false
            resultMap["error"] = "unknown"
            resultMap["message"] = e.message ?: "Unknown error"
            wrapper.success(resultMap)
        }
    }

    private fun disconnect(wrapper: ResultWrapper) {
        Log.d(APP_TAG, "disconnect() 호출")
        healthDataStore = null
        val resultMap = mutableMapOf<String, Any>("isConnect" to false)
        wrapper.success(resultMap)
    }

    private fun requestPermissions(types: List<String>, wrapper: ResultWrapper) {
        Log.d(APP_TAG, "requestPermissions() 호출: $types")

        val store = healthDataStore
        if (store == null) {
            wrapper.error("STORE_NOT_READY", "Samsung Health에 먼저 연결하세요", null)
            return
        }

        val act = activity
        if (act == null) {
            wrapper.error("ACTIVITY_NOT_READY", "Activity가 없습니다", null)
            return
        }

        val permissionsToRequest = if (types.isEmpty()) {
            allPermissions
        } else {
            types.mapNotNull { getPermissionForType(it) }.toSet()
        }

        if (permissionsToRequest.isEmpty()) {
            wrapper.success(mapOf("granted" to emptyMap<String, Boolean>(), "message" to "요청할 권한이 없습니다"))
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val grantedPermissions = store.getGrantedPermissions(permissionsToRequest)

                if (grantedPermissions.containsAll(permissionsToRequest)) {
                    Log.d(APP_TAG, "모든 권한이 이미 허용됨")
                    val grantedMap = permissionsToRequest.associate {
                        getDataTypeNameForPermission(it) to true
                    }
                    wrapper.success(mapOf("granted" to grantedMap, "message" to "모든 권한 허용됨"))
                } else {
                    store.requestPermissions(permissionsToRequest, act)

                    // 권한 요청 후 다시 확인
                    val newGrantedPermissions = store.getGrantedPermissions(permissionsToRequest)
                    val grantedMap = permissionsToRequest.associate {
                        getDataTypeNameForPermission(it) to newGrantedPermissions.contains(it)
                    }
                    val deniedList = permissionsToRequest
                        .filter { !newGrantedPermissions.contains(it) }
                        .map { getDataTypeNameForPermission(it) }

                    val response = mutableMapOf<String, Any>("granted" to grantedMap)
                    if (deniedList.isNotEmpty()) {
                        response["denied_permissions"] = deniedList
                    } else {
                        response["message"] = "모든 권한 허용됨"
                    }
                    wrapper.success(response)
                }
            } catch (e: Exception) {
                Log.e(APP_TAG, "권한 요청 실패: ${e.message}", e)
                wrapper.error("PERMISSION_ERROR", e.message, null)
            }
        }
    }

    private fun getGrantedPermissions(wrapper: ResultWrapper) {
        Log.d(APP_TAG, "getGrantedPermissions() 호출")

        val store = healthDataStore
        if (store == null) {
            wrapper.error("STORE_NOT_READY", "Samsung Health에 먼저 연결하세요", null)
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val grantedPermissions = store.getGrantedPermissions(allPermissions)
                val grantedList = grantedPermissions.map { getDataTypeNameForPermission(it) }
                wrapper.success(grantedList)
            } catch (e: Exception) {
                Log.e(APP_TAG, "권한 조회 실패: ${e.message}", e)
                wrapper.error("PERMISSION_QUERY_ERROR", e.message, null)
            }
        }
    }

    private fun getTotalData(start: Long, end: Long, wrapper: ResultWrapper) {
        val store = healthDataStore
        if (store == null) {
            wrapper.error("STORE_NOT_READY", "Samsung Health에 먼저 연결하세요", null)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val startTime = Instant.ofEpochMilli(start).atZone(ZoneOffset.systemDefault()).toLocalDateTime()
                val endTime = Instant.ofEpochMilli(end).atZone(ZoneOffset.systemDefault()).toLocalDateTime()

                val exercise = try { readExerciseData(store, startTime, endTime) } catch (e: Exception) { Log.e(APP_TAG, "Exercise error: ${e.message}"); emptyList() }
                val heartRate = try { readHeartRateData(store, startTime, endTime) } catch (e: Exception) { Log.e(APP_TAG, "HeartRate error: ${e.message}"); emptyList() }
                val sleep = try { readSleepData(store, startTime, endTime) } catch (e: Exception) { Log.e(APP_TAG, "Sleep error: ${e.message}"); emptyList() }
                val steps = try { readStepData(store, startTime, endTime) } catch (e: Exception) { Log.e(APP_TAG, "Steps error: ${e.message}"); emptyList() }
                val nutrition = try { readNutritionData(store, startTime, endTime) } catch (e: Exception) { Log.e(APP_TAG, "Nutrition error: ${e.message}"); emptyList() }
                val weight = try { readWeightData(store, startTime, endTime) } catch (e: Exception) { Log.e(APP_TAG, "Weight error: ${e.message}"); emptyList() }
                val bloodOxygen = try { readOxygenSaturationData(store, startTime, endTime) } catch (e: Exception) { Log.e(APP_TAG, "BloodOxygen error: ${e.message}"); emptyList() }
                val bodyTemperature = try { readBodyTemperatureData(store, startTime, endTime) } catch (e: Exception) { Log.e(APP_TAG, "BodyTemp error: ${e.message}"); emptyList() }
                val bloodGlucose = try { readBloodGlucoseData(store, startTime, endTime) } catch (e: Exception) { Log.e(APP_TAG, "BloodGlucose error: ${e.message}"); emptyList() }

                val totalResult = mapOf(
                    "exercise" to exercise,
                    "heart_rate" to heartRate,
                    "sleep" to sleep,
                    "step_count" to steps,
                    "nutrition" to nutrition,
                    "weight" to weight,
                    "oxygen_saturation" to bloodOxygen,
                    "body_temperature" to bodyTemperature,
                    "blood_glucose" to bloodGlucose,
                )

                withContext(Dispatchers.Main) {
                    wrapper.success(totalResult)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    wrapper.error("TOTAL_DATA_ERROR", "데이터 수집 실패: ${e.message}", null)
                }
            }
        }
    }

    private fun getExerciseData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper) { store, startTime, endTime ->
            readExerciseData(store, startTime, endTime)
        }
    }

    private fun getHeartRateData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper) { store, startTime, endTime ->
            readHeartRateData(store, startTime, endTime)
        }
    }

    private fun getSleepData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper) { store, startTime, endTime ->
            readSleepData(store, startTime, endTime)
        }
    }

    private fun getStepData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper) { store, startTime, endTime ->
            readStepData(store, startTime, endTime)
        }
    }

    private fun getNutritionData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper) { store, startTime, endTime ->
            readNutritionData(store, startTime, endTime)
        }
    }

    private fun getWeightData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper) { store, startTime, endTime ->
            readWeightData(store, startTime, endTime)
        }
    }

    private fun getOxygenSaturationData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper) { store, startTime, endTime ->
            readOxygenSaturationData(store, startTime, endTime)
        }
    }

    private fun getBodyTemperatureData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper) { store, startTime, endTime ->
            readBodyTemperatureData(store, startTime, endTime)
        }
    }

    private fun getBloodGlucoseData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper) { store, startTime, endTime ->
            readBloodGlucoseData(store, startTime, endTime)
        }
    }

    private fun readDataWithWrapper(
        start: Long,
        end: Long,
        wrapper: ResultWrapper,
        reader: suspend (HealthDataStore, LocalDateTime, LocalDateTime) -> List<Map<String, Any>>
    ) {
        val store = healthDataStore
        if (store == null) {
            wrapper.error("STORE_NOT_READY", "Samsung Health에 먼저 연결하세요", null)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val startTime = Instant.ofEpochMilli(start).atZone(ZoneOffset.systemDefault()).toLocalDateTime()
                val endTime = Instant.ofEpochMilli(end).atZone(ZoneOffset.systemDefault()).toLocalDateTime()
                val data = reader(store, startTime, endTime)
                withContext(Dispatchers.Main) {
                    wrapper.success(data)
                }
            } catch (e: Exception) {
                Log.e(APP_TAG, "데이터 읽기 실패: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    wrapper.error("READ_ERROR", e.message, null)
                }
            }
        }
    }

    // ===== 데이터 읽기 함수들 =====

    private suspend fun readExerciseData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "운동 데이터 조회 시작")
        val readRequest = DataTypes.EXERCISE.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()

        for (dataPoint in result.dataList) {
            val exerciseType = dataPoint.getValue(DataType.ExerciseType.EXERCISE_TYPE)
            val sessions = dataPoint.getValue(DataType.ExerciseType.SESSIONS)

            sessions?.forEach { session ->
                resultList.add(mapOf(
                    "uid" to (dataPoint.uid ?: ""),
                    "exercise_type" to (exerciseType?.ordinal ?: 0),
                    "exercise_type_name" to (exerciseType?.name ?: "Unknown"),
                    "start_time" to (session.startTime?.toEpochMilli() ?: 0L),
                    "end_time" to (session.endTime?.toEpochMilli() ?: 0L),
                    "duration" to (session.duration?.toMillis() ?: 0L),
                    "calories" to (session.calories ?: 0f),
                    "distance" to (session.distance ?: 0f),
                    "max_heart_rate" to (session.maxHeartRate ?: 0f),
                    "mean_heart_rate" to (session.meanHeartRate ?: 0f),
                    "min_heart_rate" to (session.minHeartRate ?: 0f),
                ))
            }
        }

        Log.d(APP_TAG, "운동 데이터 조회 완료: ${resultList.size}건")
        return resultList
    }

    private suspend fun readHeartRateData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "심박 데이터 조회 시작")
        val readRequest = DataTypes.HEART_RATE.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()

        for (dataPoint in result.dataList) {
            val heartRate = dataPoint.getValue(DataType.HeartRateType.HEART_RATE)
            val seriesData = dataPoint.getValue(DataType.HeartRateType.SERIES_DATA)

            val dataMap = mutableMapOf<String, Any>(
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                "heart_rate" to (heartRate ?: 0f),
            )

            // 시리즈 데이터가 있으면 추가
            seriesData?.let { series ->
                val seriesList = series.map { s ->
                    mapOf(
                        "start_time" to (s.startTime?.toEpochMilli() ?: 0L),
                        "heart_rate" to (s.heartRate ?: 0f)
                    )
                }
                dataMap["series_data"] = seriesList
            }

            resultList.add(dataMap)
        }

        Log.d(APP_TAG, "심박 데이터 조회 완료: ${resultList.size}건")
        return resultList
    }

    private suspend fun readSleepData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "수면 데이터 조회 시작")
        // 수면은 전날 시작될 수 있으므로 하루 전부터 조회
        val adjustedStartTime = startTime.minusDays(1)

        val readRequest = DataTypes.SLEEP.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(adjustedStartTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()

        for (dataPoint in result.dataList) {
            val sleepSessions = dataPoint.getValue(DataType.SleepType.SESSIONS)

            val sleepMap = mutableMapOf<String, Any>(
                "uid" to (dataPoint.uid ?: ""),
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
            )

            // 세션 및 수면 단계 데이터 포함
            sleepSessions?.let { sessions ->
                val sessionsData = sessions.map { session ->
                    val stagesData = session.stages?.map { stage ->
                        mapOf(
                            "stage_type" to (stage.stage?.ordinal ?: 0),
                            "stage_type_name" to (stage.stage?.name ?: "Unknown"),
                            "start_time" to (stage.startTime?.toEpochMilli() ?: 0L),
                            "end_time" to (stage.endTime?.toEpochMilli() ?: 0L),
                        )
                    } ?: emptyList<Map<String, Any>>()

                    mapOf(
                        "start_time" to (session.startTime?.toEpochMilli() ?: 0L),
                        "end_time" to (session.endTime?.toEpochMilli() ?: 0L),
                        "duration" to (session.duration?.toMillis() ?: 0L),
                        "stages" to stagesData
                    )
                }
                sleepMap["sessions"] = sessionsData
            }

            resultList.add(sleepMap)
        }

        Log.d(APP_TAG, "수면 데이터 조회 완료: ${resultList.size}건")
        return resultList
    }

    private suspend fun readStepData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "걸음 데이터 조회 시작")

        // 새 SDK는 집계 데이터 조회 - DataType.StepsType.TOTAL.requestBuilder 사용
        val readRequest = DataType.StepsType.TOTAL.requestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .build()

        val result = store.aggregateData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()

        for (aggregateData in result.dataList) {
            resultList.add(mapOf(
                "start_time" to (aggregateData.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (aggregateData.endTime?.toEpochMilli() ?: 0L),
                "steps" to (aggregateData.value ?: 0L),
            ))
        }

        Log.d(APP_TAG, "걸음 데이터 조회 완료: ${resultList.size}건")
        return resultList
    }

    private suspend fun readNutritionData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "영양 데이터 조회 시작")
        val readRequest = DataTypes.NUTRITION.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()

        for (dataPoint in result.dataList) {
            resultList.add(mapOf(
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "title" to (dataPoint.getValue(DataType.NutritionType.TITLE) ?: ""),
                "meal_type" to (dataPoint.getValue(DataType.NutritionType.MEAL_TYPE)?.ordinal ?: 0),
                "meal_type_name" to (dataPoint.getValue(DataType.NutritionType.MEAL_TYPE)?.name ?: "Unknown"),
                "calories" to (dataPoint.getValue(DataType.NutritionType.CALORIES) ?: 0f),
                "total_fat" to (dataPoint.getValue(DataType.NutritionType.TOTAL_FAT) ?: 0f),
                "saturated_fat" to (dataPoint.getValue(DataType.NutritionType.SATURATED_FAT) ?: 0f),
                "protein" to (dataPoint.getValue(DataType.NutritionType.PROTEIN) ?: 0f),
                "carbohydrate" to (dataPoint.getValue(DataType.NutritionType.CARBOHYDRATE) ?: 0f),
                "sugar" to (dataPoint.getValue(DataType.NutritionType.SUGAR) ?: 0f),
                "dietary_fiber" to (dataPoint.getValue(DataType.NutritionType.DIETARY_FIBER) ?: 0f),
                "sodium" to (dataPoint.getValue(DataType.NutritionType.SODIUM) ?: 0f),
                "potassium" to (dataPoint.getValue(DataType.NutritionType.POTASSIUM) ?: 0f),
                "calcium" to (dataPoint.getValue(DataType.NutritionType.CALCIUM) ?: 0f),
                "iron" to (dataPoint.getValue(DataType.NutritionType.IRON) ?: 0f),
            ))
        }

        Log.d(APP_TAG, "영양 데이터 조회 완료: ${resultList.size}건")
        return resultList
    }

    private suspend fun readWeightData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "체중 데이터 조회 시작")
        val readRequest = DataTypes.BODY_COMPOSITION.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()

        for (dataPoint in result.dataList) {
            resultList.add(mapOf(
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "weight" to (dataPoint.getValue(DataType.BodyCompositionType.WEIGHT) ?: 0f),
                "height" to (dataPoint.getValue(DataType.BodyCompositionType.HEIGHT) ?: 0f),
                "body_fat" to (dataPoint.getValue(DataType.BodyCompositionType.BODY_FAT) ?: 0f),
                "skeletal_muscle" to (dataPoint.getValue(DataType.BodyCompositionType.SKELETAL_MUSCLE) ?: 0f),
                "basal_metabolic_rate" to (dataPoint.getValue(DataType.BodyCompositionType.BASAL_METABOLIC_RATE) ?: 0f),
            ))
        }

        Log.d(APP_TAG, "체중 데이터 조회 완료: ${resultList.size}건")
        return resultList
    }

    private suspend fun readOxygenSaturationData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "산소포화도 데이터 조회 시작")
        val readRequest = DataTypes.BLOOD_OXYGEN.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()

        for (dataPoint in result.dataList) {
            resultList.add(mapOf(
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                "spo2" to (dataPoint.getValue(DataType.BloodOxygenType.OXYGEN_SATURATION) ?: 0f),
            ))
        }

        Log.d(APP_TAG, "산소포화도 데이터 조회 완료: ${resultList.size}건")
        return resultList
    }

    private suspend fun readBodyTemperatureData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "체온 데이터 조회 시작")
        val readRequest = DataTypes.BODY_TEMPERATURE.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()

        for (dataPoint in result.dataList) {
            resultList.add(mapOf(
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "temperature" to (dataPoint.getValue(DataType.BodyTemperatureType.BODY_TEMPERATURE) ?: 0f),
            ))
        }

        Log.d(APP_TAG, "체온 데이터 조회 완료: ${resultList.size}건")
        return resultList
    }

    private suspend fun readBloodGlucoseData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "혈당 데이터 조회 시작")
        val readRequest = DataTypes.BLOOD_GLUCOSE.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()

        for (dataPoint in result.dataList) {
            val glucoseMmol = dataPoint.getValue(DataType.BloodGlucoseType.GLUCOSE_LEVEL) ?: 0f
            val glucoseMgdl = glucoseMmol * 18.018f

            resultList.add(mapOf(
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "glucose_mmol" to glucoseMmol,
                "glucose_mgdl" to glucoseMgdl,
                "measurement_type" to (dataPoint.getValue(DataType.BloodGlucoseType.MEASUREMENT_TYPE)?.ordinal ?: 0),
                "meal_status" to (dataPoint.getValue(DataType.BloodGlucoseType.MEAL_STATUS)?.ordinal ?: 0),
            ))
        }

        Log.d(APP_TAG, "혈당 데이터 조회 완료: ${resultList.size}건")
        return resultList
    }

    // ===== EventChannel 구현 =====

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        events?.let { eventSinks.add(it) }
    }

    override fun onCancel(arguments: Any?) {
        eventSinks.clear()
    }

    // ===== FlutterPlugin 생명주기 =====

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    // ===== ActivityAware 구현 =====

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

/** Result 중복 호출 방지 래퍼 */
class ResultWrapper(private val result: MethodChannel.Result) {
    private var isResultSent = false

    fun success(response: Any?) {
        if (isResultSent) return
        isResultSent = true
        result.success(response)
    }

    fun error(code: String, message: String?, details: Any?) {
        if (isResultSent) return
        isResultSent = true
        result.error(code, message, details)
    }

    fun notImplemented() {
        if (isResultSent) return
        isResultSent = true
        result.notImplemented()
    }
}
