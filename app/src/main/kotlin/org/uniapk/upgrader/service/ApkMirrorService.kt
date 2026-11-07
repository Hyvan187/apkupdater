package org.uniapk.upgrader.service

import org.uniapk.upgrader.data.apkmirror.AppExistsRequest
import org.uniapk.upgrader.data.apkmirror.AppExistsResponse
import org.uniapk.upgrader.BuildConfig
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST


interface ApkMirrorService {

    @Headers(
        "User-Agent: UniAPK-v" + BuildConfig.VERSION_NAME,
        "Content-Type: application/json"
    )
    @POST("/wp-json/apkm/v1/app_exists/")
	suspend fun appExists(
        @Body request: AppExistsRequest
    ): AppExistsResponse

}
