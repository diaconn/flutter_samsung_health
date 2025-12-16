package com.example.flutter_samsung_health

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import com.samsung.android.sdk.health.data.request.InstantTimeFilter
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.data.Change
import com.samsung.android.sdk.health.data.data.ChangeType
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.error.HealthDataException
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.EventChannel.StreamHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** FlutterSamsungHealth - Samsung Health Data SDK 1.0.0 */
class FlutterSamsungHealth : FlutterPlugin, MethodCallHandler, ActivityAware, StreamHandler {

    private lateinit var channel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private lateinit var context: Context
    private var healthDataStore: HealthDataStore? = null
    private var activity: Activity? = null
    private var eventSink: EventSink? = null

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

    // ===== 옵저버 관련 선언 =====
    /**
     * 옵저버가 감시할 수 있는 데이터 타입
     */
    enum class ObserverDataType(val typeName: String) {
        EXERCISE("exercise"),
        NUTRITION("nutrition"), 
        BLOOD_GLUCOSE("blood_glucose")
    }
    
    /**
     * 옵저버 상태
     */
    enum class ObserverStatus {
        STOPPED,    // 중단됨
        RUNNING,    // 실행중
        ERROR       // 오류 상태
    }
    
    /**
     * 옵저버 상태 관리 클래스
     */
    data class ObserverState(
        val dataType: ObserverDataType,
        val status: ObserverStatus,
        val lastSyncTime: Long = 0L,
        val errorMessage: String? = null
    )
    
    // 통합 옵저버 상태 맵
    private val observerStates = mutableMapOf<ObserverDataType, ObserverState>()
    private val observerJobs = mutableMapOf<ObserverDataType, kotlinx.coroutines.Job>()
    
    // 코루틴 스코프 (옵저버용)
    private var observerScope: CoroutineScope? = null
    
    // 각 데이터 타입별 마지막 동기화 시간 저장
    private val lastSyncTimes = mutableMapOf<ObserverDataType, Instant>()
    
    // 처리된 UID 캐시 (중복 방지용)
    private val processedUids = mutableMapOf<ObserverDataType, MutableSet<String>>()
    private val uidCacheCleanupTime = mutableMapOf<ObserverDataType, Long>()
    
    // SharedPreferences for persistent observer state
    private lateinit var sharedPreferences: SharedPreferences
    private var hasRestoredOnce = false // 한 번만 복원되도록 플래그
    private companion object {
        const val OBSERVER_PREFS = "samsung_health_observers"
        const val OBSERVER_STATE_PREFIX = "observer_"
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_samsung_health")
        channel.setMethodCallHandler(this)
        
        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "flutter_samsung_health/stream")
        eventChannel.setStreamHandler(this)

