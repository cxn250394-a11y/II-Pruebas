package mx.charly.canal

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Credentials and TDLib database key encrypted with a non-exportable Android Keystore key. */
class Vault(context:Context){
 private val prefs=context.getSharedPreferences("vault",Context.MODE_PRIVATE)
 private fun key():SecretKey {val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};val old=store.getKey("canal-v1",null);if(old is SecretKey)return old
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder("canal-v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()
 }
 fun put(name:String,value:String){val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());val data=c.iv+c.doFinal(value.toByteArray(Charsets.UTF_8));check(prefs.edit().putString(name,Base64.encodeToString(data,Base64.NO_WRAP)).commit())}
 fun get(name:String):String? {val s=prefs.getString(name,null)?:return null;val b=Base64.decode(s,Base64.NO_WRAP);require(b.size>28);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,b.copyOfRange(0,12)));return String(c.doFinal(b.copyOfRange(12,b.size)),Charsets.UTF_8)}
 fun databaseKey():ByteArray {val old=get("database-key");if(old!=null)return Base64.decode(old,Base64.NO_WRAP);val b=ByteArray(32);java.security.SecureRandom().nextBytes(b);put("database-key",Base64.encodeToString(b,Base64.NO_WRAP));return b}
 companion object{fun validApi(id:String,hash:String)=id.toIntOrNull()?.let{it>0}==true&&hash.matches(Regex("[a-fA-F0-9]{32}"))}
}
