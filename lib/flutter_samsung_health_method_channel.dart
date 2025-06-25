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

  /// 삼성 헬스 권한
  @override
  Future<Map<String, dynamic>> requestPermissions() async {
    final result = await methodChannel.invokeMethod<Map>('requestPermissions', {});
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {'isConnect': false};
  }

  /// 전체 데이터 조회
  @override
  Future<Map<String, List<Map<String, dynamic>>>> getTotalData(int start, int end) async {
    final result = await methodChannel.invokeMethod<Map>('getTotalData', {
      'start': start,
      'end': end,
    });
    if (result == null) return {};

    Map<String, List<Map<String, dynamic>>> groupedData = {};

    result.forEach((key, value) {
      if (value is List) {
        final List<Map<String, dynamic>> parsedList = value.map<Map<String, dynamic>>((item) {
          if (item is Map) {
            return Map<String, dynamic>.from(item.map((k, v) => MapEntry(k.toString(), v)));
          }
          return {}; // fallback
        }).toList();

        groupedData[key.toString()] = parsedList;
      }
    });

    return groupedData;
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

  /// 심박 조회
  @override
  Future<List<Map<String, dynamic>>> getHeartRateData(int start, int end) async {
    final result = await methodChannel.invokeMethod<List>('getHeartRateData', {
      'start': start,
      'end': end,
    });
    return (result ?? [])
        .map((item) => Map<String, dynamic>.from((item as Map).map((key, value) => MapEntry(key.toString(), value))))
        .toList();
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

  /// 수면 단계 조회
  @override
  Future<List<Map<String, dynamic>>> getSleepStageData(int start, int end) async {
    final result = await methodChannel.invokeMethod<List>('getSleepStageData', {
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
    final result = await methodChannel.invokeMethod<List>('getNutritionData', {
      'start': start,
      'end': end,
    });
    return (result ?? [])
        .map((item) => Map<String, dynamic>.from((item as Map).map((key, value) => MapEntry(key.toString(), value))))
        .toList();
  }
}
