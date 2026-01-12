import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_samsung_health/flutter_samsung_health.dart';

/// Samsung Health 관련 비즈니스 로직을 담당하는 서비스 클래스
class SamsungHealthService extends ChangeNotifier {
  final FlutterSamsungHealth _plugin = FlutterSamsungHealth();
  StreamSubscription<Map<String, dynamic>>? _healthDataSubscription;
  
  // 실행 중인 옵저버 목록 (권한 있는 것만)
  final Set<String> _activeObservers = {};
  
  // 상태 관리
  String _connectionStatus = '연결되지 않음';
  String _installStatus = '확인 중...';
  List<String> _grantedPermissions = [];
  Map<String, dynamic>? _lastResult;
  bool _isConnected = false;
  
  // UI 상태
  bool _isResultExpanded = false;
  bool _showPermissionSelection = false;
  bool _showObserverSelection = false;
  bool _showDateSelection = false;
  
  // 날짜 범위
  DateTime _startDate = DateTime.now();
  DateTime _endDate = DateTime.now();
  
  // 권한 선택 상태
  final Map<String, bool> _permissionChecks = {
    'exercise': false,
    'heart_rate': false,
    'steps': false,
    'sleep': false,
    'nutrition': false,
    'body_composition': false,
    'oxygen_saturation': false,
    'body_temperature': false,
    'blood_glucose': false,
  };
  
  final Map<String, String> _permissionLabels = {
    'exercise': '운동',
    'heart_rate': '심박수',
    'steps': '걸음수',
    'sleep': '수면',
    'nutrition': '영양소',
    'body_composition': '신체구성',
    'oxygen_saturation': '산소포화도',
    'body_temperature': '체온',
    'blood_glucose': '혈당',
  };
  
  // 옵저버 선택 상태
  final Map<String, bool> _observerChecks = {
    'exercise': false,
    'nutrition': false,
    'blood_glucose': false,
  };
  
  final Map<String, String> _observerLabels = {
    'exercise': '운동',
    'nutrition': '영양소',
    'blood_glucose': '혈당',
  };
  
  // Getters for UI
  String get connectionStatus => _connectionStatus;
  String get installStatus => _installStatus;
  List<String> get grantedPermissions => List.unmodifiable(_grantedPermissions);
  Map<String, dynamic>? get lastResult => _lastResult;
  bool get isConnected => _isConnected;
  
  // UI 상태 getters
  bool get isResultExpanded => _isResultExpanded;
  bool get showPermissionSelection => _showPermissionSelection;
  bool get showObserverSelection => _showObserverSelection;
  bool get showDateSelection => _showDateSelection;
  
  // 날짜 범위 getters
  DateTime get startDate => _startDate;
  DateTime get endDate => _endDate;
  
  // 권한/옵저버 상태 getters
  Map<String, bool> get permissionChecks => Map.unmodifiable(_permissionChecks);
  Map<String, String> get permissionLabels => Map.unmodifiable(_permissionLabels);
  Map<String, bool> get observerChecks => Map.unmodifiable(_observerChecks);
  Map<String, String> get observerLabels => Map.unmodifiable(_observerLabels);
  
  // 활성 옵저버 상태 getter
  Set<String> get activeObservers => Set.unmodifiable(_activeObservers);
  bool get hasActiveObservers => _activeObservers.isNotEmpty;

  /// UI 상태 변경 메소드들
  void toggleResultExpanded() {
    _isResultExpanded = !_isResultExpanded;
    notifyListeners();
  }
  
  void togglePermissionSelection() {
    _showPermissionSelection = !_showPermissionSelection;
    notifyListeners();
  }
  
  void toggleObserverSelection() {
    _showObserverSelection = !_showObserverSelection;
    notifyListeners();
  }
  
  void toggleDateSelection() {
    _showDateSelection = !_showDateSelection;
    notifyListeners();
  }
  
  /// 날짜 변경 메소드들
  void updateStartDate(DateTime date) {
    _startDate = date;
    notifyListeners();
  }
  
  void updateEndDate(DateTime date) {
    _endDate = date;
    notifyListeners();
  }
  
  /// 권한 체크 상태 변경
  void updatePermissionCheck(String key, bool value) {
    _permissionChecks[key] = value;
    notifyListeners();
  }
  
