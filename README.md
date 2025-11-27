# flutter\_samsung\_health

Android용 삼성 헬스 데이터 SDK를 연동하는 Flutter 플러그인입니다.

## 📦 설치 방법

당신의 `pubspec.yaml`에 다음과 같이 추가하세요:

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

// 데이터 조회 (Unix timestamp in milliseconds)
final now = DateTime.now().millisecondsSinceEpoch;
final weekAgo = now - (7 * 24 * 60 * 60 * 1000);

final exerciseData = await plugin.getExerciseData(start: weekAgo, end: now);
final sleepData = await plugin.getSleepData(start: weekAgo, end: now);
final stepData = await plugin.getStepData(start: weekAgo, end: now);

// 연결 해제
await plugin.disconnect();
```

### 지원하는 데이터 타입

| 메서드 | 설명 |
|--------|------|
| `getExerciseData()` | 운동 데이터 |
| `getHeartRateData()` | 심박수 데이터 |
| `getSleepData()` | 수면 데이터 (수면 단계 포함) |
| `getStepData()` | 걸음 데이터 (일별 집계) |
| `getNutritionData()` | 영양 데이터 |
| `getWeightData()` | 체중/신체구성 데이터 |
| `getOxygenSaturationData()` | 산소포화도 데이터 |
| `getBodyTemperatureData()` | 체온 데이터 |
| `getBloodGlucoseData()` | 혈당 데이터 |
| `getTotalData()` | 모든 데이터 타입 일괄 조회 |

### 권한 타입

`requestPermissions()`에 전달할 수 있는 권한 타입:
- `exercise`
- `heart_rate`
- `sleep`
- `steps`
- `nutrition`
- `body_composition`
- `blood_oxygen`
- `body_temperature`
- `blood_glucose`

---

## 🛠 최소 요구사항

* Android SDK 26 이상 (Android 8.0+)
* 삼성 헬스 앱 설치 및 초기화 완료 상태
* Java 11 / Kotlin JVM Target 11

---

## ⚠️ 이전 버전과의 차이점

Samsung Health SDK for Android (1.5.x)에서 Samsung Health Data SDK (1.0.0)로 마이그레이션되었습니다:

**제거된 API:**
- `enableObservers()` - 실시간 옵저버 미지원
- `disableObservers()` - 실시간 옵저버 미지원
- `getObserversStatus()` - 실시간 옵저버 미지원
- `getSleepStageData()` - `getSleepData()`에 통합 (세션 데이터에 단계 포함)

**변경된 API:**
- `getStepData()` - 5분 간격 대신 일별 집계 데이터 반환
- `getWeightData()` - Weight에서 BodyComposition으로 데이터 타입 변경
- `getOxygenSaturationData()` - OxygenSaturation에서 BloodOxygen으로 데이터 타입 변경

---

## 🔐 라이선스

이 플러그인은 내부 사용을 목적으로 하며, 삼성의 사유 라이브러리에 의존합니다.
