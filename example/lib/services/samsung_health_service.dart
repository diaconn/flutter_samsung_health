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
    SamsungHealthDataType.oxygenSaturation: '산소포화도',
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
}
