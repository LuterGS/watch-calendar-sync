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
./gradlew :mobile:assembleRelease :wear:assembleRelease

# 폰과 워치가 각각 다른 adb 대상이므로 -s 로 지정해서 설치
adb devices -l
adb -s <phone-serial> install -r mobile/build/outputs/apk/release/mobile-release.apk
adb -s <watch-serial> install -r wear/build/outputs/apk/release/wear-release.apk
```

**워치에는 release 빌드를 쓸 것.** Compose debug 빌드는 release보다 몇 배 느리고,
워치 CPU에서는 그 차이가 스크롤 버벅임으로 그대로 드러난다. release도 debug 키로
서명되므로 Data Layer 페어링은 그대로 동작한다. 설치 직후 baseline profile이
적용되도록 강제하려면:

```bash
adb -s <watch-serial> shell cmd package compile -f -m speed-profile dev.lutergs.watchcalsync
```

### Compose 성능 진단

두 앱 모두 Compose 컴파일러 리포트가 켜져 있다. 스크롤이 느려지면 먼저 여기를 본다:

```bash
./gradlew :wear:assembleDebug --rerun-tasks
grep unstable wear/build/compose-reports/wear-composables.txt
```

`unstable` 파라미터가 보이면 그 composable은 스킵되지 않고 매 프레임 재구성된다.
`:shared`는 Compose 컴파일러가 적용되지 않아 그 모델들이 기본적으로 unstable로
추론되므로, 루트의 `compose_stability.conf`에 stable로 선언해두었다.

두 APK는 applicationId가 같으므로 **같은 기기에 동시에 설치할 수 없다**. 폰에는
`:mobile`, 워치에는 `:wear`만 설치한다.

## 진행 상황

- [x] **1단계** — `:mobile` 캘린더 읽기 단독 동작 (계정/캘린더/이벤트 로그 출력)
- [x] **2단계** — Data Layer로 워치에 전송 (gzip, 변경 없을 때 전송 생략)
- [x] **3단계** — `:wear` 아젠다 리스트 UI (날짜 그룹 + 색상 + 로터리 스크롤)
- [x] **4단계** — `ContentObserver`(포그라운드) + content-URI 트리거(백그라운드) + 30분 주기 백스톱
- [x] ~~5단계~~ — 캘린더별 on/off 필터 (중복 공휴일 때문에 1.5단계로 앞당김)
- [ ] **5단계 (스트레치)** — Tile / Complication

### 1단계 확인 방법

앱을 실행하면 권한을 요청하고, 허용하면 화면에 캘린더 목록과 일정이 뜬다.
동시에 전체 덤프가 logcat으로 나간다:

```bash
adb -s <phone-serial> logcat -s WatchCalSync
```

`distinct accounts = [...]` 줄에 개인/회사 계정이 **둘 다** 보이는지 확인하는 것이
이 단계의 핵심이다.

### 자동 동기화 구조

변경 감지는 세 겹이다. 하나로는 안 되기 때문이다.

| 경로 | 언제 | 왜 필요한가 |
| --- | --- | --- |
| `CalendarChangeObserver` | 앱이 떠 있을 때 | 즉시 반응. 단 프로세스와 함께 죽는다 |
| `CalendarChangeWorker` (content-URI 트리거) | 프로세스가 죽어 있어도 | 시스템에 등록되므로 콜드 스타트로 실행됨 |
| `CalendarSyncWorker` (30분 주기) | 항상 | 위 둘이 놓친 변경 보정 + 트리거 재무장 |

**content-URI 트리거는 일회성 작업에만 붙는다.** 그래서 발동될 때마다 다시 등록해야
하는데, 워커가 자기 자신을 재등록하면 안 된다 — 같은 unique work 이름에 `REPLACE`를
쓰면 실행 중인 자기 자신이 취소된다. 재무장은 주기 워커와 앱 UI가 담당한다.

동작 확인:

```bash
adb -s <phone> logcat -s WatchCalSync   # "calendar changed: pushed N events"
adb -s <phone> shell dumpsys jobscheduler | grep -A20 CalendarChangeWorker
```

주기 워커는 `cmd jobscheduler run` 으로 앞당길 수 없다. WorkManager가 주기 도래 전
실행을 거부한다 (`Delaying execution ... executed before schedule`).
