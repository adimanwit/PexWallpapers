package com.adwi.pexwallpapers.shared.work

import android.content.Context
import androidx.work.*
import com.adwi.pexwallpapers.data.local.entity.Wallpaper
import com.adwi.pexwallpapers.util.Constants.Companion.WORKER_AUTO_WALLPAPER_IMAGE_URL_FULL
import com.adwi.pexwallpapers.util.Constants.Companion.WORKER_AUTO_WALLPAPER_NOTIFICATION_IMAGE
import com.adwi.pexwallpapers.util.Constants.Companion.WORK_AUTO_WALLPAPER
import com.adwi.pexwallpapers.util.Constants.Companion.WORK_AUTO_WALLPAPER_NAME
import dagger.hilt.android.internal.Contexts
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val TAG = "WorkTools"
private const val timeSpeeding = 10
private const val minutesWorkTimes = 3

class WorkTools @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val application = Contexts.getApplication(context)
    private val workManager = WorkManager.getInstance(application)

    fun cancelWorks(workTag: String) {
        workManager.cancelAllWorkByTag(workTag)
        Timber.tag(TAG).d("cancelWorks - $WORK_AUTO_WALLPAPER")
    }

    fun setupAutoChangeWallpaperWorks(
        favorites: List<Wallpaper>,
        timeUnit: TimeUnit,
        timeValue: Float
    ) {
        Timber.tag(TAG).d("setupAllWorks")
        cancelWorks(WORK_AUTO_WALLPAPER)

        val list = makeMultiplierList(favorites.size)

        var multiplier = 1

        for (number in 1..minutesWorkTimes) {
            favorites.forEachIndexed { index, wallpaper ->

                val delay = getDelay(
                    timeUnit = timeUnit,
                    timeValue = timeValue
                ) / timeSpeeding

                createAutoChangeWallpaperWork(
                    workName = "${number}_${WORK_AUTO_WALLPAPER_NAME}_${wallpaper.id}",
                    wallpaper = wallpaper,
                    delay = delay * multiplier
                )
                multiplier++

                Timber.tag(TAG)
                    .d("delay is: ${delay + (delay)}, multiplier = $multiplier, number = $number")
            }
        }
    }

    private fun makeMultiplierList(listSize: Int): List<Int> {
        val list = mutableListOf<Int>()
        val delayMultipliers = minutesWorkTimes * listSize
        for (i in 1..delayMultipliers) {
            list += i
        }
        return list
    }

    private fun createAutoChangeWallpaperWork(
        workName: String,
        wallpaper: Wallpaper,
        delay: Long
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .setRequiresBatteryNotLow(true)
            .build()
        val work = OneTimeWorkRequestBuilder<AutoChangeWallpaperWork>()
            .setInputData(createDataForAutoChangeWallpaperWorker(wallpaper))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
//            .setConstraints(constraints)
            .addTag(WORK_AUTO_WALLPAPER)
            .build()

        WorkManager.getInstance(this.application.applicationContext).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            work
        )

        Timber.tag(TAG)
            .d("Created work: \nwallpaperId = ${wallpaper.id}, \ndelay = $delay")
        workManager.enqueue(work)
    }

    private fun createDataForAutoChangeWallpaperWorker(wallpaper: Wallpaper): Data {
        val builder = Data.Builder()
        builder.putString(WORKER_AUTO_WALLPAPER_IMAGE_URL_FULL, wallpaper.src?.portrait)
        builder.putString(WORKER_AUTO_WALLPAPER_NOTIFICATION_IMAGE, wallpaper.src?.portrait)
        Timber.tag(TAG).d("createDataForAutoChangeWallpaperWorker")
        return builder.build()
    }

    private fun getDelay(timeUnit: TimeUnit, timeValue: Float): Long {
        val minute = 60 * 1000
        val hour = 60 * minute
        val day = 24 * hour
        val value = timeValue.toInt()
        val delay = when (timeUnit) {
            TimeUnit.MINUTES -> minute * value
            TimeUnit.HOURS -> hour * value
            else -> day * value
        }.toLong()

        return delay
    }
}