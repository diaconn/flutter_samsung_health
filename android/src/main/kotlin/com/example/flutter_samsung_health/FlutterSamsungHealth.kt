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
                val startTime = Instant.ofEpochMilli(start).atZone(ZoneOffset.systemDefault()).toLocalDateTime()
                val endTime = Instant.ofEpochMilli(end).atZone(ZoneOffset.systemDefault()).toLocalDateTime()
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

                val steps = runCatching { readStepData(store, startTime, endTime) }
                    .onFailure { Log.w(APP_TAG, "Steps 데이터 조회 실패: ${it.message}") }
                    .getOrElse { emptyList() }

                val nutrition = runCatching { readNutritionData(store, startTime, endTime) }
                    .onFailure { Log.w(APP_TAG, "Nutrition 데이터 조회 실패: ${it.message}") }
                    .getOrElse { emptyList() }

                val weight = runCatching { readWeightData(store, startTime, endTime) }
                    .onFailure { Log.w(APP_TAG, "Weight 데이터 조회 실패: ${it.message}") }
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
                    "weight" to weight,
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
            runCatching {
                val startTime = Instant.ofEpochMilli(start).atZone(ZoneOffset.systemDefault()).toLocalDateTime()
                val endTime = Instant.ofEpochMilli(end).atZone(ZoneOffset.systemDefault()).toLocalDateTime()
                Log.i(APP_TAG, "데이터 읽기 시작: ${startTime} ~ ${endTime}")
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
        Log.d(APP_TAG, "운동 데이터 조회 결과: ${result.dataList.size}개 데이터 포인트")

        for (dataPoint in result.dataList) {
            Log.v(APP_TAG, "운동 데이터 포인트 UID: ${dataPoint.uid}")
            val exerciseType = dataPoint.getValue(DataType.ExerciseType.EXERCISE_TYPE)
            val sessions = dataPoint.getValue(DataType.ExerciseType.SESSIONS)
            
            Log.v(APP_TAG, "운동 타입: ${exerciseType?.name} (${exerciseType?.ordinal}), 세션 수: ${sessions?.size ?: 0}")

            sessions?.forEach { session ->
                val sessionData = mapOf(
                    "uid" to (dataPoint.uid ?: ""),
                    "data_point_start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                    "data_point_end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                    "exercise_type" to (exerciseType?.ordinal ?: 0),
                    "exercise_type_name" to (exerciseType?.name ?: "Unknown"),
                    "session_start_time" to (session.startTime?.toEpochMilli() ?: 0L),
                    "session_end_time" to (session.endTime?.toEpochMilli() ?: 0L),
                    "duration" to (session.duration?.toMillis() ?: 0L),
                    "calories" to (session.calories ?: 0f),
                    "distance" to (session.distance ?: 0f),
                    "max_heart_rate" to (session.maxHeartRate ?: 0f),
                    "mean_heart_rate" to (session.meanHeartRate ?: 0f),
                    "min_heart_rate" to (session.minHeartRate ?: 0f),
                    // 추가 필드들은 Samsung Health SDK에서 지원하지 않을 수 있음
                    // "steps" to (session.steps ?: 0L),
                    // "speed" to (session.speed ?: 0f),
                    // "pace" to (session.pace ?: 0f),
                    // "incline" to (session.incline ?: 0f),
                    // "altitude" to (session.altitude ?: 0f),
                    // "decline" to (session.decline ?: 0f),
                    // "floor_climbed" to (session.floorClimbed ?: 0f),
                    // "location_data" to (session.locationData?.toString() ?: "")
                    "available_fields" to "duration,calories,distance,heart_rate_metrics"
                )
                Log.v(APP_TAG, "운동 세션 데이터: ${sessionData}")
                resultList.add(sessionData)
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
        Log.d(APP_TAG, "심박 데이터 조회 시작 - 시작시간: $startTime, 끝시간: $endTime")
        val readRequest = DataTypes.HEART_RATE.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()
        Log.d(APP_TAG, "심박 데이터 조회 결과: ${result.dataList.size}개 데이터 포인트")

        for (dataPoint in result.dataList) {
            Log.v(APP_TAG, "심박 데이터 포인트 UID: ${dataPoint.uid}")
            val heartRate = dataPoint.getValue(DataType.HeartRateType.HEART_RATE)
            val seriesData = dataPoint.getValue(DataType.HeartRateType.SERIES_DATA)
            val minHeartRate = dataPoint.getValue(DataType.HeartRateType.MIN_HEART_RATE)
            val maxHeartRate = dataPoint.getValue(DataType.HeartRateType.MAX_HEART_RATE)
            
            Log.v(APP_TAG, "심박 데이터: HR=${heartRate}, Min=${minHeartRate}, Max=${maxHeartRate}, 시리즈 수=${seriesData?.size ?: 0}")

            val dataMap = mutableMapOf<String, Any>(
                "uid" to (dataPoint.uid ?: ""),
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                "heart_rate" to (heartRate ?: 0f),
                "min_heart_rate" to (minHeartRate ?: 0f),
                "max_heart_rate" to (maxHeartRate ?: 0f),
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
                Log.v(APP_TAG, "시리즈 데이터 추가: ${seriesList.size}개")
            }

            Log.v(APP_TAG, "심박 데이터 맵: ${dataMap}")
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
        val adjustedStartTime = startTime.minusDays(1)
        Log.d(APP_TAG, "수면 데이터 조회 시작 - 조정된 시작시간: $adjustedStartTime, 끝시간: $endTime")

        val readRequest = DataTypes.SLEEP.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(adjustedStartTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()
        Log.d(APP_TAG, "수면 데이터 조회 결과: ${result.dataList.size}개 데이터 포인트")

        for (dataPoint in result.dataList) {
            Log.v(APP_TAG, "수면 데이터 포인트 UID: ${dataPoint.uid}")
            val sleepSessions = dataPoint.getValue(DataType.SleepType.SESSIONS)
            val sleepScore = dataPoint.getValue(DataType.SleepType.SLEEP_SCORE)
            val totalDuration = dataPoint.getValue(DataType.SleepType.DURATION)
            
            Log.v(APP_TAG, "수면 데이터: Score=${sleepScore}, Duration=${totalDuration}, 세션 수=${sleepSessions?.size ?: 0}")

            val sleepMap = mutableMapOf<String, Any>(
                "uid" to (dataPoint.uid ?: ""),
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                "duration" to (totalDuration?.toMillis() ?: 0L),
                "sleep_score" to (sleepScore ?: 0f),
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
                            // "duration" to (stage.duration?.toMillis() ?: 0L) // 지원하지 않는 필드일 수 있음
                            "stage_duration" to 0L
                        )
                    } ?: emptyList<Map<String, Any>>()

                    mapOf(
                        "session_start_time" to (session.startTime?.toEpochMilli() ?: 0L),
                        "session_end_time" to (session.endTime?.toEpochMilli() ?: 0L),
                        "session_duration" to (session.duration?.toMillis() ?: 0L),
                        // "sleep_efficiency" to (session.sleepEfficiency ?: 0f), // 지원하지 않는 필드일 수 있음
                        "sleep_session_id" to (session.toString().hashCode()),
                        "stages" to stagesData,
                        "stage_count" to stagesData.size
                    )
                }
                sleepMap["sessions"] = sessionsData
                Log.v(APP_TAG, "수면 세션 데이터 추가: ${sessionsData.size}개")
            }

            Log.v(APP_TAG, "수면 데이터 맵: ${sleepMap}")
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
        Log.d(APP_TAG, "걸음 데이터 조회 시작 - 시작시간: $startTime, 끝시간: $endTime")

        // 새 SDK는 집계 데이터 조회 - DataType.StepsType.TOTAL.requestBuilder 사용
        val readRequest = DataType.StepsType.TOTAL.requestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .build()

        val result = store.aggregateData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()
        Log.d(APP_TAG, "걸음 집계 데이터 조회 결과: ${result.dataList.size}개 데이터 포인트")

        for (aggregateData in result.dataList) {
            val stepData = mapOf(
                "start_time" to (aggregateData.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (aggregateData.endTime?.toEpochMilli() ?: 0L),
                "steps" to (aggregateData.value ?: 0L),
                "data_type" to "TOTAL_STEPS"
            )
            Log.v(APP_TAG, "걸음 데이터: ${stepData}")
            resultList.add(stepData)
        }

        Log.d(APP_TAG, "걸음 데이터 조회 완료: ${resultList.size}건")
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
        Log.d(APP_TAG, "영양 데이터 조회 결과: ${result.dataList.size}개 데이터 포인트")

        for (dataPoint in result.dataList) {
            Log.v(APP_TAG, "영양 데이터 포인트 UID: ${dataPoint.uid}")
            val title = dataPoint.getValue(DataType.NutritionType.TITLE) ?: ""
            val mealType = dataPoint.getValue(DataType.NutritionType.MEAL_TYPE)
            val calories = dataPoint.getValue(DataType.NutritionType.CALORIES) ?: 0f
            
            Log.v(APP_TAG, "영양 데이터: Title=$title, MealType=${mealType?.name}, Calories=$calories")
            
            val nutritionData = mapOf(
                "uid" to (dataPoint.uid ?: ""),
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                "title" to title,
                "meal_type" to (mealType?.ordinal ?: 0),
                "meal_type_name" to (mealType?.name ?: "Unknown"),
                "calories" to calories,
                "total_fat" to (dataPoint.getValue(DataType.NutritionType.TOTAL_FAT) ?: 0f),
                "saturated_fat" to (dataPoint.getValue(DataType.NutritionType.SATURATED_FAT) ?: 0f),
                // "trans_fat" to (dataPoint.getValue(DataType.NutritionType.TRANS_FAT) ?: 0f),
                // "cholesterol" to (dataPoint.getValue(DataType.NutritionType.CHOLESTEROL) ?: 0f),
                "trans_fat" to 0f,
                "cholesterol" to 0f,
                "protein" to (dataPoint.getValue(DataType.NutritionType.PROTEIN) ?: 0f),
                "carbohydrate" to (dataPoint.getValue(DataType.NutritionType.CARBOHYDRATE) ?: 0f),
                "sugar" to (dataPoint.getValue(DataType.NutritionType.SUGAR) ?: 0f),
                "dietary_fiber" to (dataPoint.getValue(DataType.NutritionType.DIETARY_FIBER) ?: 0f),
                "sodium" to (dataPoint.getValue(DataType.NutritionType.SODIUM) ?: 0f),
                "potassium" to (dataPoint.getValue(DataType.NutritionType.POTASSIUM) ?: 0f),
                "calcium" to (dataPoint.getValue(DataType.NutritionType.CALCIUM) ?: 0f),
                "iron" to (dataPoint.getValue(DataType.NutritionType.IRON) ?: 0f),
                // 비타민 필드들은 Samsung Health SDK에서 지원하지 않을 수 있음
                // "vitamin_a" to (dataPoint.getValue(DataType.NutritionType.VITAMIN_A) ?: 0f),
                // "vitamin_c" to (dataPoint.getValue(DataType.NutritionType.VITAMIN_C) ?: 0f),
                // "vitamin_d" to (dataPoint.getValue(DataType.NutritionType.VITAMIN_D) ?: 0f),
                // "vitamin_e" to (dataPoint.getValue(DataType.NutritionType.VITAMIN_E) ?: 0f),
                // "vitamin_k" to (dataPoint.getValue(DataType.NutritionType.VITAMIN_K) ?: 0f)
                "nutrition_category" to "basic_nutrients"
            )
            
            Log.v(APP_TAG, "영양 데이터 맵: ${nutritionData}")
            resultList.add(nutritionData)
        }

        Log.d(APP_TAG, "영양 데이터 조회 완료: ${resultList.size}건")
        return resultList
    }

    private suspend fun readWeightData(
        store: HealthDataStore,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Map<String, Any>> {
        Log.d(APP_TAG, "체중 데이터 조회 시작 - 시작시간: $startTime, 끝시간: $endTime")
        val readRequest = DataTypes.BODY_COMPOSITION.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .setOrdering(Ordering.DESC)
            .build()

        val result = store.readData(readRequest)
        val resultList = mutableListOf<Map<String, Any>>()
        Log.d(APP_TAG, "체중 데이터 조회 결과: ${result.dataList.size}개 데이터 포인트")

        for (dataPoint in result.dataList) {
            Log.v(APP_TAG, "체중 데이터 포인트 UID: ${dataPoint.uid}")
            val weight = dataPoint.getValue(DataType.BodyCompositionType.WEIGHT) ?: 0f
            val height = dataPoint.getValue(DataType.BodyCompositionType.HEIGHT) ?: 0f
            val bodyFat = dataPoint.getValue(DataType.BodyCompositionType.BODY_FAT) ?: 0f
            
            Log.v(APP_TAG, "체중 데이터: Weight=$weight, Height=$height, BodyFat=$bodyFat")
            
            val bodyData = mapOf(
                "uid" to (dataPoint.uid ?: ""),
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                "weight" to weight,
                "height" to height,
                "body_fat" to bodyFat,
                "skeletal_muscle" to (dataPoint.getValue(DataType.BodyCompositionType.SKELETAL_MUSCLE) ?: 0f),
                "basal_metabolic_rate" to (dataPoint.getValue(DataType.BodyCompositionType.BASAL_METABOLIC_RATE) ?: 0f),
                // 고급 체성분 필드들은 Samsung Health SDK에서 지원하지 않을 수 있음
                // "body_water" to (dataPoint.getValue(DataType.BodyCompositionType.BODY_WATER) ?: 0f),
                // "visceral_fat_level" to (dataPoint.getValue(DataType.BodyCompositionType.VISCERAL_FAT_LEVEL) ?: 0f),
                // "bone_mass" to (dataPoint.getValue(DataType.BodyCompositionType.BONE_MASS) ?: 0f),
                // "muscle_mass" to (dataPoint.getValue(DataType.BodyCompositionType.MUSCLE_MASS) ?: 0f)
                "body_composition_category" to "basic_metrics"
            )
            
            Log.v(APP_TAG, "체중 데이터 맵: ${bodyData}")
            resultList.add(bodyData)
        }

        Log.d(APP_TAG, "체중 데이터 조회 완료: ${resultList.size}건")
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
        Log.d(APP_TAG, "산소포화도 데이터 조회 결과: ${result.dataList.size}개 데이터 포인트")

        for (dataPoint in result.dataList) {
            Log.v(APP_TAG, "산소포화도 데이터 포인트 UID: ${dataPoint.uid}")
            val oxygenSaturation = dataPoint.getValue(DataType.BloodOxygenType.OXYGEN_SATURATION) ?: 0f
            // val heartRate = dataPoint.getValue(DataType.BloodOxygenType.HEART_RATE) ?: 0f // HEART_RATE 필드가 지원되지 않음
            
            Log.v(APP_TAG, "산소포화도 데이터: SpO2=$oxygenSaturation")
            
            val oxygenData = mapOf(
                "uid" to (dataPoint.uid ?: ""),
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                "oxygen_saturation" to oxygenSaturation,
                "spo2" to oxygenSaturation
                // "heart_rate" to heartRate // 산소포화도 데이터에서는 심박수가 지원되지 않을 수 있음
            )
            
            Log.v(APP_TAG, "산소포화도 데이터 맵: ${oxygenData}")
            resultList.add(oxygenData)
        }

        Log.d(APP_TAG, "산소포화도 데이터 조회 완료: ${resultList.size}건")
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
        Log.d(APP_TAG, "체온 데이터 조회 결과: ${result.dataList.size}개 데이터 포인트")

        for (dataPoint in result.dataList) {
            Log.v(APP_TAG, "체온 데이터 포인트 UID: ${dataPoint.uid}")
            val temperature = dataPoint.getValue(DataType.BodyTemperatureType.BODY_TEMPERATURE) ?: 0f
            
            Log.v(APP_TAG, "체온 데이터: Temperature=${temperature}°C")
            
            val temperatureData = mapOf(
                "uid" to (dataPoint.uid ?: ""),
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                "temperature" to temperature,
                "body_temperature" to temperature,
                "temperature_celsius" to temperature,
                "temperature_fahrenheit" to ((temperature * 9 / 5) + 32)
            )
            
            Log.v(APP_TAG, "체온 데이터 맵: ${temperatureData}")
            resultList.add(temperatureData)
        }

        Log.d(APP_TAG, "체온 데이터 조회 완료: ${resultList.size}건")
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
        Log.d(APP_TAG, "혈당 데이터 조회 결과: ${result.dataList.size}개 데이터 포인트")

        for (dataPoint in result.dataList) {
            Log.v(APP_TAG, "혈당 데이터 포인트 UID: ${dataPoint.uid}")
            val glucoseMmol = dataPoint.getValue(DataType.BloodGlucoseType.GLUCOSE_LEVEL) ?: 0f
            val glucoseMgdl = glucoseMmol * 18.018f
            val measurementType = dataPoint.getValue(DataType.BloodGlucoseType.MEASUREMENT_TYPE)
            val mealStatus = dataPoint.getValue(DataType.BloodGlucoseType.MEAL_STATUS)
            
            Log.v(APP_TAG, "혈당 데이터: Glucose=${glucoseMmol}mmol/L (${glucoseMgdl}mg/dL), MeasurementType=${measurementType?.name}, MealStatus=${mealStatus?.name}")

            val glucoseData = mapOf(
                "uid" to (dataPoint.uid ?: ""),
                "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
                "glucose_level" to glucoseMmol,
                "glucose_mmol" to glucoseMmol,
                "glucose_mgdl" to glucoseMgdl,
                "measurement_type" to (measurementType?.ordinal ?: 0),
                "measurement_type_name" to (measurementType?.name ?: "Unknown"),
                "meal_status" to (mealStatus?.ordinal ?: 0),
                "meal_status_name" to (mealStatus?.name ?: "Unknown")
            )
            
            Log.v(APP_TAG, "혈당 데이터 맵: ${glucoseData}")
            resultList.add(glucoseData)
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
