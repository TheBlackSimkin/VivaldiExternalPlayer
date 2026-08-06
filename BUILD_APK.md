# Build the APK without editing code

## GitHub Actions

1. Create a private GitHub repository.
2. Upload the contents of this project, preserving the `.github` directory.
3. Open **Actions → Build installable debug APK → Run workflow**.
4. After the job finishes, download the artifact named `VivaldiExternalPlayer-debug-apk`.
5. Extract it and install `app-debug.apk` on the Android phone.

The debug APK is automatically signed with Android's generated debug key. Android may ask you to allow installation from the app used to open the APK.

## Android Studio

Open the project in a current Android Studio installation with Android SDK 36, JDK 17, and Gradle 8.13. Then select **Build → Build APK(s)**. The APK is written to:

`app/build/outputs/apk/debug/app-debug.apk`
