package com.example.flutter_samsung_health

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.util.Log
import com.samsung.android.sdk.healthdata.HealthConnectionErrorResult
import com.samsung.android.sdk.healthdata.HealthConstants
import com.samsung.android.sdk.healthdata.HealthConstants.BodyTemperature
import com.samsung.android.sdk.healthdata.HealthConstants.Exercise
import com.samsung.android.sdk.healthdata.HealthConstants.HeartRate
import com.samsung.android.sdk.healthdata.HealthConstants.Sleep
import com.samsung.android.sdk.healthdata.HealthConstants.SleepStage
import com.samsung.android.sdk.healthdata.HealthConstants.StepCount
import com.samsung.android.sdk.healthdata.HealthConstants.Nutrition
import com.samsung.android.sdk.healthdata.HealthConstants.Weight
import com.samsung.android.sdk.healthdata.HealthConstants.OxygenSaturation
import com.samsung.android.sdk.healthdata.HealthDataResolver
import com.samsung.android.sdk.healthdata.HealthDataResolver.ReadRequest
import com.samsung.android.sdk.healthdata.HealthDataResolver.AggregateRequest
import com.samsung.android.sdk.healthdata.HealthDataResolver.AggregateRequest.AggregateFunction
import com.samsung.android.sdk.healthdata.HealthDataObserver
import com.samsung.android.sdk.healthdata.HealthDataStore
import com.samsung.android.sdk.healthdata.HealthPermissionManager
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionKey
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionType
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

private const val PREF_NAME = "samsung_health_preferences"
private const val PREF_KEY_PERMISSION_REQUESTED = "permission_requested"

/** FlutterSamsungHealth */
class FlutterSamsungHealth : FlutterPlugin, MethodCallHandler, ActivityAware, EventChannel.StreamHandler {
    /// The MethodChannel that will the communication between Flutter and native Android
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private lateinit var mStore: HealthDataStore
    private val APP_TAG: String = "FlutterSamsungHealth"
    private var activity: Activity? = null
    private val lastEventTimes = mutableMapOf<String, Long>()
    private val debounceMillis = 5_000L // 5초

    private val permissions = setOf(
        PermissionKey(Exercise.HEALTH_DATA_TYPE, PermissionType.READ),
        PermissionKey(HeartRate.HEALTH_DATA_TYPE, PermissionType.READ),
        PermissionKey(Sleep.HEALTH_DATA_TYPE, PermissionType.READ),
        PermissionKey(SleepStage.HEALTH_DATA_TYPE, PermissionType.READ),
        PermissionKey(StepCount.HEALTH_DATA_TYPE, PermissionType.READ),
        PermissionKey(Nutrition.HEALTH_DATA_TYPE, PermissionType.READ),
        PermissionKey(Weight.HEALTH_DATA_TYPE, PermissionType.READ),
        PermissionKey(OxygenSaturation.HEALTH_DATA_TYPE, PermissionType.READ),
        PermissionKey(BodyTemperature.HEALTH_DATA_TYPE, PermissionType.READ),
    )

    private val observers = mutableSetOf<HealthDataObserver>()
    private val eventSinks = mutableListOf<EventChannel.EventSink>()

    private val observedDataTypes = setOf(
        Exercise.HEALTH_DATA_TYPE,
        Nutrition.HEALTH_DATA_TYPE
    )

