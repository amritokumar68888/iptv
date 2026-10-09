# In-App Auto Update — Setup Guide

গ্রাহকের ফোনে অ্যাপ নিজে থেকে নতুন version check করবে, download করবে, আর
install করে নতুন version-এ চলে যাবে।

---

## ⚠️ আগে এটা পড়ুন — "Install unknown apps" prompt

**Sideload করা (Play Store-এর বাইরের) APK-তে "Install unknown apps" prompt
কোনোভাবেই সরানো যায় না।** এটা Android-এর core security feature, কোনো কোড/API
দিয়ে common অ্যাপে bypass করা অসম্ভব।

অ্যাপ এখন **দুটো পথ** নিজেই বুঝে নেয়:

| গ্রাহকের ফোনে অ্যাপ যেভাবে | কোন updater চলবে | Permission prompt |
|---|---|---|
| **Google Play Store** | Play In-App Updates | ❌ **কখনোই আসে না** |
| Sideload (APK কপি করে) | Drive updater | ✅ একবার allow করতে হবে |

সিদ্ধান্ত:
- **সম্পূর্ণ prompt-free normal app experience চাইলে → Play Store-এ publish করুন** (নিচে SECTION B)
- Sideload-ই রাখতে হলে → SECTION A (একবার permission, তারপর normal)

---

## কীভাবে কাজ করে (flow)

```
App খোলে
   │
   ├─ UpdateChecker  ──►  Google Drive থেকে update.json নামায়
   │                         (versionCode, apkUrl, changelog, forceUpdate)
   │
   ├─ নতুন version?
   │      না  ──►  চুপচাপ চালু হয়
   │      হ্যাঁ ──►  "Update Available" dialog দেখায় (changelog সহ)
   │
   ├─ "Update Now"  ──►  APK download (progress bar সহ)
   │
   └─ Download শেষ ──►  Installer নিজে থেকে খোলে
                            │
                            └─ গ্রাহক "Install" চাপে → নতুন version চালু
```

---

## SECTION A — Sideload (APK কপি করে দিয়ে update)

> গ্রাহকের ফোনে একবার **"Install unknown apps" allow** করতে হবে।
> অ্যাপ নিজেই সেই settings page খুলে দেবে।

### ১. `versionCode` বাড়ান
`app/build.gradle` ফাইলে:

```gradle
defaultConfig {
    versionCode 2        // ← প্রতিবার ১ করে বাড়াতে হবে (2, 3, 4 ...)
    versionName "1.1"    // ← গ্রাহক যা দেখবে
}
```

> ⚠️ `versionCode` **না** বাড়ালে অ্যাপ update ধরবে না। এটাই সবচেয়ে জরুরি step।

### ২. Signed release APK build করুন

```bash
./gradlew assembleRelease
```

APK পাবেন: `app/build/outputs/apk/release/app-release.apk`

### ৩. APK টা Google Drive-এ upload করুন

1. Drive-এ APK file upload করুন
2. Right-click → **Share** → **Anyone with the link** → Done
3. Link-টা এমন হবে:
   ```
   https://drive.google.com/file/d/1AbCdEfGhIjKlMnOp/view?usp=sharing
                                    └───── এই অংশটাই FILE_ID ─────┘
   ```

### ৪. `update/update-manifest.json` এডিট করুন

```json
{
  "versionCode": 2,
  "versionName": "1.1",
  "apkUrl": "https://drive.google.com/uc?export=download&id=1AbCdEfGhIjKlMnOp",
  "changelog": "• নতুন চ্যানেল যোগ করা হয়েছে\n• বাগ ফিক্স",
  "forceUpdate": false,
  "minSupportedVersionCode": 1
}
```

| Field | মানে |
|---|---|
| `versionCode` | নতুন APK-এর versionCode — `build.gradle`-এর সাথে **হুবহু** মিলতে হবে |
| `versionName` | dialog-এ দেখানো হবে |
| `apkUrl` | APK-এর direct download link |
| `changelog` | "What's New" তে দেখাবে (`\n` দিয়ে নতুন লাইন) |
| `forceUpdate` | `true` = গ্রাহক "Later" চাপতে পারবে না, update বাধ্যতামূলক |
| `minSupportedVersionCode` | এর চেয়ে পুরনো version হলে জোর করে update |

> 💡 বড় update বাধ্যতামূলক করতে: `"forceUpdate": true` করে দিন।

### ৫. manifest JSON টা Drive-এ upload করুন

1. এডিট করা `update-manifest.json` Drive-এ upload করুন
2. Share = **Anyone with the link**
3. তার **FILE_ID** কপি করুন

### ৬. `UpdateConfig.kt` এ FILE_ID বসান

`app/src/main/java/com/iptvplayer/app/data/update/UpdateConfig.kt`:

```kotlin
const val MANIFEST_FILE_ID = "1XyZ...এখানে manifest file id..."
```

### ৭. নতুন APK build করে গ্রাহকদের দিয়ে দিন

এবার থেকে যেসব গ্রাহকের ফোনে **পুরনো** অ্যাপ আছে, তারা অ্যাপ খুললেই
update-এর কথা দেখবে। শুধু দরকার গ্রাহক একবার
**"Install unknown apps" allow** করবে — অ্যাপ নিজেই সেই settings page খুলে দেবে।

---

## Drive-এর বদলে নিজের server ব্যবহার করতে চাইলে

