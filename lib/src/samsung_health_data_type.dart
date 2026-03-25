/// Samsung Health 데이터 타입
///
/// 권한 요청, 옵저버, 데이터 조회 시 사용되는 데이터 타입 enum
enum SamsungHealthDataType {
  /// 운동 데이터
  exercise('exercise'),

  /// 심박수 데이터
  heartRate('heart_rate'),

  /// 걸음수 데이터
  steps('steps'),

  /// 5분 간격 걸음수 데이터
  fiveMinuteSteps('five_minute_steps'),

  /// 수면 데이터
  sleep('sleep'),

  /// 영양소 데이터
  nutrition('nutrition'),

  /// 신체구성 데이터
  bodyComposition('body_composition'),

  /// 산소포화도 데이터
  bloodOxygen('blood_oxygen'),

  /// 체온 데이터
  bodyTemperature('body_temperature'),

  /// 혈당 데이터
  bloodGlucose('blood_glucose');

  /// Kotlin으로 전달되는 실제 값
  final String value;

  const SamsungHealthDataType(this.value);

  /// 권한 요청 가능 여부
  bool get canRequestPermission => this != fiveMinuteSteps;

  /// 옵저버 지원 여부
  bool get supportsObserver => this == exercise || this == nutrition || this == bloodGlucose;

  /// String 값에서 enum으로 변환
  static SamsungHealthDataType? fromValue(String value) {
    for (final type in values) {
      if (type.value == value) return type;
    }
    return null;
  }

  /// 권한 요청 가능한 타입 목록
  static List<SamsungHealthDataType> get permissionTypes => values.where((e) => e.canRequestPermission).toList();

  /// 옵저버 지원 타입 목록
  static List<SamsungHealthDataType> get observerTypes => values.where((e) => e.supportsObserver).toList();
}

/// List<SamsungHealthDataType>을 List<String>으로 변환하는 확장
extension SamsungHealthDataTypeListExtension on List<SamsungHealthDataType> {
  /// enum 리스트를 string 리스트로 변환
  List<String> toStringValues() => map((e) => e.value).toList();
}
