# Direct APK installer log capture

Use this only for the known standalone APK tap-install failure. Do **not** uninstall the working signed app just to test this path.

The app already passes CI package/sign/alignment checks and installs by `adb install -r`, so the useful next evidence is the Android PackageInstaller / Play Protect reason emitted during the failed tap flow.

## Capture steps

1. Connect the device with USB debugging enabled.
2. Leave the currently installed app in place.
3. Put the signed release APK on the device exactly the way you normally test direct tap install.
4. In a terminal, clear logs:

```bash
adb logcat -c
```

5. Start filtered logging:

```bash
adb logcat -v time \
  PackageInstaller:* PackageManager:* PackageManagerService:* \
  PackageInstallerSession:* InstallStaging:* \
  GooglePlayProtect:* PlayProtect:* GmsPackageInstaller:* \
  ActivityTaskManager:* AndroidRuntime:* *:S
```

6. On the device, tap the standalone APK and follow the same install flow until it fails or blocks.
7. Stop logcat with Ctrl+C and save the output.

## Broader fallback filter

Some devices log Play Protect decisions under Google Play services tags that vary by build. If the focused filter misses the reason, rerun with:

```bash
adb logcat -c
adb logcat -v time | grep -Ei \
  'packageinstaller|packagemanager|install|verifier|verify|play protect|playprotect|gms|blocked|unsafe|unknown app|session|staging'
```

Then repeat the direct tap install attempt.

## What to look for

Useful lines usually mention one of these:

- install session failure or abandoned session
- package verifier / verifier response
- Play Protect verdict or block reason
- `INSTALL_FAILED_*` reason
- user action ignored or blocked after “Install anyway”
- source package / installer package involved in the flow

Keep any captured URL, token, or private path out of shared logs. Package names, version code, verifier status, and error codes are safe to report.
