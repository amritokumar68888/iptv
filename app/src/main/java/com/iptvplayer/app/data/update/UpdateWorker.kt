package com.iptvplayer.app.data.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Background-এ update খোঁজে — অ্যাপ বন্ধ থাকলেও গ্রাহক notification পায়।
 *
 * Android-এর সীমা: periodic work **সর্বনিম্ন ১৫ মিনিট**, আর ডিভাইস নিজেই
 * সময় ঠিক করে (battery বাঁচাতে)। তাই এটা "তাৎক্ষণিক" নয় —
 * তাৎক্ষণিক জানানোর জন্য অ্যাপ খোলার সময় check করা হয়
 * (MainActivity.checkForAppUpdate)।
 */
class UpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        return try {
            // Play Store থেকে install করা app → Play নিজেই update দেয়,
            // তাই এখানে notification দিয়ে বিভ্রান্ত করি না
            if (InstallSource.isFromPlay(ctx)) {
                return Result.success()
            }

            when (val result = UpdateChecker.check(ctx)) {
                is UpdateChecker.Result.Available ->
                    UpdateNotifier.notifyAvailable(ctx, result.info, result.forced)

                // update নেই / check fail → পুরনো notification সরাও
                else -> UpdateNotifier.clear(ctx)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "skyott_update_check"

        /**
         * Background check চালু করে। বারবার call করলেও একটাই কাজ চলে
         * (KEEP policy) — তাই MainActivity-র onCreate থেকে নিশ্চিন্তে ডাকা যায়।
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateWorker>(
                6, TimeUnit.HOURS          // Android এটাকে নিজের মতো করে শিডিউল করবে
            )
                .setConstraints(constraints)
                .build()

            try {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (e: Exception) {
                // WorkManager unavailable হলেও app চলবে
            }
        }

        /** Background check বন্ধ করে। */
        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context)
                    .cancelUniqueWork(UNIQUE_NAME)
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
