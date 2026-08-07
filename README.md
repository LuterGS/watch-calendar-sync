# watch-calendar-sync

폰의 캘린더 프로바이더(개인 + 회사 계정 전체)를 읽어서, 워치에 별도 로그인 없이
Wearable Data Layer로 동기화해 보여주는 개인용 앱.

Wear OS 내장 Google Calendar 앱은 **시계에 직접 로그인된 계정**만 표시한다.
회사 계정이 워치에 추가되지 않으면 회사 일정이 안 보이는데, 폰의 `CalendarContract`는
기기에 등록된 **모든 계정**의 캘린더를 노출하므로 이를 읽어서 워치로 밀어주면 해결된다.
서버·클라우드·외부 인증 없이 블루투스 페어링만으로 동작한다.

## 모듈

| 모듈 | 설명 |
| --- | --- |
| `:shared` | 두 앱이 공유하는 데이터 모델(`CalendarEvent`, `CalendarSnapshot`)과 Data Layer 경로/키 상수 |
| `:mobile` | 폰 앱. `READ_CALENDAR` 권한, `CalendarContract` 조회, 워치로 push |
| `:wear` | 워치 앱. Data Layer 수신, 로컬 캐시, Compose for Wear OS 아젠다 UI |

`:mobile`과 `:wear`는 **applicationId가 동일해야** 한다 (`dev.lutergs.watchcalsync`).
Data Layer는 applicationId + 서명 키로 폰/워치 앱을 짝지으므로 둘 중 하나라도 다르면
서로를 찾지 못한다. 그래서 release 빌드도 debug 키로 서명한다 (개인 사이드로드 전용).

## 툴체인

시스템에 Android Studio가 없으므로 SDK는 홈 디렉토리에 있다.

- JDK 21 (`/usr/lib/jvm/java-21-openjdk`, pacman `jdk21-openjdk`)
- Android SDK: `~/Android/Sdk` (`local.properties`의 `sdk.dir`, git에 커밋하지 않음)
- Gradle 9.7.0 (wrapper), AGP 9.3.1, compileSdk/targetSdk 37, minSdk 30

AGP 9부터 Kotlin 지원이 내장이라 `org.jetbrains.kotlin.android` 플러그인을 적용하지
**않는다**. compose/serialization 컴파일러 플러그인만 명시적으로 선언한다.

SDK를 새로 받아야 한다면:

```bash
export ANDROID_HOME=~/Android/Sdk
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"
```

## 빌드 / 설치

```bash
export ANDROID_HOME=~/Android/Sdk
./gradlew :mobile:assembleDebug :wear:assembleDebug

# 폰과 워치가 각각 다른 adb 대상이므로 -s 로 지정해서 설치
adb devices -l
adb -s <phone-serial> install -r mobile/build/outputs/apk/debug/mobile-debug.apk
adb -s <watch-serial> install -r wear/build/outputs/apk/debug/wear-debug.apk
```

두 APK는 applicationId가 같으므로 **같은 기기에 동시에 설치할 수 없다**. 폰에는
`:mobile`, 워치에는 `:wear`만 설치한다.

## 진행 상황

- [x] **1단계** — `:mobile` 캘린더 읽기 단독 동작 (계정/캘린더/이벤트 로그 출력)
- [ ] **2단계** — Data Layer로 워치에 전송
- [ ] **3단계** — `:wear` 리스트 UI 렌더링
- [ ] **4단계** — `ContentObserver` + `WorkManager` 자동 동기화
- [ ] **5단계 (스트레치)** — 캘린더별 on/off 필터, Tile

### 1단계 확인 방법

앱을 실행하면 권한을 요청하고, 허용하면 화면에 캘린더 목록과 일정이 뜬다.
동시에 전체 덤프가 logcat으로 나간다:

```bash
adb -s <phone-serial> logcat -s WatchCalSync
```

`distinct accounts = [...]` 줄에 개인/회사 계정이 **둘 다** 보이는지 확인하는 것이
이 단계의 핵심이다.
