<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/4d9bdf6b-0ab1-4faf-a5d4-1d0ce9f76343

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project (it will generate the Gradle wrapper on first sync).
4. Run the app on an emulator or physical device. Debug builds are signed automatically with the standard Android debug keystore — no manual setup required.

> Note: the project no longer requires a `GEMINI_API_KEY` or any custom debug keystore. The app runs entirely on-device (Android `MediaStore`); the only network use is loading demo thumbnails/videos when the gallery is empty or permissions are denied.
