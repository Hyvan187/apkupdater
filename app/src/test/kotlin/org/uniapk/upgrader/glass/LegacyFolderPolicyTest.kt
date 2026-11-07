package org.uniapk.upgrader.glass
import android.app.Application
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[23],application=Application::class)
class LegacyFolderPolicyTest {
    @Test fun android6TreePermissionCanBeValidatedWithoutCallingNewerApi(){assertTrue(DownloadFolderPolicy.isTree(Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload%2FUniAPK")))}
    @Test fun plainDocumentAndUntrustedSchemeAreNotWritableTrees(){assertFalse(DownloadFolderPolicy.isTree(Uri.parse("content://provider/document/id")));assertFalse(DownloadFolderPolicy.isTree(Uri.parse("file:///tree/folder")))}
}
