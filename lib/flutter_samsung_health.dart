import 'flutter_samsung_health_platform_interface.dart';

class FlutterSamsungHealth {
  /// 삼성 헬스 설치 유무 체크
  Future<Map<String, dynamic>> isSamsungHealthInstalled() {
    return FlutterSamsungHealthPlatform.instance.isSamsungHealthInstalled();
  }

  /// 삼성 헬스 앱 실행
  Future<Map<String, dynamic>> openSamsungHealth() {
    return FlutterSamsungHealthPlatform.instance.openSamsungHealth();
  }

  /// 삼성 헬스 SDK 연결
  Future<Map<String, dynamic>> connect() {
    return FlutterSamsungHealthPlatform.instance.connect();
  }

  /// 삼성 헬스 권한
  Future<Map<String, dynamic>> requestPermissions() {
    return FlutterSamsungHealthPlatform.instance.requestPermissions();
  }

  /// 삼성 헬스 승인 권한
  Future<Map<String, dynamic>> getGrantedPermissions() {
    return FlutterSamsungHealthPlatform.instance.getGrantedPermissions();
  }

  /// 전체 데이터 조회
  Future<Map<String,List<Map<String, dynamic>>>> getTotalData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getTotalData(start, end);
  }

  /// 운동 조회
  Future<List<Map<String, dynamic>>> getExerciseSessions({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getExerciseSessions(start, end);
  }

  /// 운동 조회
  Future<List<Map<String, dynamic>>> getExerciseSessionsAsync({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getExerciseSessionsAsync(start, end);
  }

  /// 심박수 조회
  Future<List<Map<String, dynamic>>> getHeartRateData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getHeartRateData(start, end);
  }

  /// 심박수 조회(5분 평균)
  Future<List<Map<String, dynamic>>> getHeartRate5minSeries({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getHeartRate5minSeries(start, end);
  }

  /// 걷기 조회(5분 누적)
  Future<List<Map<String, dynamic>>> getStepCountSeries({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getStepCountSeries(start, end);
  }

  /// 수면 조회
  Future<List<Map<String, dynamic>>> getSleepData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getSleepData(start, end);
  }

  /// 수면 단계 조회
  Future<List<Map<String, dynamic>>> getSleepStageData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getSleepStageData(start, end);
  }

  /// 영양소 조회
  Future<List<Map<String, dynamic>>> getNutritionData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getNutritionData(start, end);
  }

  /// 무게 조회
  Future<List<Map<String, dynamic>>> getWeightData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getWeightData(start, end);
  }

  /// 산소 포화도 조회
  Future<List<Map<String, dynamic>>> getOxygenSaturationData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getOxygenSaturationData(start, end);
  }
}
