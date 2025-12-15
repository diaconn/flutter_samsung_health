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

  /// 앱 설치 여부 확인
  Future<Map<String, dynamic>> isSamsungHealthInstalled() {
    return FlutterSamsungHealthPlatform.instance.isSamsungHealthInstalled();
  }

  /// 앱 실행
  Future<Map<String, dynamic>> openSamsungHealth() {
    return FlutterSamsungHealthPlatform.instance.openSamsungHealth();
  }

  /// SDK 연결
  Future<Map<String, dynamic>> connect() {
    return FlutterSamsungHealthPlatform.instance.connect();
  }

  /// SDK 연결 해제
  Future<Map<String, dynamic>> disconnect() {
    return FlutterSamsungHealthPlatform.instance.disconnect();
  }

  /// 데이터 권한 요청
  Future<Map<String, dynamic>> requestPermissions(List<String>? types) {
    return FlutterSamsungHealthPlatform.instance.requestPermissions(types);
  }

  /// 승인된 권한 조회
  Future<Map<String, dynamic>> getGrantedPermissions() {
    return FlutterSamsungHealthPlatform.instance.getGrantedPermissions();
  }

  /// 권한 설정 화면 열기
  Future<Map<String, dynamic>> openSamsungHealthPermissions() {
    return FlutterSamsungHealthPlatform.instance.openSamsungHealthPermissions();
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

  /// 전체 데이터 조회
  Future<Map<String,List<Map<String, dynamic>>>> getTotalData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getTotalData(start, end);
  }

  /// 운동 데이터 조회
  Future<Map<String, dynamic>> getExerciseData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getExerciseData(start, end);
  }

  /// 심박수 데이터 조회
  Future<Map<String, dynamic>> getHeartRateData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getHeartRateData(start, end);
  }

  /// 걸음수 데이터 조회
  Future<Map<String, dynamic>> getStepsData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getStepsData(start, end);
  }

  /// 5분 간격 걸음수 데이터 조회
  Future<Map<String, dynamic>> getFiveMinuteStepsData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getFiveMinuteStepsData(start, end);
  }

  /// 수면 데이터 조회
  Future<Map<String, dynamic>> getSleepData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getSleepData(start, end);
  }

  /// 영양소 데이터 조회
  Future<Map<String, dynamic>> getNutritionData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getNutritionData(start, end);
  }

  /// 혈당 데이터 조회
  Future<Map<String, dynamic>> getBloodGlucoseData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getBloodGlucoseData(start, end);
  }

  /// 신체 구성 데이터 조회
  Future<Map<String, dynamic>> getBodyCompositionData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getBodyCompositionData(start, end);
  }

  /// 산소 포화도 데이터 조회
  Future<Map<String, dynamic>> getOxygenSaturationData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getOxygenSaturationData(start, end);
  }

  /// 체온 데이터 조회
  Future<Map<String, dynamic>> getBodyTemperatureData({
    required int start,
    required int end,
  }) {
    return FlutterSamsungHealthPlatform.instance.getBodyTemperatureData(start, end);
  }
}
