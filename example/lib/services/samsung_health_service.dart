import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_samsung_health/flutter_samsung_health.dart';

/// Samsung Health 관련 비즈니스 로직을 담당하는 서비스 클래스
class SamsungHealthService extends ChangeNotifier {
  final FlutterSamsungHealth _plugin = FlutterSamsungHealth();
  StreamSubscription<Map<String, dynamic>>? _healthDataSubscription;

  // 실행 중인 옵저버 목록
  final Set<SamsungHealthDataType> _activeObservers = {};

  // 상태 관리
  String _connectionStatus = '연결되지 않음';
  String _installStatus = '확인 중...';
  List<SamsungHealthDataType> _grantedPermissions = [];
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
  final Map<SamsungHealthDataType, bool> _permissionChecks = {
    for (final type in SamsungHealthDataType.permissionTypes) type: false,
  };

  // 권한 레이블
  static const Map<SamsungHealthDataType, String> _permissionLabels = {
    SamsungHealthDataType.exercise: '운동',
    SamsungHealthDataType.heartRate: '심박수',
    SamsungHealthDataType.steps: '걸음수',
    SamsungHealthDataType.sleep: '수면',
    SamsungHealthDataType.nutrition: '영양소',
    SamsungHealthDataType.bodyComposition: '신체구성',
    SamsungHealthDataType.bloodOxygen: '산소포화도',
    SamsungHealthDataType.bodyTemperature: '체온',
    SamsungHealthDataType.bloodGlucose: '혈당',
  };

  // 옵저버 선택 상태
  final Map<SamsungHealthDataType, bool> _observerChecks = {
    for (final type in SamsungHealthDataType.observerTypes) type: false,
  };

  // 옵저버 레이블
  static const Map<SamsungHealthDataType, String> _observerLabels = {
    SamsungHealthDataType.exercise: '운동',
    SamsungHealthDataType.nutrition: '영양소',
    SamsungHealthDataType.bloodGlucose: '혈당',
  };

  // Getters for UI
  String get connectionStatus => _connectionStatus;
  String get installStatus => _installStatus;
  List<SamsungHealthDataType> get grantedPermissions => List.unmodifiable(_grantedPermissions);
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

  // 권한/옵저버 상태 getters (UI 위젯용 String 키 변환)
  Map<String, bool> get permissionChecks => {
    for (final entry in _permissionChecks.entries) entry.key.value: entry.value,
  };
  Map<String, String> get permissionLabels => {
    for (final entry in _permissionLabels.entries) entry.key.value: entry.value,
  };
  Map<String, bool> get observerChecks => {
    for (final entry in _observerChecks.entries) entry.key.value: entry.value,
  };
  Map<String, String> get observerLabels => {
    for (final entry in _observerLabels.entries) entry.key.value: entry.value,
  };

  // 활성 옵저버 상태 getter
  Set<SamsungHealthDataType> get activeObservers => Set.unmodifiable(_activeObservers);
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

  /// 권한 체크 상태 변경 (UI 위젯에서 String 키 사용)
  void updatePermissionCheck(String key, bool value) {
    final type = SamsungHealthDataType.fromValue(key);
    if (type != null) {
      _permissionChecks[type] = value;
      notifyListeners();
    }
  }

  /// 옵저버 체크 상태 변경 (UI 위젯에서 String 키 사용)
  void updateObserverCheck(String key, bool value) {
    final type = SamsungHealthDataType.fromValue(key);
    if (type != null) {
      _observerChecks[type] = value;
      notifyListeners();
    }
  }

  /// 선택된 권한 목록 반환
  List<SamsungHealthDataType> getSelectedPermissions() {
    return _permissionChecks.entries
        .where((entry) => entry.value == true)
        .map((entry) => entry.key)
        .toList();
  }

  /// 선택된 옵저버 목록 반환
  List<SamsungHealthDataType> getSelectedObservers() {
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
  Future<void> requestPermissions([List<SamsungHealthDataType>? permissions]) async {
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
      final grantedStrings = List<String>.from(result['result']?['granted'] ?? []);
      _grantedPermissions = grantedStrings
          .map((s) => SamsungHealthDataType.fromValue(s))
          .whereType<SamsungHealthDataType>()
          .toList();
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
      final grantedStrings = List<String>.from(result['result']?['granted'] ?? []);
      _grantedPermissions = grantedStrings
          .map((s) => SamsungHealthDataType.fromValue(s))
          .whereType<SamsungHealthDataType>()
          .toList();
    } catch (e) {
      // 조용히 무시
    }
    notifyListeners();
  }

