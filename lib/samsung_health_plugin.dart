import 'samsung_health_plugin_platform_interface.dart';

class SamsungHealthPlugin {
  Future<String?> getPlatformVersion() {
    return SamsungHealthPluginPlatform.instance.getPlatformVersion();
  }

  Future<List<Map<String, dynamic>>> getHeartRate5minSeries({
    required int startMillis,
    required int endMillis,
  }) {
    return SamsungHealthPluginPlatform.instance.getHeartRate5minSeries(startMillis, endMillis);
  }
}
