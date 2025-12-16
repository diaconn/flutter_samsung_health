import 'package:flutter/material.dart';
import 'package:flutter_samsung_health/flutter_samsung_health.dart';

/// Samsung Health 관련 비즈니스 로직을 담당하는 서비스 클래스
class SamsungHealthService extends ChangeNotifier {
  final FlutterSamsungHealth _plugin = FlutterSamsungHealth();
  
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
  DateTime _startDate = DateTime.now().subtract(const Duration(days: 7));
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
    } catch (e) {
      _connectionStatus = '오류: $e';
      _isConnected = false;
      _lastResult = {'error': e.toString()};
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
    final startLocal = DateTime(startDate.year, startDate.month, startDate.day, 0, 0, 0);
    final endLocal = DateTime(endDate.year, endDate.month, endDate.day, 23, 59, 59);
    final timeZoneOffsetMillis = startDate.timeZoneOffset.inMilliseconds;
    final start = startLocal.millisecondsSinceEpoch + timeZoneOffsetMillis;
    final end = endLocal.millisecondsSinceEpoch + timeZoneOffsetMillis;
    return (start, end);
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