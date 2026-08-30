<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/03bd716c-cd6c-4f99-8b50-c02610cda002

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.

## Supabase deployment

Apply the migration in `supabase/migrations` before running the app. Deploy the Edge Function with:

```text
supabase functions deploy b2-upload
supabase functions deploy b2-delete
supabase secrets set B2_KEY_ID=... B2_APPLICATION_KEY=... B2_ENDPOINT=... B2_REGION=... B2_BUCKET=...
```

The B2 credentials belong only in Supabase Edge Function secrets. Do not add them to the Android `.env` or APK. The migration enables Postgres Changes for chat messages; realtime still requires the project to have Realtime enabled in the Supabase dashboard.

### B2 bucket privacy (private bucket + secure gateway)

The Backblaze B2 bucket (`B2_BUCKET`) stays **fully Private**. Uploaded media is never exposed through raw bucket URLs:

1. `b2-upload` returns a stable **gateway URL** of the form
   `https://{project}.supabase.co/functions/v1/b2-download?path=users/{uid}/{type}/{file}`
   which is what gets stored in the database (`avatar_type`, `cover_type`, posts, stories, reels, chat media).
2. `b2-download` authenticates the viewer's Supabase JWT, validates the requested path, then 302-redirects to a short-lived (30 min) pre-signed B2 URL. The bucket keys/credentials never reach the client.
3. The Android app registers a global Coil image loader (`Vyn9Application`) that attaches `apikey` + `Authorization: Bearer <token>` to every `b2-download` request — so only signed-in app sessions can resolve media. Everyone else gets a 401 and the UI shows a graceful placeholder instead of a blank image.

Deploy both functions after any change:

```text
supabase functions deploy b2-upload
supabase functions deploy b2-download
supabase functions deploy b2-delete
supabase secrets set B2_KEY_ID=... B2_APPLICATION_KEY=... B2_ENDPOINT=... B2_REGION=... B2_BUCKET=...
```

Apply `supabase/migrations/20260827010000_private_media_gateway_rewrite.sql` once to rewrite already-stored legacy bucket URLs to the gateway form so older uploads keep rendering.

## Deep links (App Links)

The launcher declares App Links for `https://vyn9.app` with `autoVerify="true"` for
`/@username`, `/post/`, `/video/` and `/reel/` paths. Verification only succeeds once
`https://vyn9.app/.well-known/assetlinks.json` is live on the domain and contains the
**SHA256 certificate fingerprint of the release signing key** (debug keystores do not
verify). See `deploy/well-known/assetlinks.example.json` — put the finished file at
`{web-root}/.well-known/assetlinks.json` on the server that serves `vyn9.app`.

## Build notes

- Supabase URL/key and Gemini key come from `.env` (see `.env.example`); the Supabase
  values used to be hardcoded in `Backend.kt` — never re-add them to source.
- Release builds now run R8 (`isMinifyEnabled`) with keep rules in `app/proguard-rules.pro`
  for Moshi generated adapters and WebRTC native entry points.
- Sensitive SharedPreferences (auth tokens, monetization, rewards) are excluded from
  Android Auto Backup / device transfer.
