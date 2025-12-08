import 'dart:async';

import 'package:flutter/services.dart';
import 'flutter_samsung_health_platform_interface.dart';

class FlutterSamsungHealth {
  static const EventChannel _eventChannel = EventChannel('flutter_samsung_health/stream');
  StreamSubscription? _subscription;
  
  /// 실시간 헬스 데이터 스트림
  Stream<Map<String, dynamic>> get healthDataStream => 
      _eventChannel.receiveBroadcastStream().cast<Map<String, dynamic>>();
  
  /// 헬스 데이터 스트림 시작 (편의 메서드)
  StreamSubscription startListening(Function(Map<String, dynamic>) onData, {Function(Object)? onError}) {
    _subscription?.cancel(); // 기존 구독 취소
    _subscription = healthDataStream.listen(
      onData,
      onError: onError,
    );
    return _subscription!;
  }
  
  /// 헬스 데이터 스트림 중지
  void stopListening() {
    _subscription?.cancel();
    _subscription = null;
  }

  /// 설치 여부 체크
  Future<Map<String, dynamic>> isSamsungHealthInstalled() {
    return FlutterSamsungHealthPlatform.instance.isSamsungHealthInstalled();
  }

  /// 앱 실행
  Future<Map<String, dynamic>> openSamsungHealth() {
    return FlutterSamsungHealthPlatform.instance.openSamsungHealth();
  }

  /// 연결
  Future<Map<String, dynamic>> connect() {
    return FlutterSamsungHealthPlatform.instance.connect();
  }

  /// 연결 해제
  Future<Map<String, dynamic>> disconnect() {
    return FlutterSamsungHealthPlatform.instance.disconnect();
  }

  /// 권한
  Future<Map<String, dynamic>> requestPermissions(List<String>? types) {
    return FlutterSamsungHealthPlatform.instance.requestPermissions(types);
  }

  /// 승인 권한
  Future<Map<String, dynamic>> getGrantedPermissions() {
    return FlutterSamsungHealthPlatform.instance.getGrantedPermissions();
  }

  /// 전체 데이터 조회
  Future<Map<String,List<Map<String, dynamic>>>> getTotalData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getTotalData(start, end);
  }

  /// 운동 조회
  Future<List<Map<String, dynamic>>> getExerciseData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getExerciseData(start, end);
  }

  /// 심박 조회
  Future<List<Map<String, dynamic>>> getHeartRateData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getHeartRateData(start, end);
  }

  /// 수면 조회 (수면 단계 포함)
  Future<List<Map<String, dynamic>>> getSleepData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getSleepData(start, end);
  }

  /// 걸음 조회
  Future<List<Map<String, dynamic>>> getStepsData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getStepsData(start, end);
  }

  /// 5분 간격 걸음 조회
  Future<List<Map<String, dynamic>>> getFiveMinuteStepsData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getFiveMinuteStepsData(start, end);
  }

  /// 영양소 조회
  Future<List<Map<String, dynamic>>> getNutritionData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getNutritionData(start, end);
  }

  /// 신체 조회
  Future<List<Map<String, dynamic>>> getBodyCompositionData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getBodyCompositionData(start, end);
  }

  /// 산소 포화도 조회
  Future<List<Map<String, dynamic>>> getOxygenSaturationData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getOxygenSaturationData(start, end);
  }

  /// 체온 조회
  Future<List<Map<String, dynamic>>> getBodyTemperatureData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getBodyTemperatureData(start, end);
  }

  /// 혈당 조회
  Future<List<Map<String, dynamic>>> getBloodGlucoseData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getBloodGlucoseData(start, end);
  }

  /// 옵저버 시작
  Future<Map<String, dynamic>> startObserver([List<String>? dataTypes]) {
    return FlutterSamsungHealthPlatform.instance.startObserver(dataTypes);
  }

  /// 옵저버 중단
  Future<Map<String, dynamic>> stopObserver([List<String>? dataTypes]) {
    return FlutterSamsungHealthPlatform.instance.stopObserver(dataTypes);
  }

  /// 옵저버 상태 조회
  Future<dynamic> getObserverStatus([List<String>? dataTypes]) {
    return FlutterSamsungHealthPlatform.instance.getObserverStatus(dataTypes);
  }

  /// Samsung Health 권한 설정 화면 열기
  /// 자동 권한 요청이 실패했을 때 사용자가 수동으로 권한을 설정할 수 있도록 도와줍니다.
  /// 
  /// 반환값:
  /// - action: 'opened_permissions', 'opened_app', 'app_not_found', 'failed'
  /// - message: 상세 메시지
  /// 
  /// 사용 예:
  /// ```dart
  /// final result = await FlutterSamsungHealth.openSamsungHealthPermissions();
  /// if (result['action'] == 'opened_permissions') {
  ///   // 권한 설정 화면이 열림 - 사용자 액션 대기
  /// }
  /// ```
  Future<Map<String, dynamic>> openSamsungHealthPermissions() {
    return FlutterSamsungHealthPlatform.instance.openSamsungHealthPermissions();
  }
}
