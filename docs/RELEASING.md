# Publishing on GitHub

The repository is prepared for GPL-3.0 distribution. Repository visibility and GitHub releases are managed by the owner.

## Sign the APK

The debug APK is for testing. The release task produces an unsigned APK until a release keystore is configured.

1. With JDK 17 or newer on your PATH, create a signing key once:

   ```powershell
   keytool -genkeypair -v -keystore magic-themer-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias magic-themer
   ```

2. Copy `keystore.properties.example` to `keystore.properties` and fill in its four values. Use forward slashes for an absolute Windows keystore path. Both the properties file and keystore files are ignored by Git. Back up the key and passwords privately: every future update needs the same key.
3. Build and check:

   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
   $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
   .\gradlew.bat :app:assembleRelease :app:testDebugUnitTest :app:lintRelease
   & "$env:ANDROID_HOME\build-tools\36.0.0\apksigner.bat" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
   ```

Install the signed APK on the target phone and check pack selection, original icons, Settings, export and application through HONOR Themes. A release key differs from the debug key, so switching from a debug install requires uninstalling it first; this clears Magic Themer's saved assignments and settings. Export any theme you want to keep before uninstalling.

## Publish

Review `git diff` and the files being committed. Local themes, test output, APKs, signing keys and signing properties are ignored and should stay out of Git. The privacy statement is in the README and Settings: the app works offline, has no network permission, and has no accounts or analytics.

Commit the release source and push it. Create the version's tag on that commit and a GitHub release using its entry in `CHANGELOG.md`. Attach the verified signed APK under a clear name such as `Magic-Themer-0.7.1.apk`, along with its SHA-256 from `Get-FileHash -Algorithm SHA256`. Keep the corresponding source available under GPL-3.0.

The unsigned release APK cannot be installed. Do not attach it as the public installation download. See [validation](VALIDATION.md) for the checks completed locally and their limits.

## Existing release key

The permanent release key was created for v0.7.0 on 27 October 2026. Future updates must reuse it. Its public certificate SHA-256 is `8f4e78877186c72e128376d60e231aa108ba8009ad59a44b534fc29216b9e460`.

The application ID changed once, from `dev.magicthemer` to `com.tzii.magicthemer`, in v0.7.1. That release installs separately from earlier versions; future updates must keep both the new ID and the existing release key.

The local keystore and filled signing properties are ignored by Git. Keep a private backup in your password manager before moving or deleting the checkout. A public certificate fingerprint cannot restore the signing key. Do not generate a replacement key for an ordinary update.