        // SharedPreferences 초기화
        sharedPreferences = context.getSharedPreferences(OBSERVER_PREFS, Context.MODE_PRIVATE)
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
            "openSamsungHealthPermissions" -> openSamsungHealthPermissions(wrapper)
            "startObserver" -> {
                val dataTypes = call.argument<List<String>?>("dataTypes")
                startObserver(dataTypes, wrapper)
            }
            "stopObserver" -> {
                val dataTypes = call.argument<List<String>?>("dataTypes")
                stopObserver(dataTypes, wrapper)
            }
            "getObserverStatus" -> {
                val dataTypes = call.argument<List<String>?>("dataTypes")
                getObserverStatus(dataTypes, wrapper)
            }
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
            "getSleepData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                getSleepData(start, end, wrapper)
            }
            "getNutritionData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                getNutritionData(start, end, wrapper)
            }
            "getBloodGlucoseData" -> {
                val start = call.argument<Long>("start")!!
                val end = call.argument<Long>("end")!!
                getBloodGlucoseData(start, end, wrapper)
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
            else -> wrapper.notImplemented()
        }
    }

    /// 앱 설치 여부 확인
    private fun isSamsungHealthInstalled(wrapper: ResultWrapper) {
        Log.d(APP_TAG, "isSamsungHealthInstalled() 호출")
        try {
            val packageInfo = context.packageManager.getPackageInfo("com.sec.android.app.shealth", 0)
            wrapper.success(mapOf(
                "success" to true,
                "result" to mapOf(
                    "isInstalled" to true,
                    "status" to "installed",
                    "versionName" to (packageInfo.versionName ?: "Unknown")
                ),
                "message" to "설치 확인 완료"
            ))
        } catch (e: Exception) {
            wrapper.success(mapOf(
                "success" to true,
                "result" to mapOf(
                    "isInstalled" to false,
                    "status" to "not_installed"
                ),
                "message" to "설치 확인 완료"
            ))
        }
    }

    /// 앱 실행
    private fun openSamsungHealth(wrapper: ResultWrapper) {
        Log.d(APP_TAG, "openSamsungHealth() 호출")
        val packageName = "com.sec.android.app.shealth"

        try {
            val packageManager = context.packageManager
            packageManager.getPackageInfo(packageName, 0)

            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                wrapper.success(mapOf(
                    "success" to true,
                    "result" to mapOf(
                        "isLaunched" to true,
                        "status" to "launched"
                    ),
                    "message" to "앱 실행 완료"
                ))
            } else {
                wrapper.success(mapOf(
                    "success" to true,
                    "result" to mapOf(
                        "isLaunched" to false,
                        "status" to "error"
                    ),
                    "message" to "앱 실행 실패 - 인텐트를 찾을 수 없음"
                ))
            }
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=$packageName")
                    setPackage("com.android.vending")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                wrapper.success(mapOf(
                    "success" to true,
                    "result" to mapOf(
                        "isLaunched" to false,
                        "status" to "moved_to_store"
                    ),
                    "message" to "앱 실행 완료"
                ))
            } catch (storeException: Exception) {
                wrapper.success(mapOf(
                    "success" to true,
                    "result" to mapOf(
                        "isLaunched" to false,
                        "status" to "error"
                    ),
                    "message" to "앱 실행 실패 - 앱 및 스토어 접근 불가"
                ))
            }
        }
    }


    /// SDK 연결
    private fun connect(wrapper: ResultWrapper) {
        Log.d(APP_TAG, "connect() 호출")

        runCatching {
            healthDataStore = HealthDataService.getStore(context)
        }.onSuccess {
            Log.i(APP_TAG, "Health data service is connected")
            wrapper.success(mapOf(
                "success" to true,
                "result" to mapOf(
                    "isConnected" to true,
                    "connectionTime" to System.currentTimeMillis()
                ),
                "message" to "연결 완료"
            ))
            
            // 연결 성공 후 저장된 옵저버 상태 복원 (매번 체크)
            restoreObserverStatesIfNeeded()
        }.onFailure { error ->
            Log.e(APP_TAG, "연결 실패: ${error.message}")

            when (error) {
                is ResolvablePlatformException -> {
                    if (error.hasResolution && activity != null) {
                        Log.i(APP_TAG, "ResolvablePlatformException - attempting to resolve")
                        
                        // 비동기적으로 resolve 실행
                        runCatching {
                            error.resolve(activity!!)
                        }.onFailure { resolveError ->
                            Log.e(APP_TAG, "Resolution failed: ${resolveError.message}")
                        }
                        
                        wrapper.success(mapOf(
                            "success" to true,
                            "result" to mapOf(
                                "isConnected" to false,
                                "status" to "user_action_required"
                            ),
                            "message" to "연결 완료 - 사용자 액션 필요"
                        ))
                    } else {
                        wrapper.success(mapOf(
                            "success" to true,
                            "result" to mapOf(
                                "isConnected" to false,
                                "status" to "error"
                            ),
                            "message" to "연결 완료 - Activity 부재"
                        ))
                    }
                }

                is HealthDataException -> {
                    wrapper.success(mapOf(
                        "success" to true,
                        "result" to mapOf(
                            "isConnected" to false,
                            "status" to "error"
                        ),
                        "message" to "연결 실패 - Samsung Health 오류"
                    ))
                }

                else -> {
                    wrapper.success(mapOf(
                        "success" to true,
                        "result" to mapOf(
                            "isConnected" to false,
                            "status" to "error"
                        ),
                        "message" to "연결 실패 - 알 수 없는 오류"
                    ))
                }
            }
        }
    }

    /// SDK 연결 해제
    private fun disconnect(wrapper: ResultWrapper) {
        Log.d(APP_TAG, "disconnect() 호출")
        
        val wasConnected = healthDataStore != null
        
        if (!wasConnected) {
            wrapper.success(mapOf(
                "success" to true,
                "result" to mapOf(
                    "isDisconnected" to true,
                    "status" to "already_disconnected"
                ),
                "message" to "연결 해제 완료"
            ))
            return
        }
        
        try {
            // 연결 해제 시 모든 옵저버 완전 정리
            cleanupObservers()
            
            wrapper.success(mapOf(
                "success" to true,
                "result" to mapOf(
                    "isDisconnected" to true,
                    "status" to "disconnected"
                ),
                "message" to "연결 해제 완료"
            ))
        } catch (e: Exception) {
            Log.e(APP_TAG, "연결 해제 실패: ${e.message}")
            wrapper.success(mapOf(
                "success" to true,
                "result" to mapOf(
                    "isDisconnected" to false,  // 연결 해제에 실패 = 여전히 연결됨
                    "status" to "error"
                ),
                "message" to "연결 해제 실패 - 알 수 없는 오류"
            ))
        } finally {
            // finally에서 무조건 실행 (이것도 실패할 수 있지만...)
            try {
                healthDataStore = null
            } catch (finalE: Exception) {
                Log.e(APP_TAG, "healthDataStore null 설정 실패: ${finalE.message}")
            }
        }
    }

    /// 데이터 권한 요청
    private fun requestPermissions(types: List<String>, wrapper: ResultWrapper) {
        Log.d(APP_TAG, "requestPermissions() 호출: $types")

        val store = healthDataStore
        if (store == null) {
            wrapper.success(mapOf(
                "success" to false,
                "result" to mapOf(
                    "granted" to emptyList<String>(),
                    "denied" to types,
                    "status" to "error"
                ),
                "message" to "권한 요청 실패 - 연결 필요",
                "error" to "STORE_NOT_READY"
            ))
            return
        }

        val act = activity
        if (act == null) {
            wrapper.success(mapOf(
                "success" to false,
                "result" to mapOf(
                    "granted" to emptyList<String>(),
                    "denied" to types,
                    "status" to "error"
                ),
                "message" to "권한 요청 실패 - Activity 부재",
                "error" to "ACTIVITY_NOT_READY"
            ))
            return
        }

        if (act.isDestroyed || act.isFinishing) {
            wrapper.success(mapOf(
                "success" to false,
                "result" to mapOf(
                    "granted" to emptyList<String>(),
                    "denied" to types,
                    "status" to "error"
                ),
                "message" to "권한 요청 실패 - Activity 무효",
                "error" to "ACTIVITY_INVALID"
            ))
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
            wrapper.success(mapOf(
                "success" to true,
                "result" to mapOf(
                    "granted" to emptyList<String>(),
                    "denied" to emptyList<String>(),
                    "status" to "no_permissions"
                ),
                "message" to "권한 요청 완료"
            ))
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            runCatching {
                // 항상 권한 다이얼로그 표시
                store.requestPermissions(permissionsToRequest, act)
                kotlinx.coroutines.delay(1500) // 권한 다이얼로그 처리 대기

                val finalGrantedPermissions = store.getGrantedPermissions(permissionsToRequest)

                val grantedList = permissionsToRequest
                    .filter { finalGrantedPermissions.contains(it) }
                    .map { _getDataTypeNameForPermission(it) }

                val deniedList = permissionsToRequest
                    .filter { !finalGrantedPermissions.contains(it) }
                    .map { _getDataTypeNameForPermission(it) }

                val allGranted = deniedList.isEmpty()
                wrapper.success(mapOf(
                    "success" to true,
                    "result" to mapOf(
                        "granted" to grantedList,
                        "denied" to deniedList,
                        "status" to if (allGranted) "all_granted" else "partial_granted"
                    ),
                    "message" to if (allGranted)
                        "권한 요청 완료"
                    else
                        "권한 요청 완료"
                ))
            }.onFailure { error ->
                Log.e(APP_TAG, "권한 요청 실패: ${error.message}")

                when (error) {
                    is ResolvablePlatformException -> {
                        if (error.hasResolution) {
                            runCatching { error.resolve(act) }
                        }
                        wrapper.success(mapOf(
                            "success" to true,
                            "result" to mapOf(
                                "granted" to emptyList<String>(),
                                "denied" to types,
                                "status" to "user_action_required"
                            ),
                            "message" to "권한 요청 완료 - 사용자 액션 필요"
                        ))
                    }

                    is HealthDataException -> {
                        wrapper.success(mapOf(
                            "success" to false,
                            "result" to mapOf(
                                "granted" to emptyList<String>(),
                                "denied" to types,
                                "status" to "error"
                            ),
                            "message" to "권한 요청 실패 - Samsung Health 오류",
                            "error" to "HEALTH_DATA_ERROR"
                        ))
                    }

                    else -> {
                        wrapper.success(mapOf(
                            "success" to false,
                            "result" to mapOf(
                                "granted" to emptyList<String>(),
                                "denied" to types,
                                "status" to "error"
                            ),
                            "message" to "권한 요청 실패 - 알 수 없는 오류",
                            "error" to "PERMISSION_ERROR"
                        ))
                    }
                }
            }
        }
    }

    /// 승인된 권한 조회
    private fun getGrantedPermissions(wrapper: ResultWrapper) {
        Log.d(APP_TAG, "getGrantedPermissions() 호출")

        val store = healthDataStore
        if (store == null) {
            wrapper.success(mapOf(
                "success" to false,
                "result" to mapOf(
                    "granted" to emptyList<String>(),
                    "status" to "error"
                ),
                "message" to "권한 조회 실패 - Samsung Health에 먼저 연결하세요",
                "error" to "STORE_NOT_READY"
            ))
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            runCatching {
                val grantedPermissions = store.getGrantedPermissions(allPermissions)
                val grantedList = grantedPermissions.map { _getDataTypeNameForPermission(it) }
                Log.i(APP_TAG, "권한 조회 성공: ${grantedList.size}개 권한 허용됨")

                wrapper.success(mapOf(
                    "success" to true,
                    "result" to mapOf(
                        "granted" to grantedList,
                        "status" to if (grantedList.isEmpty()) "no_permissions" else "has_permissions"
                    ),
                    "message" to if (grantedList.isEmpty())
                        "권한 조회 완료 - 허용된 권한이 없습니다"
                    else
                        "권한 조회 완료 - ${grantedList.size}개 권한이 허용되었습니다"
                ))
            }.onFailure { error ->
                Log.e(APP_TAG, "권한 조회 실패: ${error.message}", error)

                when (error) {
                    is HealthDataException -> {
                        wrapper.success(mapOf(
                            "success" to false,
                            "result" to mapOf(
                                "granted" to emptyList<String>(),
                                "status" to "error"
                            ),
                            "message" to "권한 조회 실패 - Samsung Health 오류",
                            "error" to "HEALTH_DATA_ERROR"
                        ))
                    }

                    else -> {
                        wrapper.success(mapOf(
                            "success" to false,
                            "result" to mapOf(
                                "granted" to emptyList<String>(),
                                "status" to "error"
                            ),
                            "message" to "권한 조회 실패 - 알 수 없는 오류",
                            "error" to "PERMISSION_QUERY_ERROR"
                        ))
                    }
                }
            }
        }
    }

    /// 권한 설정 화면 열기
    private fun openSamsungHealthPermissions(wrapper: ResultWrapper) {
        Log.d(APP_TAG, "openSamsungHealthPermissions() 호출")

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
                wrapper.success(mapOf(
                    "success" to true,
                    "result" to mapOf(
                        "isOpened" to true,
                        "status" to "opened_permissions"
                    ),
                    "message" to "권한 설정 화면 열기 완료"
                ))
            } else {
                // 일반 Samsung Health 앱 실행
                val generalIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (generalIntent != null) {
                    generalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(generalIntent)
                    wrapper.success(mapOf(
                        "success" to true,
                        "result" to mapOf(
                            "isOpened" to true,
                            "status" to "opened_app"
                        ),
                        "message" to "권한 설정 화면 열기 완료"
                    ))
                } else {
                    wrapper.success(mapOf(
                        "success" to false,
                        "result" to mapOf(
                            "isOpened" to false,
                            "status" to "app_not_found"
                        ),
                        "message" to "권한 설정 화면 열기 실패 - 앱 미설치"
                    ))
                }
            }
        }.onFailure { error ->
            Log.e(APP_TAG, "Samsung Health 권한 화면 열기 실패: ${error.message}")
            wrapper.success(mapOf(
                "success" to false,
                "result" to mapOf(
                    "isOpened" to false,
                    "status" to "error"
                ),
                "message" to "권한 화면 열기 실패 - 알 수 없는 오류",
                "error" to "PERMISSION_SCREEN_ERROR"
            ))
        }
    }
    
    /**
     * 통합 옵저버 시작
     */
    /// 옵저버 시작
    private fun startObserver(dataTypeNames: List<String>?, wrapper: ResultWrapper) {
        Log.d(APP_TAG, "startObserver() 호출: $dataTypeNames")
        
        // 연결 상태와 무관하게 옵저버 상태 관리 (실제 동작은 연결 시에만)
        
        // 데이터 타입 결정
        val targetTypes = when {
            dataTypeNames == null || dataTypeNames.isEmpty() -> {
                // null이거나 비어있으면 모든 타입
                ObserverDataType.values().toList()
            }
            else -> {
                // 지정된 타입들만 처리
                dataTypeNames.mapNotNull { parseObserverDataType(it) }
            }
        }
        
        if (targetTypes.isEmpty()) {
            wrapper.success(mapOf(
                "success" to false,
                "result" to mapOf(
                    "started" to emptyList<String>(),
                    "already_running" to emptyList<String>(),
                    "failed" to emptyList<String>(),
                    "status" to "error"
                ),
                "message" to "옵저버 시작 실패 - 유효한 데이터 타입 없음",
                "error" to "INVALID_DATA_TYPES"
            ))
            return
        }
        
        // 옵저버 스코프 초기화
        if (observerScope == null) {
            observerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
        
        // 코루틴 스코프에서 전체 로직 실행
        observerScope?.launch {
            val successTypes = mutableListOf<String>()
            val failedTypes = mutableListOf<String>()
            val alreadyRunningTypes = mutableListOf<String>()
            
            for (dataType in targetTypes) {
                // 권한 체크 먼저 (suspend 함수이므로 코루틴 내에서 실행)
                if (!hasPermissionForObserverType(dataType)) {
                    Log.w(APP_TAG, "[${dataType.typeName}] 권한 없음 - 옵저버 시작 스킵")
                    failedTypes.add(dataType.typeName)
                    continue
                }
                
                val currentState = observerStates[dataType]
                if (currentState?.status == ObserverStatus.RUNNING) {
                    alreadyRunningTypes.add(dataType.typeName)
                } else {
                    // 상태 초기화
                    observerStates[dataType] = ObserverState(
                        dataType = dataType,
                        status = ObserverStatus.RUNNING,
                        lastSyncTime = System.currentTimeMillis()
                    )
                    
                    // SharedPreferences에 상태 저장
                    saveObserverState(dataType, true)
                    
                    // UID 캐시 초기화
                    if (!observerJobs.containsKey(dataType)) {
                        processedUids[dataType]?.clear()
                    }
                    
                    // Samsung Health 연결 상태에 따라 실제 Job 시작 여부 결정
                    val store = healthDataStore
                    if (store != null) {
                        // 연결되어 있으면 옵저버 Job 시작
                        val job = observerScope!!.launch {
                            observeDataType(store, dataType)
                        }
                        observerJobs[dataType] = job
                    }
                    
                    successTypes.add(dataType.typeName)
                }
            }
            
            val overallStatus = when {
                successTypes.isNotEmpty() && alreadyRunningTypes.isNotEmpty() -> "partially_started"
                alreadyRunningTypes.isNotEmpty() && successTypes.isEmpty() -> "already_running"
                successTypes.isNotEmpty() -> "started"
                else -> "failed"
            }
            
            wrapper.success(mapOf(
                "success" to (successTypes.isNotEmpty() || alreadyRunningTypes.isNotEmpty()),
                "result" to mapOf(
                    "started" to successTypes,
                    "already_running" to alreadyRunningTypes,
                    "failed" to failedTypes,
                    "status" to overallStatus
                ),
                "message" to "옵저버 시작 완료"
            ))
        }
    }
    
    /**
     * 통합 옵저버 중단
     */
    /// 옵저버 중단
    private fun stopObserver(dataTypeNames: List<String>?, wrapper: ResultWrapper) {
        Log.d(APP_TAG, "stopObserver() 호출: $dataTypeNames")
        
        // 데이터 타입 결정
        val targetTypes = when {
            dataTypeNames == null || dataTypeNames.isEmpty() -> {
                // null이거나 비어있으면 모든 타입
                ObserverDataType.values().toList()
            }
            else -> {
                // 지정된 타입들만 처리
                dataTypeNames.mapNotNull { parseObserverDataType(it) }
            }
        }
        
        if (targetTypes.isEmpty()) {
            wrapper.success(mapOf(
                "success" to false,
                "result" to mapOf(
                    "stopped" to emptyList<String>(),
                    "not_running" to emptyList<String>(),
                    "status" to "error"
                ),
                "message" to "옵저버 중단 실패 - 유효한 데이터 타입 없음",
                "error" to "INVALID_DATA_TYPES"
            ))
            return
        }
        
        val stoppedTypes = mutableListOf<String>()
        val notRunningTypes = mutableListOf<String>()
        
        for (dataType in targetTypes) {
            val job = observerJobs[dataType]
            if (job != null) {
                job.cancel()
                observerJobs.remove(dataType)
                
                // 상태 업데이트
                observerStates[dataType] = ObserverState(
                    dataType = dataType,
                    status = ObserverStatus.STOPPED,
                    lastSyncTime = System.currentTimeMillis()
                )
                
                // SharedPreferences에 상태 저장
                saveObserverState(dataType, false)
                
                stoppedTypes.add(dataType.typeName)
            } else {
                notRunningTypes.add(dataType.typeName)
            }
        }
        
        val overallStatus = when {
            stoppedTypes.isNotEmpty() && notRunningTypes.isNotEmpty() -> "partially_stopped"
            stoppedTypes.isNotEmpty() -> "stopped"
            else -> "not_running"
        }
        
        wrapper.success(mapOf(
            "success" to true,
            "result" to mapOf(
                "stopped" to stoppedTypes,
                "not_running" to notRunningTypes,
                "status" to overallStatus
            ),
            "message" to "옵저버 중단 완료"
        ))
    }
    
    /**
     * 통합 옵저버 상태 조회
     */
    /// 옵저버 상태 조회
    private fun getObserverStatus(dataTypeNames: List<String>?, wrapper: ResultWrapper) {
        Log.d(APP_TAG, "getObserverStatus() 호출: $dataTypeNames")
        
        // 데이터 타입 결정
        val targetTypes = when {
            dataTypeNames == null || dataTypeNames.isEmpty() -> {
                // null이거나 비어있으면 모든 타입
                ObserverDataType.values().toList()
            }
            else -> {
                // 지정된 타입들만 처리
                dataTypeNames.mapNotNull { parseObserverDataType(it) }
            }
        }
        
        if (targetTypes.isEmpty()) {
            wrapper.success(mapOf(
                "success" to false,
                "result" to mapOf(
                    "running" to emptyList<String>(),
                    "stopped" to emptyList<String>(),
                    "status" to "error"
                ),
                "message" to "옵저버 상태 조회 실패 - 유효한 데이터 타입 없음",
                "error" to "INVALID_DATA_TYPES"
            ))
            return
        }
        
        val runningTypes = mutableListOf<String>()
        val stoppedTypes = mutableListOf<String>()
        
        for (dataType in targetTypes) {
            val state = observerStates[dataType] ?: ObserverState(
                dataType = dataType,
                status = ObserverStatus.STOPPED
            )
            
            if (state.status == ObserverStatus.RUNNING) {
                runningTypes.add(dataType.typeName)
            } else {
                stoppedTypes.add(dataType.typeName)
            }
        }
        
        val overallStatus = when {
            runningTypes.isNotEmpty() && stoppedTypes.isNotEmpty() -> "mixed"
            runningTypes.isNotEmpty() -> "running"
            else -> "stopped"
        }
        
        wrapper.success(mapOf(
            "success" to true,
            "result" to mapOf(
                "running" to runningTypes,
                "stopped" to stoppedTypes,
                "status" to overallStatus
            ),
            "message" to "옵저버 상태 조회 완료"
        ))
    }

    /// 전체 데이터 조회
    private fun getTotalData(start: Long, end: Long, wrapper: ResultWrapper) {
        val store = healthDataStore
        if (store == null) {
            wrapper.success(mapOf(
                "success" to false,
                "result" to emptyMap<String, List<Map<String, Any>>>(),
                "message" to "전체 데이터 조회 실패 - Samsung Health에 먼저 연결하세요",
                "error" to "STORE_NOT_READY"
            ))
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val startTime = Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDateTime()
                val endTime = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDateTime()
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
                    "steps" to steps,
                    "nutrition" to nutrition,
                    "body_composition" to bodyComposition,
                    "blood_oxygen" to bloodOxygen,
                    "body_temperature" to bodyTemperature,
                    "blood_glucose" to bloodGlucose,
                )

                Log.i(APP_TAG, "전체 데이터 조회 완료")
                totalResult
            }.onSuccess { result ->
                withContext(Dispatchers.Main) {
                    wrapper.success(mapOf(
                        "success" to true,
                        "result" to result,
                        "message" to "전체 데이터 조회 완료"
                    ))
                }
            }.onFailure { error ->
                Log.e(APP_TAG, "전체 데이터 조회 실패: ${error.message}", error)
                withContext(Dispatchers.Main) {
                    when (error) {
                        is HealthDataException -> {
                            wrapper.success(mapOf(
                                "success" to false,
                                "result" to emptyMap<String, List<Map<String, Any>>>(),
                                "message" to "전체 데이터 조회 실패 - 오류: ${error.message}",
                                "error" to "HEALTH_DATA_ERROR"
                            ))
                        }

                        else -> {
                            wrapper.success(mapOf(
                                "success" to false,
                                "result" to emptyMap<String, List<Map<String, Any>>>(),
                                "message" to "전체 데이터 조회 실패 - 오류: ${error.message ?: "알 수 없는 오류"}",
                                "error" to "TOTAL_DATA_ERROR"
                            ))
                        }
                    }
                }
            }
        }
    }

    /// 운동 데이터 조회
    private fun getExerciseData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper, "운동") { store, startTime, endTime ->
            readExerciseData(store, startTime, endTime)
        }
    }

    /// 심박수 데이터 조회
    private fun getHeartRateData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper, "심박수") { store, startTime, endTime ->
            readHeartRateData(store, startTime, endTime)
        }
    }

    /// 수면 데이터 조회
    private fun getSleepData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper, "수면") { store, startTime, endTime ->
            readSleepData(store, startTime, endTime)
        }
    }

    /// 걸음수 데이터 조회
    private fun getStepsData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper, "걸음") { store, startTime, endTime ->
            readStepsData(store, startTime, endTime)
        }
    }

    /// 5분 간격 걸음수 데이터 조회
    private fun getFiveMinuteStepsData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper, "5분간격 걸음") { store, startTime, endTime ->
            readFiveMinuteStepsData(store, startTime, endTime)
        }
    }

    /// 영양소 데이터 조회
    private fun getNutritionData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper, "영양소") { store, startTime, endTime ->
            readNutritionData(store, startTime, endTime)
        }
    }

    /// 신체 구성 데이터 조회
    private fun getBodyCompositionData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper, "신체구성") { store, startTime, endTime ->
            readBodyCompositionData(store, startTime, endTime)
        }
    }

    /// 산소 포화도 데이터 조회
    private fun getOxygenSaturationData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper, "산소포화도") { store, startTime, endTime ->
            readOxygenSaturationData(store, startTime, endTime)
        }
    }

    /// 체온 데이터 조회
    private fun getBodyTemperatureData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper, "체온") { store, startTime, endTime ->
            readBodyTemperatureData(store, startTime, endTime)
        }
    }

    /// 혈당 데이터 조회
    private fun getBloodGlucoseData(start: Long, end: Long, wrapper: ResultWrapper) {
        readDataWithWrapper(start, end, wrapper, "혈당") { store, startTime, endTime ->
            readBloodGlucoseData(store, startTime, endTime)
        }
    }

    private fun readDataWithWrapper(
        start: Long,
        end: Long,
        wrapper: ResultWrapper,
        dataTypeName: String,
        reader: suspend (HealthDataStore, LocalDateTime, LocalDateTime) -> List<Map<String, Any>>
    ) {
        val store = healthDataStore
        if (store == null) {
            wrapper.success(mapOf(
                "success" to false,
                "result" to emptyList<Map<String, Any>>(),
                "message" to "${dataTypeName} 데이터 조회 실패 - Samsung Health에 먼저 연결하세요",
                "error" to "STORE_NOT_READY"
            ))
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val startTime = Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDateTime()
                val endTime = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDateTime()
                Log.d(APP_TAG, "${dataTypeName} 데이터 읽기: ${startTime} ~ ${endTime}")
                reader(store, startTime, endTime)
            }.onSuccess { data ->
                Log.i(APP_TAG, "${dataTypeName} 데이터 읽기 성공: ${data.size}건")
                withContext(Dispatchers.Main) {
                    wrapper.success(mapOf(
                        "success" to true,
                        "result" to data,
                    ))
                }
            }.onFailure { error ->
                Log.e(APP_TAG, "${dataTypeName} 데이터 읽기 실패: ${error.message}", error)
                withContext(Dispatchers.Main) {
                    when (error) {
                        is HealthDataException -> {
                            // 권한 오류인지 확인
                            if (error.message?.contains("not allowed to read") == true || error.message?.contains("2000") == true) {
                                wrapper.success(mapOf(
                                    "success" to false,
                                    "result" to emptyList<Map<String, Any>>(),
                                    "message" to "${dataTypeName} 권한 없음",
                                    "error" to "PERMISSION_ERROR"
                                ))
                            } else {
                                wrapper.success(mapOf(
                                    "success" to false,
                                    "result" to emptyList<Map<String, Any>>(),
                                    "message" to "${dataTypeName} 데이터 조회 실패 - Samsung Health 오류",
                                    "error" to "HEALTH_DATA_ERROR"
                                ))
                            }
                        }

                        is SecurityException -> {
                            wrapper.success(mapOf(
                                "success" to false,
                                "result" to emptyList<Map<String, Any>>(),
                                "message" to "${dataTypeName} 권한 없음",
                                "error" to "PERMISSION_ERROR"
                            ))
                        }

                        else -> {
                            wrapper.success(mapOf(
                                "success" to false,
                                "result" to emptyList<Map<String, Any>>(),
                                "message" to "${dataTypeName} 데이터 조회 실패 - 알 수 없는 오류",
                                "error" to "READ_ERROR"
                            ))
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
                val sessionsData = sessions.mapIndexed { index, session ->
                    val stagesData = session.stages?.map { stage ->
                        mapOf(
                            "stage_type" to (stage.stage?.ordinal ?: 0),
                            "stage_type_name" to (stage.stage?.name ?: "Unknown"),
                            "start_time" to (stage.startTime?.toEpochMilli() ?: 0L),
                            "end_time" to (stage.endTime?.toEpochMilli() ?: 0L),
                        )
                    } ?: emptyList<Map<String, Any>>()

                    mapOf(
                        "session_no" to (index + 1),
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
                    "start_time" to currentTime.toEpochSecond(ZoneOffset.UTC) * 1000,
                    "end_time" to actualEnd.toEpochSecond(ZoneOffset.UTC) * 1000,
                    "steps" to intervalSteps,
                    "interval_minutes" to 5,
                    "data_type" to "FIVE_MINUTE_STEPS"
                )
                resultList.add(stepData)
                
            } catch (e: Exception) {
                Log.w(APP_TAG, "5분 구간 걸음수 조회 실패 ($currentTime ~ $actualEnd): ${e.message}")
                // 실패한 구간도 0으로라도 넣어줄지 결정
                val stepData = mapOf(
                    "start_time" to currentTime.toEpochSecond(ZoneOffset.UTC) * 1000,
                    "end_time" to actualEnd.toEpochSecond(ZoneOffset.UTC) * 1000,
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

    // ===== 내부 메서드들 =====
    /**
     * 데이터 타입 감시 (폴링 방식)
     */
    private suspend fun observeDataType(store: HealthDataStore, dataType: ObserverDataType) {
        // 초기 동기화 시간 설정
        val isFirstStart = !lastSyncTimes.containsKey(dataType)
        var lastSync = if (isFirstStart) {
            // 처음 시작: 정확히 현재 시점부터 (옵저버 켠 이후 데이터만)
            val now = Instant.now()
            now
        } else {
            // 재시작: 이전 동기화 시점부터 이어서
            lastSyncTimes[dataType]!!
        }
        lastSyncTimes[dataType] = lastSync

        try {
            // 첫 번째 체크 전에 잠깐 대기 (시작 직후 시간 범위 문제 방지)
            if (isFirstStart) {
                delay(2000L) // 2초 대기
            }

            while (currentCoroutineContext().isActive) {
                val end = Instant.now()

                // Samsung Health API는 최소 간격이 필요
                if (lastSync.isAfter(end) || lastSync.equals(end)) {
                    Log.v(APP_TAG, "[${dataType.typeName}] 시간 범위 스킵: lastSync=$lastSync, end=$end")
                    delay(1000L) // 1초 대기
                    continue
                }

                // 최소 1초 간격 보장
                if (lastSync.plusSeconds(1).isAfter(end)) {
                    Log.v(APP_TAG, "[${dataType.typeName}] 최소 간격 대기 중...")
                    delay(1000L)
                    continue
                }

                try {
                    val changes = readChangesForDataType(store, dataType, lastSync, end)
                    if (changes.isNotEmpty()) {
                        Log.d(APP_TAG, "[${dataType.typeName}] ${changes.size}개 변경사항 감지 (필터링 전)")

                        // UID 캐시 정리 (1시간마다)
                        cleanupUidCacheIfNeeded(dataType)

                        // 새로운 변경사항만 필터링
                        val newChanges = filterNewChanges(dataType, changes)

                        if (newChanges.isNotEmpty()) {
                            Log.d(APP_TAG, "[${dataType.typeName}] ${newChanges.size}개 새로운 변경사항 처리")

                            // Flutter로 전송할 데이터 준비
                            val dataToSend = mutableListOf<Map<String, Any>>()

                            // 새로운 변경사항만 처리
                            for (change in newChanges) {
                                val uid = change.upsertDataPoint?.uid ?: change.deleteDataUid ?: "unknown"
                                Log.d(APP_TAG, "[${dataType.typeName}] ${change.changeType} - UID: $uid")

                                // 상세 데이터 수집 (getExerciseData 같은 방식으로)
                                if (change.changeType == ChangeType.UPSERT && change.upsertDataPoint != null) {
                                    val detailedData = getDetailedDataForLog(dataType, change.upsertDataPoint!!)
                                    dataToSend.add(detailedData)
                                    Log.d(APP_TAG, "[${dataType.typeName}] 상세 데이터: $detailedData")
                                }
                            }

                            // Flutter로 실시간 데이터 전송
                            if (dataToSend.isNotEmpty()) {
                                sendDataToFlutter(dataType, dataToSend)
                            }
                        } else {
                            Log.d(APP_TAG, "[${dataType.typeName}] 모든 변경사항이 이미 처리됨 (중복 제거)")
                        }

                        // 가장 최근 changeTime으로 업데이트 (방법2: 1초 후로 설정)
                        changes.maxByOrNull { it.changeTime }?.let { latestChange ->
                            lastSync = latestChange.changeTime.plusSeconds(1) // 1나노초 → 1초로 변경
                            lastSyncTimes[dataType] = lastSync
                        }
                    }

                    // 상태 업데이트 (성공)
                    observerStates[dataType] = ObserverState(
                        dataType = dataType,
                        status = ObserverStatus.RUNNING,
                        lastSyncTime = System.currentTimeMillis()
                    )

                } catch (e: Exception) {
                    Log.e(APP_TAG, "[${dataType.typeName}] 옵저버 오류: ${e.message}", e)

                    // 시간 범위 오류인 경우 특별 처리
                    if (e.message?.contains("Time Range is invalid") == true) {
                        Log.w(APP_TAG, "[${dataType.typeName}] 시간 범위 오류 - lastSync를 현재시간으로 재설정")
                        lastSync = Instant.now().minusSeconds(1)
                        lastSyncTimes[dataType] = lastSync
                        delay(2000L) // 2초 대기 후 재시도
                        continue
                    }

                    // 상태 업데이트 (오류)
                    observerStates[dataType] = ObserverState(
                        dataType = dataType,
                        status = ObserverStatus.ERROR,
                        lastSyncTime = System.currentTimeMillis(),
                        errorMessage = e.message
                    )
                }

                delay(30_000L) // 30초 간격 폴링
            }
        } catch (e: Exception) {
            Log.e(APP_TAG, "[${dataType.typeName}] 옵저버 예외: ${e.message}", e)
        } finally {
            // 최종 상태 업데이트
            observerStates[dataType] = ObserverState(
                dataType = dataType,
                status = ObserverStatus.STOPPED,
                lastSyncTime = System.currentTimeMillis()
            )

            // SharedPreferences에 상태 저장
            saveObserverState(dataType, false)
        }
    }


    /**
     * 특정 데이터 타입의 변경사항 조회
     */
    private suspend fun readChangesForDataType(
        store: HealthDataStore,
        dataType: ObserverDataType,
        startTime: Instant,
        endTime: Instant
    ): List<Change<HealthDataPoint>> {
        val dataTypes = when (dataType) {
            ObserverDataType.EXERCISE -> DataTypes.EXERCISE
            ObserverDataType.NUTRITION -> DataTypes.NUTRITION
            ObserverDataType.BLOOD_GLUCOSE -> DataTypes.BLOOD_GLUCOSE
        }

        val request = dataTypes.changedDataRequestBuilder
            .setChangeTimeFilter(InstantTimeFilter.of(startTime, endTime))
            .build()

        val response = store.readChanges(request)
        return response.dataList
    }

    /**
     * 옵저버 Jobs만 정리 (상태는 유지)
     */
    private fun cleanupObserverJobsOnly() {
        // 모든 옵저버 Jobs 취소
        observerJobs.values.forEach { it.cancel() }
        observerJobs.clear()

        // 옵저버 스코프 정리
        observerScope?.cancel()
        observerScope = null

        // 메모리 상태는 STOPPED으로 변경하되 SharedPreferences는 유지
        for ((dataType, _) in observerStates) {
            observerStates[dataType] = ObserverState(
                dataType = dataType,
                status = ObserverStatus.STOPPED,
                lastSyncTime = System.currentTimeMillis()
            )
        }

        // UID 캐시 정리
        processedUids.clear()
        uidCacheCleanupTime.clear()
    }

    /**
     * 모든 옵저버 완전 정리 (상태도 삭제)
     */
    private fun cleanupObservers() {
        // 모든 옵저버 Jobs 취소
        observerJobs.values.forEach { it.cancel() }
        observerJobs.clear()

        // 옵저버 스코프 정리
        observerScope?.cancel()
        observerScope = null

        // 상태 초기화 및 SharedPreferences 정리
        for ((dataType, _) in observerStates) {
            observerStates[dataType] = ObserverState(
                dataType = dataType,
                status = ObserverStatus.STOPPED,
                lastSyncTime = System.currentTimeMillis()
            )
            // SharedPreferences에서도 제거
            saveObserverState(dataType, false)
        }

        // UID 캐시 정리
        processedUids.clear()
        uidCacheCleanupTime.clear()
    }

    /**
     * 문자열 타입명을 ObserverDataType enum으로 변환
     */
    private fun parseObserverDataType(typeName: String): ObserverDataType? {
        return when (typeName.lowercase()) {
            "exercise" -> ObserverDataType.EXERCISE
            "nutrition" -> ObserverDataType.NUTRITION
            "blood_glucose" -> ObserverDataType.BLOOD_GLUCOSE
            else -> null
        }
    }

    /**
     * 특정 옵저버 데이터 타입에 대한 권한 확인
     */
    private suspend fun hasPermissionForObserverType(dataType: ObserverDataType): Boolean {
        val store = healthDataStore ?: return false

        val permission = when (dataType) {
            ObserverDataType.EXERCISE -> Permission.of(DataTypes.EXERCISE, AccessType.READ)
            ObserverDataType.NUTRITION -> Permission.of(DataTypes.NUTRITION, AccessType.READ)
            ObserverDataType.BLOOD_GLUCOSE -> Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ)
        }

        return try {
            val grantedPermissions = store.getGrantedPermissions(setOf(permission))
            val hasPermission = grantedPermissions.contains(permission)
            Log.d(APP_TAG, "[${dataType.typeName}] 권한 확인: $hasPermission")
            hasPermission
        } catch (e: Exception) {
            Log.e(APP_TAG, "[${dataType.typeName}] 권한 확인 실패: ${e.message}")
            false
        }
    }

    /**
     * UID 캐시 정리 (1시간마다)
     */
    private fun cleanupUidCacheIfNeeded(dataType: ObserverDataType) {
        val now = System.currentTimeMillis()
        val lastCleanup = uidCacheCleanupTime[dataType] ?: 0
        
        // 1시간(3600초)마다 정리
        if (now - lastCleanup > 3600_000L) {
            Log.d(APP_TAG, "[${dataType.typeName}] UID 캐시 정리 실행")
            processedUids[dataType]?.clear()
            uidCacheCleanupTime[dataType] = now
        }
    }
    
    /**
     * 새로운 변경사항만 필터링 (이미 처리된 UID 제외)
     */
    private fun filterNewChanges(dataType: ObserverDataType, changes: List<Change<HealthDataPoint>>): List<Change<HealthDataPoint>> {
        val processedSet = processedUids.getOrPut(dataType) { mutableSetOf() }
        val newChanges = mutableListOf<Change<HealthDataPoint>>()
        
        for (change in changes) {
            val uid = change.upsertDataPoint?.uid ?: change.deleteDataUid
            if (uid != null) {
                if (!processedSet.contains(uid)) {
                    // 새로운 UID면 처리 목록에 추가
                    newChanges.add(change)
                    processedSet.add(uid)
                    // 새로운 UID 등록
                } else {
                    // 중복 UID 스킵
                }
            } else {
                // UID가 없는 경우 (예외 상황)
                newChanges.add(change)
                Log.w(APP_TAG, "[${dataType.typeName}] UID 없는 변경사항: ${change.changeType}")
            }
        }
        
        return newChanges
    }
    
    /**
     * 옵저버 상태를 SharedPreferences에 저장
     */
    private fun saveObserverState(dataType: ObserverDataType, isRunning: Boolean) {
        if (!::sharedPreferences.isInitialized) {
            Log.e(APP_TAG, "SharedPreferences not initialized")
            return
        }
        
        val key = "${OBSERVER_STATE_PREFIX}${dataType.typeName}"
        sharedPreferences.edit().putBoolean(key, isRunning).apply()
    }
    
    /**
     * 연결 후 저장된 옵저버 상태들을 복원하고 자동 시작 (앱 세션 당 한 번만)
     */
    private fun restoreObserverStatesAfterConnect() {
        // 이미 복원했으면 스킵
        if (hasRestoredOnce) {
            Log.d(APP_TAG, "옵저버 상태 이미 복원됨 - 스킵")
            return
        }
        
        if (!::sharedPreferences.isInitialized) {
            Log.e(APP_TAG, "SharedPreferences가 초기화되지 않음 - 복원 실패")
            return
        }
        
        Log.d(APP_TAG, "저장된 옵저버 상태 복원 시작 (첫 번째 연결)")
        
        // 모든 데이터 타입에 대해 저장된 상태 확인
        val savedObservers = mutableListOf<ObserverDataType>()
        
        for (dataType in ObserverDataType.values()) {
            val key = "${OBSERVER_STATE_PREFIX}${dataType.typeName}"
            val isRunning = sharedPreferences.getBoolean(key, false)
            
            Log.d(APP_TAG, "[${dataType.typeName}] 저장된 상태 확인: key=$key, value=$isRunning")
            
            if (isRunning) {
                savedObservers.add(dataType)
                Log.d(APP_TAG, "[${dataType.typeName}] 복원 대상 옵저버 발견")
            }
        }
        
        if (savedObservers.isNotEmpty()) {
            Log.d(APP_TAG, "총 ${savedObservers.size}개 옵저버 자동 복원 시작")
            
            // 연결 직후 즉시 복원 (HealthDataStore가 이미 연결된 상태)
            CoroutineScope(Dispatchers.Main).launch {
                for (dataType in savedObservers) {
                    try {
                        // HealthDataStore가 연결되어 있는지 확인
                        if (healthDataStore != null) {
                            startObserverInternal(dataType)
                            Log.d(APP_TAG, "[${dataType.typeName}] 옵저버 자동 복원 성공")
                        } else {
                            Log.w(APP_TAG, "[${dataType.typeName}] HealthDataStore 연결 필요 - 복원 실패")
                            saveObserverState(dataType, false)
                        }
                    } catch (e: Exception) {
                        Log.e(APP_TAG, "[${dataType.typeName}] 옵저버 자동 복원 실패: ${e.message}")
                        // 실패한 옵저버는 저장된 상태에서 제거
                        saveObserverState(dataType, false)
                    }
                }
            }
        } else {
            Log.d(APP_TAG, "복원할 옵저버 없음")
        }
        
        // 복원 완료 플래그 설정
        hasRestoredOnce = true
        Log.d(APP_TAG, "옵저버 상태 복원 완료 - 이후 connect() 호출 시 스킵됨")
    }
    
    /**
     * 필요 시 옵저버 상태 복원 (매 연결마다 체크)
     */
    private fun restoreObserverStatesIfNeeded() {
        if (!::sharedPreferences.isInitialized) return
        
        var restoredCount = 0
        
        // 현재 실행 중인 옵저버와 저장된 상태 비교
        for (dataType in ObserverDataType.values()) {
            val key = "${OBSERVER_STATE_PREFIX}${dataType.typeName}"
            val savedState = sharedPreferences.getBoolean(key, false)
            val currentState = observerStates[dataType]?.status == ObserverStatus.RUNNING
            
            if (savedState && !currentState) {
                // 저장된 상태는 실행중인데 현재는 중지됨 → 복원 필요
                try {
                    startObserverInternal(dataType)
                    restoredCount++
                    Log.d(APP_TAG, "[${dataType.typeName}] Observer restored from saved state")
                } catch (e: Exception) {
                    Log.e(APP_TAG, "[${dataType.typeName}] Observer restore failed: ${e.message}")
                    saveObserverState(dataType, false)
                }
            }
        }
        
        if (restoredCount > 0) {
            Log.i(APP_TAG, "Restored $restoredCount observers from saved state")
        }
    }
    
    /**
     * 단일 옵저버 내부 시작 로직 (복원용)
     */
    private fun startObserverInternal(dataType: ObserverDataType) {
        // 연결 상태와 무관하게 상태 관리, 연결되어 있으면 실제 Job도 시작
        val store = healthDataStore
        
        // 이미 실행 중이면 무시
        if (observerStates[dataType]?.status == ObserverStatus.RUNNING) {
            Log.d(APP_TAG, "[${dataType.typeName}] 이미 실행 중 - 무시")
            return
        }
        
        // 코루틴 스코프 초기화
        if (observerScope == null) {
            observerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
        
        // 기존 job이 있으면 취소
        observerJobs[dataType]?.cancel()
        
        // 상태 초기화
        observerStates[dataType] = ObserverState(
            dataType = dataType,
            status = ObserverStatus.RUNNING,
            lastSyncTime = System.currentTimeMillis()
        )
        
        // UID 캐시 초기화
        processedUids[dataType]?.clear()
        
        // 동기화 시간 설정 (현재 시점)
        lastSyncTimes[dataType] = Instant.now()
        
        // 연결되어 있으면 실제 Job 시작
        if (store != null) {
            val job = observerScope!!.launch {
                observeDataType(store, dataType)
            }
            observerJobs[dataType] = job
        }
    }

    /**
     * Flutter로 실시간 데이터 전송
     */
    private fun sendDataToFlutter(dataType: ObserverDataType, data: List<Map<String, Any>>) {
        eventSink?.let { sink ->
            val eventData = mapOf(
                "dataType" to dataType.typeName,
                "data" to data,
                "timestamp" to System.currentTimeMillis(),
                "count" to data.size
            )

            // UI 스레드에서 전송
            CoroutineScope(Dispatchers.Main).launch {
                sink.success(eventData)
                Log.d(APP_TAG, "[${dataType.typeName}] Flutter로 ${data.size}개 데이터 전송 완료")
            }
        } ?: run {
            Log.w(APP_TAG, "[${dataType.typeName}] EventSink가 null - Flutter 전송 실패")
        }
    }
    
    /**
     * 옵저버용 완전한 데이터 생성 (실제 API와 동일한 구조)
     */
    private fun getDetailedDataForLog(dataType: ObserverDataType, dataPoint: HealthDataPoint): Map<String, Any> {
        return when (dataType) {
            ObserverDataType.EXERCISE -> {
                parseExerciseDataPoint(dataPoint)
            }
            
            ObserverDataType.NUTRITION -> {
                parseNutritionDataPoint(dataPoint)
            }
            
            ObserverDataType.BLOOD_GLUCOSE -> {
                parseBloodGlucoseDataPoint(dataPoint)
            }
        }
    }
    
    /**
     * 운동 데이터 파싱 (getExerciseData와 동일한 구조)
     */
    private fun parseExerciseDataPoint(dataPoint: HealthDataPoint): Map<String, Any> {
        val exerciseType = dataPoint.getValue(DataType.ExerciseType.EXERCISE_TYPE)
        val sessions = dataPoint.getValue(DataType.ExerciseType.SESSIONS)
        
        // 세션 데이터 파싱
        val sessionList = sessions?.map { session ->
            mapOf(
                "start_time" to (session.startTime?.toEpochMilli() ?: 0L),
                "end_time" to (session.endTime?.toEpochMilli() ?: 0L),
                "duration" to (session.duration?.toMillis() ?: 0L),
                "calories" to (session.calories ?: 0f),
                "distance" to (session.distance ?: 0f),
                "max_heart_rate" to (session.maxHeartRate ?: 0f),
                "mean_heart_rate" to (session.meanHeartRate ?: 0f),
                "min_heart_rate" to (session.minHeartRate ?: 0f)
            )
        } ?: emptyList()
        
        val firstSession = sessions?.firstOrNull()
        
        return mapOf(
            "uid" to (dataPoint.uid ?: ""),
            "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
            "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
            "exercise_type" to (exerciseType?.ordinal ?: 0),
            "exercise_type_name" to (exerciseType?.name ?: "Unknown"),
            "duration" to (firstSession?.duration?.toMillis() ?: 0L),
            "calories" to (firstSession?.calories ?: 0f),
            "distance" to (firstSession?.distance ?: 0f),
            "max_heart_rate" to (firstSession?.maxHeartRate ?: 0f),
            "mean_heart_rate" to (firstSession?.meanHeartRate ?: 0f),
            "min_heart_rate" to (firstSession?.minHeartRate ?: 0f),
            "session_count" to (sessions?.size ?: 0),
            "sessions" to sessionList
        )
    }
    
    /**
     * 영양소 데이터 파싱 (getNutritionData와 동일한 구조)
     */
    private fun parseNutritionDataPoint(dataPoint: HealthDataPoint): Map<String, Any> {
        val mealType = dataPoint.getValue(DataType.NutritionType.MEAL_TYPE)
        
        return mapOf(
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
    }
    
    /**
     * 혈당 데이터 파싱 (getBloodGlucoseData와 동일한 구조)
     */
    private fun parseBloodGlucoseDataPoint(dataPoint: HealthDataPoint): Map<String, Any> {
        val glucoseMmol = dataPoint.getValue(DataType.BloodGlucoseType.GLUCOSE_LEVEL) ?: 0f
        val measurementType = dataPoint.getValue(DataType.BloodGlucoseType.MEASUREMENT_TYPE)
        val mealStatus = dataPoint.getValue(DataType.BloodGlucoseType.MEAL_STATUS)
        
        return mapOf(
            "uid" to (dataPoint.uid ?: ""),
            "start_time" to (dataPoint.startTime?.toEpochMilli() ?: 0L),
            "end_time" to (dataPoint.endTime?.toEpochMilli() ?: 0L),
            "glucose_mmol" to glucoseMmol,
            "glucose_mgdl" to (glucoseMmol * 18.018f),
            "measurement_type" to (measurementType?.ordinal ?: 0),
            "measurement_type_name" to (measurementType?.name ?: "Unknown"),
            "meal_status" to (mealStatus?.ordinal ?: 0),
            "meal_status_name" to (mealStatus?.name ?: "Unknown")
        )
    }

    // ===== FlutterPlugin 생명주기 =====

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        eventSink = null

        // 앱 종료 시에는 옵저버 상태를 유지 (재시작 시 복원을 위해)
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

    // ===== EventChannel StreamHandler 구현 =====
    
    override fun onListen(arguments: Any?, events: EventSink?) {
        Log.d(APP_TAG, "Event Channel 연결됨")
        eventSink = events
    }

    override fun onCancel(arguments: Any?) {
        Log.d(APP_TAG, "Event Channel 연결 해제됨")
        eventSink = null
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
