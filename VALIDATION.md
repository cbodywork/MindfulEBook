# 검증 결과 · 알아차림 전자책 1.0.0

앱 소스 커밋: `e7fe77cfea501080f1c5bcfb3392068bec5ca03d`

검증 실행: https://github.com/cbodywork/MindfulEBook/actions/runs/34535681342

## 통과한 검사

- Android `assembleDebug`: 설치용 개발 서명 APK 생성 성공.
- Android `lintDebug`: 오류 없이 완료(경고는 보고서 참조).
- JVM 단위 테스트 6개: UTF-8/CP949 한글, 긴 용어 우선 발음 변환·비연쇄 치환, 서로게이트 문단 분할, EPUB 상대경로, 경로 이탈·외부 주소 거부.
- Android 15(API 35), Pixel Tablet 프로필 에뮬레이터의 기기 테스트 6개: TXT 가져오기, EPUB spine 순서, DOCX 분할 run 결합·제목, PDF 텍스트 추출, UTF-16 DTD 거부, 실제 앱 실행·가로 회전.
- Chromium UI 자동 검증: 1280×800 가로/800×1280 세로 배치, 책갈피, 낭독 문단 전환과 오래된 완료 콜백 무시, 검색 이동, 위치·테마·사전 재로드, 긴 책 문단 렌더링, 문서 문자열의 HTML 비실행.
- 위 검증 실행의 `tablet-screenshots` 아티팩트: 실제 UI 테스트에서 캡처한 화면. 브라우저 검증에서는 Android 음성·파일 연결을 모의 객체로 대체했습니다.

## 설치 파일

- 파일명: `MindfulEBook-1.0.0.apk`
- 크기: 9,135,128 bytes
- SHA-256: `6ecd5b03d93bca27f6caf2eaa44ab0e4e5d45e8640c59595636eb1eff97b4e5d`
- Android 8.0 이상, 개발 서명 직접 설치용 APK. Google Play 배포판이 아닙니다.

## 아직 검증되지 않은 부분

- 사용자 소유의 실제 태블릿과 제조사별 TTS 음성 품질·설치 절차.
- 모든 출판사의 EPUB/DOCX/PDF 변형 및 대용량 파일에서의 성능.
- 실물 기기에서의 장시간 연속 낭독·배터리 사용량.

OCR, DRM 해제, 백그라운드 낭독, EPUB/DOCX 원본 지면 재현은 구현 범위에 포함되지 않습니다. 상세 지원 범위는 README와 앱 내부 사용 안내를 참조하십시오.