  /// 선택된 데이터 타입 옵저버 시작
  Future<void> startObserver([List<SamsungHealthDataType>? dataTypes]) async {
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
            final startedStrings = List<String>.from(resultData['started'] ?? []);
            final alreadyRunningStrings = List<String>.from(resultData['already_running'] ?? []);

            // String을 enum으로 변환하여 추가
            for (final s in [...startedStrings, ...alreadyRunningStrings]) {
              final type = SamsungHealthDataType.fromValue(s);
              if (type != null) _activeObservers.add(type);
            }
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
  Future<void> stopObserver([List<SamsungHealthDataType>? dataTypes]) async {
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
            final stoppedStrings = List<String>.from(resultData['stopped'] ?? []);

            if (selectedDataTypes.isEmpty) {
              // 전체 중지인 경우 모든 활성 옵저버 제거
              _activeObservers.clear();
            } else {
              // 특정 타입만 중지인 경우 해당 타입들만 제거
              for (final s in stoppedStrings) {
                final type = SamsungHealthDataType.fromValue(s);
                if (type != null) _activeObservers.remove(type);
              }
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
  Future<void> getObserverStatus([List<SamsungHealthDataType>? dataTypes]) async {
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

  /// 설정된 날짜 범위의 시간 범위를 계산 (플러그인에서 시스템 타임존 자동 적용)
  (int, int) calculateTimeRange(DateTime startDate, DateTime endDate) {
    return (startDate.millisecondsSinceEpoch, endDate.millisecondsSinceEpoch);
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
  Future<void> getBloodOxygenData(DateTime startDate, DateTime endDate) async {
    try {
      final (start, end) = calculateTimeRange(startDate, endDate);
      final result = await _plugin.getBloodOxygenData(start: start, end: end);
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

  // ===== 샘플 데이터 생성 메서드들 =====

  /// 샘플 디바이스 정보 생성
  Map<String, dynamic> _getSampleDeviceInfo() {
    return {
      'device_info': {
        'android_version': '14',
        'sdk_version': 34,
        'device_manufacturer': 'Samsung',
        'device_model': 'Galaxy S24 Ultra',
      }
    };
  }

  /// 전체 샘플 데이터 조회
  void getSampleTotalData() {
    final now = DateTime.now();
    _lastResult = {
      'success': true,
      'result': {
        'exercise': _generateSampleExerciseData(now),
        'heart_rate': _generateSampleHeartRateData(now),
        'sleep': _generateSampleSleepData(now),
        'steps': _generateSampleStepsData(now),
        'five_minute_steps': _generateSampleFiveMinuteStepsData(now),
        'nutrition': _generateSampleNutritionData(now),
        'body_composition': _generateSampleBodyCompositionData(now),
        'blood_oxygen': _generateSampleBloodOxygenData(now),
        'body_temperature': _generateSampleBodyTemperatureData(now),
        'blood_glucose': _generateSampleBloodGlucoseData(now),
      },
      'message': '샘플 데이터 조회 완료 (전체)',
      'isSampleData': true,
    };
    notifyListeners();
  }

  /// 샘플 운동 데이터 생성
  List<Map<String, dynamic>> _generateSampleExerciseData(DateTime baseTime) {
    final deviceInfo = _getSampleDeviceInfo();
    return [
      {
        'uid': 'sample_exercise_001',
        'start_time': baseTime.subtract(const Duration(hours: 2)).millisecondsSinceEpoch,
        'end_time': baseTime.subtract(const Duration(hours: 1)).millisecondsSinceEpoch,
        'exercise_type': 1001,
        'exercise_type_name': 'RUNNING',
        'duration': 3600000, // 1시간
        'calories': 450.5,
        'distance': 5200.0, // 5.2km
        'max_heart_rate': 165.0,
        'mean_heart_rate': 142.0,
        'min_heart_rate': 95.0,
        ...deviceInfo,
      },
      {
        'uid': 'sample_exercise_002',
        'start_time': baseTime.subtract(const Duration(days: 1, hours: 3)).millisecondsSinceEpoch,
        'end_time': baseTime.subtract(const Duration(days: 1, hours: 2)).millisecondsSinceEpoch,
        'exercise_type': 1002,
        'exercise_type_name': 'WALKING',
        'duration': 3600000,
        'calories': 180.0,
        'distance': 3500.0,
        'max_heart_rate': 110.0,
        'mean_heart_rate': 95.0,
        'min_heart_rate': 72.0,
        ...deviceInfo,
      },
      {
        'uid': 'sample_exercise_003',
        'start_time': baseTime.subtract(const Duration(days: 2, hours: 1)).millisecondsSinceEpoch,
        'end_time': baseTime.subtract(const Duration(days: 2)).millisecondsSinceEpoch,
        'exercise_type': 1010,
        'exercise_type_name': 'CYCLING',
        'duration': 3600000,
        'calories': 320.0,
        'distance': 15000.0, // 15km
        'max_heart_rate': 155.0,
        'mean_heart_rate': 130.0,
        'min_heart_rate': 85.0,
        ...deviceInfo,
      },
    ];
  }

  /// 샘플 심박수 데이터 생성
  List<Map<String, dynamic>> _generateSampleHeartRateData(DateTime baseTime) {
    final deviceInfo = _getSampleDeviceInfo();
    return [
      {
        'uid': 'sample_hr_001',
        'start_time': baseTime.subtract(const Duration(minutes: 30)).millisecondsSinceEpoch,
        'end_time': baseTime.millisecondsSinceEpoch,
        'heart_rate': 72.0,
        'min_heart_rate': 68.0,
        'max_heart_rate': 85.0,
        'series_count': 3,
        'series_data': [
          {'start_time': baseTime.subtract(const Duration(minutes: 30)).millisecondsSinceEpoch, 'heart_rate': 68.0},
          {'start_time': baseTime.subtract(const Duration(minutes: 15)).millisecondsSinceEpoch, 'heart_rate': 72.0},
          {'start_time': baseTime.millisecondsSinceEpoch, 'heart_rate': 85.0},
        ],
        ...deviceInfo,
      },
      {
        'uid': 'sample_hr_002',
        'start_time': baseTime.subtract(const Duration(hours: 2)).millisecondsSinceEpoch,
        'end_time': baseTime.subtract(const Duration(hours: 1, minutes: 30)).millisecondsSinceEpoch,
        'heart_rate': 145.0,
        'min_heart_rate': 120.0,
        'max_heart_rate': 168.0,
        'series_count': 2,
        'series_data': [
          {'start_time': baseTime.subtract(const Duration(hours: 2)).millisecondsSinceEpoch, 'heart_rate': 120.0},
          {'start_time': baseTime.subtract(const Duration(hours: 1, minutes: 30)).millisecondsSinceEpoch, 'heart_rate': 168.0},
        ],
        ...deviceInfo,
      },
    ];
  }

  /// 샘플 수면 데이터 생성
  List<Map<String, dynamic>> _generateSampleSleepData(DateTime baseTime) {
    final deviceInfo = _getSampleDeviceInfo();
    final sleepStart = DateTime(baseTime.year, baseTime.month, baseTime.day - 1, 23, 30);
    final sleepEnd = DateTime(baseTime.year, baseTime.month, baseTime.day, 7, 15);

    return [
      {
        'uid': 'sample_sleep_001',
        'start_time': sleepStart.millisecondsSinceEpoch,
        'end_time': sleepEnd.millisecondsSinceEpoch,
        'duration': sleepEnd.difference(sleepStart).inMilliseconds,
        'sleep_score': 85.0,
        'session_count': 1,
        'sessions': [
          {
            'session_no': 1,
            'session_start_time': sleepStart.millisecondsSinceEpoch,
            'session_end_time': sleepEnd.millisecondsSinceEpoch,
            'session_duration': sleepEnd.difference(sleepStart).inMilliseconds,
            'sleep_session_id': 12345,
            'stage_count': 5,
            'stages': [
              {
                'stage_type': 1,
                'stage_type_name': 'AWAKE',
                'start_time': sleepStart.millisecondsSinceEpoch,
                'end_time': sleepStart.add(const Duration(minutes: 15)).millisecondsSinceEpoch,
              },
              {
                'stage_type': 2,
                'stage_type_name': 'LIGHT',
                'start_time': sleepStart.add(const Duration(minutes: 15)).millisecondsSinceEpoch,
                'end_time': sleepStart.add(const Duration(hours: 1, minutes: 30)).millisecondsSinceEpoch,
              },
              {
                'stage_type': 3,
                'stage_type_name': 'DEEP',
                'start_time': sleepStart.add(const Duration(hours: 1, minutes: 30)).millisecondsSinceEpoch,
                'end_time': sleepStart.add(const Duration(hours: 3)).millisecondsSinceEpoch,
              },
              {
                'stage_type': 4,
                'stage_type_name': 'REM',
                'start_time': sleepStart.add(const Duration(hours: 3)).millisecondsSinceEpoch,
                'end_time': sleepStart.add(const Duration(hours: 4, minutes: 30)).millisecondsSinceEpoch,
              },
              {
                'stage_type': 2,
                'stage_type_name': 'LIGHT',
                'start_time': sleepStart.add(const Duration(hours: 4, minutes: 30)).millisecondsSinceEpoch,
                'end_time': sleepEnd.millisecondsSinceEpoch,
              },
            ],
          },
        ],
        ...deviceInfo,
      },
    ];
  }

  /// 샘플 걸음수 데이터 생성
  List<Map<String, dynamic>> _generateSampleStepsData(DateTime baseTime) {
    final deviceInfo = _getSampleDeviceInfo();
    return [
      {
        'date': baseTime.toString().substring(0, 10),
        'start_time': DateTime(baseTime.year, baseTime.month, baseTime.day).millisecondsSinceEpoch,
        'end_time': DateTime(baseTime.year, baseTime.month, baseTime.day + 1).millisecondsSinceEpoch,
        'steps': 8542,
        'data_type': 'DAILY_STEPS',
        ...deviceInfo,
      },
      {
        'date': baseTime.subtract(const Duration(days: 1)).toString().substring(0, 10),
        'start_time': DateTime(baseTime.year, baseTime.month, baseTime.day - 1).millisecondsSinceEpoch,
        'end_time': DateTime(baseTime.year, baseTime.month, baseTime.day).millisecondsSinceEpoch,
        'steps': 12350,
        'data_type': 'DAILY_STEPS',
        ...deviceInfo,
      },
      {
        'date': baseTime.subtract(const Duration(days: 2)).toString().substring(0, 10),
        'start_time': DateTime(baseTime.year, baseTime.month, baseTime.day - 2).millisecondsSinceEpoch,
        'end_time': DateTime(baseTime.year, baseTime.month, baseTime.day - 1).millisecondsSinceEpoch,
        'steps': 6780,
        'data_type': 'DAILY_STEPS',
        ...deviceInfo,
      },
    ];
  }

  /// 샘플 5분 간격 걸음수 데이터 생성
  List<Map<String, dynamic>> _generateSampleFiveMinuteStepsData(DateTime baseTime) {
    final deviceInfo = _getSampleDeviceInfo();
    final baseStart = DateTime(baseTime.year, baseTime.month, baseTime.day, 9, 0);

    return [
      {
        'start_time': baseStart.millisecondsSinceEpoch,
        'end_time': baseStart.add(const Duration(minutes: 5)).millisecondsSinceEpoch,
        'steps': 125,
        'interval_minutes': 5,
        'data_type': 'FIVE_MINUTE_STEPS',
        ...deviceInfo,
      },
      {
        'start_time': baseStart.add(const Duration(minutes: 5)).millisecondsSinceEpoch,
        'end_time': baseStart.add(const Duration(minutes: 10)).millisecondsSinceEpoch,
        'steps': 230,
        'interval_minutes': 5,
        'data_type': 'FIVE_MINUTE_STEPS',
        ...deviceInfo,
      },
      {
        'start_time': baseStart.add(const Duration(minutes: 10)).millisecondsSinceEpoch,
        'end_time': baseStart.add(const Duration(minutes: 15)).millisecondsSinceEpoch,
        'steps': 180,
        'interval_minutes': 5,
        'data_type': 'FIVE_MINUTE_STEPS',
        ...deviceInfo,
      },
      {
        'start_time': baseStart.add(const Duration(minutes: 15)).millisecondsSinceEpoch,
        'end_time': baseStart.add(const Duration(minutes: 20)).millisecondsSinceEpoch,
        'steps': 95,
        'interval_minutes': 5,
        'data_type': 'FIVE_MINUTE_STEPS',
        ...deviceInfo,
      },
    ];
  }

  /// 샘플 영양소 데이터 생성
  List<Map<String, dynamic>> _generateSampleNutritionData(DateTime baseTime) {
    final deviceInfo = _getSampleDeviceInfo();
    return [
      {
        'uid': 'sample_nutrition_001',
        'start_time': DateTime(baseTime.year, baseTime.month, baseTime.day, 8, 0).millisecondsSinceEpoch,
        'end_time': DateTime(baseTime.year, baseTime.month, baseTime.day, 8, 30).millisecondsSinceEpoch,
        'title': '아침식사',
        'meal_type': 1,
        'meal_type_name': 'BREAKFAST',
        'calories': 450.0,
        'total_fat': 15.0,
        'saturated_fat': 5.0,
        'trans_fat': 0.0,
        'polysaturated_fat': 3.0,
        'monosaturated_fat': 7.0,
        'cholesterol': 180.0,
        'protein': 25.0,
        'carbohydrate': 55.0,
        'sugar': 12.0,
        'dietary_fiber': 8.0,
        'sodium': 650.0,
        'potassium': 420.0,
        'calcium': 250.0,
        'iron': 3.5,
        'vitamin_a': 120.0,
        'vitamin_c': 25.0,
        ...deviceInfo,
      },
      {
        'uid': 'sample_nutrition_002',
        'start_time': DateTime(baseTime.year, baseTime.month, baseTime.day, 12, 30).millisecondsSinceEpoch,
        'end_time': DateTime(baseTime.year, baseTime.month, baseTime.day, 13, 0).millisecondsSinceEpoch,
        'title': '점심식사',
        'meal_type': 2,
        'meal_type_name': 'LUNCH',
        'calories': 720.0,
        'total_fat': 25.0,
        'saturated_fat': 8.0,
        'trans_fat': 0.5,
        'polysaturated_fat': 5.0,
        'monosaturated_fat': 11.0,
        'cholesterol': 220.0,
        'protein': 35.0,
        'carbohydrate': 85.0,
        'sugar': 18.0,
        'dietary_fiber': 12.0,
        'sodium': 1200.0,
        'potassium': 680.0,
        'calcium': 180.0,
        'iron': 5.0,
        'vitamin_a': 80.0,
        'vitamin_c': 35.0,
        ...deviceInfo,
      },
      {
        'uid': 'sample_nutrition_003',
        'start_time': DateTime(baseTime.year, baseTime.month, baseTime.day, 19, 0).millisecondsSinceEpoch,
        'end_time': DateTime(baseTime.year, baseTime.month, baseTime.day, 19, 45).millisecondsSinceEpoch,
        'title': '저녁식사',
        'meal_type': 3,
        'meal_type_name': 'DINNER',
        'calories': 650.0,
        'total_fat': 22.0,
        'saturated_fat': 7.0,
        'trans_fat': 0.0,
        'polysaturated_fat': 4.0,
        'monosaturated_fat': 10.0,
        'cholesterol': 195.0,
        'protein': 40.0,
        'carbohydrate': 70.0,
        'sugar': 10.0,
        'dietary_fiber': 10.0,
        'sodium': 980.0,
        'potassium': 750.0,
        'calcium': 220.0,
        'iron': 4.5,
        'vitamin_a': 150.0,
        'vitamin_c': 45.0,
        ...deviceInfo,
      },
    ];
  }

  /// 샘플 신체구성 데이터 생성
  List<Map<String, dynamic>> _generateSampleBodyCompositionData(DateTime baseTime) {
    final deviceInfo = _getSampleDeviceInfo();
    return [
      {
        'uid': 'sample_body_001',
        'start_time': baseTime.subtract(const Duration(hours: 1)).millisecondsSinceEpoch,
        'end_time': baseTime.millisecondsSinceEpoch,
        'weight': 72.5,
        'height': 175.0,
        'body_fat': 18.5,
        'skeletal_muscle': 35.2,
        'basal_metabolic_rate': 1680.0,
        'muscle_mass': 32.5,
        'body_fat_mass': 13.4,
        'fat_free_mass': 59.1,
        'fat_free': 81.5,
        'skeletal_muscle_mass': 25.5,
        'total_body_water': 43.2,
        ...deviceInfo,
      },
      {
        'uid': 'sample_body_002',
        'start_time': baseTime.subtract(const Duration(days: 7)).millisecondsSinceEpoch,
        'end_time': baseTime.subtract(const Duration(days: 7)).millisecondsSinceEpoch,
        'weight': 73.2,
        'height': 175.0,
        'body_fat': 19.0,
        'skeletal_muscle': 34.8,
        'basal_metabolic_rate': 1665.0,
        'muscle_mass': 32.0,
        'body_fat_mass': 13.9,
        'fat_free_mass': 59.3,
        'fat_free': 81.0,
        'skeletal_muscle_mass': 25.2,
        'total_body_water': 42.8,
        ...deviceInfo,
      },
    ];
  }

  /// 샘플 산소포화도 데이터 생성
  List<Map<String, dynamic>> _generateSampleBloodOxygenData(DateTime baseTime) {
    final deviceInfo = _getSampleDeviceInfo();
    return [
      {
        'uid': 'sample_blood_oxygen_001',
        'start_time': baseTime.subtract(const Duration(hours: 1)).millisecondsSinceEpoch,
        'end_time': baseTime.millisecondsSinceEpoch,
        'oxygen_saturation': 98.0,
        ...deviceInfo,
      },
      {
        'uid': 'sample_blood_oxygen_002',
        'start_time': baseTime.subtract(const Duration(hours: 4)).millisecondsSinceEpoch,
        'end_time': baseTime.subtract(const Duration(hours: 3)).millisecondsSinceEpoch,
        'oxygen_saturation': 97.0,
        ...deviceInfo,
      },
      {
        'uid': 'sample_blood_oxygen_003',
        'start_time': baseTime.subtract(const Duration(hours: 8)).millisecondsSinceEpoch,
        'end_time': baseTime.subtract(const Duration(hours: 7)).millisecondsSinceEpoch,
        'oxygen_saturation': 96.5,
        ...deviceInfo,
      },
    ];
  }

  /// 샘플 체온 데이터 생성
  List<Map<String, dynamic>> _generateSampleBodyTemperatureData(DateTime baseTime) {
    final deviceInfo = _getSampleDeviceInfo();
    return [
      {
        'uid': 'sample_temp_001',
        'start_time': baseTime.subtract(const Duration(hours: 2)).millisecondsSinceEpoch,
        'end_time': baseTime.millisecondsSinceEpoch,
        'temperature': 36.5,
        'temperature_fahrenheit': 97.7,
        ...deviceInfo,
      },
      {
        'uid': 'sample_temp_002',
        'start_time': baseTime.subtract(const Duration(days: 1)).millisecondsSinceEpoch,
        'end_time': baseTime.subtract(const Duration(days: 1)).millisecondsSinceEpoch,
        'temperature': 36.8,
        'temperature_fahrenheit': 98.24,
        ...deviceInfo,
      },
    ];
  }

  /// 샘플 혈당 데이터 생성
  List<Map<String, dynamic>> _generateSampleBloodGlucoseData(DateTime baseTime) {
    final deviceInfo = _getSampleDeviceInfo();
    return [
      {
        'uid': 'sample_glucose_001',
        'start_time': DateTime(baseTime.year, baseTime.month, baseTime.day, 7, 0).millisecondsSinceEpoch,
        'end_time': DateTime(baseTime.year, baseTime.month, baseTime.day, 7, 0).millisecondsSinceEpoch,
        'glucose_mmol': 5.2,
        'glucose_mgdl': 93.7,
        'measurement_type': 0,
        'measurement_type_name': 'CAPILLARY_BLOOD',
        'meal_status': 0,
        'meal_status_name': 'FASTING',
        ...deviceInfo,
      },
      {
        'uid': 'sample_glucose_002',
        'start_time': DateTime(baseTime.year, baseTime.month, baseTime.day, 10, 0).millisecondsSinceEpoch,
        'end_time': DateTime(baseTime.year, baseTime.month, baseTime.day, 10, 0).millisecondsSinceEpoch,
        'glucose_mmol': 6.8,
        'glucose_mgdl': 122.5,
        'measurement_type': 0,
        'measurement_type_name': 'CAPILLARY_BLOOD',
        'meal_status': 1,
        'meal_status_name': 'AFTER_MEAL',
        ...deviceInfo,
      },
      {
        'uid': 'sample_glucose_003',
        'start_time': DateTime(baseTime.year, baseTime.month, baseTime.day, 14, 30).millisecondsSinceEpoch,
        'end_time': DateTime(baseTime.year, baseTime.month, baseTime.day, 14, 30).millisecondsSinceEpoch,
        'glucose_mmol': 5.5,
        'glucose_mgdl': 99.1,
        'measurement_type': 0,
        'measurement_type_name': 'CAPILLARY_BLOOD',
        'meal_status': 2,
        'meal_status_name': 'BEFORE_MEAL',
        ...deviceInfo,
      },
    ];
  }

  /// 샘플 운동 데이터 조회
  void getSampleExerciseData() {
    _lastResult = {
      'success': true,
      'result': _generateSampleExerciseData(DateTime.now()),
      'message': '샘플 운동 데이터',
      'isSampleData': true,
    };
    notifyListeners();
  }

  /// 샘플 심박수 데이터 조회
  void getSampleHeartRateData() {
    _lastResult = {
      'success': true,
      'result': _generateSampleHeartRateData(DateTime.now()),
      'message': '샘플 심박수 데이터',
      'isSampleData': true,
    };
    notifyListeners();
  }

  /// 샘플 걸음수 데이터 조회
  void getSampleStepsData() {
    _lastResult = {
      'success': true,
      'result': _generateSampleStepsData(DateTime.now()),
      'message': '샘플 걸음수 데이터',
      'isSampleData': true,
    };
    notifyListeners();
  }

  /// 샘플 5분 간격 걸음수 데이터 조회
  void getSampleFiveMinuteStepsData() {
    _lastResult = {
      'success': true,
      'result': _generateSampleFiveMinuteStepsData(DateTime.now()),
      'message': '샘플 5분 간격 걸음수 데이터',
      'isSampleData': true,
    };
    notifyListeners();
  }

  /// 샘플 수면 데이터 조회
  void getSampleSleepData() {
    _lastResult = {
      'success': true,
      'result': _generateSampleSleepData(DateTime.now()),
      'message': '샘플 수면 데이터',
      'isSampleData': true,
    };
    notifyListeners();
  }

  /// 샘플 영양소 데이터 조회
  void getSampleNutritionData() {
    _lastResult = {
      'success': true,
      'result': _generateSampleNutritionData(DateTime.now()),
      'message': '샘플 영양소 데이터',
      'isSampleData': true,
    };
    notifyListeners();
  }

  /// 샘플 신체구성 데이터 조회
  void getSampleBodyCompositionData() {
    _lastResult = {
      'success': true,
      'result': _generateSampleBodyCompositionData(DateTime.now()),
      'message': '샘플 신체구성 데이터',
      'isSampleData': true,
    };
    notifyListeners();
  }

  /// 샘플 산소포화도 데이터 조회
  void getSampleBloodOxygenData() {
    _lastResult = {
      'success': true,
      'result': _generateSampleBloodOxygenData(DateTime.now()),
      'message': '샘플 산소포화도 데이터',
      'isSampleData': true,
    };
    notifyListeners();
  }

  /// 샘플 체온 데이터 조회
  void getSampleBodyTemperatureData() {
    _lastResult = {
      'success': true,
      'result': _generateSampleBodyTemperatureData(DateTime.now()),
      'message': '샘플 체온 데이터',
      'isSampleData': true,
    };
    notifyListeners();
  }

  /// 샘플 혈당 데이터 조회
  void getSampleBloodGlucoseData() {
    _lastResult = {
      'success': true,
      'result': _generateSampleBloodGlucoseData(DateTime.now()),
      'message': '샘플 혈당 데이터',
      'isSampleData': true,
    };
    notifyListeners();
  }
}
