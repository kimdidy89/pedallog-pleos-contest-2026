# 페달로그

급발진 의심 상황에서 속도와 브레이크 관측을 기록하고 날짜와 시각으로 확인하는 차량용 네이티브 앱.

## 실행

서명된 APK는 apk/김용욱_페달로그_release.apk에 있습니다. 버전은 0.10.0-mascot-preview입니다.
Android 9 API 28 이상 설치 대상이며 실제 검증 환경은 Android 15 API 35 에뮬레이터입니다.
앱을 열어 첫 안내를 마치고 홈의 체험 기록 시작으로 약 10초 모의 기록을 남깁니다.
방금 기록 보기와 지난 기록에서 시각별 속도·브레이크, 날짜 목록을 확인합니다.
설정에서 라이트·다크·기기 설정 모드를 고를 수 있습니다.
모의 데모에 별도 서버나 로그인 키가 필요하지 않습니다.

## 빌드

JDK 17 이상, Android SDK 35, 플랫폼 도구와 인터넷이 필요합니다.
SDK 위치는 ANDROID_HOME이나 로컬 local.properties에 설정합니다.
Windows: gradlew.bat :app:assembleDebug
macOS/Linux: sh gradlew :app:assembleDebug
출력: app/build/outputs/apk/debug/app-debug.apk
release 재서명 키는 동봉하지 않습니다. 로컬 서명 설정은 docs/RELEASE.md에 설명합니다.

## 현재 범위

수동 로컬 기록, JSONL 저장·내보내기, 날짜별 조회, 시점별 재생과 누락 신호 구분을 구현했습니다.
SDK 연결 코드는 있으나 실차 권한과 실제 수신은 미검증입니다. 가속페달 접근은 미확인입니다.
시동 연계 자동 기록·장시간 백그라운드·별도 서버 동기화·실제 OAuth는 미구현입니다.
카카오·네이버는 UI 데모입니다. 계정이나 서버 접근권한을 생성하지 않습니다.
운전자 의도·오조작·급발진 원인·사고 예방 효과를 판정하거나 보장하지 않습니다.

## 데이터와 라이선스

samples의 demo JSONL과 앱 내 예시는 모의 데이터입니다. 실제 주행 로그는 포함하지 않습니다.
한글 글꼴과 사용 에셋·코드의 출처와 고지는 assets/licenses와 assets/third-party-manifest.json에 있습니다.
마스코트는 내장된 3D 렌더 스타일의 2D PNG이며 제작 정보는 assets/mascot에 보존합니다.
전체 코드에 임의의 새 오픈소스 라이선스를 부여하지 않았으며 제3자 고지는 그대로 보존합니다.

## 개발 환경 상세

Pleos Connect 전용 에뮬레이터 버전: 미사용, 연결 검증 전입니다.
모의 실행은 일반 Android 15 API 35 x86_64 에뮬레이터이며 휴대폰 1080×1920 420dpi, 차량형 화면 1920×1080 240dpi를 확인했습니다.
2026-11-11 로컬 SDK 확인값은 Android Emulator 36.3.10입니다. 전용 Pleos 버전으로 표현하지 않습니다.
