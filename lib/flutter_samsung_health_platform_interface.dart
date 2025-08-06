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

  /// 헬스 데이터 변경 감지 스트림
  Stream<String> get healthDataChanges;

  /// 설치 여부 체크
  Future<Map<String, dynamic>> isSamsungHealthInstalled();

  /// 앱 실행
  Future<Map<String, dynamic>> openSamsungHealth();

  /// 연결 (권한 확인 및 요청)
  Future<Map<String, dynamic>> connect();

  /// 권한
  Future<Map<String, dynamic>> requestPermissions();

  /// 승인 권한
  Future<Map<String, dynamic>> getGrantedPermissions();

  /// 전체 데이터
  Future<Map<String, List<Map<String, dynamic>>>> getTotalData(int start, int end);

  /// 운동
  Future<List<Map<String, dynamic>>> getExerciseData(int start, int end);

  /// 심박
  Future<List<Map<String, dynamic>>> getHeartRateData(int start, int end);

  /// 수면
  Future<List<Map<String, dynamic>>> getSleepData(int start, int end);

  /// 수면 단계
  Future<List<Map<String, dynamic>>> getSleepStageData(int start, int end);

  /// 걷기 조회(5분 누적)
  Future<List<Map<String, dynamic>>> getStepData(int start, int end);

  /// 영양소 정보
  Future<List<Map<String, dynamic>>> getNutritionData(int start, int end);

  /// 무게 정보
  Future<List<Map<String, dynamic>>> getWeightData(int start, int end);

  /// 산소 포화도 정보
  Future<List<Map<String, dynamic>>> getOxygenSaturationData(int start, int end);

  /// 체온 정보
  Future<List<Map<String, dynamic>>> getBodyTemperatureData(int start, int end);
}
