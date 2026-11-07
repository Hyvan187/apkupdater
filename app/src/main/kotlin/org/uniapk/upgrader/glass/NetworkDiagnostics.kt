package org.uniapk.upgrader.glass
import android.content.Context
import android.net.ConnectivityManager
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
/** Uses the current Android network, including its VPN/private-DNS policy. No third-party DNS override. */
class NetworkDns(private val context:Context):Dns {
 override fun lookup(hostname:String):List<InetAddress>{
  val manager=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  return try{val network=manager.activeNetwork;val addresses=if(network!=null)network.getAllByName(hostname).toList() else Dns.SYSTEM.lookup(hostname);if(addresses.isEmpty())throw UnknownHostException(hostname);addresses}
  catch(e:SecurityException){Dns.SYSTEM.lookup(hostname)}
 }
}
object NetworkDiagnostics {
 fun hints(problem:String)=when{
  problem.contains("401")->"Die API verlangt eine Anmeldung oder einen gültigen Zugriffsschlüssel. Wiederholungen lösen das nicht. Die Webseite bleibt separat nutzbar; API-Anfragen pausieren vorübergehend."
  problem.contains("DNS")->"Adresse kann über das aktuelle Netz nicht aufgelöst werden. Verbindung erneut testen; bei Bedarf privates DNS, VPN oder DNS-Filter prüfen. Andere Quellen bleiben nutzbar."
  problem.contains("403")->"Der Anbieter verweigert den App-Zugriff. Webseite manuell öffnen; Schutzmechanismen werden nicht umgangen."
  problem.contains("429")->"Anfragelimit erreicht. Später erneut prüfen; häufige Wiederholungen verlängern möglicherweise die Sperre."
  else->"Netz oder Anbieter vorübergehend nicht erreichbar. Erneut testen oder die Quellseite öffnen."
 }
 suspend fun test(context:Context):String=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){
  try{NetworkDns(context).lookup("www.apkmirror.com");val client=okhttp3.OkHttpClient.Builder().dns(NetworkDns(context)).connectTimeout(10,java.util.concurrent.TimeUnit.SECONDS).callTimeout(15,java.util.concurrent.TimeUnit.SECONDS).build()
   client.newCall(okhttp3.Request.Builder().url("https://www.apkmirror.com/").head().build()).execute().use{if(it.isSuccessful)"DNS und HTTPS erreichbar. Die API-Abfrage wird bei der nächsten Suche separat geprüft." else "DNS funktioniert · HTTP ${it.code}. ${if(it.code==403)"Anbieterzugriff eingeschränkt; Web-Fallback verwenden." else "Keine bestätigte API-Verfügbarkeit."}"}
  }catch(e:Exception){SourceHealth.describe(e)+"\n"+hints(SourceHealth.describe(e))}
 }
}
