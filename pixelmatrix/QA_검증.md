# PixelMatrix Studio v3.2 정적 검증 범위

- Android applicationId: com.pixelmatrix.studio.stable
- versionCode: 3200, versionName: 3.2.0
- 내장 GIF 200개/10개 분류 유지 (GitHub Actions 복원 후 압축 포맷·카탈로그 검사)
- 앱 기본 보기 모드. 편집하기 버튼을 누르기 전에는 픽셀 변경 불가.
- 편집 중 로컬 자동 저장 금지; 수정 저장 후에만 적용. 취소 시 원래 프로젝트 복원.
- 이미지를 드래그하거나 두 손가락으로 확대/이동하는 작업은 보기 모드에서 원본 변경 없음.
- GIF 원본 크기에 관계없이 16/32/64 변환. 주 디코더 실패 시 Android Movie 보조 디코더 재시도.
- 갤러리 → 편집기로 가져오기 이벤트 및 BLE 이미지/GIF 전송 진입점 유지.

### 검증 한계
- 브라우저 미리보기/Android 실기기 실행은 하지 않음.
- 다양한 실제 GIF 샘플은 앱 실행 환경에서 확인 필요. Android Movie 디코더가 모든 손상된 파일을 지원하지는 않음.
- 블루투스 iDotMatrix 장치 실기 연결·전송은 검증되지 않음.
- v3.1 스크린샷의 정확한 설치 ID 및 서명은 알 수 없어, 이 APK가 v3.1을 덮어쓰는지 보장할 수 없음.