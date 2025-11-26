import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'flutter_samsung_health_platform_interface.dart';

/// An implementation of [FlutterSamsungHealthPlatform] that uses method channels.
class MethodChannelFlutterSamsungHealth extends FlutterSamsungHealthPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_samsung_health');

  /// 설치 여부 체크
  @override
  Future<Map<String, dynamic>> isSamsungHealthInstalled() async {
    final result = await methodChannel.invokeMethod<Map>('isSamsungHealthInstalled', {});
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {'isInstalled': false};
  }

  /// 앱 실행
  @override
  Future<Map<String, dynamic>> openSamsungHealth() async {
    final result = await methodChannel.invokeMethod<Map>('openSamsungHealth', {});
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {'action': "failed"};
  }

  /// 연결
  @override
  Future<Map<String, dynamic>> connect() async {
    final result = await methodChannel.invokeMethod<Map>('connect', {});
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {'isConnect': false};
  }

  /// 연결 해제
  @override
  Future<Map<String, dynamic>> disconnect() async {
    final result = await methodChannel.invokeMethod<Map>('disconnect', {});
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {'isConnect': false};
  }

  /// 권한
  @override
  Future<Map<String, dynamic>> requestPermissions(List<String>? types) async {
    final result = await methodChannel.invokeMethod<Map>('requestPermissions', {"types": types});
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {'isConnect': false};
  }

  /// 승인 권한
  @override
  Future<Map<String, dynamic>> getGrantedPermissions() async {
    final List<dynamic>? result = await methodChannel.invokeMethod<List<dynamic>>('getGrantedPermissions', {});
    final List<String> grantedList = result != null
        ? result.whereType<String>().toList()
        : [];
    return {
      'granted': grantedList,
      'isGranted': grantedList.isNotEmpty,
    };
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

  /// 운동 조회
  @override
  Future<List<Map<String, dynamic>>> getExerciseData(int start, int end) async {
    final result = await methodChannel.invokeMethod<List>('getExerciseData', {
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

  /// 걸음 조회 (집계 데이터)
  @override
  Future<List<Map<String, dynamic>>> getStepData(int start, int end) async {
    final result = await methodChannel.invokeMethod<List>('getStepData', {
      'start': start,
      'end': end,
    });
    return (result ?? [])
        .map((item) => Map<String, dynamic>.from((item as Map).map((key, value) => MapEntry(key.toString(), value))))
        .toList();
  }

  /// 영양소 조회
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

  /// 신체 조회
  @override
  Future<List<Map<String, dynamic>>> getWeightData(int start, int end) async {
    final result = await methodChannel.invokeMethod<List>('getWeightData', {
      'start': start,
      'end': end,
    });
    return (result ?? [])
        .map((item) => Map<String, dynamic>.from((item as Map).map((key, value) => MapEntry(key.toString(), value))))
        .toList();
  }

  /// 산소 포화도 조회
  @override
  Future<List<Map<String, dynamic>>> getOxygenSaturationData(int start, int end) async {
    final result = await methodChannel.invokeMethod<List>('getOxygenSaturationData', {
      'start': start,
      'end': end,
    });
    return (result ?? [])
        .map((item) => Map<String, dynamic>.from((item as Map).map((key, value) => MapEntry(key.toString(), value))))
        .toList();
  }

  /// 체온 조회
  @override
  Future<List<Map<String, dynamic>>> getBodyTemperatureData(int start, int end) async {
    final result = await methodChannel.invokeMethod<List>('getBodyTemperatureData', {
      'start': start,
      'end': end,
    });
    return (result ?? [])
        .map((item) => Map<String, dynamic>.from((item as Map).map((key, value) => MapEntry(key.toString(), value))))
        .toList();
  }

  /// 혈당 조회
  @override
  Future<List<Map<String, dynamic>>> getBloodGlucoseData(int start, int end) async {
    final result = await methodChannel.invokeMethod<List>('getBloodGlucoseData', {
      'start': start,
      'end': end,
    });
    return (result ?? [])
        .map((item) => Map<String, dynamic>.from((item as Map).map((key, value) => MapEntry(key.toString(), value))))
        .toList();
  }
}
