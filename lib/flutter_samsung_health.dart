import 'flutter_samsung_health_platform_interface.dart';

class FlutterSamsungHealth {
  /// 설치 여부 체크
  Future<Map<String, dynamic>> isSamsungHealthInstalled() {
    return FlutterSamsungHealthPlatform.instance.isSamsungHealthInstalled();
  }

  /// 앱 실행
  Future<Map<String, dynamic>> openSamsungHealth() {
    return FlutterSamsungHealthPlatform.instance.openSamsungHealth();
  }

  /// 연결
  Future<Map<String, dynamic>> connect() {
    return FlutterSamsungHealthPlatform.instance.connect();
  }

  /// 연결 해제
  Future<Map<String, dynamic>> disconnect() {
    return FlutterSamsungHealthPlatform.instance.disconnect();
  }

  /// 권한
  Future<Map<String, dynamic>> requestPermissions(List<String>? types) {
    return FlutterSamsungHealthPlatform.instance.requestPermissions(types);
  }

  /// 승인 권한
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
  Future<List<Map<String, dynamic>>> getExerciseData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getExerciseData(start, end);
  }

  /// 심박 조회
  Future<List<Map<String, dynamic>>> getHeartRateData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getHeartRateData(start, end);
  }

  /// 수면 조회 (수면 단계 포함)
  Future<List<Map<String, dynamic>>> getSleepData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getSleepData(start, end);
  }

  /// 걸음 조회 (집계 데이터)
  Future<List<Map<String, dynamic>>> getStepData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getStepData(start, end);
  }

  /// 영양소 조회
  Future<List<Map<String, dynamic>>> getNutritionData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getNutritionData(start, end);
  }

  /// 신체 조회
  Future<List<Map<String, dynamic>>> getBodyMeasurementData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getBodyMeasurementData(start, end);
  }

  /// 산소 포화도 조회
  Future<List<Map<String, dynamic>>> getOxygenSaturationData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getOxygenSaturationData(start, end);
  }

  /// 체온 조회
  Future<List<Map<String, dynamic>>> getBodyTemperatureData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getBodyTemperatureData(start, end);
  }

  /// 혈당 조회
  Future<List<Map<String, dynamic>>> getBloodGlucoseData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getBloodGlucoseData(start, end);
  }
}
