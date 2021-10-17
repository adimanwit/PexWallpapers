package com.adwi.pexwallpapers.util

import com.adwi.pexwallpapers.BuildConfig

class Constants {
    companion object {

        // PexApi
        const val BASE_URL = "https://api.pexels.com/"
        const val API_KEY = BuildConfig.PEX_API_ACCESS_KEY
        const val AUTHORIZATION = "Authorization"
        const val CURATED_PAGE_SIZE = 20
        const val SEARCH_PAGE_SIZE = 20

        // Pager
        const val PAGING_SIZE = 20
        const val PAGING_MAX_SIZE = 200

        // Database
        const val WALLPAPER_DATABASE = "wallpaper_database"

        // Work
        const val WORK_AUTO_WALLPAPER = "work_auto_wallpaper"
        const val NOTIFICATION_WORK = "appName_notification_work"
        const val WORKER_AUTO_WALLPAPER_IMAGE_URL_FULL = "WallpaperUrlFull"
        const val WORKER_AUTO_WALLPAPER_NOTIFICATION_IMAGE = "WallpaperUrlTiny"
    }
}