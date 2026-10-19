# PixelMatrix Studio release policy

- Application ID: `com.pixelmatrix.studio.stable` fixed across the v3.2 line and all subsequent compatible updates.
- First release: versionName=3.2.0, versionCode=3200.
- Increment versionCode for every update; do not change applicationId or signing certificate.
- Signing on GitHub Actions: encrypted build cache key reused; subsequent builds **fail** if signing keystore unavailable. User should safely back up keystore outside the repository for long-term releases.
- Earlier experimental APKs and the separate 3.1.0 screenshot build may have a different package ID / signature and cannot be overwritten without matching their signing key.