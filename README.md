# Peeerly Android app

The live site, https://www.peeerly.tech, in one WebView. The app opens on the same first
page as the website: the landing page, or the dashboard when logged in.
The website code is not part of this project and nothing here changes it.

The earlier version with a Java home screen is saved in `D:\PeerlyBackup\android-v1-native-home`.

## How it works

| Part | File | What it does |
|---|---|---|
| Main screen | `MainActivity.java` | Splash, first page, Back button, offline screen |
| Allowed links | `Routes.java` | Only peeerly.tech opens inside the app. Everything else opens in the browser |
| Fullscreen and uploads | `SiteChromeClient.java`, `FullscreenFrame.java` | Quiz fullscreen, Back asks before leaving a quiz, file picker |
| Page bridge | `PageBridge.java` | Saves the site's blob downloads; follows the site's light/dark mode |
| Downloads | `Downloads.java` | Saves PDFs and CSVs to the phone's Downloads folder |
| Google login | `GoogleLogin.java` | Android account picker, then the site's existing login endpoint |

Making it feel fast:

- The Peeerly splash stays until the first page has drawn (4 seconds at most), so there is no white flash.
- When logged in, the app opens `/dashboard` directly instead of `/`, which would only redirect there.
- The app goes straight to `www`, skipping the site's redirect.
- Back on the first page puts the app in the background instead of closing it, so reopening is instant.
- The status bar follows the site's light/dark mode.

## Steps

### 1. Keep Gradle off drive C:

In Android Studio, open **File → Settings → Build, Execution, Deployment → Build Tools → Gradle**.
Set **Gradle user home** to `D:\GradleHome` and click **OK**.

### 2. Open the project

1. Click **File → Open**, choose `D:\PeerlyAndroid`, then click **OK**.
2. If asked, click **Trust Project**.
3. Wait for the bottom bar to finish **Gradle sync**. The first sync can take a few minutes.

Don't click **Upgrade** if Android Studio suggests new versions.

### 3. Run it on your phone

1. On the phone, open **Settings → About phone** and tap **Build number** 7 times.
2. Open **Settings → System → Developer options** and turn on **USB debugging**.
3. Connect the phone with a USB cable and tap **Allow** on the phone.
4. In Android Studio, choose your phone in the device list at the top, then click the green **▶ Run**.

### 4. Turn on Google login (one time, about 5 minutes)

Google login won't work until Google knows this app.

1. Open https://console.cloud.google.com and select the project with Peeerly's existing **Web client**.
2. Go to **APIs & Services → Credentials → Create credentials → OAuth client ID**.
3. Set **Application type** to **Android**, and name it `Peeerly Android debug`.
4. Set **Package name** to `tech.peeerly.app`.
5. Set **SHA-1** to this laptop's debug fingerprint:
   `A5:CF:4D:9D:FE:E3:94:70:CB:4A:EC:72:AA:16:1E:AD:9F:97:CD:0F`
6. Click **Create**. You don't need the ID it shows.

Don't edit or delete the existing Web client, because the website uses it.
The app uses the Web client's public ID, which is already in `strings.xml`.
The change can take a few minutes to start working.

### 5. Test on your phone

Check each of these with your own account:

- [ ] Email and password login, then logout
- [ ] Google login (shows the phone's account picker)
- [ ] Join a quiz from a second device: fullscreen, answer, Back asks "Leave the quiz?"
- [ ] Answer a poll
- [ ] Upload a profile photo
- [ ] Download a quiz report PDF and a placement document
- [ ] Turn on airplane mode, open a page, then press **Retry** after turning it off

### 6. Make the APK to share

1. Click **Build → Generate Signed App Bundle or APK → APK → Next**.
2. Click **Create new…** and save the key as `D:\PeerlyKeys\peeerly-release.jks`. Keep it outside this folder.
3. Choose strong passwords and save them in your password manager.
4. Choose **release** and click **Create**. The APK appears in `app\release\`.

**Back up `peeerly-release.jks` and its passwords outside the laptop.**
If you lose them, you can never release an update that installs over this app.

Google login in the release APK needs a second Android OAuth client with the release key's SHA-1.
Get it with **Gradle panel → Tasks → android → signingReport**, then repeat step 4 with the name `Peeerly Android release`.

For every new APK you share, raise `versionCode` by 1 in `app/build.gradle.kts`.

### 7. Save it in git (your step)

Use a separate private repository, never the website repository:

```
cd D:\PeerlyAndroid
git init
git add .
git commit -m "Peeerly Android app"
```

`.gitignore` already keeps keys, passwords, `local.properties` and build files out.
