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

  /// 옵저버 켜기
  Future<Map<String, dynamic>> enableObservers(List<String> types) async {
    final result = await FlutterSamsungHealthPlatform.instance.enableObservers(types);
    return result.map((key, value) => MapEntry(key.toString(), value));
  }

  /// 옵저버 끄기
  Future<Map<String, dynamic>> disableObservers(List<String> types) async {
    final result = await FlutterSamsungHealthPlatform.instance.disableObservers(types);
    return result.map((key, value) => MapEntry(key.toString(), value));
  }

  /// 옵저버 상태 조회
  Future<Map<String, dynamic>> getObserversStatus(List<String> types) async {
    final result = await FlutterSamsungHealthPlatform.instance.getObserversStatus(types);
    return result.map((key, value) => MapEntry(key.toString(), value));
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

  /// 걷기 조회(5분 누적)
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
