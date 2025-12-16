import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'flutter_samsung_health_method_channel.dart';

abstract class FlutterSamsungHealthPlatform extends PlatformInterface {
  /// Constructs a SamsungHealthPluginPlatform.
  FlutterSamsungHealthPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterSamsungHealthPlatform _instance = MethodChannelFlutterSamsungHealth();

  /// The default instance of [FlutterSamsungHealthPlatform] to use.
  ///
  /// Defaults to [MethodChannelFlutterSamsungHealth].
  static FlutterSamsungHealthPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FlutterSamsungHealthPlatform] when
  /// they register themselves.
  static set instance(FlutterSamsungHealthPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// 앱 설치 여부 확인
  Future<Map<String, dynamic>> isSamsungHealthInstalled();

  /// 앱 실행
  Future<Map<String, dynamic>> openSamsungHealth();

  /// SDK 연결
  Future<Map<String, dynamic>> connect();

  /// SDK 연결 해제
  Future<Map<String, dynamic>> disconnect();

  /// 데이터 권한 요청
  Future<Map<String, dynamic>> requestPermissions(List<String>? types);

  /// 승인된 권한 조회
  Future<Map<String, dynamic>> getGrantedPermissions();

  /// 권한 설정 화면 열기
  Future<Map<String, dynamic>> openSamsungHealthPermissions();

  /// 옵저버 시작
  Future<Map<String, dynamic>> startObserver(List<String>? dataTypes);

  /// 옵저버 중단
  Future<Map<String, dynamic>> stopObserver(List<String>? dataTypes);

  /// 옵저버 상태 조회
  Future<dynamic> getObserverStatus(List<String>? dataTypes);

  /// 전체 데이터 조회
  Future<Map<String, dynamic>> getTotalData(int start, int end);

  /// 운동 데이터 조회
  Future<Map<String, dynamic>> getExerciseData(int start, int end);

  /// 심박수 데이터 조회
  Future<Map<String, dynamic>> getHeartRateData(int start, int end);

  /// 걸음수 데이터 조회
  Future<Map<String, dynamic>> getStepsData(int start, int end);

  /// 5분 간격 걸음수 데이터 조회
  Future<Map<String, dynamic>> getFiveMinuteStepsData(int start, int end);

  /// 수면 데이터 조회
  Future<Map<String, dynamic>> getSleepData(int start, int end);

  /// 영양소 데이터 조회
  Future<Map<String, dynamic>> getNutritionData(int start, int end);

  /// 혈당 데이터 조회
  Future<Map<String, dynamic>> getBloodGlucoseData(int start, int end);

  /// 신체 구성 데이터 조회
  Future<Map<String, dynamic>> getBodyCompositionData(int start, int end);

  /// 산소 포화도 데이터 조회
  Future<Map<String, dynamic>> getOxygenSaturationData(int start, int end);

  /// 체온 데이터 조회
  Future<Map<String, dynamic>> getBodyTemperatureData(int start, int end);
}
