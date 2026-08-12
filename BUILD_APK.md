# Build the APK

## GitHub Actions

For the existing repository:

1. Upload/commit the updated project files to `main`.
2. Open **Actions** -> **Build installable debug APK**.
3. A push to `main` starts the workflow automatically, or choose **Run workflow**
   for a manual build.
4. After the job finishes successfully, download the artifact named
   `VivaldiExternalPlayer-debug-apk`.
5. Extract it and install `app-debug.apk` on the Android phone.

The debug APK uses Android's generated debug signing key. For this development
project, ADB installation is acceptable if normal sideloading is intercepted by
device security/Play Protect.

## Android Studio

Open the project with Android SDK 36 and JDK 17, then use **Build -> Build
APK(s)**. The APK is written to:

`app/build/outputs/apk/debug/app-debug.apk`
