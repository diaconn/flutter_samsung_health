import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'flutter_samsung_health_platform_interface.dart';

/// An implementation of [FlutterSamsungHealthPlatform] that uses method channels.
class MethodChannelFlutterSamsungHealth extends FlutterSamsungHealthPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_samsung_health');

  /// 앱 설치 여부 확인
  @override
  Future<Map<String, dynamic>> isSamsungHealthInstalled() async {
    final result = await methodChannel.invokeMethod<Map>('isSamsungHealthInstalled', {});
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': {'isInstalled': false, 'status': 'error'},
      'message': '설치 확인 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 앱 실행
  @override
  Future<Map<String, dynamic>> openSamsungHealth() async {
    final result = await methodChannel.invokeMethod<Map>('openSamsungHealth', {});
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': {'isLaunched': false, 'status': 'error'},
      'message': '앱 실행 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// SDK 연결
  @override
  Future<Map<String, dynamic>> connect() async {
    final result = await methodChannel.invokeMethod<Map>('connect', {});
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': {'isConnected': false, 'status': 'error'},
      'message': '연결 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// SDK 연결 해제
  @override
  Future<Map<String, dynamic>> disconnect() async {
    final result = await methodChannel.invokeMethod<Map>('disconnect', {});
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': {'isDisconnected': false, 'status': 'error'},
      'message': '연결 해제 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 데이터 권한 요청
  @override
  Future<Map<String, dynamic>> requestPermissions(List<String>? types) async {
    final result = await methodChannel.invokeMethod<Map>('requestPermissions', {"types": types});
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': {'granted': [], 'denied': [], 'status': 'error'},
      'message': '권한 요청 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 승인된 권한 조회
  @override
  Future<Map<String, dynamic>> getGrantedPermissions() async {
    final result = await methodChannel.invokeMethod<Map>('getGrantedPermissions', {});
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': {'granted': [], 'status': 'error'},
      'message': '권한 조회 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 권한 설정 화면 열기
  @override
  Future<Map<String, dynamic>> openSamsungHealthPermissions() async {
    final result = await methodChannel.invokeMethod<Map>('openSamsungHealthPermissions', {});
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': {'isOpened': false, 'status': 'error'},
      'message': '권한 설정 화면 열기 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 옵저버 시작
  @override
  Future<Map<String, dynamic>> startObserver(List<String>? dataTypes) async {
    final result = await methodChannel.invokeMethod<Map>('startObserver', {
      'dataTypes': dataTypes,
    });
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': {'started': [], 'already_running': [], 'failed': [], 'status': 'error'},
      'message': '옵저버 시작 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 옵저버 중단
  @override
  Future<Map<String, dynamic>> stopObserver(List<String>? dataTypes) async {
    final result = await methodChannel.invokeMethod<Map>('stopObserver', {
      'dataTypes': dataTypes,
    });
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': {'stopped': [], 'not_running': [], 'status': 'error'},
      'message': '옵저버 중단 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 옵저버 상태 조회
  @override
  Future<dynamic> getObserverStatus(List<String>? dataTypes) async {
    final result = await methodChannel.invokeMethod('getObserverStatus', {
      'dataTypes': dataTypes,
    });
    
    // 단일 타입인 경우와 여러 타입인 경우를 구분
    if (result is Map) {
      return result.map((key, value) => MapEntry(key.toString(), value));
    }
    return result ?? {
      'success': false,
      'result': {'running': [], 'stopped': [], 'status': 'error'},
      'message': '옵저버 상태 조회 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 전체 데이터 조회
  @override
  Future<Map<String, dynamic>> getTotalData(int start, int end) async {
    final result = await methodChannel.invokeMethod<Map>('getTotalData', {
      'start': start,
      'end': end,
    });
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': {},
      'message': '전체 데이터 조회 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 운동 데이터 조회
  @override
  Future<Map<String, dynamic>> getExerciseData(int start, int end) async {
    final result = await methodChannel.invokeMethod<Map>('getExerciseData', {
      'start': start,
      'end': end,
    });
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': [],
      'message': '운동 데이터 조회 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 심박수 데이터 조회
  @override
  Future<Map<String, dynamic>> getHeartRateData(int start, int end) async {
    final result = await methodChannel.invokeMethod<Map>('getHeartRateData', {
      'start': start,
      'end': end,
    });
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': [],
      'message': '심박수 데이터 조회 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 걸음수 데이터 조회
  @override
  Future<Map<String, dynamic>> getStepsData(int start, int end) async {
    final result = await methodChannel.invokeMethod<Map>('getStepsData', {
      'start': start,
      'end': end,
    });
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': [],
      'message': '걸음수 데이터 조회 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 5분 간격 걸음수 데이터 조회
  @override
  Future<Map<String, dynamic>> getFiveMinuteStepsData(int start, int end) async {
    final result = await methodChannel.invokeMethod<Map>('getFiveMinuteStepsData', {
      'start': start,
      'end': end,
    });
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': [],
      'message': '5분 간격 걸음수 데이터 조회 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 수면 데이터 조회
  @override
  Future<Map<String, dynamic>> getSleepData(int start, int end) async {
    final result = await methodChannel.invokeMethod<Map>('getSleepData', {
      'start': start,
      'end': end,
    });
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': [],
      'message': '수면 데이터 조회 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 영양소 데이터 조회
  @override
  Future<Map<String, dynamic>> getNutritionData(int start, int end) async {
    final result = await methodChannel.invokeMethod<Map>('getNutritionData', {
      'start': start,
      'end': end,
    });
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': [],
      'message': '영양소 데이터 조회 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 혈당 데이터 조회
  @override
  Future<Map<String, dynamic>> getBloodGlucoseData(int start, int end) async {
    final result = await methodChannel.invokeMethod<Map>('getBloodGlucoseData', {
      'start': start,
      'end': end,
    });
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': [],
      'message': '혈당 데이터 조회 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 신체 구성 데이터 조회
  @override
  Future<Map<String, dynamic>> getBodyCompositionData(int start, int end) async {
    final result = await methodChannel.invokeMethod<Map>('getBodyCompositionData', {
      'start': start,
      'end': end,
    });
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': [],
      'message': '신체구성 데이터 조회 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 산소 포화도 데이터 조회
  @override
  Future<Map<String, dynamic>> getOxygenSaturationData(int start, int end) async {
    final result = await methodChannel.invokeMethod<Map>('getOxygenSaturationData', {
      'start': start,
      'end': end,
    });
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': [],
      'message': '산소포화도 데이터 조회 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }

  /// 체온 데이터 조회
  @override
  Future<Map<String, dynamic>> getBodyTemperatureData(int start, int end) async {
    final result = await methodChannel.invokeMethod<Map>('getBodyTemperatureData', {
      'start': start,
      'end': end,
    });
    return result?.map((key, value) => MapEntry(key.toString(), value)) ?? {
      'success': false,
      'result': [],
      'message': '체온 데이터 조회 실패 - 메소드 채널 오류',
      'error': 'METHOD_CHANNEL_ERROR'
    };
  }
}
