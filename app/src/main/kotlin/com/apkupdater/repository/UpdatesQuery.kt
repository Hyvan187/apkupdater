package com.apkupdater.repository

import com.apkupdater.data.ui.AppUpdate
import kotlinx.coroutines.flow.Flow

/** Discovery returns metadata only. Downloading requires a separate user action. */
fun interface UpdatesQuery {
    fun updates(progressId: Long?): Flow<List<AppUpdate>>
}
