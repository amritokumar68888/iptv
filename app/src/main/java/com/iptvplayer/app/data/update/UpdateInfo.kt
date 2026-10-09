package com.iptvplayer.app.data.update

import org.json.JSONObject

/**
 * Remote manifest (JSON) থেকে পড়া update-এর তথ্য।
 *
 * JSON format:
 * {
 *   "versionCode": 2,
 *   "versionName": "1.1",
 *   "apkUrl": "https://drive.google.com/uc?export=download&id=APK_FILE_ID",
 *   "changelog": "• নতুন চ্যানেল\n• বাগ ফিক্স",
 *   "forceUpdate": false,
 *   "minSupportedVersionCode": 1
 * }
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val forceUpdate: Boolean,
    val minSupportedVersionCode: Int
) {
    companion object {
        fun fromJson(json: JSONObject): UpdateInfo {
            var apk = json.optString("apkUrl", "").trim()

            // apkUrl না দিয়ে শুধু apkFileId দিলেও কাজ করবে
            if (apk.isEmpty()) {
                val id = json.optString("apkFileId", "").trim()
                if (id.isNotEmpty()) {
                    apk = "https://drive.google.com/uc?export=download&id=$id"
                }
            }

            return UpdateInfo(
                versionCode = json.optInt("versionCode", 0),
                versionName = json.optString("versionName", "").trim(),
                apkUrl = apk,
                changelog = json.optString("changelog", "").trim(),
                forceUpdate = json.optBoolean("forceUpdate", false),
                minSupportedVersionCode = json.optInt("minSupportedVersionCode", 0)
            )
        }
    }
}
