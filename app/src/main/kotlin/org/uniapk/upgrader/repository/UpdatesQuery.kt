package org.uniapk.upgrader.repository

import org.uniapk.upgrader.data.ui.AppUpdate
import kotlinx.coroutines.flow.Flow

/** Discovery returns metadata only. Downloading requires a separate user action. */
fun interface UpdatesQuery {
    fun updates(progressId: Long?): Flow<List<AppUpdate>>
}
