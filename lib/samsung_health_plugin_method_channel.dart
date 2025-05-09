import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'samsung_health_plugin_platform_interface.dart';

/// An implementation of [SamsungHealthPluginPlatform] that uses method channels.
class MethodChannelSamsungHealthPlugin extends SamsungHealthPluginPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('samsung_health_plugin');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }

  @override
  Future<List<Map<String, dynamic>>> getHeartRate5minSeries(int startMillis, int endMillis) async {
    final result = await methodChannel.invokeMethod<List>('getHeartRate5minSeries', {
      'startMillis': startMillis,
      'endMillis': endMillis,
    });
    return (result ?? []).cast<Map<String, dynamic>>();
  }

  @override
  Future<List<Map<String, dynamic>>> getExerciseSessions(int start, int end) async {
    final result = await methodChannel.invokeMethod<List>('getExerciseSessions', {
      'startMillis': start,
      'endMillis': end,
    });
    return (result ?? []).cast<Map<String, dynamic>>();
  }

  @override
  Future<List<Map<String, dynamic>>> getSleepData(int start, int end) async {
    final result = await methodChannel.invokeMethod<List>('getSleepData', {
      'startMillis': start,
      'endMillis': end,
    });
    return (result ?? []).cast<Map<String, dynamic>>();
  }

  @override
  Future<List<Map<String, dynamic>>> getStepCountSeries(int start, int end) async {
    final result = await methodChannel.invokeMethod<List>('getStepCountSeries', {
      'startMillis': start,
      'endMillis': end,
    });
    return (result ?? []).cast<Map<String, dynamic>>();
  }

  @override
  Future<List<Map<String, dynamic>>> connect() async {
    final result = await methodChannel.invokeMethod<List>('connect', {});
    return (result ?? []).cast<Map<String, dynamic>>();
  }
}
