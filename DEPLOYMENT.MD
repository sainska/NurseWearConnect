# NurseWear Connect Deployment Guide

## 1. Local Build (Generating the APK)
To build the app for testing on a physical device or distribution:
1. Open the **Gradle** pane in Android Studio.
2. Navigate to `:app` -> `Tasks` -> `build` -> `assembleDebug` (or `assembleRelease` for final).
3. The generated APK will be located at:
   `app/build/outputs/apk/debug/app-debug.apk`

## 2. Supabase Storage (Distribution)
The referral system relies on a public download link for the app.
1. Go to your **Supabase Dashboard**.
2. Navigate to **Storage** -> **Buckets**.
3. Ensure the `deployments` bucket exists (run `SUPABASE_FIXES.sql` to create it).
4. Upload your APK file and rename it to `nursewear-connect-latest.apk`.
5. Your public download link will be:
   `https://trpsejzasbfqlshrbbae.supabase.co/storage/v1/object/public/deployments/nursewear-connect-latest.apk`

## 3. Docker (Automated Build & Deployment)
The project includes a `Dockerfile` and a GitHub Action for automated builds.

### Local Automated Build:
To build and prepare the deployment trigger:
```bash
docker build -t nursewear-connect .
docker run --rm nursewear-connect
```
This will:
1. Set up the Android SDK environment.
2. Build the debug APK.
3. **Generate a Trigger Script**: Instead of uploading automatically, it creates `supabase_upload.sh` and `supabase_upload.bat` in the project root.

### Triggering the Upload:
To actually upload the generated APK to Supabase:
1. Ensure the build finished successfully.
2. Run the generated script:
   - **Linux/macOS**: `./supabase_upload.sh`
   - **Windows**: `supabase_upload.bat`
This ensures you have control over when the network upload happens.

### CI/CD (GitHub Actions):
Any push to `main` or `master` will trigger a GitHub Action that builds the app using Docker. To enable the Supabase upload in CI, ensure you have your credentials configured in the build environment.
