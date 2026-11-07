package org.uniapk.upgrader.glass

import java.util.concurrent.ConcurrentHashMap

/** Auth/rate failures should not repeat on every settings change. No access bypass. */
object ProviderCooldown {
    data class Block(val status:Int,val until:Long)
    private val blocked=ConcurrentHashMap<String,Block>()
    fun note(name:String,status:Int,now:Long=System.currentTimeMillis()) {
        val duration=when(status){401,403->30*60_000L;429->5*60_000L;else->0L}
        if(duration>0)blocked[name]=Block(status,now+duration)
    }
    fun active(name:String,now:Long=System.currentTimeMillis()):Block? = blocked[name]?.let { if(it.until>now)it else { blocked.remove(name,it);null } }
    fun retry(name:String) { blocked.remove(name) }
}
