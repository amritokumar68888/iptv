package com.iptvplayer.app.data.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * নতুন APK download করে app-এর cache-এ রাখে, progress সহ।
 */
object ApkDownloader {

    class DownloadException(message: String) : Exception(message)

    /**
     * APK download করবে।
     * @param onProgress (downloadedBytes, totalBytes) — total -1 হলে unknown।
     *                   এই callback background thread-এ call হয়।
     * @return download করা APK File
     */
    suspend fun download(
        context: Context,
        url: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {

        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }          // পুরনো APK পরিষ্কার
        val outFile = File(dir, "skyott-update.apk")

        var response = DriveClient.ok.newCall(Request.Builder().url(url).build()).execute()

        // Drive confirm page হলে follow করো
        if (DriveClient.isHtml(response)) {
            val html = response.body?.string().orEmpty()
            response.close()
            response = DriveClient.ok.newCall(
                Request.Builder().url(DriveClient.followConfirm(url, html)).build()
            ).execute()
        }

        val body = response.body
        if (!response.isSuccessful || body == null) {
            val code = response.code
            response.close()
            throw DownloadException("Download failed (HTTP $code)")
        }

        val total = body.contentLength()
        var done = 0L

        body.byteStream().use { input ->
            FileOutputStream(outFile).use { out ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n == -1) break
                    out.write(buf, 0, n)
                    done += n
                    onProgress(done, total)
                }
            }
        }
        response.close()

        if (!isApk(outFile)) {
            outFile.delete()
            throw DownloadException("Downloaded file টা APK নয় — Drive link ও sharing যাচাই করুন")
        }
        outFile
    }

    /** পুরনো downloaded APK মুছে ফেলো। */
    fun clear(context: Context) {
        File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
    }

    /** APK আসলে একটা zip (PK header) কি না। */
    private fun isApk(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return file.inputStream().use { s ->
            s.read() == 'P'.code && s.read() == 'K'.code
        }
    }
}
