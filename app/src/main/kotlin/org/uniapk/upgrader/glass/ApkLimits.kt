package org.uniapk.upgrader.glass
import java.io.InputStream
import java.io.ByteArrayOutputStream
class InspectionException(message:String):java.io.IOException(message)
object ApkLimits {
 const val MANIFEST_BYTES=8*1024*1024
 fun readBounded(input:InputStream,maxBytes:Int=MANIFEST_BYTES):ByteArray{
  val out=ByteArrayOutputStream();val b=ByteArray(8192);var size=0
  while(true){val n=input.read(b);if(n<0)break;size+=n;if(size>maxBytes)throw InspectionException("APK-Manifest überschreitet das sichere Prüflimit.");out.write(b,0,n)}
  return out.toByteArray()
 }
}
