# flutter\_samsung\_health

Android용 삼성 헬스 데이터 SDK 1.0.0을 연동하는 Flutter 플러그인입니다.

## 📦 설치 방법

`pubspec.yaml`에 다음과 같이 추가하세요:

```yaml
dependencies:
  flutter_samsung_health:
    git:
      url: https://github.com/diaconn/flutter_samsung_health.git
      ref: master
```

> ✅ 이 플러그인은 Samsung Health Data SDK AAR 파일을 자동으로 포함하므로 별도 설정이 불필요합니다.

---

## 🔧 Android 앱 설정

### 1. Android 버전 요구사항

앱의 `android/app/build.gradle`에서 다음 설정을 확인하세요:

```gradle
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11
    }

    defaultConfig {
        minSdk = 29  // Samsung Health Data SDK requires API 29+
    }
}
```

### 2. Proguard 설정 (릴리즈 빌드 시 필수)

`android/app/proguard-rules.pro` 파일에 다음을 추가하세요:

```proguard
-keep class com.samsung.android.sdk.health.data.** { *; }
-dontwarn com.samsung.android.sdk.health.data.**
```

---

## ✅ 사용 예시

### 기본 사용법

```dart
import 'package:flutter_samsung_health/flutter_samsung_health.dart';

final plugin = FlutterSamsungHealth();

// 삼성 헬스 설치 확인
final installed = await plugin.isSamsungHealthInstalled();

// 연결
final connectResult = await plugin.connect();

// 권한 요청 (특정 타입만)
await plugin.requestPermissions(['exercise', 'heart_rate', 'sleep']);

// 또는 모든 권한 요청
await plugin.requestPermissions(null);

// 승인된 권한 조회
final granted = await plugin.getGrantedPermissions();

// 데이터 조회 (Unix timestamp in milliseconds)
final now = DateTime.now().millisecondsSinceEpoch;
final weekAgo = now - (7 * 24 * 60 * 60 * 1000);

final exerciseData = await plugin.getExerciseData(start: weekAgo, end: now);
final sleepData = await plugin.getSleepData(start: weekAgo, end: now);
final stepsData = await plugin.getStepsData(start: weekAgo, end: now);

// 전체 데이터 조회
final totalData = await plugin.getTotalData(start: weekAgo, end: now);

// 특정 항목 제외하고 전체 조회
final filteredData = await plugin.getTotalData(
  start: weekAgo,
  end: now,
  excludeTypes: ['exercise', 'nutrition', 'blood_glucose'],
);

// 연결 해제
await plugin.disconnect();
```

### 실시간 데이터 스트림

```dart
// 옵저버 시작 (exercise, nutrition, blood_glucose 지원)
await plugin.startObserver(['exercise', 'nutrition']);

// 실시간 데이터 수신
plugin.startListening((data) {
  print('새 데이터 수신: $data');
}, onError: (error) {
  print('에러: $error');
});

// 옵저버 상태 확인
final status = await plugin.getObserverStatus();

// 스트림 수신 중지
plugin.stopListening();

// 옵저버 중단
await plugin.stopObserver(['exercise', 'nutrition']);
```

---

## API 레퍼런스

### 앱 관리

| 메서드 | 설명 |
|--------|------|
| `isSamsungHealthInstalled()` | 삼성 헬스 앱 설치 여부 확인 |
| `openSamsungHealth()` | 삼성 헬스 앱 실행 |
| `openSamsungHealthPermissions()` | 권한 설정 화면 열기 |

### 연결 관리

| 메서드 | 설명 |
|--------|------|
| `connect()` | SDK 연결 |
| `disconnect()` | SDK 연결 해제 |

### 권한 관리

| 메서드 | 설명 |
|--------|------|
| `requestPermissions(List<String>? types)` | 데이터 권한 요청 (null이면 전체 권한) |
| `getGrantedPermissions()` | 승인된 권한 목록 조회 |

### 옵저버 (실시간 모니터링)

| 메서드 | 설명 |
|--------|------|
| `startObserver(List<String>? dataTypes)` | 옵저버 시작 |
| `stopObserver(List<String>? dataTypes)` | 옵저버 중단 |
| `getObserverStatus(List<String>? dataTypes)` | 옵저버 상태 조회 |
| `healthDataStream` | 실시간 헬스 데이터 스트림 |
| `startListening(callback)` | 스트림 리스닝 시작 |
| `stopListening()` | 스트림 리스닝 중지 |

