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
    private fun _getPermissionForType(type: String): Permission? {
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
    private fun _getDataTypeNameForPermission(permission: Permission): String {
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

            "getStepsData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                getStepsData(start, end, wrapper)
            }

            "getFiveMinuteStepsData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                getFiveMinuteStepsData(start, end, wrapper)
            }

            "getNutritionData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                getNutritionData(start, end, wrapper)
            }

            "getBodyCompositionData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                getBodyCompositionData(start, end, wrapper)
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

            "openSamsungHealthPermissions" -> openSamsungHealthPermissions(wrapper)
            else -> wrapper.notImplemented()
        }
    }

    private fun isSamsungHealthInstalled(wrapper: ResultWrapper) {
        Log.d(APP_TAG, "isSamsungHealthInstalled() 호출")
        val resultMap = mutableMapOf<String, Any>()
        try {
            val packageInfo = context.packageManager.getPackageInfo("com.sec.android.app.shealth", 0)
            resultMap["isInstalled"] = true
            resultMap["versionName"] = packageInfo.versionName ?: "Unknown"
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

    private fun openSamsungHealthPermissions(wrapper: ResultWrapper) {
        Log.d(APP_TAG, "openSamsungHealthPermissions() 호출")
        val resultMap = mutableMapOf<String, Any>()

        runCatching {
            val packageName = "com.sec.android.app.shealth"

            // Samsung Health 권한 설정 화면으로 이동하는 Intent
            val intent = Intent().apply {
                action = "android.intent.action.VIEW"
                data = Uri.parse("samsunghealth://permissions")
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Intent가 해결 가능한지 확인
            val packageManager = context.packageManager
            if (intent.resolveActivity(packageManager) != null) {
                context.startActivity(intent)
                resultMap["action"] = "opened_permissions"
                resultMap["message"] = "Samsung Health 권한 설정 화면을 열었습니다"
            } else {
                // 일반 Samsung Health 앱 실행
                val generalIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (generalIntent != null) {
                    generalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(generalIntent)
                    resultMap["action"] = "opened_app"
                    resultMap["message"] = "Samsung Health 앱을 열었습니다. 설정에서 권한을 확인하세요"
                } else {
                    resultMap["action"] = "app_not_found"
                    resultMap["message"] = "Samsung Health 앱이 설치되지 않았습니다"
                }
            }
        }.onSuccess {
            wrapper.success(resultMap)
        }.onFailure { error ->
            Log.e(APP_TAG, "Samsung Health 권한 화면 열기 실패: ${error.message}")
            resultMap["action"] = "failed"
            resultMap["message"] = "권한 화면 열기 실패: ${error.message}"
            wrapper.success(resultMap)
        }
    }

    private fun connect(wrapper: ResultWrapper) {
        Log.d(APP_TAG, "connect() 호출")
        val resultMap = mutableMapOf<String, Any>()

        runCatching {
            healthDataStore = HealthDataService.getStore(context)
        }.onSuccess {
            Log.i(APP_TAG, "Health data service is connected")
            resultMap["isConnect"] = true
            resultMap["message"] = "Connected successfully"
            wrapper.success(resultMap)
        }.onFailure { error ->
            Log.e(APP_TAG, "연결 실패: ${error.message}")

            when (error) {
                is ResolvablePlatformException -> {
                    if (error.hasResolution && activity != null) {
                        Log.i(APP_TAG, "ResolvablePlatformException - attempting to resolve")
                        resultMap["isConnect"] = false
                        resultMap["error"] = "user_action_required"
                        resultMap["message"] = "사용자 액션이 필요합니다. Samsung Health 설정을 확인하세요."
                        resultMap["resolvable"] = true

                        // 비동기적으로 resolve 실행
                        runCatching {
                            error.resolve(activity!!)
                        }.onFailure { resolveError ->
                            Log.e(APP_TAG, "Resolution failed: ${resolveError.message}")
                            resultMap["message"] = "권한 요청 실패: ${resolveError.message}"
                        }
                    } else {
                        resultMap["isConnect"] = false
                        resultMap["error"] = "resolvable_no_activity"
                        resultMap["message"] = "Activity가 없어 권한 요청을 할 수 없습니다."
                        resultMap["resolvable"] = false
                    }
                }

                is HealthDataException -> {
                    resultMap["isConnect"] = false
                    resultMap["error"] = "health_data_exception"
                    resultMap["message"] = error.message ?: "Samsung Health 데이터 오류"
                    resultMap["resolvable"] = false
                }

                else -> {
                    resultMap["isConnect"] = false
                    resultMap["error"] = "unknown"
                    resultMap["message"] = error.message ?: "알 수 없는 오류"
                    resultMap["resolvable"] = false
                }
            }
            wrapper.success(resultMap)
        }
    }

    private fun disconnect(wrapper: ResultWrapper) {
        Log.d(APP_TAG, "disconnect() 호출")
        healthDataStore = null
        val resultMap = mutableMapOf<String, Any>(
            "isConnect" to false,
            "action" to "disconnected",
            "message" to "연결이 해제되었습니다"
        )
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

        if (act.isDestroyed || act.isFinishing) {
            wrapper.error("ACTIVITY_INVALID", "Activity가 유효하지 않습니다", null)
            return
        }

        val permissionsToRequest = if (types.isEmpty()) {
            allPermissions
        } else {
            types.mapNotNull { type ->
                _getPermissionForType(type)
            }.toSet()
        }

        if (permissionsToRequest.isEmpty()) {
            wrapper.success(mapOf("granted" to emptyMap<String, Boolean>(), "message" to "요청할 권한이 없습니다"))
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            runCatching {
                // 항상 권한 다이얼로그 표시
                store.requestPermissions(permissionsToRequest, act)
                kotlinx.coroutines.delay(1500) // 권한 다이얼로그 처리 대기

                val finalGrantedPermissions = store.getGrantedPermissions(permissionsToRequest)
                val grantedMap = permissionsToRequest.associate {
                    _getDataTypeNameForPermission(it) to finalGrantedPermissions.contains(it)
                }

                val response = mutableMapOf<String, Any>("granted" to grantedMap)
                val deniedList = permissionsToRequest
                    .filter { !finalGrantedPermissions.contains(it) }
                    .map { _getDataTypeNameForPermission(it) }

                if (deniedList.isNotEmpty()) {
                    response["denied_permissions"] = deniedList
                    response["message"] = "일부 권한이 거부되었습니다"
                } else {
                    response["message"] = "모든 권한 허용됨"
                }

                wrapper.success(response)
            }.onFailure { error ->
                Log.e(APP_TAG, "권한 요청 실패: ${error.message}")

                when (error) {
                    is ResolvablePlatformException -> {
                        if (error.hasResolution) {
                            runCatching { error.resolve(act) }
                        }
                        wrapper.error("PERMISSION_RESOLVABLE_ERROR", "사용자 액션이 필요합니다: ${error.message}", null)
                    }

                    is HealthDataException -> {
                        wrapper.error("PERMISSION_HEALTH_DATA_ERROR", "Samsung Health 오류: ${error.message}", null)
                    }

                    else -> {
                        wrapper.error("PERMISSION_ERROR", "권한 요청 실패: ${error.message}", null)
                    }
                }
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
            runCatching {
                val grantedPermissions = store.getGrantedPermissions(allPermissions)
                val grantedList = grantedPermissions.map { _getDataTypeNameForPermission(it) }
                Log.i(APP_TAG, "권한 조회 성공: ${grantedList.size}개 권한 허용됨")
                wrapper.success(grantedList)
            }.onFailure { error ->
                Log.e(APP_TAG, "권한 조회 실패: ${error.message}", error)

                when (error) {
                    is HealthDataException -> {
                        wrapper.error("PERMISSION_QUERY_HEALTH_DATA_ERROR", "Samsung Health 오류: ${error.message}", null)
                    }

                    else -> {
                        wrapper.error("PERMISSION_QUERY_ERROR", "권한 조회 실패: ${error.message}", null)
                    }
                }
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
            runCatching {
                Log.i(APP_TAG, "전체 데이터 조회 시작: ${startTime} ~ ${endTime}")

                // 각 데이터 타입별로 runCatching을 사용하여 개별 실패를 처리
                val exercise = runCatching { readExerciseData(store, startTime, endTime) }
                    .onFailure { Log.w(APP_TAG, "Exercise 데이터 조회 실패: ${it.message}") }
                    .getOrElse { emptyList() }

                val heartRate = runCatching { readHeartRateData(store, startTime, endTime) }
                    .onFailure { Log.w(APP_TAG, "HeartRate 데이터 조회 실패: ${it.message}") }
                    .getOrElse { emptyList() }

                val sleep = runCatching { readSleepData(store, startTime, endTime) }
                    .onFailure { Log.w(APP_TAG, "Sleep 데이터 조회 실패: ${it.message}") }
                    .getOrElse { emptyList() }

                val steps = runCatching { readStepsData(store, startTime, endTime) }
                    .onFailure { Log.w(APP_TAG, "Steps 데이터 조회 실패: ${it.message}") }
                    .getOrElse { emptyList() }

                val nutrition = runCatching { readNutritionData(store, startTime, endTime) }
                    .onFailure { Log.w(APP_TAG, "Nutrition 데이터 조회 실패: ${it.message}") }
                    .getOrElse { emptyList() }

                val bodyComposition = runCatching { readBodyCompositionData(store, startTime, endTime) }
                    .onFailure { Log.w(APP_TAG, "BodyComposition 데이터 조회 실패: ${it.message}") }
                    .getOrElse { emptyList() }

                val bloodOxygen = runCatching { readOxygenSaturationData(store, startTime, endTime) }
                    .onFailure { Log.w(APP_TAG, "BloodOxygen 데이터 조회 실패: ${it.message}") }
                    .getOrElse { emptyList() }

                val bodyTemperature = runCatching { readBodyTemperatureData(store, startTime, endTime) }
                    .onFailure { Log.w(APP_TAG, "BodyTemp 데이터 조회 실패: ${it.message}") }
                    .getOrElse { emptyList() }

                val bloodGlucose = runCatching { readBloodGlucoseData(store, startTime, endTime) }
                    .onFailure { Log.w(APP_TAG, "BloodGlucose 데이터 조회 실패: ${it.message}") }
                    .getOrElse { emptyList() }

                val totalResult = mapOf(
                    "exercise" to exercise,
                    "heart_rate" to heartRate,
                    "sleep" to sleep,
                    "step_count" to steps,
                    "nutrition" to nutrition,
                    "body_composition" to bodyComposition,
                    "oxygen_saturation" to bloodOxygen,
                    "body_temperature" to bodyTemperature,
                    "blood_glucose" to bloodGlucose,
                )

                Log.i(APP_TAG, "전체 데이터 조회 완료")
                totalResult
            }.onSuccess { result ->
                withContext(Dispatchers.Main) {
                    wrapper.success(result)
                }
            }.onFailure { error ->
                Log.e(APP_TAG, "전체 데이터 조회 실패: ${error.message}", error)
                withContext(Dispatchers.Main) {
                    when (error) {
                        is HealthDataException -> {
                            wrapper.error("TOTAL_DATA_HEALTH_ERROR", "Samsung Health 오류: ${error.message}", null)
                        }

                        else -> {
                            wrapper.error("TOTAL_DATA_ERROR", "데이터 수집 실패: ${error.message}", null)
                        }
                    }
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

    private fun getStepsData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper) { store, startTime, endTime ->
            readStepsData(store, startTime, endTime)
        }
    }

    private fun getFiveMinuteStepsData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper) { store, startTime, endTime ->
            readFiveMinuteStepsData(store, startTime, endTime)
        }
    }

    private fun getNutritionData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper) { store, startTime, endTime ->
            readNutritionData(store, startTime, endTime)
        }
    }

    private fun getBodyCompositionData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper) { store, startTime, endTime ->
            readBodyCompositionData(store, startTime, endTime)
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
            runCatching {
                val startTime = Instant.ofEpochMilli(start).atZone(ZoneOffset.systemDefault()).toLocalDateTime()
                val endTime = Instant.ofEpochMilli(end).atZone(ZoneOffset.systemDefault()).toLocalDateTime()
                Log.d(APP_TAG, "데이터 읽기: ${startTime} ~ ${endTime}")
                reader(store, startTime, endTime)
            }.onSuccess { data ->
                Log.i(APP_TAG, "데이터 읽기 성공: ${data.size}건")
                withContext(Dispatchers.Main) {
                    wrapper.success(data)
                }
            }.onFailure { error ->
                Log.e(APP_TAG, "데이터 읽기 실패: ${error.message}", error)
                withContext(Dispatchers.Main) {
                    when (error) {
                        is HealthDataException -> {
                            wrapper.error("READ_HEALTH_DATA_ERROR", "Samsung Health 데이터 오류: ${error.message}", null)
                        }

                        is SecurityException -> {
                            wrapper.error("READ_PERMISSION_ERROR", "권한이 없습니다: ${error.message}", null)
                        }

                        else -> {
                            wrapper.error("READ_ERROR", "데이터 읽기 실패: ${error.message}", null)
                        }
                    }
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
        Log.d(APP_TAG, "운동 데이터 조회 시작 - 시작시간: $startTime, 끝시간: $endTime")
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
                val sessionData = mapOf(
                    "uid" to (dataPoint.uid ?: ""),
                    "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                    "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                    "exercise_type" to (exerciseType?.ordinal ?: 0),
                    "exercise_type_name" to (exerciseType?.name ?: "Unknown"),
                    "duration" to (session.duration?.toMillis() ?: 0L),
                    "calories" to (session.calories ?: 0f),
                    "distance" to (session.distance ?: 0f),
                    "max_heart_rate" to (session.maxHeartRate ?: 0f),
                    "mean_heart_rate" to (session.meanHeartRate ?: 0f),
                    "min_heart_rate" to (session.minHeartRate ?: 0f),
                )
                resultList.add(sessionData)
            }
        }
        return resultList
    }

    private suspend fun readHeartRateData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "심박 데이터 조회 시작 - 시작시간: $startTime, 끝시간: $endTime")
        val readRequest = DataTypes.HEART_RATE.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()

        for (dataPoint in result.dataList) {
            val seriesData = dataPoint.getValue(DataType.HeartRateType.SERIES_DATA)

            val dataMap = mutableMapOf<String, Any>(
                "uid" to (dataPoint.uid ?: ""),
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                "heart_rate" to (dataPoint.getValue(DataType.HeartRateType.HEART_RATE) ?: 0f),
                "min_heart_rate" to (dataPoint.getValue(DataType.HeartRateType.MIN_HEART_RATE) ?: 0f),
                "max_heart_rate" to (dataPoint.getValue(DataType.HeartRateType.MAX_HEART_RATE) ?: 0f),
                "series_count" to (seriesData?.size ?: 0)
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
        return resultList
    }

    private suspend fun readSleepData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        val adjustedStartTime = startTime.minusDays(1)
        Log.d(APP_TAG, "수면 데이터 조회 시작 - 조정된 시작시간: $adjustedStartTime, 끝시간: $endTime")

        val readRequest = DataTypes.SLEEP.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(adjustedStartTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()

        for (dataPoint in result.dataList) {
            val sleepSessions = dataPoint.getValue(DataType.SleepType.SESSIONS)
            val totalDuration = dataPoint.getValue(DataType.SleepType.DURATION)

            val sleepMap = mutableMapOf<String, Any>(
                "uid" to (dataPoint.uid ?: ""),
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                "duration" to (totalDuration?.toMillis() ?: 0L),
                "sleep_score" to (dataPoint.getValue(DataType.SleepType.SLEEP_SCORE) ?: 0f),
                "session_count" to (sleepSessions?.size ?: 0)
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
                        "session_start_time" to (session.startTime?.toEpochMilli() ?: 0L),
                        "session_end_time" to (session.endTime?.toEpochMilli() ?: 0L),
                        "session_duration" to (session.duration?.toMillis() ?: 0L),
                        "sleep_session_id" to (session.toString().hashCode()),
                        "stages" to stagesData,
                        "stage_count" to stagesData.size
                    )
                }
                sleepMap["sessions"] = sessionsData
                Log.v(APP_TAG, "수면 세션 데이터 추가: ${sessionsData.size}개")
            }
            resultList.add(sleepMap)
        }
        return resultList
    }

    private suspend fun readStepsData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "걸음 데이터 조회 시작 - 시작시간: $startTime, 끝시간: $endTime")

        // 새 SDK는 집계 데이터 조회 - DataType.StepsType.TOTAL.requestBuilder 사용
        val readRequest = DataType.StepsType.TOTAL.requestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .build()

        val result = store.aggregateData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()

        for (aggregateData in result.dataList) {
            val stepData = mapOf(
                "start_time" to (aggregateData.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (aggregateData.endTime?.toEpochMilli() ?: 0L),
                "steps" to (aggregateData.value ?: 0L),
                "data_type" to "TOTAL_STEPS"
            )
            resultList.add(stepData)
        }
        return resultList
    }

    private suspend fun readFiveMinuteStepsData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "5분 간격 걸음 데이터 조회: $startTime ~ $endTime")
        
        val resultList = mutableListOf<Map<String, Any>>()
        var currentTime = startTime
        
        // 5분씩 증가하면서 구간별 조회
        while (currentTime.isBefore(endTime)) {
            val intervalEnd = currentTime.plusMinutes(5)
            val actualEnd = if (intervalEnd.isAfter(endTime)) endTime else intervalEnd
            
            try {
                // 각 5분 구간의 걸음수 집계
                val readRequest = DataType.StepsType.TOTAL.requestBuilder
                    .setLocalTimeFilter(LocalTimeFilter.of(currentTime, actualEnd))
                    .build()
                
                val result = store.aggregateData(readRequest)
                
                var intervalSteps = 0L
                for (aggregateData in result.dataList) {
                    intervalSteps += aggregateData.value ?: 0L
                }
                
                val stepData = mapOf(
                    "start_time" to currentTime.toEpochSecond(ZoneOffset.systemDefault().rules.getOffset(currentTime)) * 1000,
                    "end_time" to actualEnd.toEpochSecond(ZoneOffset.systemDefault().rules.getOffset(actualEnd)) * 1000,
                    "steps" to intervalSteps,
                    "interval_minutes" to 5,
                    "data_type" to "FIVE_MINUTE_STEPS"
                )
                resultList.add(stepData)
                
            } catch (e: Exception) {
                Log.w(APP_TAG, "5분 구간 걸음수 조회 실패 ($currentTime ~ $actualEnd): ${e.message}")
                // 실패한 구간도 0으로라도 넣어줄지 결정
                val stepData = mapOf(
                    "start_time" to currentTime.toEpochSecond(ZoneOffset.systemDefault().rules.getOffset(currentTime)) * 1000,
                    "end_time" to actualEnd.toEpochSecond(ZoneOffset.systemDefault().rules.getOffset(actualEnd)) * 1000,
                    "steps" to 0L,
                    "interval_minutes" to 5,
                    "data_type" to "FIVE_MINUTE_STEPS",
                    "error" to true
                )
                resultList.add(stepData)
            }
            
            currentTime = currentTime.plusMinutes(5)
        }
        
        Log.d(APP_TAG, "5분 간격 조회 완료: ${resultList.size}개 구간")
        return resultList
    }

    private suspend fun readNutritionData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "영양 데이터 조회 시작 - 시작시간: $startTime, 끝시간: $endTime")
        val readRequest = DataTypes.NUTRITION.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()

        for (dataPoint in result.dataList) {
            val mealType = dataPoint.getValue(DataType.NutritionType.MEAL_TYPE)

            val nutritionData = mapOf(
                "uid" to (dataPoint.uid ?: ""),
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                "title" to (dataPoint.getValue(DataType.NutritionType.TITLE) ?: ""),
                "meal_type" to (mealType?.ordinal ?: 0),
                "meal_type_name" to (mealType?.name ?: "Unknown"),
                "calories" to (dataPoint.getValue(DataType.NutritionType.CALORIES) ?: 0f),
                "total_fat" to (dataPoint.getValue(DataType.NutritionType.TOTAL_FAT) ?: 0f),
                "saturated_fat" to (dataPoint.getValue(DataType.NutritionType.SATURATED_FAT) ?: 0f),
                "trans_fat" to (dataPoint.getValue(DataType.NutritionType.TRANS_FAT) ?: 0f),
                "polysaturated_fat" to (dataPoint.getValue(DataType.NutritionType.POLYSATURATED_FAT) ?: 0f),
                "monosaturated_fat" to (dataPoint.getValue(DataType.NutritionType.MONOSATURATED_FAT) ?: 0f),
                "cholesterol" to (dataPoint.getValue(DataType.NutritionType.CHOLESTEROL) ?: 0f),
                "protein" to (dataPoint.getValue(DataType.NutritionType.PROTEIN) ?: 0f),
                "carbohydrate" to (dataPoint.getValue(DataType.NutritionType.CARBOHYDRATE) ?: 0f),
                "sugar" to (dataPoint.getValue(DataType.NutritionType.SUGAR) ?: 0f),
                "dietary_fiber" to (dataPoint.getValue(DataType.NutritionType.DIETARY_FIBER) ?: 0f),
                "sodium" to (dataPoint.getValue(DataType.NutritionType.SODIUM) ?: 0f),
                "potassium" to (dataPoint.getValue(DataType.NutritionType.POTASSIUM) ?: 0f),
                "calcium" to (dataPoint.getValue(DataType.NutritionType.CALCIUM) ?: 0f),
                "iron" to (dataPoint.getValue(DataType.NutritionType.IRON) ?: 0f),
                "vitamin_a" to (dataPoint.getValue(DataType.NutritionType.VITAMIN_A) ?: 0f),
                "vitamin_c" to (dataPoint.getValue(DataType.NutritionType.VITAMIN_C) ?: 0f),
            )
            resultList.add(nutritionData)
        }
        return resultList
    }

    private suspend fun readBodyCompositionData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "신체 데이터 조회 시작 - 시작시간: $startTime, 끝시간: $endTime")
        val readRequest = DataTypes.BODY_COMPOSITION.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()

        for (dataPoint in result.dataList) {
            val bodyData = mapOf(
                "uid" to (dataPoint.uid ?: ""),
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                "weight" to (dataPoint.getValue(DataType.BodyCompositionType.WEIGHT) ?: 0f),
                "height" to (dataPoint.getValue(DataType.BodyCompositionType.HEIGHT) ?: 0f),
                "body_fat" to (dataPoint.getValue(DataType.BodyCompositionType.BODY_FAT) ?: 0f),
                "skeletal_muscle" to (dataPoint.getValue(DataType.BodyCompositionType.SKELETAL_MUSCLE) ?: 0f),
                "basal_metabolic_rate" to (dataPoint.getValue(DataType.BodyCompositionType.BASAL_METABOLIC_RATE) ?: 0f),
                "muscle_mass" to (dataPoint.getValue(DataType.BodyCompositionType.MUSCLE_MASS) ?: 0f),
                "body_fat_mass" to (dataPoint.getValue(DataType.BodyCompositionType.BODY_FAT_MASS) ?: 0f),
                "fat_free_mass" to (dataPoint.getValue(DataType.BodyCompositionType.FAT_FREE_MASS) ?: 0f),
                "fat_free" to (dataPoint.getValue(DataType.BodyCompositionType.FAT_FREE) ?: 0f),
                "skeletal_muscle_mass" to (dataPoint.getValue(DataType.BodyCompositionType.SKELETAL_MUSCLE_MASS) ?: 0f),
                "total_body_water" to (dataPoint.getValue(DataType.BodyCompositionType.TOTAL_BODY_WATER) ?: 0f),
            )
            resultList.add(bodyData)
        }
        return resultList
    }

    private suspend fun readOxygenSaturationData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "산소포화도 데이터 조회 시작 - 시작시간: $startTime, 끝시간: $endTime")
        val readRequest = DataTypes.BLOOD_OXYGEN.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()

        for (dataPoint in result.dataList) {
            val oxygenData = mapOf(
                "uid" to (dataPoint.uid ?: ""),
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                "oxygen_saturation" to (dataPoint.getValue(DataType.BloodOxygenType.OXYGEN_SATURATION) ?: 0f),
            )
            resultList.add(oxygenData)
        }
        return resultList
    }

    private suspend fun readBodyTemperatureData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "체온 데이터 조회 시작 - 시작시간: $startTime, 끝시간: $endTime")
        val readRequest = DataTypes.BODY_TEMPERATURE.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()

        for (dataPoint in result.dataList) {
            val temperature = dataPoint.getValue(DataType.BodyTemperatureType.BODY_TEMPERATURE) ?: 0f;

            val temperatureData = mapOf(
                "uid" to (dataPoint.uid ?: ""),
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                "temperature" to temperature,
                "temperature_fahrenheit" to ((temperature * 9 / 5) + 32)
            )
            resultList.add(temperatureData)
        }
        return resultList
    }

    private suspend fun readBloodGlucoseData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "혈당 데이터 조회 시작 - 시작시간: $startTime, 끝시간: $endTime")
        val readRequest = DataTypes.BLOOD_GLUCOSE.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()

        for (dataPoint in result.dataList) {
            val glucoseMmol = dataPoint.getValue(DataType.BloodGlucoseType.GLUCOSE_LEVEL) ?: 0f
            val glucoseMgdl = glucoseMmol * 18.018f
            val measurementType = dataPoint.getValue(DataType.BloodGlucoseType.MEASUREMENT_TYPE)
            val mealStatus = dataPoint.getValue(DataType.BloodGlucoseType.MEAL_STATUS)

            val glucoseData = mapOf(
                "uid" to (dataPoint.uid ?: ""),
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                "glucose_mmol" to glucoseMmol,
                "glucose_mgdl" to glucoseMgdl,
                "measurement_type" to (measurementType?.ordinal ?: 0),
                "measurement_type_name" to (measurementType?.name ?: "Unknown"),
                "meal_status" to (mealStatus?.ordinal ?: 0),
                "meal_status_name" to (mealStatus?.name ?: "Unknown"),
            )
            resultList.add(glucoseData)
        }
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
