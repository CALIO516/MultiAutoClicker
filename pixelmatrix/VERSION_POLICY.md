# Version and installation policy

- Stable package: `com.pixelmatrix.studio.stable` (fixed for v2.7 onwards)
- Version v2.7.0, versionCode 27; all later versions must increment `versionCode` and `versionName` while keeping `applicationId` fixed.
- Android debug APKs are signed by GitHub Actions with persistent signing key from `actions/cache`. For versions later than v2.7, the build refuses to proceed if the cached signing key cannot be restored.
- v2.6 and earlier builds may have different package/signatures: this v2.7 build intentionally installs separately, and does not overwrite legacy builds.
- GitHub Actions executes `gradle :app:clean :app:assembleDebug` and verifies APK using `apksigner` and `aapt`.
- GIF data is bundled in final APK. The native `galleryGif` Java bridge supports browsing and loading the packaged assets.
- Code/asset static tests do not replace physical iDotMatrix testing.