    private val mObserver: HealthDataObserver = object : HealthDataObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(dataTypeName: String) {
            val now = System.currentTimeMillis()
            val last = lastEventTimes[dataTypeName] ?: 0L

            if (now - last < debounceMillis) {
                return
            }

            lastEventTimes[dataTypeName] = now
            notifyFlutter(dataTypeName)
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
        when (call.method) {
            "isSamsungHealthInstalled" -> {
                isSamsungHealthInstalled(result, context)
            }

            "openSamsungHealth" -> {
                openSamsungHealthInStore(result, context)
            }

            "connect" -> {
                connectSamsungHealth(result, onlyRequest = false)
            }

            "disconnect" -> {
                disconnectSamsungHealth(result)
            }

            "requestPermissions" -> {
                connectSamsungHealth(result, onlyRequest = true)
            }

            "getGrantedPermissions" -> {
                getGrantedPermissions(result)
            }

            "getTotalData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                checkPermissionAndExecuteTotal(permissionKeys = permissions, onGranted = { grantedPermissions ->
                    getTotalData(start, end, result, grantedPermissions)
                }, onDenied = { deniedPermissions ->
                    requestPermissionTotal(result, deniedPermissions)
                })
            }

            "getExerciseData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                val permission = PermissionKey(Exercise.HEALTH_DATA_TYPE, PermissionType.READ)
                checkPermissionAndExecute(permission, onGranted = {
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val data = getExerciseData(start, end)
                            withContext(Dispatchers.Main) {
                                result.success(data)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                result.error("GET_EXERCISE_ERROR", e.message, null)
                            }
                        }
                    }
                }, onDenied = {
                    requestPermission(result, permission)
                })
            }

            "getHeartRateData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                val permission = PermissionKey(HeartRate.HEALTH_DATA_TYPE, PermissionType.READ)
                checkPermissionAndExecute(permission, onGranted = {
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val data = getHeartRateData(start, end)
                            withContext(Dispatchers.Main) {
                                result.success(data)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                result.error("GET_HEART_RATE_ERROR", e.message, null)
                            }
                        }
                    }
                }, onDenied = {
                    requestPermission(result, permission)
                })
            }

            "getSleepData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                val permission = PermissionKey(Sleep.HEALTH_DATA_TYPE, PermissionType.READ)
                checkPermissionAndExecute(permission, onGranted = {
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val data = getSleepData(start, end)
                            withContext(Dispatchers.Main) {
                                result.success(data)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                result.error("GET_SLEEP_ERROR", e.message, null)
                            }
                        }
                    }
                }, onDenied = {
                    requestPermission(result, permission)
                })
            }

            "getSleepStageData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                val permission = PermissionKey(SleepStage.HEALTH_DATA_TYPE, PermissionType.READ)
                checkPermissionAndExecute(permission, onGranted = {
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val data = getSleepStageData(start, end)
                            withContext(Dispatchers.Main) {
                                result.success(data)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                result.error("GET_SLEEPSTAGE_ERROR", e.message, null)
                            }
                        }
                    }
                }, onDenied = {
                    requestPermission(result, permission)
                })
            }

            "getStepData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                val permission = PermissionKey(StepCount.HEALTH_DATA_TYPE, PermissionType.READ)
                checkPermissionAndExecute(permission, onGranted = {
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val data = getStepData(start, end)
                            withContext(Dispatchers.Main) {
                                result.success(data)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                result.error("GET_STEP_ERROR", e.message, null)
                            }
                        }
                    }
                }, onDenied = {
                    requestPermission(result, permission)
                })
            }

            "getNutritionData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                val permission = PermissionKey(Nutrition.HEALTH_DATA_TYPE, PermissionType.READ)
                checkPermissionAndExecute(permission, onGranted = {
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val data = getNutritionData(start, end)
                            withContext(Dispatchers.Main) {
                                result.success(data)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                result.error("GET_NUTRITION_ERROR", e.message, null)
                            }
                        }
                    }
                }, onDenied = {
                    requestPermission(result, permission)
                })
            }

            "getWeightData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                val permission = PermissionKey(Weight.HEALTH_DATA_TYPE, PermissionType.READ)
                checkPermissionAndExecute(permission, onGranted = {
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val data = getWeightData(start, end)
                            withContext(Dispatchers.Main) {
                                result.success(data)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                result.error("GET_WEIGHT_ERROR", e.message, null)
                            }
                        }
                    }
                }, onDenied = {
                    requestPermission(result, permission)
                })
            }

            "getOxygenSaturationData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                val permission = PermissionKey(OxygenSaturation.HEALTH_DATA_TYPE, PermissionType.READ)
                checkPermissionAndExecute(permission, onGranted = {
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val data = getOxygenSaturationData(start, end)
                            withContext(Dispatchers.Main) {
                                result.success(data)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                result.error("GET_OXYGENSATURATION_ERROR", e.message, null)
                            }
                        }
                    }
                }, onDenied = {
                    requestPermission(result, permission)
                })
            }

            "getBodyTemperatureData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                val permission = PermissionKey(BodyTemperature.HEALTH_DATA_TYPE, PermissionType.READ)
                checkPermissionAndExecute(permission, onGranted = {
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val data = getBodyTemperatureData(start, end)
                            withContext(Dispatchers.Main) {
                                result.success(data)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                result.error("GET_BODYTEMPERATURE_ERROR", e.message, null)
                            }
                        }
                    }
                }, onDenied = {
                    requestPermission(result, permission)
                })
            }
            else -> result.notImplemented()
        }
    }


    private fun isSamsungHealthInstalled(result: MethodChannel.Result, context: Context) {
        Log.d(APP_TAG, "isSamsungHealthInstalled() 호출")
        val resultMap: MutableMap<String, Any> = mutableMapOf()

        try {
            context.packageManager.getPackageInfo("com.sec.android.app.shealth", 0)
            resultMap.put("isInstalled", true)
            result.success(resultMap)
        } catch (e: Exception) {
            resultMap.put("isInstalled", false)
            result.success(resultMap)
        }
    }

    private fun openSamsungHealthInStore(result: MethodChannel.Result, context: Context) {
        Log.d(APP_TAG, "openSamsungHealthInStore() 호출")
        val resultMap: MutableMap<String, Any> = mutableMapOf()
        val packageName = "com.sec.android.app.shealth"
        val packageManager = context.packageManager

        try {
            packageManager.getPackageInfo(packageName, 0)

            // 삼성 헬스 실행
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                resultMap.put("action", "launched")
                result.success(resultMap)
            } else {
                Log.e(APP_TAG, "삼성 헬스 실행 인텐트 없음")
                resultMap.put("action", "launch_failed")
                result.success(resultMap)
            }

        } catch (e: Exception) {
            // Play 스토어로 이동
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=$packageName")
                    setPackage("com.android.vending")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                resultMap.put("action", "move_to_store")
                result.success(resultMap)
            } catch (e: Exception) {
                Log.e(APP_TAG, "Play 스토어 이동 실패: $e")
                resultMap.put("action", "store_failed")
                result.success(resultMap)
            }
        }
    }

    /**
     * 삼성헬스 연계 관련 함수 추가.
     */
    private fun connectSamsungHealth(result: MethodChannel.Result, onlyRequest: Boolean) {
        Log.d(APP_TAG, "connectSamsungHealth() 호출")
        val resultMap: MutableMap<String, Any> = mutableMapOf()

        mStore = HealthDataStore(context, object : HealthDataStore.ConnectionListener {
            override fun onConnected() {
                Log.d(APP_TAG, "삼성 헬스 연결 성공")
                resultMap.put("isConnect", true)
                registerObservers()

                if (onlyRequest) {
                    requestPermissionsOnly(result)
                } else {
                    result.success(resultMap)
                }
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

    private fun disconnectSamsungHealth(result: MethodChannel.Result) {
        Log.d(APP_TAG, "disconnectSamsungHealth() 호출")

        if (::mStore.isInitialized) {
            try {
                mStore.disconnectService()
                Log.d(APP_TAG, "삼성 헬스 연결 해제 요청 완료")
                unregisterObservers()
                val resultMap: MutableMap<String, Any> = mutableMapOf()
                resultMap["isConnect"] = false
                result.success(resultMap)
            } catch (e: Exception) {
                Log.e(APP_TAG, "disconnect 실패: ${e.message}", e)
                result.error("DISCONNECT_ERROR", e.message, null)
            }
        } else {
            Log.w(APP_TAG, "mStore가 초기화되지 않았습니다.")
            val resultMap: MutableMap<String, Any> = mutableMapOf()
            resultMap["isConnect"] = false
            result.success(resultMap)
        }
    }

    private fun getGrantedPermissions(result: MethodChannel.Result) {
        if (!::mStore.isInitialized) {
            result.error("STORE_NOT_READY", "Samsung Health 연결되지 않음", null)
            return
        }

        try {
            val permissionManager = HealthPermissionManager(mStore)
            val resultMap = permissionManager.isPermissionAcquired(permissions)

            val grantedList = resultMap.filterValues { it }.keys.map { it.dataType.toString() }

            result.success(grantedList)
        } catch (e: Exception) {
            Log.e(APP_TAG, "권한 조회 중 오류", e)
            result.error("PERMISSION_QUERY_ERROR", "권한 조회 실패", null)
        }
    }

    private fun requestPermission(result: MethodChannel.Result, permissionKey: PermissionKey) {
        val pmsManager = HealthPermissionManager(mStore)
        try {
            Log.d(APP_TAG, "삼성헬스 권한요청 시작 ")
            // Show user permission UI for allowing user to change options
            pmsManager.requestPermissions(setOf(permissionKey), activity!!).setResultListener({ res ->
                val resultMap: Map<PermissionKey, Boolean> = res.resultMap
                if (resultMap.containsValue(false)) {
                    Log.d(APP_TAG, "일부 권한 거부됨: $resultMap")
                } else {
                    Log.d(APP_TAG, "모든 권한 획득 완료!")
                }
            })
        } catch (e: Exception) {
            showPermissionAlarmDialog()
            Log.d(APP_TAG, "Permission setting fails. $e")
        }
    }

    private fun requestPermissionTotal(
        result: MethodChannel.Result, deniedPermissions: Set<PermissionKey>
    ) {
        if (loadFromSharedPreferences()) {
            // 이미 권한 요청했음 → Flutter로 거부된 권한 이름 전달
            val deniedTypes = deniedPermissions.map { it.dataType.toString() }
            result.success(mapOf("denied_permissions" to deniedTypes))
            return
        }

        val permissionManager = HealthPermissionManager(mStore)
        try {
            permissionManager.requestPermissions(deniedPermissions, activity!!).setResultListener { res ->
                val resultMap = res.resultMap
                val stillDenied = deniedPermissions.filter { resultMap[it] != true }
                if (stillDenied.isNotEmpty()) {
                    saveToSharedPreferences(true)
                    val deniedTypes = stillDenied.map { it.dataType.toString() }
                    result.success(mapOf("denied_permissions" to deniedTypes))
                } else {
                    result.success(mapOf("message" to "모든 권한 허용됨"))
                }
            }
        } catch (e: Exception) {
            showPermissionAlarmDialog()
            Log.e(APP_TAG, "Permission request failed: $e")
            result.error("PERMISSION_ERROR", "권한 요청 실패", null)
        }
    }

    private fun requestPermissionsOnly(
        result: MethodChannel.Result
    ) {
        var isReplied = false

        fun safeSuccess(response: Any) {
            if (isReplied) return
            try {
                result.success(response)
                isReplied = true
            } catch (e: IllegalStateException) {
                Log.w(APP_TAG, "Reply already submitted exception on success: ${e.message}")
            } catch (e: Exception) {
                Log.e(APP_TAG, "Unexpected error on success reply", e)
            }
        }

        clearPermissionRequestRecordInternal()

        val permissionManager = HealthPermissionManager(mStore)
        try {
            permissionManager.requestPermissions(permissions, activity!!).setResultListener { res ->
                val grantedMap = res.resultMap
                val denied = permissions.filter { grantedMap[it] != true }

                saveToSharedPreferences(true)

                if (denied.isNotEmpty()) {
                    val deniedTypes = denied.map { it.dataType.toString() }
                    safeSuccess(mapOf("denied_permissions" to deniedTypes))
                } else {
                    safeSuccess(mapOf("message" to "모든 권한 허용됨"))
                }
            }
        } catch (e: Exception) {
            safeFlutterError(
                result,
                "PERMISSION_ERROR",
                "권한 요청 중 오류 발생",
                e.message,
                { isReplied },
                { isReplied = it })
            showPermissionAlarmDialog()
        }
    }

    private fun checkPermissionAndExecute(
        permissionKey: PermissionKey, onGranted: () -> Unit, onDenied: () -> Unit
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

    private fun checkPermissionAndExecuteTotal(
        permissionKeys: Set<PermissionKey>,
        onGranted: (Set<PermissionKey>) -> Unit,
        onDenied: (Set<PermissionKey>) -> Unit
    ) {
        val permissionManager = HealthPermissionManager(mStore)
        try {
            val resultMap = permissionManager.isPermissionAcquired(permissionKeys)
            val granted = resultMap.filterValues { it }.keys
            val denied = permissionKeys - granted

            if (granted.isNotEmpty()) {
                onGranted(granted)
            } else {
                onDenied(denied)
            }
        } catch (e: Exception) {
            Log.e(APP_TAG, "권한 확인 중 오류", e)
            onDenied(permissionKeys)
        }
    }

    private fun registerObservers() {
        for (dataType in observedDataTypes) {
            try {
                HealthDataObserver.removeObserver(mStore, mObserver)
                HealthDataObserver.addObserver(mStore, dataType, mObserver)
                Log.d(APP_TAG, "Observer registered: $dataType")
            } catch (e: Exception) {
                Log.e(APP_TAG, "Failed to register observer for $dataType", e)
            }
        }
    }

    private fun unregisterObservers() {
        for (dataType in observedDataTypes) {
            try {
                HealthDataObserver.removeObserver(mStore, mObserver)
                Log.d(APP_TAG, "Observer removed: $dataType")
            } catch (e: Exception) {
                Log.e(APP_TAG, "Failed to remove observer for $dataType", e)
            }
        }
    }

//    fun notifyFlutter(dataType: String, dataList: List<Map<String, Any>>) {
//        val payload = mapOf(
//            "type" to dataType,
//            "data" to dataList
//        )
//        Handler(Looper.getMainLooper()).post {
//            eventSinks.forEach { sink ->
//                sink.success(payload)
//            }
//        }
//    }

    fun notifyFlutter(dataType: String) {
        val payload = mapOf(
            "type" to dataType
        )
        Handler(Looper.getMainLooper()).post {
            eventSinks.forEach { sink ->
                sink.success(payload)
            }
        }
    }

    private fun showPermissionAlarmDialog() {
        Log.d(APP_TAG, "showPermissionAlarmDialog 호출")
    }

    private fun saveToSharedPreferences(requested: Boolean) {
        val prefs = context.getSharedPreferences("samsung_health_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("permission_requested", requested).apply()
    }

    private fun loadFromSharedPreferences(): Boolean {
        val prefs = context.getSharedPreferences("samsung_health_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("permission_requested", false)
    }

    private fun clearPermissionRequestRecordInternal() {
        try {
            val sharedPref = activity?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            sharedPref?.edit()?.remove(PREF_KEY_PERMISSION_REQUESTED)?.apply()
            Log.d(APP_TAG, "Permission request record cleared")
        } catch (e: Exception) {
            Log.e(APP_TAG, "Failed to clear permission request record", e)
        }
    }

    private fun safeFlutterError(
        result: MethodChannel.Result?,
        errorCode: String,
        errorMessage: String,
        errorDetails: Any? = null,
        isRepliedFlag: () -> Boolean,
        setRepliedFlag: (Boolean) -> Unit
    ) {
        if (isRepliedFlag()) {
            // 이미 응답 완료된 경우 무시
            Log.w(APP_TAG, "Flutter error reply already submitted: $errorMessage")
            return
        }
        try {
            result?.error(errorCode, errorMessage, errorDetails)
            setRepliedFlag(true)
        } catch (e: IllegalStateException) {
            Log.w(APP_TAG, "Flutter error reply already submitted: $errorMessage")
        } catch (e: Exception) {
            Log.e(APP_TAG, "Unexpected error sending Flutter error response", e)
        }
    }

    private fun getTotalData(
        start: Long, end: Long, result: MethodChannel.Result, grantedPermissions: Set<PermissionKey>
    ) {
        /// 각 데이터들 비동기로 호출하고 결과값 맵에 담아 반환
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val exercise = async {
                    if (grantedPermissions.contains(
                            PermissionKey(
                                Exercise.HEALTH_DATA_TYPE,
                                PermissionType.READ
                            )
                        )
                    ) getExerciseData(start, end)
                    else emptyList()
                }
                val heartRate = async {
                    if (grantedPermissions.contains(
                            PermissionKey(
                                HeartRate.HEALTH_DATA_TYPE,
                                PermissionType.READ
                            )
                        )
                    ) getHeartRateData(start, end)
                    else emptyList()
                }
                val sleep = async {
                    if (grantedPermissions.contains(
                            PermissionKey(
                                Sleep.HEALTH_DATA_TYPE,
                                PermissionType.READ
                            )
                        )
                    ) getSleepData(start, end)
                    else emptyList()
                }
                val sleepStage = async {
                    if (grantedPermissions.contains(
                            PermissionKey(
                                SleepStage.HEALTH_DATA_TYPE,
                                PermissionType.READ
                            )
                        )
                    ) getSleepStageData(start, end)
                    else emptyList()
                }
                val steps = async {
                    if (grantedPermissions.contains(
                            PermissionKey(
                                StepCount.HEALTH_DATA_TYPE,
                                PermissionType.READ
                            )
                        )
                    ) getStepData(start, end)
                    else emptyList()
                }
                val nutrition = async {
                    if (grantedPermissions.contains(
                            PermissionKey(
                                Nutrition.HEALTH_DATA_TYPE,
                                PermissionType.READ
                            )
                        )
                    ) getNutritionData(start, end)
                    else emptyList()
                }
                val weight = async {
                    if (grantedPermissions.contains(
                            PermissionKey(
                                Weight.HEALTH_DATA_TYPE,
                                PermissionType.READ
                            )
                        )
                    ) getWeightData(start, end)
                    else emptyList()
                }
                val oxygenSaturation = async {
                    if (grantedPermissions.contains(
                            PermissionKey(
                                OxygenSaturation.HEALTH_DATA_TYPE, PermissionType.READ
                            )
                        )
                    ) getOxygenSaturationData(start, end)
                    else emptyList()
                }
                val bodyTemperature = async {
                    if (grantedPermissions.contains(
                            PermissionKey(
                                BodyTemperature.HEALTH_DATA_TYPE, PermissionType.READ
                            )
                        )
                    ) getBodyTemperatureData(start, end)
                    else emptyList()
                }

                val totalResult = mapOf(
                    "exercise" to exercise.await(),
                    "heart_rate" to heartRate.await(),
                    "sleep" to sleep.await(),
                    "sleep_stage" to sleepStage.await(),
                    "step" to steps.await(),
                    "nutrition" to nutrition.await(),
                    "weight" to weight.await(),
                    "oxygen_saturation" to oxygenSaturation.await(),
                    "body_temperature" to bodyTemperature.await(),
                )
                withContext(Dispatchers.Main) {
                    result.success(totalResult)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    result.error("TOTAL_DATA_ERROR", "데이터 수집 실패: ${e.message}", null)
                }
            }

        }
    }

    /**
     * 운동 조회
     */
    suspend fun getExerciseData(start: Long, end: Long): List<Map<String, Any>> = withContext(Dispatchers.Main) {
        suspendCoroutine { cont ->
            try {
                Log.d(APP_TAG, "운동 데이터 시작")
                val request =
                    ReadRequest.Builder()
                        .setDataType(HealthConstants.Exercise.HEALTH_DATA_TYPE)
                        .setLocalTimeRange(
                            HealthConstants.Exercise.START_TIME, HealthConstants.Exercise.TIME_OFFSET, start, end
                        )
                        .setSort(HealthConstants.Exercise.START_TIME, HealthDataResolver.SortOrder.DESC)
                        .setProperties(
                            arrayOf(
                                HealthConstants.Exercise.DEVICE_UUID,
                                HealthConstants.Exercise.EXERCISE_TYPE,
                                HealthConstants.Exercise.START_TIME,
                                HealthConstants.Exercise.END_TIME,
                                HealthConstants.Exercise.TIME_OFFSET,
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
                val resultList = mutableListOf<Map<String, Any>>()
                resolver.read(request).setResultListener { result ->
                    for (data in result) {
                        resultList.add(
                            mapOf(
                                "device_uuid" to data.getString(HealthConstants.Exercise.DEVICE_UUID),
                                "exercise_type" to data.getInt(HealthConstants.Exercise.EXERCISE_TYPE),
                                "exercise_type_name" to ExerciseTypeMapper.getName(data.getInt(HealthConstants.Exercise.EXERCISE_TYPE)),
                                "start_time" to data.getLong(HealthConstants.Exercise.START_TIME),
                                "end_time" to data.getLong(HealthConstants.Exercise.END_TIME),
                                "time_offset" to data.getLong(HealthConstants.Exercise.TIME_OFFSET),
                                "duration" to data.getLong(HealthConstants.Exercise.DURATION),
                                "distance" to data.getFloat(HealthConstants.Exercise.DISTANCE),
                                "calorie" to data.getFloat(HealthConstants.Exercise.CALORIE),
                                "max_heart_rate" to data.getFloat(HealthConstants.Exercise.MAX_HEART_RATE),
                                "mean_heart_rate" to data.getFloat(HealthConstants.Exercise.MEAN_HEART_RATE),
                                "min_heart_rate" to data.getFloat(HealthConstants.Exercise.MIN_HEART_RATE),
                                "liveData" to data.getString(HealthConstants.Exercise.LIVE_DATA)
                            )
                        )
                    }
                    Log.d(APP_TAG, "운동 데이터 종료")
                    cont.resume(resultList)
                }
            } catch (e: Exception) {
                Log.e(APP_TAG, "운동 데이터 Exception: ${e.message}")
                cont.resume(emptyList())
            }
        }
    }

    /**
     * 심박 조회
     */
    private suspend fun getHeartRateData(start: Long, end: Long): List<Map<String, Any>> =
        withContext(Dispatchers.Main) {
            suspendCoroutine { cont ->
                try {
                    Log.d(APP_TAG, "심박 데이터 시작")
                    val request =
                        ReadRequest.Builder().setDataType(HealthConstants.HeartRate.HEALTH_DATA_TYPE)
                            .setLocalTimeRange(
                                HealthConstants.HeartRate.START_TIME,
                                HealthConstants.HeartRate.TIME_OFFSET,
                                start,
                                end
                            ).setSort(HealthConstants.HeartRate.START_TIME, HealthDataResolver.SortOrder.DESC)
                            .setProperties(
                                arrayOf(
                                    HealthConstants.HeartRate.DEVICE_UUID,
                                    HealthConstants.HeartRate.START_TIME,
                                    HealthConstants.HeartRate.END_TIME,
                                    HealthConstants.HeartRate.TIME_OFFSET,
                                    HealthConstants.HeartRate.HEART_RATE,
                                    HealthConstants.HeartRate.HEART_BEAT_COUNT,
                                    HealthConstants.HeartRate.MIN,
                                    HealthConstants.HeartRate.MAX,
                                    HealthConstants.HeartRate.BINNING_DATA
                                )
                            ).build()

                    val resolver = HealthDataResolver(mStore, null)
                    val resultList = mutableListOf<Map<String, Any>>()
                    resolver.read(request).setResultListener { result ->
                        for (data in result) {
                            resultList.add(
                                mapOf(
                                    "device_uuid" to data.getString(HealthConstants.HeartRate.DEVICE_UUID),
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
                        Log.d(APP_TAG, "심박 데이터 종료")
                        cont.resume(resultList)
                    }
                } catch (e: Exception) {
                    Log.e(APP_TAG, "심박 데이터 Exception: ${e.message}")
                    cont.resume(emptyList())
                }
            }
        }

    /**
     * 수면 조회
     */
    private suspend fun getSleepData(start: Long, end: Long): List<Map<String, Any>> =
        withContext(Dispatchers.Main) {
            suspendCoroutine { cont ->
                try {
                    Log.d(APP_TAG, "수면 데이터 시작")
                    val adjustedStart = start - (24 * 60 * 60 * 1000) // 하루 전
                    val request =
                        ReadRequest.Builder().setDataType(HealthConstants.Sleep.HEALTH_DATA_TYPE).setLocalTimeRange(
                            HealthConstants.Sleep.START_TIME, HealthConstants.Sleep.TIME_OFFSET, adjustedStart, end
                        ).setSort(HealthConstants.Sleep.START_TIME, HealthDataResolver.SortOrder.DESC)
                            .setProperties(
                                arrayOf(
                                    HealthConstants.Sleep.DEVICE_UUID,
                                    HealthConstants.Sleep.START_TIME,
                                    HealthConstants.Sleep.END_TIME,
                                    HealthConstants.Sleep.TIME_OFFSET
                                )
                            ).build()

                    val resolver = HealthDataResolver(mStore, null)
                    val resultList = mutableListOf<Map<String, Any>>()
                    resolver.read(request).setResultListener { result ->
                        for (data in result) {
                            resultList.add(
                                mapOf(
                                    "device_uuid" to data.getString(HealthConstants.Sleep.DEVICE_UUID),
                                    "start_time" to data.getLong(HealthConstants.Sleep.START_TIME),
                                    "end_time" to data.getLong(HealthConstants.Sleep.END_TIME),
                                    "time_offset" to data.getLong(HealthConstants.Sleep.TIME_OFFSET)
                                )
                            )
                        }
                        Log.d(APP_TAG, "수면 데이터 종료")
                        cont.resume(resultList)
                    }
                } catch (e: Exception) {
                    Log.e(APP_TAG, "수면 데이터 Exception: ${e.message}")
                    cont.resume(emptyList())
                }
            }
        }

    /**
     * 수면 단계 조회
     */
    private suspend fun getSleepStageData(start: Long, end: Long): List<Map<String, Any>> =
        withContext(Dispatchers.Main) {
            suspendCoroutine { cont ->
                try {
                    Log.d(APP_TAG, "수면 단계 데이터 시작")
                    val adjustedStart = start - (24 * 60 * 60 * 1000) // 하루 전
                    val request =
                        ReadRequest.Builder()
                            .setDataType(HealthConstants.SleepStage.HEALTH_DATA_TYPE)
                            .setLocalTimeRange(
                                HealthConstants.SleepStage.START_TIME,
                                HealthConstants.SleepStage.TIME_OFFSET,
                                adjustedStart,
                                end
                            )
                            .setSort(HealthConstants.SleepStage.START_TIME, HealthDataResolver.SortOrder.DESC)
                            .setProperties(
                                arrayOf(
                                    HealthConstants.SleepStage.DEVICE_UUID,
                                    HealthConstants.SleepStage.START_TIME,
                                    HealthConstants.SleepStage.END_TIME,
                                    HealthConstants.SleepStage.TIME_OFFSET,
                                    HealthConstants.SleepStage.SLEEP_ID,
                                    HealthConstants.SleepStage.STAGE,
                                )
                            )
                            .build()

                    val resolver = HealthDataResolver(mStore, null)
                    val resultList = mutableListOf<Map<String, Any>>()
                    resolver.read(request).setResultListener { result ->
                        for (data in result) {
                            resultList.add(
                                mapOf(
                                    "device_uuid" to data.getString(HealthConstants.SleepStage.DEVICE_UUID),
                                    "start_time" to data.getLong(HealthConstants.SleepStage.START_TIME),
                                    "end_time" to data.getLong(HealthConstants.SleepStage.END_TIME),
                                    "time_offset" to data.getLong(HealthConstants.SleepStage.TIME_OFFSET),
                                    "sleep_id" to data.getString(HealthConstants.SleepStage.SLEEP_ID),
                                    "stage_type" to data.getInt(HealthConstants.SleepStage.STAGE),
                                    "stage_type_name" to SleepTypeMapper.getName(data.getInt(HealthConstants.SleepStage.STAGE))
                                )
                            )
                        }
                        Log.d(APP_TAG, "수면 단계 데이터 종료")
                        cont.resume(resultList)
                    }
                } catch (e: Exception) {
                    Log.e(APP_TAG, "수면 단계 데이터 Exception: ${e.message}")
                    cont.resume(emptyList())
                }
            }
        }

    /**
     * 걷기 조회(5분 누적)
     */
    private suspend fun getStepData(start: Long, end: Long): List<Map<String, Any>> =
        withContext(Dispatchers.Main) {
            suspendCoroutine { cont ->
                try {
                    Log.d(APP_TAG, "걷기 데이터 시작")
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    sdf.timeZone = TimeZone.getDefault()

                    val request: AggregateRequest =
                        AggregateRequest.Builder().setDataType(StepCount.HEALTH_DATA_TYPE)
                            .addFunction(AggregateFunction.SUM, StepCount.COUNT, "total_step")
                            .addFunction(AggregateFunction.SUM, StepCount.CALORIE, "total_calorie")
                            .addFunction(AggregateFunction.SUM, StepCount.DISTANCE, "total_distance")
                            .setLocalTimeRange(StepCount.START_TIME, StepCount.TIME_OFFSET, start, end)
                            .setTimeGroup(
                                AggregateRequest.TimeGroupUnit.MINUTELY,
                                5,
                                HealthConstants.StepCount.START_TIME,
                                StepCount.TIME_OFFSET,
                                "minute"
                            ).setSort(HealthConstants.StepCount.START_TIME, HealthDataResolver.SortOrder.DESC)
                            .build()

                    val resolver = HealthDataResolver(mStore, null)
                    val hourlyStepList = mutableListOf<Map<String, Any>>()
                    resolver.aggregate(request).setResultListener { dataResult ->
                        for (data in dataResult) {
                            val timeStr = data.getString("minute") ?: continue
                            val steps = data.getInt("total_step")
                            val calorie = data.getFloat("total_calorie")
                            val distance = data.getFloat("total_distance")

                            val timestamp = try {
                                sdf.parse(timeStr)?.time ?: continue
                            } catch (e: Exception) {
                                Log.e(APP_TAG, "날짜 파싱 오류: $timeStr", e)
                                continue
                            }

                            hourlyStepList.add(
                                mapOf(
                                    "timestamp" to timestamp,
                                    "time_str" to timeStr,
                                    "steps" to steps,
                                    "calorie" to calorie,
                                    "distance" to distance
                                )
                            )
                        }
                        Log.d(APP_TAG, "걷기 데이터 종료")
                        cont.resume(hourlyStepList)
                    }
                } catch (e: Exception) {
                    Log.e(APP_TAG, "걷기 데이터 Exception: ${e.message}")
                    cont.resume(emptyList())
                }

            }
        }

    /**
     * 영양소 조회
     */
    private suspend fun getNutritionData(start: Long, end: Long): List<Map<String, Any>> =
        withContext(Dispatchers.Main) {
            suspendCoroutine { cont ->
                try {
                    Log.d(APP_TAG, "영양소 데이터 시작")
                    val request = ReadRequest.Builder().setDataType(Nutrition.HEALTH_DATA_TYPE).setLocalTimeRange(
                        HealthConstants.Nutrition.START_TIME, HealthConstants.Nutrition.TIME_OFFSET, start, end
                    ).setSort(HealthConstants.Nutrition.START_TIME, HealthDataResolver.SortOrder.DESC)
                        .setProperties(
                            arrayOf(
                                HealthConstants.Nutrition.DEVICE_UUID,
                                HealthConstants.Nutrition.START_TIME,
                                HealthConstants.Nutrition.TIME_OFFSET,
                                HealthConstants.Nutrition.MEAL_TYPE,
                                HealthConstants.Nutrition.TITLE,
                                HealthConstants.Nutrition.CALORIE,
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
                            )
                        ).build()

                    val resolver = HealthDataResolver(mStore, null)
                    val nutritionList = mutableListOf<Map<String, Any>>()
                    resolver.read(request).setResultListener { dataResult ->
                        for (data in dataResult) {
                            nutritionList.add(
                                mapOf(
                                    "device_uuid" to data.getString(HealthConstants.Nutrition.DEVICE_UUID),
                                    "start_time" to data.getLong(HealthConstants.Nutrition.START_TIME),
                                    "time_offset" to data.getLong(HealthConstants.Nutrition.TIME_OFFSET),
                                    "meal_type" to data.getInt(HealthConstants.Nutrition.MEAL_TYPE),
                                    "meal_type_name" to MealTypeMapper.getName(data.getInt(HealthConstants.Nutrition.MEAL_TYPE)),
                                    "title" to data.getString(HealthConstants.Nutrition.TITLE),
                                    "calorie" to data.getFloat(HealthConstants.Nutrition.CALORIE),
                                    "total_fat" to data.getFloat(HealthConstants.Nutrition.TOTAL_FAT),
                                    "saturated_fat" to data.getFloat(HealthConstants.Nutrition.SATURATED_FAT),
                                    "polysaturated_fat" to data.getFloat(HealthConstants.Nutrition.POLYSATURATED_FAT),
                                    "monosaturated_fat" to data.getFloat(HealthConstants.Nutrition.MONOSATURATED_FAT),
                                    "trans_fat" to data.getFloat(HealthConstants.Nutrition.TRANS_FAT),
                                    "carbohydrate" to data.getFloat(HealthConstants.Nutrition.CARBOHYDRATE),
                                    "dietary_fiber" to data.getFloat(HealthConstants.Nutrition.DIETARY_FIBER),
                                    "sugar" to data.getFloat(HealthConstants.Nutrition.SUGAR),
                                    "protein" to data.getFloat(HealthConstants.Nutrition.PROTEIN),
                                    "cholesterol" to data.getFloat(HealthConstants.Nutrition.CHOLESTEROL),
                                    "sodium" to data.getFloat(HealthConstants.Nutrition.SODIUM),
                                    "potassium" to data.getFloat(HealthConstants.Nutrition.POTASSIUM),
                                    "vitamin_a" to data.getFloat(HealthConstants.Nutrition.VITAMIN_A),
                                    "vitamin_c" to data.getFloat(HealthConstants.Nutrition.VITAMIN_C),
                                    "calcium" to data.getFloat(HealthConstants.Nutrition.CALCIUM),
                                    "iron" to data.getFloat(HealthConstants.Nutrition.IRON)
                                )
                            )
                        }
                        Log.d(APP_TAG, "영양소 데이터 종료")
                        cont.resume(nutritionList)
                    }
                } catch (e: Exception) {
                    Log.e(APP_TAG, "영양소 데이터 Exception: ${e.message}")
                    cont.resume(emptyList())
                }
            }
        }

    /**
     * 무게 조회
     */
    private suspend fun getWeightData(start: Long, end: Long): List<Map<String, Any>> =
        withContext(Dispatchers.Main) {
            suspendCoroutine { cont ->
                try {
                    Log.d(APP_TAG, "무게 데이터 시작")
                    val request = ReadRequest.Builder().setDataType(Weight.HEALTH_DATA_TYPE).setLocalTimeRange(
                        HealthConstants.Weight.START_TIME, HealthConstants.Weight.TIME_OFFSET, start, end
                    ).setSort(HealthConstants.Weight.START_TIME, HealthDataResolver.SortOrder.DESC).setProperties(
                        arrayOf(
                            HealthConstants.Weight.DEVICE_UUID,
                            HealthConstants.Weight.START_TIME,
                            HealthConstants.Weight.TIME_OFFSET,
                            HealthConstants.Weight.WEIGHT,
                            HealthConstants.Weight.HEIGHT,
                            HealthConstants.Weight.BODY_FAT,
                            HealthConstants.Weight.SKELETAL_MUSCLE,
                            HealthConstants.Weight.MUSCLE_MASS,
                            HealthConstants.Weight.BASAL_METABOLIC_RATE,
                            HealthConstants.Weight.BODY_FAT_MASS,
                            HealthConstants.Weight.FAT_FREE_MASS,
                            HealthConstants.Weight.FAT_FREE,
                            HealthConstants.Weight.SKELETAL_MUSCLE_MASS,
                            HealthConstants.Weight.TOTAL_BODY_WATER
                        )
                    ).build()

                    val resolver = HealthDataResolver(mStore, null)
                    val weightList = mutableListOf<Map<String, Any>>()
                    resolver.read(request).setResultListener { dataResult ->
                        for (data in dataResult) {
                            weightList.add(
                                mapOf(
                                    "device_uuid" to data.getString(HealthConstants.Weight.DEVICE_UUID),
                                    "start_time" to data.getLong(HealthConstants.Weight.START_TIME),
                                    "time_offset" to data.getLong(HealthConstants.Weight.TIME_OFFSET),
                                    "weight" to data.getFloat(HealthConstants.Weight.WEIGHT),
                                    "height" to data.getFloat(HealthConstants.Weight.HEIGHT),
                                    "body_fat" to data.getFloat(HealthConstants.Weight.BODY_FAT),
                                    "skeletal_muscle" to data.getFloat(HealthConstants.Weight.SKELETAL_MUSCLE),
                                    "muscle_mass" to data.getFloat(HealthConstants.Weight.MUSCLE_MASS),
                                    "basal_metabolic_rate" to data.getInt(HealthConstants.Weight.BASAL_METABOLIC_RATE),
                                    "body_fat_mass" to data.getFloat(HealthConstants.Weight.BODY_FAT_MASS),
                                    "fat_free_mass" to data.getFloat(HealthConstants.Weight.FAT_FREE_MASS),
                                    "fat_free" to data.getFloat(HealthConstants.Weight.FAT_FREE),
                                    "skeletal_muscle_mass" to data.getFloat(HealthConstants.Weight.SKELETAL_MUSCLE_MASS),
                                    "total_body_water" to data.getFloat(HealthConstants.Weight.TOTAL_BODY_WATER)
                                )
                            )
                        }
                        Log.d(APP_TAG, "무게 데이터 종료")
                        cont.resume(weightList)
                    }
                } catch (e: Exception) {
                    Log.e(APP_TAG, "무게 데이터 Exception: ${e.message}")
                    cont.resume(emptyList())
                }
            }
        }

    /**
     * 산소 포화도 조회
     */
    private suspend fun getOxygenSaturationData(start: Long, end: Long): List<Map<String, Any>> =
        withContext(Dispatchers.Main) {
            suspendCoroutine { cont ->
                try {
                    Log.d(APP_TAG, "산소 포화도 데이터 시작")
                    val request =
                        ReadRequest.Builder().setDataType(OxygenSaturation.HEALTH_DATA_TYPE).setLocalTimeRange(
                            HealthConstants.OxygenSaturation.START_TIME,
                            HealthConstants.OxygenSaturation.TIME_OFFSET,
                            start,
                            end
                        ).setSort(HealthConstants.OxygenSaturation.START_TIME, HealthDataResolver.SortOrder.DESC)
                            .setProperties(
                                arrayOf(
                                    HealthConstants.OxygenSaturation.DEVICE_UUID,
                                    HealthConstants.OxygenSaturation.START_TIME,
                                    HealthConstants.OxygenSaturation.END_TIME,
                                    HealthConstants.OxygenSaturation.TIME_OFFSET,
                                    HealthConstants.OxygenSaturation.SPO2,
                                    HealthConstants.OxygenSaturation.HEART_RATE
                                )
                            ).build()

                    val resolver = HealthDataResolver(mStore, null)
                    val oxygenSaturationList = mutableListOf<Map<String, Any>>()
                    resolver.read(request).setResultListener { dataResult ->
                        for (data in dataResult) {
                            oxygenSaturationList.add(
                                mapOf(
                                    "device_uuid" to data.getString(HealthConstants.OxygenSaturation.DEVICE_UUID),
                                    "start_time" to data.getLong(HealthConstants.OxygenSaturation.START_TIME),
                                    "end_time" to data.getLong(HealthConstants.OxygenSaturation.END_TIME),
                                    "time_offset" to data.getLong(HealthConstants.OxygenSaturation.TIME_OFFSET),
                                    "spo2" to data.getFloat(HealthConstants.OxygenSaturation.SPO2),
                                    "heart_rate" to data.getFloat(HealthConstants.OxygenSaturation.HEART_RATE)
                                )
                            )
                        }
                        Log.d(APP_TAG, "산소 포화도 데이터 종료")
                        cont.resume(oxygenSaturationList)
                    }
                } catch (e: Exception) {
                    Log.e(APP_TAG, "산소 포화도 데이터 Exception: ${e.message}")
                    cont.resume(emptyList())
                }
            }
        }

    /**
     * 체온 조회
     */
    private suspend fun getBodyTemperatureData(start: Long, end: Long): List<Map<String, Any>> =
        withContext(Dispatchers.Main) {
            suspendCoroutine { cont ->
                try {
                    Log.d(APP_TAG, "체온 데이터 시작")
                    val request =
                        ReadRequest.Builder().setDataType(BodyTemperature.HEALTH_DATA_TYPE).setLocalTimeRange(
                            HealthConstants.BodyTemperature.START_TIME,
                            HealthConstants.BodyTemperature.TIME_OFFSET,
                            start,
                            end
                        ).setSort(HealthConstants.BodyTemperature.START_TIME, HealthDataResolver.SortOrder.DESC)
                            .setProperties(
                                arrayOf(
                                    HealthConstants.BodyTemperature.DEVICE_UUID,
                                    HealthConstants.BodyTemperature.START_TIME,
                                    HealthConstants.BodyTemperature.TIME_OFFSET,
                                    HealthConstants.BodyTemperature.TEMPERATURE,
                                )
                            ).build()

                    val resolver = HealthDataResolver(mStore, null)
                    val bodyTemperatureList = mutableListOf<Map<String, Any>>()
                    resolver.read(request).setResultListener { dataResult ->
                        for (data in dataResult) {
                            bodyTemperatureList.add(
                                mapOf(
                                    "device_uuid" to data.getString(HealthConstants.BodyTemperature.DEVICE_UUID),
                                    "start_time" to data.getLong(HealthConstants.BodyTemperature.START_TIME),
                                    "time_offset" to data.getLong(HealthConstants.BodyTemperature.TIME_OFFSET),
                                    "temperature" to data.getFloat(HealthConstants.BodyTemperature.TEMPERATURE),
                                )
                            )
                        }
                        Log.d(APP_TAG, "체온 데이터 종료")
                        cont.resume(bodyTemperatureList)
                    }
                } catch (e: Exception) {
                    Log.e(APP_TAG, "체온 데이터 Exception: ${e.message}")
                    cont.resume(emptyList())
                }
            }
        }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        events?.let {
            eventSinks.add(it)
            if (::mStore.isInitialized) {
                registerObservers()
            } else {
                Log.w(APP_TAG, "Store not initialized yet, observer not registered")
            }
        }
    }

    override fun onCancel(arguments: Any?) {
        eventSinks.clear()
        if (::mStore.isInitialized) {
            unregisterObservers()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
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

    object ExerciseTypeMapper {
        private val typeMap = mapOf(
            1001 to "Walking",
            1002 to "Running",
            2001 to "Baseball, general",
            2002 to "Softball, general",
            2003 to "Cricket",
            3001 to "Golf, general",
            3002 to "Billiards",
            3003 to "Bowling, alley",
            4001 to "Hockey",
            4002 to "Rugby, touch, non-competitive",
            4003 to "Basketball, general",
            4004 to "Football, general (Soccer)",
            4005 to "Handball, general",
            4006 to "American football, general, touch",
            5001 to "Volleyball, general, 6~9 member team, non-competitive",
            5002 to "Beach volleyball",
            6001 to "Squash, general",
            6002 to "Tennis, general",
            6003 to "Badminton, competitive",
            6004 to "Table tennis",
            6005 to "Racquetball, general",
            7001 to "T'ai chi, general(- Deprecated, use 7003 (Martial arts)",
            7002 to "Boxing, in ring",
            7003 to "Martial arts, moderate pace (Judo, Jujitsu, Karate, Taekwondo)",
            8001 to "Ballet, general, rehearsal or class",
            8002 to "Dancing, general (Fork, Irish step, Polka)",
            8003 to "Ballroom dancing, fast",
            9001 to "Pilates",
            9002 to "Yoga",
            10001 to "Stretching",
            10002 to "Jump rope, moderate pace (100~120 skips/min), 2 foot skip",
            10003 to "Hula-hooping",
            10004 to "Push-ups (Press-ups)",
            10005 to "Pull-ups (Chin-up)",
            10006 to "Sit-ups",
            10007 to "Circuit training, moderate effort",
            10008 to "Mountain climbers",
            10009 to "Jumping Jacks",
            10010 to "Burpee",
            10011 to "Bench press",
            10012 to "Squats",
            10013 to "Lunges",
            10014 to "Leg presses",
            10015 to "Leg extensions",
            10016 to "Leg curls",
            10017 to "Back extensions",
            10018 to "Lat pull-downs",
            10019 to "Deadlifts",
            10020 to "ShoulderPresses",
            10021 to "Front raises",
            10022 to "Lateral raises",
            10023 to "Crunches",
            10024 to "Leg raises",
            10025 to "Plank",
            10026 to "Arm curls",
            10027 to "Arm extensions",
            11001 to "Inline skating, moderate pace",
            11002 to "Hang gliding",
            11003 to "Pistol shooting",
            11004 to "Archery, non-hunting",
            11005 to "Horseback riding, general",
            11007 to "Cycling",
            11008 to "Flying disc, general, playing",
            11009 to "Roller skating",
            12001 to "Aerobics, general",
            13001 to "Hiking",
            13002 to "Rock climbing, low to moderate difficulty",
            13003 to "Backpacking",
            13004 to "Mountain biking, general",
            13005 to "Orienteering",
            14001 to "Swimming, general, leisurely, not lap swimming",
            14002 to "Aquarobics",
            14003 to "Canoeing, general, for pleasure",
            14004 to "Sailing, leisure, ocean sailing",
            14005 to "Scuba diving, general",
            14006 to "Snorkeling",
            14007 to "Kayaking, moderate effort",
            14008 to "Kitesurfing",
            14009 to "Rafting",
            14010 to "Rowing machine, general, for pleasure",
            14011 to "Windsurfing, general",
            14012 to "Yachting, leisure",
            14013 to "Water skiing",
            15001 to "Step machine",
            15002 to "Weight machine",
            15003 to "Exercise bike, Moderate to vigorous effort (90-100 watts)",
            15004 to "Rowing machine",
            15005 to "Treadmill, combination of jogging and walking",
            15006 to "Elliptical trainer, moderate effort",
            16001 to "Cross-country skiing, general, moderate speed (4.0~4.9 mph)",
            16002 to "Skiing, general, downhill, moderate effort",
            16003 to "Ice dancing",
            16004 to "Ice skating, general",
            16006 to "Ice hockey, general",
            16007 to "Snowboarding, general, moderate effort",
            16008 to "Alpine skiing, general, moderate effort",
            16009 to "Snowshoeing, moderate effort"
        )

        fun getName(type: Int): String {
            return typeMap[type] ?: "Unknown"
        }
    }

    object SleepTypeMapper {
        private val map = mapOf(
            40001 to "Awake", 40002 to "Light", 40003 to "Deep", 40004 to "REM"
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
}