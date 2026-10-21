# 06 — GitHub updates

Boards: **16 Settings — updates** (`SettingsUpdates.dc.html`) and the "Update available" dialog on **18** (`Dialogs.dc.html`).

The app is sideloaded, so it updates itself from **GitHub Releases**. There's no server of our own.

## How it works

```
App start (auto-check on, at most once per 12 h)  or  "Check now"
  └─ GET https://api.github.com/repos/OWNER/REPO/releases/latest      (stable)
     GET https://api.github.com/repos/OWNER/REPO/releases?per_page=10  (beta: newest incl. prerelease)
  └─ parse tag_name "v0.2.0" → compare with BuildConfig.VERSION_NAME
  └─ newer?  → Settings badge + dialog (auto) / card (16)
  └─ "Download & install" → download the .apk asset to cacheDir/updates/
  └─ hand to the package installer → user confirms → app restarts on the new version
```

## The API

`GET /repos/{owner}/{repo}/releases/latest`, headers `Accept: application/vnd.github+json`, `User-Agent: AveteroTV`.

```json
{
  "tag_name": "v0.2.0",
  "name": "0.2.0",
  "prerelease": false,
  "published_at": "2026-10-19T10:00:00Z",
  "body": "- Catch-up for channels with archive\n- Faster guide loading…",
  "assets": [
    {"name": "relay-iptv-0.2.0.apk", "size": 14890000,
     "content_type": "application/vnd.android.package-archive",
     "browser_download_url": "https://github.com/OWNER/REPO/releases/download/v0.2.0/relay-iptv-0.2.0.apk"}
  ]
}
```

- `/latest` never returns prereleases or drafts. For the **Include beta releases** setting, list `/releases` and take the first non-draft entry.
- Pick the asset whose name ends `.apk`. With no APK asset, treat it as no update.
- Show `body` as the release notes (Markdown, rendered as plain bullets by splitting lines that start with `- `).
- Card meta: `published_at` → "Released 19 Oct 2026", and `size` → "14.2 MB".

## Version comparison

- The tag format is **`vMAJOR.MINOR.PATCH`**, optionally `-beta.N`.
- Compare it numerically with `BuildConfig.VERSION_NAME` (enable `buildConfig = true`). Don't compare strings: `0.10.0 > 0.9.0`.
- `versionCode` must also increase every release or Android rejects the install. Derive it in Gradle: `versionCode = major*10000 + minor*100 + patch`.
- "Skip this version" stores the tag in prefs and suppresses the auto dialog for that tag only.

## Download & install

Manifest:

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.updates"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/update_paths" />
</provider>
```

`res/xml/update_paths.xml`: `<paths><cache-path name="updates" path="updates/" /></paths>` (FileProvider needs `androidx.core:core`, which Compose already depends on).

Flow:

1. If `!packageManager.canRequestPackageInstalls()`, explain and open `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` with `package:` + our id. Return and retry on resume.
2. Download with `HttpURLConnection`, which follows the GitHub → objects.githubusercontent redirect (both https). Stream to `cacheDir/updates/update.apk` and report progress to the card from `contentLength`.
3. Optional integrity check: compare the file size with `asset.size`.
4. Install:
   - **Simple (chosen):** `Intent(ACTION_VIEW).setDataAndType(FileProvider.getUriForFile(…), "application/vnd.android.package-archive").addFlags(FLAG_GRANT_READ_URI_PERMISSION or FLAG_ACTIVITY_NEW_TASK)`.
   - Alternative: `PackageInstaller.Session`, which is more robust on some TV OEM skins where ACTION_VIEW has no handler. Switch to it if QA finds such a box.
5. Delete old files in `cacheDir/updates` on the next start.

## Must-knows

- **Same signing key every release.** Android refuses an update signed with a different key, and the only fix is uninstalling, which loses settings. Keep the keystore safe (password manager + CI secret).
- **Debug and release builds can't update each other**, because they use different keys. Sideload release builds only.
- **Public repo** (or a public "releases-only" repo) is required for unauthenticated API and asset downloads. A private repo would need a token inside the APK, which anyone can extract. Don't do that.
- **Rate limit:** unauthenticated API is 60 requests/hour per IP, and a household behind one IP shares it. Throttle auto-checks to once per 12 h. On `403` with `X-RateLimit-Remaining: 0`, back off silently.
- The asset download URL isn't rate-limited like the API.
- Configure owner/repo in `app/build.gradle.kts` as `buildConfigField("String", "UPDATE_REPO", "\"OWNER/REPO\"")`. It's shown read-only in 16 as "Update source".

## Release checklist

1. Bump `versionName` in `app/build.gradle.kts` (versionCode is derived from it).
2. Update `CHANGELOG.md`, because the release body is copied from it.
3. `git tag v0.2.0 && git push origin v0.2.0`.
4. CI builds, signs and attaches `relay-iptv-0.2.0.apk` to the release. For a beta, tag `v0.3.0-beta.1` and CI marks it as a prerelease.
5. Install the previous release on a test box, open Settings → Updates → Check now → install, and confirm settings survived.

## GitHub Actions workflow

`.github/workflows/release.yml`

```yaml
name: release
on:
  push:
    tags: ["v*"]
permissions:
  contents: write
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "17" }
      - uses: gradle/actions/setup-gradle@v4
      - name: Decode keystore
        run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > app/release.jks
      - name: Build signed APK
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew :app:assembleRelease :app:testReleaseUnitTest
      - name: Rename
        run: cp app/build/outputs/apk/release/app-release.apk "relay-iptv-${GITHUB_REF_NAME#v}.apk"
      - uses: softprops/action-gh-release@v2
        with:
          files: relay-iptv-*.apk
          prerelease: ${{ contains(github.ref_name, '-') }}
          generate_release_notes: true
```

Signing config in `app/build.gradle.kts` (reads env so no secrets are in git):

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("release.jks")
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}
buildTypes { release { signingConfig = signingConfigs.getByName("release") } }
```

Add `app/release.jks` to `.gitignore`. Create the keystore once with `keytool -genkeypair -v -keystore release.jks -alias avetero -keyalg RSA -keysize 4096 -validity 10000`.
