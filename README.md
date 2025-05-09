# samsung\_health\_plugin

Android용 삼성 헬스 SDK를 연동하는 Flutter 플러그인입니다.

## 📦 설치 방법

당신의 `pubspec.yaml`에 다음과 같이 추가하세요:

```yaml
dependencies:
  samsung_health_plugin:
    git:
      url: https://github.com/your-org/samsung_health_plugin.git
      ref: main
```

> ⚠️ 이 플러그인은 삼성의 `samsung-health-data-1.5.1.aar` 파일을 필요로 하며, 이 파일은 앱 프로젝트에 **직접 포함**해야 합니다.

---

## 🔧 Android 앱 설정

### 1. `.aar` 파일 추가

`samsung-health-data-1.5.1.aar` 파일을 다운로드하여 다음 위치에 넣어주세요:

```
my_flutter_app/
└── android/
    └── app/
        └── libs/
            └── samsung-health-data-1.5.1.aar
```

### 2. `android/app/build.gradle` 수정

다음 내용을 추가하세요:

```gradle
repositories {
    google()
    mavenCentral()
    flatDir {
        dirs 'libs'
    }
}

dependencies {
    implementation(name: 'samsung-health-data-1.5.1', ext: 'aar')
}
```

### 3. Proguard 설정 (릴리즈 빌드 시 필수)

`android/app/proguard-rules.pro` 파일에 다음을 추가하세요:

```proguard
-keep class com.samsung.android.sdk.healthdata.** { *; }
-dontwarn com.samsung.android.sdk.healthdata.**
```

---

## ✅ 사용 예시

Dart 코드에서 플러그인을 사용하려면 다음과 같이 호출하세요:

```dart
import 'package:samsung_health_plugin/samsung_health_plugin.dart';

final plugin = SamsungHealthPlugin();
await plugin.connect();
```

전체 예시는 `example/` 폴더를 참고하세요.

---

## 🛠 최소 요구사항

* Android SDK 21 이상
* 삼성 헬스 앱 설치 및 초기화 완료 상태

---

## 🔐 라이선스

이 플러그인은 내부 사용을 목적으로 하며, 삼성의 사유 라이브러리에 의존할 수 있습니다.
