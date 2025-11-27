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
        Log.d(APP_TAG, "getPermissionForType() 호출: $type")

        val permission = when (type) {
            "exercise", "com.samsung.health.exercise" -> Permission.of(DataTypes.EXERCISE, AccessType.READ)
            "heart_rate", "com.samsung.health.heart_rate" -> Permission.of(DataTypes.HEART_RATE, AccessType.READ)
            "sleep", "com.samsung.health.sleep" -> Permission.of(DataTypes.SLEEP, AccessType.READ)
            "steps", "com.samsung.health.step" -> Permission.of(DataTypes.STEPS, AccessType.READ)
            "nutrition", "com.samsung.health.nutrition" -> Permission.of(DataTypes.NUTRITION, AccessType.READ)
            "body_composition", "com.samsung.health.body_composition" -> Permission.of(DataTypes.BODY_COMPOSITION, AccessType.READ)
            "blood_oxygen", "com.samsung.health.blood_oxygen" -> Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.READ)
            "body_temperature", "com.samsung.health.body_temperature" -> Permission.of(DataTypes.BODY_TEMPERATURE, AccessType.READ)
            "blood_glucose", "com.samsung.health.blood_glucose" -> Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ)
            else -> {
                Log.w(APP_TAG, "알 수 없는 권한 타입: $type")
                null
            }
        }

        if (permission != null) {
            Log.d(APP_TAG, "권한 매핑 성공: $type -> ${permission.dataType}")
        } else {
            Log.e(APP_TAG, "권한 매핑 실패: $type")
        }

        return permission
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
            "openSamsungHealthPermissions" -> openSamsungHealthPermissions(wrapper)
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
            Log.e(APP_TAG, "Activity가 null입니다. 권한 다이얼로그를 표시할 수 없습니다.")
            wrapper.error("ACTIVITY_NOT_READY", "Activity가 없습니다. 앱이 포그라운드에 있는지 확인하세요.", null)
            return
        }

        Log.d(APP_TAG, "Activity 확인됨: ${act.javaClass.simpleName}")

        // Activity가 유효한지 추가 확인
        if (act.isDestroyed) {
            Log.e(APP_TAG, "Activity가 destroyed 상태입니다")
            wrapper.error("ACTIVITY_DESTROYED", "Activity가 종료된 상태입니다", null)
            return
        }

        if (act.isFinishing) {
            Log.e(APP_TAG, "Activity가 finishing 상태입니다")
            wrapper.error("ACTIVITY_FINISHING", "Activity가 종료 중입니다", null)
            return
        }

        Log.d(APP_TAG, "입력받은 권한 타입들: $types")

        val permissionsToRequest = if (types.isEmpty()) {
            Log.d(APP_TAG, "모든 권한 요청")
            allPermissions
        } else {
            Log.d(APP_TAG, "특정 권한 요청 시작")
            val mappedPermissions = types.mapNotNull { type ->
                val permission = getPermissionForType(type)
                if (permission == null) {
                    Log.w(APP_TAG, "매핑 실패한 타입: $type")
                }
                permission
            }.toSet()
            Log.d(APP_TAG, "매핑된 권한 수: ${mappedPermissions.size}")
            mappedPermissions
        }

        Log.d(APP_TAG, "최종 요청할 권한 수: ${permissionsToRequest.size}")

        if (permissionsToRequest.isEmpty()) {
            Log.e(APP_TAG, "요청할 권한이 없습니다 - 모든 권한 매핑 실패")
            wrapper.success(mapOf("granted" to emptyMap<String, Boolean>(), "message" to "요청할 권한이 없습니다"))
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            runCatching {
                // 먼저 현재 권한 상태 확인
                val grantedPermissions = store.getGrantedPermissions(permissionsToRequest)

                if (grantedPermissions.containsAll(permissionsToRequest)) {
                    Log.i(APP_TAG, "모든 권한이 이미 허용됨")
                    val grantedMap = permissionsToRequest.associate {
                        getDataTypeNameForPermission(it) to true
                    }
                    wrapper.success(mapOf("granted" to grantedMap, "message" to "모든 권한 허용됨"))
                } else {
                    // 권한 요청 실행 - 비동기 처리
                    Log.i(APP_TAG, "권한 요청 실행")

                    runCatching {
                        // Samsung Health Data SDK 1.0.0의 올바른 권한 요청 방식
                        store.requestPermissions(permissionsToRequest, act)

                        // 권한 다이얼로그가 표시되고 사용자 응답을 기다리기 위한 지연
                        kotlinx.coroutines.delay(1000)

                        // 권한 요청 결과 확인 - 여러 번 시도
                        var attempts = 0
                        val maxAttempts = 10
                        var newGrantedPermissions = setOf<Permission>()

                        while (attempts < maxAttempts) {
                            kotlinx.coroutines.delay(500) // 0.5초 대기
                            newGrantedPermissions = store.getGrantedPermissions(permissionsToRequest)

                            // 이전 권한 상태와 비교하여 변경이 있었는지 확인
                            if (newGrantedPermissions != grantedPermissions) {
                                Log.i(APP_TAG, "권한 상태 변경 감지됨")
                                break
                            }

                            attempts++
                            Log.d(APP_TAG, "권한 확인 시도: $attempts/$maxAttempts")
                        }

                        val grantedMap = permissionsToRequest.associate {
                            getDataTypeNameForPermission(it) to newGrantedPermissions.contains(it)
                        }
                        val deniedList = permissionsToRequest
                            .filter { !newGrantedPermissions.contains(it) }
                            .map { getDataTypeNameForPermission(it) }

                        val response = mutableMapOf<String, Any>("granted" to grantedMap)
                        if (deniedList.isNotEmpty()) {
                            response["denied_permissions"] = deniedList
                            response["message"] = "일부 권한이 거부되었습니다"
                            Log.w(APP_TAG, "거부된 권한: $deniedList")
                        } else {
                            response["message"] = "모든 권한 허용됨"
                            Log.i(APP_TAG, "모든 권한 허용됨")
                        }

                        response["attempts_used"] = attempts
                        wrapper.success(response)

                    }.onFailure { requestError ->
                        Log.e(APP_TAG, "권한 요청 처리 중 오류: ${requestError.message}")

                        // 권한 요청 실패 시 현재 상태라도 반환
                        val currentGranted = store.getGrantedPermissions(permissionsToRequest)
                        val fallbackMap = permissionsToRequest.associate {
                            getDataTypeNameForPermission(it) to currentGranted.contains(it)
                        }

                        wrapper.success(mapOf(
                            "granted" to fallbackMap,
                            "message" to "권한 요청 중 오류 발생, 현재 상태 반환: ${requestError.message}",
                            "error_during_request" to true
                        ))
                    }
                }
            }.onFailure { error ->
                Log.e(APP_TAG, "권한 요청 실패: ${error.message}", error)

                when (error) {
                    is ResolvablePlatformException -> {
                        if (error.hasResolution && act != null) {
                            Log.i(APP_TAG, "권한 요청 중 ResolvablePlatformException 발생 - 해결 시도")
                            runCatching {
                                error.resolve(act)
                            }.onFailure { resolveError ->
                                Log.e(APP_TAG, "권한 해결 실패: ${resolveError.message}")
                            }
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
                val grantedList = grantedPermissions.map { getDataTypeNameForPermission(it) }
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