### 데이터 조회

모든 데이터 조회 메서드는 `start`와 `end` 파라미터(Unix timestamp, milliseconds)가 필요합니다.

| 메서드 | 설명 |
|--------|------|
| `getTotalData({excludeTypes})` | 모든 데이터 타입 일괄 조회 (excludeTypes로 제외할 타입 지정 가능) |
| `getExerciseData()` | 운동 데이터 |
| `getHeartRateData()` | 심박수 데이터 |
| `getStepsData()` | 걸음수 데이터 (일별 집계) |
| `getFiveMinuteStepsData()` | 5분 간격 걸음수 데이터 |
| `getSleepData()` | 수면 데이터 (수면 단계 포함) |
| `getNutritionData()` | 영양소 데이터 |
| `getBodyCompositionData()` | 신체구성 데이터 (체중, 체지방 등) |
| `getOxygenSaturationData()` | 산소포화도 데이터 |
| `getBodyTemperatureData()` | 체온 데이터 |
| `getBloodGlucoseData()` | 혈당 데이터 |

---

## 권한 타입

`requestPermissions()`에 전달할 수 있는 권한 타입:

| 권한 키 | 설명 |
|--------|------|
| `exercise` | 운동 데이터 |
| `heart_rate` | 심박수 데이터 |
| `sleep` | 수면 데이터 |
| `steps` | 걸음수 데이터 |
| `nutrition` | 영양소 데이터 |
| `body_composition` | 신체구성 데이터 |
| `oxygen_saturation` | 산소포화도 데이터 |
| `body_temperature` | 체온 데이터 |
| `blood_glucose` | 혈당 데이터 |

---

## getTotalData 제외 타입

`getTotalData()`의 `excludeTypes` 파라미터에 전달할 수 있는 데이터 타입 키:

| 데이터 타입 키 | 설명 |
|---------------|------|
| `exercise` | 운동 데이터 |
| `heart_rate` | 심박수 데이터 |
| `sleep` | 수면 데이터 |
| `steps` | 걸음수 데이터 |
| `five_minute_steps` | 5분 간격 걸음수 데이터 |
| `nutrition` | 영양소 데이터 |
| `body_composition` | 신체구성 데이터 |
| `blood_oxygen` | 산소포화도 데이터 |
| `body_temperature` | 체온 데이터 |
| `blood_glucose` | 혈당 데이터 |

---

## 옵저버 지원 데이터 타입

실시간 옵저버는 다음 3가지 타입만 지원합니다:

- `exercise` - 운동 데이터
- `nutrition` - 영양소 데이터
- `blood_glucose` - 혈당 데이터

---

## 🛠 최소 요구사항

* Android API 29 이상 (Android 10+)
* 삼성 헬스 앱 설치 및 초기화 완료 상태
* Java 11 / Kotlin JVM Target 11
* Flutter 3.3.0 이상
* Dart SDK 3.6.0 이상

---

## ⚠️ 이전 버전과의 차이점

Samsung Health SDK for Android (1.5.x)에서 Samsung Health Data SDK (1.0.0)로 마이그레이션되었습니다:

**변경된 API:**
- `getStepData()` → `getStepsData()` (메서드명 변경, 일별 집계 데이터 반환)
- `getWeightData()` → `getBodyCompositionData()` (데이터 타입 변경)
- `getSleepStageData()` → `getSleepData()`에 통합 (세션 데이터에 단계 포함)

**새로 추가된 API:**
- `getFiveMinuteStepsData()` - 5분 간격 걸음수 데이터
- `getGrantedPermissions()` - 승인된 권한 조회
- `openSamsungHealthPermissions()` - 권한 설정 화면 열기
- `startObserver()`, `stopObserver()`, `getObserverStatus()` - 옵저버 관리
- `healthDataStream`, `startListening()`, `stopListening()` - 실시간 스트림

---

## 🔐 라이선스

이 플러그인은 내부 사용을 목적으로 하며, 삼성의 사유 라이브러리에 의존합니다.
