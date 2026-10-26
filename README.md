# Save Video 📥

A dead-simple Android app for your Samsung phone. **Share any video from TikTok,
Instagram, or YouTube to it, and it downloads straight into your Gallery.** No
copy-pasting links, no ads, no sign-in.

It uses the open-source **yt-dlp** engine under the hood (the same engine many
popular downloaders use), and that engine keeps itself up to date so it keeps
working as the apps change.

---

## Part 1 — Build the app (one-time, ~10 minutes)

You don't need a computer program or any coding. A free GitHub account builds the
installable file for you in the cloud.

1. **Make a free GitHub account** at <https://github.com/signup> (skip if you have one).
2. Click **New repository** (the **+** at the top-right → *New repository*).
   - Name it anything, e.g. `save-video`.
   - Choose **Public** (free builds) and click **Create repository**.
3. On the new repo's page, click **uploading an existing file** (the link in the
   "Quick setup" box).
4. Drag **all the files and folders from this project** into the browser window,
   then click **Commit changes**.
   - Make sure the hidden `.github` folder comes along — it contains the build
     recipe. (On Windows, enable *View → Hidden items* in File Explorer first.)
5. Go to the **Actions** tab at the top of your repo.
   - If it asks, click **"I understand my workflows, enable them."**
   - A build called **Build APK** starts automatically. Wait for the green
     checkmark ✓ (about 5 minutes).
6. Click into that finished build. At the bottom, under **Artifacts**, download
   **`SaveVideo-app`**. It's a `.zip` — open it to get **`app-debug.apk`**.

> Tip: you can rebuild any time from the **Actions** tab → **Build APK** →
> **Run workflow**.

---

## Part 2 — Install it on your Samsung phone

1. Get `app-debug.apk` onto your phone (email it to yourself, or download the
   artifact directly in your phone's browser from the GitHub Actions page).
2. Tap the `.apk` file. Android will say installs from this source are blocked —
   tap **Settings → Allow from this source**, then go back and tap **Install**.
   (This is normal for apps not from the Play Store.)
3. Open **Save Video** once. Tap **Allow** when it asks to send notifications
   (that's how it shows you download progress).

That's it. You're done. ✅

---

## Part 3 — How to use it

1. In **TikTok / Instagram / YouTube**, open a video.
2. Tap **Share** (the arrow/paper-plane icon).
3. In the share menu, tap **Save Video**.
4. A notification shows "Downloading… / Saved to your gallery ✓".

The video lands in your **Gallery → Albums → SaveVideo**.

You can also open the app and **paste a link** manually if you prefer.

---

## Troubleshooting

- **"Save Video" isn't in the share menu** → Scroll the share sheet; the first
  time, tap *More* and look for it. Reboot once if it still doesn't appear.
- **Instagram video won't download** → Private accounts and login-walled content
  can't be fetched. Public Reels and posts work.
- **A download fails** → The app automatically updates its engine and retries. If
  it still fails, that specific video may be region-locked or private.
- **Nothing saves to the gallery** → Open the app, make sure you allowed
  notifications, and try once more.

---

## Notes

- The app builds as a **debug** APK, which is why Android warns it's from an
  "unknown source." That's expected for a personal app that isn't on the Play
  Store — it's your own build.
- Please only download videos you have the right to save, and respect each
  platform's terms of service. This app is for personal use.

---

## For developers

Standard single-module Android project (Kotlin, AGP 8.5, Gradle 8.9, minSdk 26).

```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Key pieces:
- `ShareActivity` — receives the `ACTION_SEND` text/plain intent and extracts the URL.
- `DownloadService` — foreground service; runs yt-dlp (`youtubedl-android`),
  merges with ffmpeg, then writes the file to `MediaStore` (Movies/SaveVideo).
- `.github/workflows/build.yml` — cloud build that publishes the APK as an artifact.
