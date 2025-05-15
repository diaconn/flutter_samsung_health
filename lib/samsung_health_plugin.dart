import 'samsung_health_plugin_platform_interface.dart';

class SamsungHealthPlugin {
  /// 삼성 헬스 SDK 연결
  Future<Map<String, dynamic>> connect() {
    return SamsungHealthPluginPlatform.instance.connect();
  }

  /// 심박수 조회(5분 평균)
  Future<List<Map<String, dynamic>>> getHeartRate5minSeries({
    required int start,
    required int end,
  }) {
    return SamsungHealthPluginPlatform.instance.getHeartRate5minSeries(start, end);
  }

  /// 운동 세션 조회
  Future<List<Map<String, dynamic>>> getExerciseSessions({
    required int start,
    required int end,
  }) {
    return SamsungHealthPluginPlatform.instance.getExerciseSessions(start, end);
  }

  /// 걸음수 조회(5분 누적)
  Future<List<Map<String, dynamic>>> getStepCountSeries({
    required int start,
    required int end,
  }) {
    return SamsungHealthPluginPlatform.instance.getStepCountSeries(start, end);
  }

  /// 수면 정보 조회
  Future<List<Map<String, dynamic>>> getSleepData({
    required int start,
    required int end,
  }) {
    return SamsungHealthPluginPlatform.instance.getSleepData(start, end);
  }

  /// 식사 영양소 정보 조회
  Future<List<Map<String, dynamic>>> getNutritionData({
    required int start,
    required int end,
  }) {
    return SamsungHealthPluginPlatform.instance.getNutritionData(start, end);
  }
}
