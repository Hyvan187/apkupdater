package org.uniapk.upgrader.settings

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupException(message:String):Exception(message)

/** Strict bounded JSON; duplicate names, trailing data and excessive nesting are rejected. */
object BackupJson {
    fun parse(text:String):JsonObject {
        require(text.toByteArray(Charsets.UTF_8).size<=BackupCodec.MAX_PLAIN)
        val reader=JsonReader(StringReader(text)).apply{strictness=Strictness.STRICT}
        var nodes=0
        fun value(depth:Int):JsonElement {
            require(depth<=12&&++nodes<=100000)
            return when(reader.peek()){
                JsonToken.BEGIN_OBJECT->JsonObject().apply{reader.beginObject();while(reader.hasNext()){val name=reader.nextName();require(name.length<=512&&!has(name));add(name,value(depth+1))};reader.endObject()}
                JsonToken.BEGIN_ARRAY->JsonArray().apply{reader.beginArray();while(reader.hasNext()){require(size()<10000);add(value(depth+1))};reader.endArray()}
                JsonToken.STRING->JsonPrimitive(reader.nextString().also{require(it.length<=4096)})
                JsonToken.NUMBER->JsonPrimitive(reader.nextString().also{require(it.length<=32)}.toBigDecimal())
                JsonToken.BOOLEAN->JsonPrimitive(reader.nextBoolean())
                JsonToken.NULL->{reader.nextNull();JsonNull.INSTANCE}
                else->throw IllegalArgumentException("Ungültige Backup-Struktur.")
            }
        }
        return reader.use{val result=value(0);require(result.isJsonObject&&reader.peek()==JsonToken.END_DOCUMENT);result.asJsonObject}
    }
}

/** Portable authenticated envelope. No device Keystore key or plaintext temporary file is exported. */
object BackupCodec {
    const val MAX_PLAIN=2*1024*1024
    const val MAX_FILE=MAX_PLAIN+1024
    const val ITERATIONS=600000
    private val magic="UNIAPKB1".toByteArray(Charsets.US_ASCII)
    private const val HEADER=8+4+16+12
    fun read(input:InputStream):ByteArray {
        val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384)
        while(true){val n=input.read(buffer);if(n<0)break;if(output.size()+n>MAX_FILE)throw BackupException("Die Datei ist zu groß für ein Einstellungsbackup.");output.write(buffer,0,n)}
        return output.toByteArray()
    }
    fun checkPassword(password:CharArray){if(password.size !in 12..256)throw BackupException("Bitte ein Passwort mit 12 bis 256 Zeichen verwenden.")}
    internal fun derive(password:CharArray,salt:ByteArray,iterations:Int=ITERATIONS,legacy:Boolean=android.os.Build.VERSION.SDK_INT<26):ByteArray {
        if(!legacy){val spec=PBEKeySpec(password,salt,iterations,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded}finally{spec.clearPassword()}}
        // Maintained library fallback for Android 6/7; do not register or replace a system provider.
        val bytes=org.bouncycastle.crypto.PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(password)
        try{val generator=org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator(org.bouncycastle.crypto.digests.SHA256Digest());generator.init(bytes,salt,iterations);return (generator.generateDerivedParameters(256) as org.bouncycastle.crypto.params.KeyParameter).key}
        finally{bytes.fill(0)}
    }
    fun seal(plain:ByteArray,password:CharArray):ByteArray {
        checkPassword(password);if(plain.size>MAX_PLAIN)throw BackupException("Zu viele Einstellungsdaten für dieses Backupformat.")
        val salt=ByteArray(16);val iv=ByteArray(12);SecureRandom().apply{nextBytes(salt);nextBytes(iv)}
        val header=ByteBuffer.allocate(HEADER).put(magic).putInt(ITERATIONS).put(salt).put(iv).array()
        val key=derive(password,salt)
        try{val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv));cipher.updateAAD(header);return header+cipher.doFinal(plain)}finally{key.fill(0)}
    }
    fun open(encrypted:ByteArray,password:CharArray):ByteArray {
        if(encrypted.size !in HEADER+16..MAX_FILE||!encrypted.copyOfRange(0,8).contentEquals(magic))throw BackupException("Keine unterstützte UniAPK-Backupdatei.")
        val header=encrypted.copyOfRange(0,HEADER);val b=ByteBuffer.wrap(header);b.position(8)
        if(b.int!=ITERATIONS)throw BackupException("Die Backup-Verschlüsselung wird von dieser App-Version nicht unterstützt.")
        if(password.size !in 1..256)throw BackupException("Bitte das Backup-Passwort eingeben.")
        val salt=ByteArray(16).also{b.get(it)};val iv=ByteArray(12).also{b.get(it)};val key=derive(password,salt)
        try{val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv));cipher.updateAAD(header);return cipher.doFinal(encrypted,HEADER,encrypted.size-HEADER).also{if(it.size>MAX_PLAIN)throw BackupException("Backup zu groß.")}}
        catch(_:javax.crypto.BadPaddingException){throw BackupException("Passwort falsch oder Backupdatei beschädigt. Es wurde nichts geändert.")}
        finally{key.fill(0)}
    }
}
