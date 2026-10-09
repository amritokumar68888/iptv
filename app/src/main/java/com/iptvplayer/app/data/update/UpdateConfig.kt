package com.iptvplayer.app.data.update

/**
 * App auto-update-এর সব setting একজায়গায়।
 *
 * ────────────────────────────────────────────────────────────────────────────
 *  কীভাবে চালাবেন (step by step: UPDATE_SETUP.md দেখুন)
 * ────────────────────────────────────────────────────────────────────────────
 *  ১) নতুন APK build করুন (app/build.gradle-এ versionCode বাড়িয়ে)
 *  ২) APK টা Google Drive-এ upload করে share = "Anyone with the link"
 *  ৩) Drive link: https://drive.google.com/file/d/<APK_FILE_ID>/view
 *     → <APK_FILE_ID> কপি করুন
 *  ৪) নিচের JSON file টা Drive-এ upload করুন (update/update-manifest.json দেখুন),
 *     যেখানে apkUrl = https://drive.google.com/uc?export=download&id=<APK_FILE_ID>
 *  ৫) সেই JSON file-এর <MANIFEST_FILE_ID> নিচে বসান
 *
 *  ⚠️ গ্রাহকের ফোনে একবার "Install unknown apps" allow করতে হবে —
 *     app নিজেই সেই settings page খুলে দেবে।
 */
object UpdateConfig {

    /**
     * Google Drive-এ রাখা JSON manifest file-এর ID।
     * খালি/placeholder থাকলে update check বন্ধ থাকবে।
     */
    const val MANIFEST_FILE_ID = "YOUR_MANIFEST_FILE_ID"

    /**
     * Manifest-এর সরাসরি URL।
     *
     * GitHub Actions প্রতিটি push-এ এই file টা নিজে থেকে আপডেট করে
     * (build → Release → manifest commit)। তাই হাতে কিছু এডিট করতে হয় না।
     *
     * ⚠️ এর জন্য repo টা **Public** হতে হবে, নাহলে fetch fail করবে।
     */
    const val MANIFEST_URL_OVERRIDE =
        "https://raw.githubusercontent.com/amritokumar68888/iptv/main/update/update-manifest.json"

    /** কত ঘণ্টা পর পর নিজে থেকে update check করবে (0 = প্রতিবার app খুললে) */
    const val CHECK_INTERVAL_HOURS = 6

    /** App খোলার সাথে সাথে check করবে কি না */
    const val CHECK_ON_START = true

    /**
     * Play Store থেকে install করা app-এর ক্ষেত্রে —
     * true  = বাধ্যতামূলক full-screen update (Play নিজের progress bar দেখায়,
     *         update না করা পর্যন্ত app চলবে না)
     * false = background (flexible) update — গ্রাহক app চালাতে পারবে,
     *         download শেষ হলে নিজে থেকে install হবে
     *
     * 📌 কোনোটাতেই "Install unknown apps" prompt আসে না।
     */
    const val PLAY_FORCE_UPDATE = false

    val manifestUrl: String
        get() = if (MANIFEST_URL_OVERRIDE.isNotBlank()) MANIFEST_URL_OVERRIDE
        else "https://drive.google.com/uc?export=download&id=$MANIFEST_FILE_ID"

    /** ঠিকঠাক configure করা হয়েছে কি না */
    val isConfigured: Boolean
        get() = MANIFEST_URL_OVERRIDE.isNotBlank() || !MANIFEST_FILE_ID.startsWith("YOUR_")
}
