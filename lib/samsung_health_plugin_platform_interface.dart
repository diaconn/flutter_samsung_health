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

  ///  연결 (권한 확인 및 요청)
  Future<Map<String, dynamic>> connect();

  /// 5분 심박
  Future<List<Map<String, dynamic>>> getHeartRate5minSeries(int start, int end);

  /// 운동
  Future<List<Map<String, dynamic>>> getExerciseSessions(int start, int end);

  /// 걷기 수
  Future<List<Map<String, dynamic>>> getStepCountSeries(int start, int end);

  /// 수면
  Future<List<Map<String, dynamic>>> getSleepData(int start, int end);

  /// 식사(영양소) 정보
  Future<List<Map<String, dynamic>>> getNutritionData(int start, int end);
}
