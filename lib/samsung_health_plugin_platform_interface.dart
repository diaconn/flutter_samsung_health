import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'samsung_health_plugin_method_channel.dart';

abstract class SamsungHealthPluginPlatform extends PlatformInterface {
  /// Constructs a SamsungHealthPluginPlatform.
  SamsungHealthPluginPlatform() : super(token: _token);

  static final Object _token = Object();

  static SamsungHealthPluginPlatform _instance = MethodChannelSamsungHealthPlugin();

  /// The default instance of [SamsungHealthPluginPlatform] to use.
  ///
  /// Defaults to [MethodChannelSamsungHealthPlugin].
  static SamsungHealthPluginPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [SamsungHealthPluginPlatform] when
  /// they register themselves.
  static set instance(SamsungHealthPluginPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  Future<List<Map<String, dynamic>>> getHeartRate5minSeries(int startMillis, int endMillis);
  Future<List<Map<String, dynamic>>> getExerciseSessions(int start, int end);
  Future<List<Map<String, dynamic>>> getStepCountSeries(int start, int end);
  Future<List<Map<String, dynamic>>> getSleepData(int start, int end);
}