`UpdateConfig.kt` এ:

```kotlin
const val MANIFEST_URL_OVERRIDE = "https://yourserver.com/skyott/update.json"
```

আর manifest-এর `apkUrl`-এও নিজের server-এর direct APK link দিন।
কোনো কোড change লাগবে না।

---

## ফাইল কোথায় কোথায়

| File | কাজ |
|---|---|
| `data/update/UpdateConfig.kt` | সব setting — Drive file ID, check interval, Play force flag |
| `data/update/InstallSource.kt` | app Play থেকে install হয়েছে কি না detect করে |
| `data/update/PlayUpdateManager.kt` | **Play In-App Updates** — prompt-free update (Play path) |
| `data/update/UpdateInfo.kt` | manifest JSON-এর model |
| `data/update/UpdateChecker.kt` | নতুন version আছে কি না check করে (sideload path) |
| `data/update/ApkDownloader.kt` | APK download (progress সহ) |
| `data/update/InstallHelper.kt` | APK install + permission |
| `data/update/DriveClient.kt` | Drive/OkHttp shared client |
| `data/update/AppVersion.kt` | ইনস্টল করা version বের করে |
| `ui/update/UpdateDialog.kt` | sideload update dialog + progress |
| `res/layout/dialog_update.xml` | dialog-এর design |
| `res/xml/file_paths.xml` | FileProvider paths |

---

## SECTION B — Google Play Store (কোনো prompt নেই, true normal app)

এই path-এ গ্রাহকের কাছে **কোনো** "Install unknown apps", permission, বা
installer screen আসে না। Play নিজেই download + install + restart করে দেয় —
ঠিক যেভাবে Facebook/WhatsApp update হয়।

### যা লাগবে
1. **Google Play Developer account** (একবার $25)
2. Play Console-এ app তৈরি করে **Internal testing** track-এ upload করা

### ধাপে ধাপে

#### ১. Signed App Bundle (AAB) build করুন
```bash
./gradlew bundleRelease
```
পাবেন: `app/build/outputs/bundle/release/app-release.aab`

> Play-তে AAB দিতে হয় (APK নয়)। upload করার সময় Play স্বয়ংক্রিয়ভাবে
> signed APK বানিয়ে গ্রাহকের ফোনে দেয়।

#### ২. Play Console → app তৈরি → Internal testing → AAB upload
- **Internal testing** track-এ `app-release.aab` upload করুন
- Release note লিখুন (এখানেই "What's New" দেখাবে)
- `versionCode` অবশ্যই আগেরটার চেয়ে বড় হতে হবে

#### ৩. গ্রাহকদের tester হিসেবে add করুন
Internal testing-এ tester email যোগ করে link share করুন।
তারা Play Store থেকে install করবে → **এবার থেকে এটাই normal app**।

#### ৪. প্রতিবার নতুন version দিতে
```gradle
versionCode 3        // বাড়ান
versionName "1.2"
```
```bash
./gradlew bundleRelease
```
তারপর Play Console-এ নতুন AAB upload → **Publish**।
গ্রাহকের অ্যাপ খোলার সময় নিজে থেকেই update হয়ে যাবে।

### Force (বাধ্যতামূলক) update করতে
`UpdateConfig.kt` এ:
```kotlin
const val PLAY_FORCE_UPDATE = true   // full-screen Play update, "Later" নেই
```
- `false` (default) = background/flexible update — গ্রাহক app চালাতে পারে,
  download শেষে নিজে থেকে install হয়
- `true` = IMMEDIATE — Play-এর full-screen progress + auto install + restart

> **Note:** Play-তে "update available" তখনই আসে যখন নতুন AAB **Published**
> হয় এবং ডিভাইসের versionCode ছোট। Play নিজেই সব সামলায়।

### Production-এ নিতে
Internal testing-এ সব ঠিক থাকলে **Closed / Open testing** → **Production**।
Production-এ ১০০% rollout দিলে সব গ্রাহক normal app-এর মতো update পাবে।

---

## ট্রাবলশুটিং

| সমস্যা | কারণ / সমাধান |
|---|---|
| update dialog আসে না | `versionCode` বাড়ানো হয়নি, বা Drive JSON-এর `versionCode` ছোট |
| "Downloaded file টা APK নয়" | Drive link-এ file টা share করা নেই (Anyone with the link), অথবা ভুল file id |
| Drive "quota exceeded" / virus warning | APK বড়/blocked হলে নিজের server বা GitHub Releases-এ APK রাখুন, শুধু `apkUrl` বদলান |
| Installer খুলছে কিন্তু "Blocked" | গ্রাহককে Settings → Install unknown apps → allow করতে বলুন |
| প্রতিবার check করতে চাই | `CHECK_INTERVAL_HOURS = 0` |
| Play-তে update আসছে না | AAB **Publish** করা হয়নি, বা `versionCode` আগেরটার চেয়ে বড় নয়, বা device সেই track-এ নেই |
| Play version-এ update dialog দেখাচ্ছে | ভুল — Play path হলে `InstallSource.isFromPlay()` true হয়, তখন dialog আসে না। একবার Play থেকে install করলে ঠিক হয়ে যাবে |
| "Install unknown apps" পুরোপুরি বন্ধ চাই | শুধু **SECTION B (Play Store)** দিয়েই সম্ভব — এটা Android-এর security, কোড দিয়ে bypass হয় না |

