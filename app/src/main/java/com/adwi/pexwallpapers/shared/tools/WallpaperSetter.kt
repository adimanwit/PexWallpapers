package com.adwi.pexwallpapers.shared.tools

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.adwi.pexwallpapers.R
import com.adwi.pexwallpapers.util.showToast
import com.github.ajalt.timberkt.Timber
import com.github.ajalt.timberkt.d
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.IOException


class WallpaperSetter(private val context: Context, private val imageURL: String) {

    private lateinit var wallpaperManager: WallpaperManager

    companion object {
        private const val TAG = "WallpaperSetter"
    }

    suspend fun setWallpaperByImagePath(
        setHomeScreen: Boolean = false,
        setLockScreen: Boolean = false
    ) {
        coroutineScope {
            launch {
                Timber.tag(TAG).d { "setWallpaperByImagePath" }
                val bitmap = getImageUsingCoil()
                setWallpaper(bitmap, setHomeScreen, setLockScreen)
            }
        }
    }

    private suspend fun getImageUsingCoil(): Bitmap {
        Timber.tag(TAG).d { "getImageUsingCoil" }
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(imageURL)
            .allowHardware(false)
            .build()

        val result = (loader.execute(request) as SuccessResult).drawable
        return (result as BitmapDrawable).bitmap
    }

    private fun setWallpaper(bitmap: Bitmap, setHomeScreen: Boolean, setLockScreen: Boolean) {
        Timber.tag(TAG).d { "setWallpaper" }
        wallpaperManager = WallpaperManager.getInstance(context)
        try {
            if (setHomeScreen && !setLockScreen) setHomeScreenWallpaper(bitmap)
            if (!setHomeScreen && setLockScreen) setLockScreenWallpaper(bitmap)
            if (setHomeScreen && setLockScreen) setHomeAndLockScreenWallpaper(bitmap)
        } catch (ex: IOException) {
            Timber.tag(TAG).d { "Exception: ${ex.printStackTrace()}" }
        }
    }

    private fun setHomeScreenWallpaper(
        bitmap: Bitmap,
        message: String? = context.getString(R.string.home_wallpaper_set)
    ) {
        Timber.tag(TAG).d { "setHomeScreenWallpaper" }
        wallpaperManager.setBitmap(bitmap)
        if (message != null) {
            showToast(context, message)
        }
    }

    private fun setLockScreenWallpaper(
        bitmap: Bitmap,
        message: String = context.getString(R.string.lock_wallpaper_set)
    ) {
        Timber.tag(TAG).d { "setLockScreenWallpaper" }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(
                    bitmap, null, true,
                    WallpaperManager.FLAG_LOCK
                )
                showToast(context, message)
            } else {
                showToast(
                    context,
                    context.getString(R.string.lock_screen_wallpaper_not_supported)
                )
            }
        } catch (e: Exception) {
            e.message?.let { showToast(context, it) }
        }
    }

    private fun setHomeAndLockScreenWallpaper(bitmap: Bitmap) {
        Timber.tag(TAG).d { "setHomeAndLockScreenWallpaper" }
        setHomeScreenWallpaper(bitmap, null)
        setLockScreenWallpaper(
            bitmap,
            context.getString(R.string.home_and_lock_screen_wallpaper_set)
        )
    }
}