PixelMatrix Studio v3.5.0

- 기존 앱의 안정 버전(com.pixelmatrix.studio.stable) 유지
- GIF 200종 내장 유지
- 일반 GIF 자동 32×32 변환 후 내 GIF 보관함 저장
- 안드로이드 앱 내부 개인 GIF 보관(IndexedDB와 동시 저장)
- 보기 전용 편집기: 편집하기 → 수정 → 저장/취소
- 저장 전 자동 BLE 픽셀 전송 금지
- 원본 GIF 속도 및 다중 프레임 최대한 유지
- 다른 설치 패키지(.clean)도 별도 제공

검증 범위: 소스 정적 검사, JavaScript 문법, Android Gradle 빌드, 서명 및 APK 내 GIF 포함 검사. 실제 기기 통신 실기 검증은 별도 필요.