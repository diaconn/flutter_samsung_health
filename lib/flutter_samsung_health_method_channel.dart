import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'flutter_samsung_health_platform_interface.dart';

/// An implementation of [FlutterSamsungHealthPlatform] that uses method channels.
class MethodChannelFlutterSamsungHealth extends FlutterSamsungHealthPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_samsung_health');

  /// 삼성 헬스 연결
  @override
  Future<Map<String, dynamic>> connect() async {
    final result = await methodChannel.invokeMethod<Map>('connect', {});
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {'isConnect': false};
  }

  /// 심박 조회(5분 평균)
  @override
  Future<List<Map<String, dynamic>>> getHeartRate5minSeries(int start, int end) async {
    final result = await methodChannel.invokeMethod<List>('getHeartRate5minSeries', {
      'start': start,
      'end': end,
    });
    return (result ?? [])
        .map((item) => Map<String, dynamic>.from((item as Map).map((key, value) => MapEntry(key.toString(), value))))
        .toList();
  }

  /// 운동 세션 조회
  @override
  Future<List<Map<String, dynamic>>> getExerciseSessions(int start, int end) async {
    final result = await methodChannel.invokeMethod<List>('getExerciseSessions', {
      'start': start,
      'end': end,
    });
    return (result ?? [])
        .map((item) => Map<String, dynamic>.from((item as Map).map((key, value) => MapEntry(key.toString(), value))))
        .toList();
  }

  /// 수면 조회
  @override
  Future<List<Map<String, dynamic>>> getSleepData(int start, int end) async {
    final result = await methodChannel.invokeMethod<List>('getSleepData', {
      'start': start,
      'end': end,
    });
    return (result ?? [])
        .map((item) => Map<String, dynamic>.from((item as Map).map((key, value) => MapEntry(key.toString(), value))))
        .toList();
  }

  /// 걷기수 조회 (5분 누적)
  @override
  Future<List<Map<String, dynamic>>> getStepCountSeries(int start, int end) async {
    final result = await methodChannel.invokeMethod<List>('getStepCountSeries', {
      'start': start,
      'end': end,
    });
    return (result ?? [])
        .map((item) => Map<String, dynamic>.from((item as Map).map((key, value) => MapEntry(key.toString(), value))))
        .toList();
  }

  /// 식사 영양소 조회
  @override
  Future<List<Map<String, dynamic>>> getNutritionData(int start, int end) async {
    final result = await methodChannel.invokeMethod<List>('getNutrition', {
      'start': start,
      'end': end,
    });
    return (result ?? [])
        .map((item) => Map<String, dynamic>.from((item as Map).map((key, value) => MapEntry(key.toString(), value))))
        .toList();
  }
}
