package org.uniapk.upgrader.glass
import org.uniapk.upgrader.prefs.Prefs
import okhttp3.Interceptor
import okhttp3.Response
/** Network interceptor: credentials are attached only to the intended HTTPS host, including redirects. */
class SourceCredentials(private val prefs:()->Prefs):Interceptor {
    override fun intercept(chain:Interceptor.Chain):Response {
        val request=chain.request();val p=prefs()
        val value=if(!request.url.isHttps)null else when(request.url.host){
            "www.apkmirror.com"->p.apkMirrorKey.get().takeIf{it.isNotBlank()}?.let{if(it.startsWith("Basic ")||it.startsWith("Bearer "))it else "Basic $it"}
            "api.github.com"->p.githubToken.get().takeIf{it.isNotBlank()}?.let{"Bearer $it"}
            "gitlab.com"->p.gitlabToken.get().takeIf{it.isNotBlank()}?.let{"Bearer $it"}
            else->null
        }
        val next=request.newBuilder().removeHeader("Authorization")
        if(value!=null)next.header("Authorization",value)
        return chain.proceed(next.build())
    }
}
object ApkMirrorWeb {
    fun option(update:org.uniapk.upgrader.data.ui.AppUpdate)=org.uniapk.upgrader.data.ui.AppUpdate(
        name=update.name,packageName=update.packageName,version="Websuche",oldVersion=update.oldVersion,
        versionCode=0,oldVersionCode=update.oldVersionCode,source=org.uniapk.upgrader.data.ui.ApkMirrorSource,
        iconUri=update.iconUri,link=org.uniapk.upgrader.data.ui.Link.Url(search(update.packageName)),system=update.system
    )
    fun search(packageName:String)="https://www.apkmirror.com/?post_type=app_release&searchtype=apk&s="+java.net.URLEncoder.encode(packageName,"UTF-8")
}
