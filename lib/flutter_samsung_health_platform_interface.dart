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

  /// 설치 여부 체크
  Future<Map<String, dynamic>> isSamsungHealthInstalled();

  /// 앱 실행
  Future<Map<String, dynamic>> openSamsungHealth();

  /// 연결
  Future<Map<String, dynamic>> connect();

  /// 연결 해제
  Future<Map<String, dynamic>> disconnect();

  /// 권한 요청
  /// [types]: 요청할 권한 타입 목록 (예: ['exercise', 'heart_rate', 'sleep', 'steps', 'nutrition', 'body_composition', 'blood_oxygen', 'body_temperature', 'blood_glucose'])
  /// 빈 배열이면 모든 권한 요청
  Future<Map<String, dynamic>> requestPermissions(List<String>? types);

  /// 승인된 권한 조회
  Future<Map<String, dynamic>> getGrantedPermissions();

  /// 전체 데이터 조회
  Future<Map<String, List<Map<String, dynamic>>>> getTotalData(int start, int end);

  /// 운동 조회
  Future<List<Map<String, dynamic>>> getExerciseData(int start, int end);

  /// 심박 조회
  Future<List<Map<String, dynamic>>> getHeartRateData(int start, int end);

  /// 수면 조회 (수면 단계 포함)
  Future<List<Map<String, dynamic>>> getSleepData(int start, int end);

  /// 걸음 조회 (집계 데이터)
  Future<List<Map<String, dynamic>>> getStepData(int start, int end);

  /// 영양소 조회
  Future<List<Map<String, dynamic>>> getNutritionData(int start, int end);

  /// 체중/신체 구성 조회
  Future<List<Map<String, dynamic>>> getWeightData(int start, int end);

  /// 산소 포화도 조회
  Future<List<Map<String, dynamic>>> getOxygenSaturationData(int start, int end);

  /// 체온 조회
  Future<List<Map<String, dynamic>>> getBodyTemperatureData(int start, int end);

  /// 혈당 조회
  Future<List<Map<String, dynamic>>> getBloodGlucoseData(int start, int end);
}
