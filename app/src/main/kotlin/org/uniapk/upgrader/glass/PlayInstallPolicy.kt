package org.uniapk.upgrader.glass
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
object PlayInstallPolicy {
 const val PLAY="com.android.vending"
 fun installedByPlay(context:Context,pkg:String)=runCatching{if(Build.VERSION.SDK_INT>=30)context.packageManager.getInstallSourceInfo(pkg).installingPackageName==PLAY else @Suppress("DEPRECATION") (context.packageManager.getInstallerPackageName(pkg)==PLAY)}.getOrDefault(false)
 fun open(context:Context,packageName:String){
  val market=Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id=$packageName")).setPackage(PLAY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  runCatching{context.startActivity(market)}.getOrElse{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
 }
}
