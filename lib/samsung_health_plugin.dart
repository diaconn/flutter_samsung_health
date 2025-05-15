import 'samsung_health_plugin_platform_interface.dart';

class SamsungHealthPlugin {
  /// 삼성 헬스 SDK 연결
  Future<Map<String, dynamic>> connect() {
    return SamsungHealthPluginPlatform.instance.connect();
  }

  /// 심박수 조회(5분 평균)
  Future<List<Map<String, dynamic>>> getHeartRate5minSeries({
    required int startMillis,
    required int endMillis,
  }) {
    return SamsungHealthPluginPlatform.instance.getHeartRate5minSeries(startMillis, endMillis);
  }

  /// 운동 세션 조회
  Future<List<Map<String, dynamic>>> getExerciseSessions({
    required int startMillis,
    required int endMillis,
  }) {
    return SamsungHealthPluginPlatform.instance.getExerciseSessions(startMillis, endMillis);
  }

  /// 걸음수 조회(5분 누적)
  Future<List<Map<String, dynamic>>> getStepCountSeries({
    required int startMillis,
    required int endMillis,
  }) {
    return SamsungHealthPluginPlatform.instance.getStepCountSeries(startMillis, endMillis);
  }

  /// 수면 정보 조회
  Future<List<Map<String, dynamic>>> getSleepData({
    required int startMillis,
    required int endMillis,
  }) {
    return SamsungHealthPluginPlatform.instance.getSleepData(startMillis, endMillis);
  }
}
