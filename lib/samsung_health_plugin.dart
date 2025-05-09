import 'samsung_health_plugin_platform_interface.dart';

class SamsungHealthPlugin {
  Future<String?> getPlatformVersion() {
    return SamsungHealthPluginPlatform.instance.getPlatformVersion();
  }

  Future<Map<String, dynamic>> connect() {
    return SamsungHealthPluginPlatform.instance.connect();
  }

  Future<List<Map<String, dynamic>>> getHeartRate5minSeries({
    required int startMillis,
    required int endMillis,
  }) {
    return SamsungHealthPluginPlatform.instance.getHeartRate5minSeries(startMillis, endMillis);
  }

  Future<List<Map<String, dynamic>>> getExerciseSessions({
    required int startMillis,
    required int endMillis,
  }) {
    return SamsungHealthPluginPlatform.instance.getExerciseSessions(startMillis, endMillis);
  }

  Future<List<Map<String, dynamic>>> getStepCountSeries({
    required int startMillis,
    required int endMillis,
  }) {
    return SamsungHealthPluginPlatform.instance.getStepCountSeries(startMillis, endMillis);
  }

  Future<List<Map<String, dynamic>>> getSleepData({
    required int startMillis,
    required int endMillis,
  }) {
    return SamsungHealthPluginPlatform.instance.getSleepData(startMillis, endMillis);
  }
}
