# PixelMatrix Studio v3.2

- Default canvas is view-only; button `편집하기` unlocks pixel tools.
- `수정 저장` commits edits to local project autosave; `취소` discards them.
- Explicit `파일로 내보내기` still saves a portable project JSON.
- General GIF input: 16 / 32 / 64 pixel output, frame limit 30/60/120, speed and color quantization options.
- Android GIF fallback decoder is used when the pure JS GIF decoder cannot process a file. Input up to 50MB.
- Built-in 200 animated examples in ten categories; file import does not require original input to be 32×32.
- BLE connection and device commands retained; not verified on a physical device.
- Stable application ID `com.pixelmatrix.studio.stable`, version 3.2.0 (3200).
- Do not distribute as an in-place update to a differently signed / differently packaged earlier APK.