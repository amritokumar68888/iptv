package com.iptvplayer.app.data.update

/**
 * App auto-update-এর সব setting একজায়গায়।
 *
 * ────────────────────────────────────────────────────────────────────────────
 *  কীভাবে নতুন version ছাড়বেন (বিস্তারিত: UPDATE_SETUP.md)
 * ────────────────────────────────────────────────────────────────────────────
 *   ১) `app/build.gradle`-এ `versionCode` ১ করে বাড়ান (versionName-ও)
 *   ২) git push
 *
 *  ব্যস। GitHub Actions নিজে:
 *     • signed APK build করে (Secret-এ রাখা permanent keystore দিয়ে)
 *     • GitHub Release বানায়, SkyOTT.apk attach করে
 *     • `update/update-manifest.json` আপডেট করে commit করে
 *  → গ্রাহকের অ্যাপ পরেরবার খুললেই নতুন version পেয়ে যায়।
 *
 *  ⚠️ প্রতিবার **একই** keystore দিয়ে sign হতে হবে, নাহলে update install হবে না।
 *     (আগের CI প্রতিবার নতুন key বানাত — সেটাই ছিল সবচেয়ে বড় bug।)
 *
 *  ⚠️ Sideload করা app-এ একবার "Install unknown apps" allow লাগে —
 *     Play Store থেকে install করা app-এ কিছুই লাগে না (Play path)।
 */
object UpdateConfig {

    /**
     * legacy Drive fallback — এখন ব্যবহার হয় না।
     * GitHub-hosted manifest চালু থাকলে এটা উপেক্ষিত হয়।
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

    /**
     * Optional 2nd source (mirror)। GitHub fail হলে এটা try করা হবে।
     *
     * Google Drive-এ রাখতে চাইলে file টা "Anyone with the link" করে share
     * করে এখানে বসান:
     *   "https://drive.google.com/uc?export=download&id=<MANIFEST_FILE_ID>"
     *
     * খালি রাখলে শুধু MANIFEST_URL_OVERRIDE ব্যবহার হবে।
     */
    const val MANIFEST_URL_FALLBACK = ""

    /** কত ঘণ্টা পর পর নিজে থেকে update check করবে (0 = প্রতিবার app খুললে) */
    const val CHECK_INTERVAL_HOURS = 1

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

    /**
     * যে URL গুলো ক্রমে try করা হবে — প্রথম যেটা সফল হবে সেটাই ব্যবহার হবে।
     * একটা host block/down হলেও update চেক বন্ধ হবে না।
     */
    val manifestUrls: List<String>
        get() = listOf(manifestUrl, MANIFEST_URL_FALLBACK)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** ঠিকঠাক configure করা হয়েছে কি না */
    val isConfigured: Boolean
        get() = MANIFEST_URL_OVERRIDE.isNotBlank() || !MANIFEST_FILE_ID.startsWith("YOUR_")
}
