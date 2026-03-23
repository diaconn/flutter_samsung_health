# flutter_samsung_health_example

flutter_samsung_health 플러그인의 사용 예시를 보여주는 데모 앱입니다.

## 기능

이 예제 앱은 다음 기능들을 테스트할 수 있습니다:

### 앱 관리
- 삼성 헬스 설치 여부 확인
- 삼성 헬스 앱 실행

### 연결 관리
- SDK 연결/연결 해제

### 권한 관리
- 9가지 데이터 타입별 권한 요청
- 승인된 권한 조회

### 옵저버 (실시간 모니터링)
- 옵저버 시작/중지 (exercise, nutrition, blood_glucose)
- 옵저버 상태 확인
- 실시간 데이터 스트림 수신

### 데이터 조회
- 날짜 범위 선택
- 10가지 데이터 타입 조회:
  - 운동 데이터
  - 심박수 데이터
  - 걸음수 데이터 (일별)
  - 5분 간격 걸음수 데이터
  - 수면 데이터
  - 영양소 데이터
  - 신체구성 데이터
  - 산소포화도 데이터
  - 체온 데이터
  - 혈당 데이터
  - 전체 데이터 일괄 조회

## 실행 방법

```bash
cd example
flutter pub get
flutter run
```

## 요구사항

- Android API 29 이상 기기
- 삼성 헬스 앱 설치 및 초기화 완료

## 프로젝트 구조

```
lib/
├── main.dart                          # 앱 진입점
├── screens/
│   └── samsung_health_demo_screen.dart  # 메인 UI 화면
├── services/
│   └── samsung_health_service.dart      # 비즈니스 로직
└── widgets/
    ├── checkbox_section_widget.dart     # 권한/옵저버 선택
    ├── date_selection_widget.dart       # 날짜 범위 선택
    ├── result_display_widget.dart       # 결과 표시
    └── status_card_widget.dart          # 상태 카드
```