  /// 옵저버 체크 상태 변경  
  void updateObserverCheck(String key, bool value) {
    _observerChecks[key] = value;
    notifyListeners();
  }
  
  /// 선택된 권한 목록 반환
  List<String> getSelectedPermissions() {
    return _permissionChecks.entries
        .where((entry) => entry.value == true)
        .map((entry) => entry.key)
        .toList();
  }
  
  /// 선택된 옵저버 목록 반환
  List<String> getSelectedObservers() {
    return _observerChecks.entries
        .where((entry) => entry.value == true)
        .map((entry) => entry.key)
        .toList();
  }

  /// 실시간 헬스 데이터 스트림 시작
  void _startHealthDataStream() {
    // 이미 리스너가 등록되어 있으면 통과
    if (_healthDataSubscription != null) {
      return;
    }
    
    // 연결 시 무조건 스트림 리스너 등록
    _healthDataSubscription = _plugin.healthDataStream.listen(
      (data) {
        // 실시간 데이터를 결과에 표시
        final dataType = data['dataType']?.toString() ?? 'unknown';
        final timestamp = data['timestamp'] ?? DateTime.now().millisecondsSinceEpoch;
        
        // 실시간 데이터 수신 메시지
        String message = '실시간 $dataType 데이터 수신';
        
        _lastResult = {
          'success': true,
          'result': data,
          'message': message,
          'timestamp': timestamp,
          'realtime': true,
        };
        try {
          notifyListeners();
        } catch (e) {
          // disposed 상태에서 notifyListeners 호출 시 에러 무시
        }
      },
      onError: (error) {
        _lastResult = {
          'success': false,
          'result': {},
          'message': '실시간 데이터 수신 오류: $error',
          'error': 'STREAM_ERROR',
          'realtime': true,
        };
        try {
          notifyListeners();
        } catch (e) {
          // disposed 상태에서 notifyListeners 호출 시 에러 무시
        }
        
        // 오류 발생 시 스트림이 끊어질 수 있으므로 재연결 시도
        _healthDataSubscription = null;
        _startHealthDataStream();
      },
      cancelOnError: false,
    );
  }


  /// 강제 스트림 중지 (연결 해제 등에서 사용)
  void stopHealthDataStream() {
    _healthDataSubscription?.cancel();
    _healthDataSubscription = null;
  }

  /// 옵저버 시작시 자동으로 스트림도 시작
  @override
  void dispose() {
    _healthDataSubscription?.cancel();
    super.dispose();
  }

