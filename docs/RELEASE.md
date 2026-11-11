# Release 빌드

APK 위치: `apk/pedallog_release.apk`. 버전 0.10.0-mascot-preview (versionCode 10).

Compose·Material3 네이티브 기록 상태·기기 기록 이력·설정·카카오/네이버 간편가입 데모를 포함한다. 첫 실행 안내·직접 기록 체험·기기 파일 상세 조회·라이트/다크/기기 설정 전환을 제공한다. 실제 외부 인증은 하지 않는다. 웹은 앱의 실행 구성요소가 아니다.
이전 0.2 시연 영상은 과거 자료다. 최신 제출 영상은 `submission/2026-11-09/페달로그_앱시연_0.10.mp4`이며, 최신 화면은 `artifacts/mascot-pages/`와 `artifacts/contest-2026/`를 따른다.
debug APK의 이름을 바꾼 파일이 아니라 release variant를 전용 로컬 키로 서명한 빌드다.
코드의 진단·모의 기능이 release에 포함돼 있다. 서명 성공은 Pleos 실차 승인·안전 검증을 의미하지 않는다.

## 로컬 서명

`_info/private/release-signing.properties`를 Gradle이 읽는다. 이 폴더와 서명키는 Git 제외 대상이다.
예시 필드 이름만 아래에 기록한다. 실제 비밀번호와 키는 공유하지 않는다.

```
storeFile=_info/private/pedallog-release.jks
storePassword=LOCAL_SECRET
keyAlias=pedallog
keyPassword=LOCAL_SECRET
```

새 PC에서는 소유자가 보관한 키·설정을 안전하게 복원해야 같은 서명으로 업데이트할 수 있다.
키가 없는 checkout에서는 `assembleDebug`로 개발할 수 있고, release 패키징은 서명 설정 부재로 중단된다.

## 명령

JDK 17/21, Android SDK 35와 `ANDROID_HOME`이 필요하다. 이번 검증은 Android Studio의 JBR 21을 사용했다.
Windows에서 DEX 파일 잠금이 발생하면 실행 중인 빌드를 먼저 마친 뒤 Gradle daemon을 종료하고
`--no-daemon --max-workers=2`로 재시도한다. 서명키나 원본 데이터를 삭제하지 않는다.

```
.\gradlew.bat :app:assembleRelease :app:lintRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Gradle 8.7 wrapper와 공식 배포 파일의 SHA-256을 포함한다.
사용자가 지정한 한글 작업 경로를 유지하기 위해 `android.overridePathCheck=true`를 설정했고 실제 빌드로 검증한다.

최종 제출은 공식 파일명 규칙에 맞춘 복사본을 준비한다. 개인정보가 들어간 파일명은 `_info/private`에만 둔다.
기존 debug와 release는 서명이 다르므로 같은 패키지 업데이트 설치가 안 될 수 있다.
검증 시에는 전용 에뮬레이터의 앱만 제거한 뒤 release를 설치하며, 사용자 기기의 앱·데이터를 임의로 제거하지 않는다.