  /// Samsung Health 설치 여부 확인
  Future<void> checkInstallation() async {
    try {
      final result = await _plugin.isSamsungHealthInstalled();
      _installStatus = result['result']?['isInstalled'] == true 
          ? '삼성헬스가 설치됨' 
          : '삼성헬스가 설치되지 않음';
      _lastResult = result;
    } catch (e) {
      _installStatus = '오류: $e';
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }

  /// Samsung Health SDK 연결
  Future<void> connect() async {
    try {
      final result = await _plugin.connect();
      _connectionStatus = result['success'] == true ? '연결됨' : '연결 실패';
      _isConnected = result['success'] == true;
      _lastResult = result;
      
      // 연결 성공 시 실시간 데이터 스트림 시작
      if (_isConnected) {
        _startHealthDataStream();
      }
    } catch (e) {
      _connectionStatus = '오류: $e';
      _isConnected = false;
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }

  /// Samsung Health SDK 연결 해제
  Future<void> disconnect() async {
    try {
      final result = await _plugin.disconnect();
      _connectionStatus = result['success'] == true ? '연결 해제됨' : '연결 해제 실패';
      _isConnected = false;
      _lastResult = result;
      
      // 연결 해제 시 활성 옵저버 목록 초기화 및 스트림 중지
      _activeObservers.clear();
      stopHealthDataStream();
    } catch (e) {
      _connectionStatus = '오류: $e';
      _isConnected = false;
      _lastResult = {'error': e.toString()};
      
      // 오류 발생 시에도 활성 옵저버 초기화 및 스트림 중지
      _activeObservers.clear();
      stopHealthDataStream();
    }
    notifyListeners();
  }

  /// Samsung Health 앱 열기 (UI 결과 표시 안함)
  Future<void> openSamsungHealthApp() async {
    try {
      await _plugin.openSamsungHealth();
    } catch (e) {
      // 조용히 무시
    }
  }

  /// 선택된 권한들 요청
  Future<void> requestPermissions([List<String>? permissions]) async {
    try {
      final selectedPermissions = permissions ?? getSelectedPermissions();
      if (selectedPermissions.isEmpty) {
        _lastResult = {'error': '권한을 선택해주세요'};
        notifyListeners();
        return;
      }
      
      final result = await _plugin.requestPermissions(selectedPermissions);
      _lastResult = result;
      
      // 권한 요청 후 자동으로 승인된 권한 업데이트
      await Future.delayed(const Duration(milliseconds: 500));
      await _updateGrantedPermissions();
    } catch (e) {
      _lastResult = {'error': e.toString()};
      notifyListeners();
    }
  }

  /// 승인된 권한 조회 (결과도 표시)
  Future<void> getGrantedPermissions() async {
    try {
      final result = await _plugin.getGrantedPermissions();
      _grantedPermissions = List<String>.from(result['result']?['granted'] ?? []);
      _lastResult = result;
    } catch (e) {
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }
  
  /// 승인된 권한 목록만 업데이트 (결과 화면 변경 안함)
  Future<void> _updateGrantedPermissions() async {
    try {
      final result = await _plugin.getGrantedPermissions();
      _grantedPermissions = List<String>.from(result['result']?['granted'] ?? []);
    } catch (e) {
      // 조용히 무시
    }
    notifyListeners();
  }

  /// 선택된 데이터 타입 옵저버 시작
  Future<void> startObserver([List<String>? dataTypes]) async {
    try {
      final selectedDataTypes = dataTypes ?? getSelectedObservers();
      if (selectedDataTypes.isEmpty) {
        _lastResult = {'error': '옵저버를 시작할 데이터 타입을 선택해주세요'};
        notifyListeners();
        return;
      }
      
      final result = await _plugin.startObserver(selectedDataTypes);
      _lastResult = result;
      
      // 결과에서 성공한 옵저버들을 활성 목록에 추가
      if (result['success'] == true && result['result'] != null) {
        try {
          final resultData = result['result'];
          if (resultData is Map) {
            final startedTypes = List<String>.from(resultData['started'] ?? []);
            final alreadyRunningTypes = List<String>.from(resultData['already_running'] ?? []);
            
            // 성공한 것들과 이미 실행 중인 것들을 활성 목록에 추가
            _activeObservers.addAll(startedTypes);
            _activeObservers.addAll(alreadyRunningTypes);
          }
        } catch (e) {
          // 타입 변환 실패 시 로그만 찍고 계속 진행
          print('옵저버 결과 파싱 오류: $e');
        }
      }
    } catch (e) {
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }

  /// 선택된 데이터 타입 옵저버 중지
  Future<void> stopObserver([List<String>? dataTypes]) async {
    try {
      final selectedDataTypes = dataTypes ?? getSelectedObservers();
      final result = selectedDataTypes.isEmpty
          ? await _plugin.stopObserver()
          : await _plugin.stopObserver(selectedDataTypes);
      _lastResult = result;
      
      // 결과에서 중지된 옵저버들을 활성 목록에서 제거
      if (result['success'] == true && result['result'] != null) {
        try {
          final resultData = result['result'];
          if (resultData is Map) {
            final stoppedTypes = List<String>.from(resultData['stopped'] ?? []);
            
            if (selectedDataTypes.isEmpty) {
              // 전체 중지인 경우 모든 활성 옵저버 제거
              _activeObservers.clear();
            } else {
              // 특정 타입만 중지인 경우 해당 타입들만 제거
              _activeObservers.removeAll(stoppedTypes);
            }
          }
        } catch (e) {
          // 타입 변환 실패 시 로그만 찍고 계속 진행
          print('옵저버 중지 결과 파싱 오류: $e');
        }
      }
    } catch (e) {
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }

  /// 옵저버 상태 확인
  Future<void> getObserverStatus([List<String>? dataTypes]) async {
    try {
      final selectedDataTypes = dataTypes ?? getSelectedObservers();
      final result = selectedDataTypes.isEmpty 
          ? await _plugin.getObserverStatus()
          : await _plugin.getObserverStatus(selectedDataTypes);
      _lastResult = result;
    } catch (e) {
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }

  /// 설정된 날짜 범위의 시간 범위를 계산 (시간대 오프셋 적용)
  (int, int) calculateTimeRange(DateTime startDate, DateTime endDate) {
    final timeZoneOffsetMills = endDate.timeZoneOffset.inMilliseconds;
    final startMillis = startDate.millisecondsSinceEpoch + timeZoneOffsetMills;
    final endMillis = endDate.millisecondsSinceEpoch + timeZoneOffsetMills;
    
    return (startMillis, endMillis);
  }

  /// 전체 데이터 조회
  Future<void> getTotalData([DateTime? startDate, DateTime? endDate]) async {
    try {
      final start = startDate ?? _startDate;
      final end = endDate ?? _endDate;
      final (startTime, endTime) = calculateTimeRange(start, end);
      final result = await _plugin.getTotalData(start: startTime, end: endTime);
      _lastResult = result;
    } catch (e) {
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }

  /// 운동 데이터 조회
  Future<void> getExerciseData(DateTime startDate, DateTime endDate) async {
    try {
      final (start, end) = calculateTimeRange(startDate, endDate);
      final result = await _plugin.getExerciseData(start: start, end: end);
      _lastResult = result;
    } catch (e) {
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }

  /// 심박수 데이터 조회
  Future<void> getHeartRateData(DateTime startDate, DateTime endDate) async {
    try {
      final (start, end) = calculateTimeRange(startDate, endDate);
      final result = await _plugin.getHeartRateData(start: start, end: end);
      _lastResult = result;
    } catch (e) {
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }

  /// 걸음수 데이터 조회
  Future<void> getStepsData(DateTime startDate, DateTime endDate) async {
    try {
      final (start, end) = calculateTimeRange(startDate, endDate);
      final result = await _plugin.getStepsData(start: start, end: end);
      _lastResult = result;
    } catch (e) {
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }

  /// 5분 간격 걸음수 데이터 조회
  Future<void> getFiveMinuteStepsData(DateTime startDate, DateTime endDate) async {
    try {
      final (start, end) = calculateTimeRange(startDate, endDate);
      final result = await _plugin.getFiveMinuteStepsData(start: start, end: end);
      _lastResult = result;
    } catch (e) {
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }

  /// 수면 데이터 조회
  Future<void> getSleepData(DateTime startDate, DateTime endDate) async {
    try {
      final (start, end) = calculateTimeRange(startDate, endDate);
      final result = await _plugin.getSleepData(start: start, end: end);
      _lastResult = result;
    } catch (e) {
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }

  /// 영양소 데이터 조회
  Future<void> getNutritionData(DateTime startDate, DateTime endDate) async {
    try {
      final (start, end) = calculateTimeRange(startDate, endDate);
      final result = await _plugin.getNutritionData(start: start, end: end);
      _lastResult = result;
    } catch (e) {
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }

  /// 신체구성 데이터 조회
  Future<void> getBodyCompositionData(DateTime startDate, DateTime endDate) async {
    try {
      final (start, end) = calculateTimeRange(startDate, endDate);
      final result = await _plugin.getBodyCompositionData(start: start, end: end);
      _lastResult = result;
    } catch (e) {
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }

  /// 산소포화도 데이터 조회
  Future<void> getOxygenSaturationData(DateTime startDate, DateTime endDate) async {
    try {
      final (start, end) = calculateTimeRange(startDate, endDate);
      final result = await _plugin.getOxygenSaturationData(start: start, end: end);
      _lastResult = result;
    } catch (e) {
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }

  /// 체온 데이터 조회
  Future<void> getBodyTemperatureData(DateTime startDate, DateTime endDate) async {
    try {
      final (start, end) = calculateTimeRange(startDate, endDate);
      final result = await _plugin.getBodyTemperatureData(start: start, end: end);
      _lastResult = result;
    } catch (e) {
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }

  /// 혈당 데이터 조회
  Future<void> getBloodGlucoseData(DateTime startDate, DateTime endDate) async {
    try {
      final (start, end) = calculateTimeRange(startDate, endDate);
      final result = await _plugin.getBloodGlucoseData(start: start, end: end);
      _lastResult = result;
    } catch (e) {
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }

  /// 5분 간격 걸음수 샘플 데이터 생성 (자정 기준 고정 구간)
  Future<void> generateFiveMinuteStepsSampleData() async {
    try {
      // 오늘 하루 동안의 샘플 데이터 생성 (자정 기준 고정 5분 구간)
      final now = DateTime.now();
      final today = DateTime(now.year, now.month, now.day);
      
      final sampleData = <Map<String, dynamic>>[];
      final random = DateTime.now().millisecondsSinceEpoch % 1000; // 간단한 시드값
      
      // 자정(00:00)부터 현재시각까지 모든 5분 구간 생성
      final totalMinutesToday = now.difference(today).inMinutes;
      final totalCompleteIntervals = totalMinutesToday ~/ 5; // 완전한 5분 구간만
      
      // 활동이 있을 만한 시간대 정의 (확률적 활동)
      final activeHours = {7, 8, 9, 12, 13, 14, 17, 18, 19, 20, 21, 22};
      
      for (int intervalIndex = 0; intervalIndex < totalCompleteIntervals; intervalIndex++) {
        final startMinutes = intervalIndex * 5;
        final startTime = today.add(Duration(minutes: startMinutes));
        final endTime = startTime.add(const Duration(minutes: 5));
        final hour = startTime.hour;
        
        // 활동 시간대가 아니면 80% 확률로 건너뛰기
        if (!activeHours.contains(hour) && (random + intervalIndex) % 10 < 8) {
          continue;
        }
        
        // 활동 시간대여도 30% 확률로 건너뛰기 (모든 구간에 데이터가 있지는 않음)
        if (activeHours.contains(hour) && (random + intervalIndex * 3) % 10 < 3) {
          continue;
        }
        
        // 걸음수는 시간대별로 다르게 설정
        int steps;
        if (hour >= 7 && hour <= 9) {
          // 아침 출근시간: 높은 걸음수
          steps = 80 + (random + intervalIndex) % 120;
        } else if (hour >= 12 && hour <= 14) {
          // 점심시간: 중간 걸음수
          steps = 40 + (random + intervalIndex) % 80;
        } else if (hour >= 17 && hour <= 19) {
          // 저녁 퇴근시간: 높은 걸음수
          steps = 90 + (random + intervalIndex) % 150;
        } else if (hour >= 20 && hour <= 22) {
          // 저녁 산책시간: 중간 걸음수
          steps = 30 + (random + intervalIndex) % 100;
        } else {
          // 기타 시간: 낮은 걸음수
          steps = 10 + (random + intervalIndex) % 50;
        }
        
        sampleData.add({
          'start_time': startTime.millisecondsSinceEpoch,
          'end_time': endTime.millisecondsSinceEpoch,
          'start_dttm': '${startTime.year.toString().padLeft(4, '0')}-${startTime.month.toString().padLeft(2, '0')}-${startTime.day.toString().padLeft(2, '0')} ${startTime.hour.toString().padLeft(2, '0')}:${startTime.minute.toString().padLeft(2, '0')}:${startTime.second.toString().padLeft(2, '0')}',
          'end_dttm': '${endTime.year.toString().padLeft(4, '0')}-${endTime.month.toString().padLeft(2, '0')}-${endTime.day.toString().padLeft(2, '0')} ${endTime.hour.toString().padLeft(2, '0')}:${endTime.minute.toString().padLeft(2, '0')}:${endTime.second.toString().padLeft(2, '0')}',
          'steps': steps,
          'interval_minutes': 5,
          'data_type': 'FIVE_MINUTE_STEPS',
          'device_info': {
            'android_version': 'Sample Data',
            'sdk_version': 0,
            'device_manufacturer': 'Sample',
            'device_model': 'Sample Device',
          },
        });
      }
      
      // 시간순으로 정렬
      sampleData.sort((a, b) => (a['start_time'] as int).compareTo(b['start_time'] as int));
      
      _lastResult = {
        'success': true,
        'result': sampleData,
        'message': '5분 간격 걸음수 샘플 데이터 생성됨 (${sampleData.length}개 구간)',
        'sample_data': true,
      };
    } catch (e) {
      _lastResult = {'error': e.toString()};
    }
    notifyListeners();
  }
}