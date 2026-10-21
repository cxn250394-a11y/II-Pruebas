package mx.charly.audio

import mx.charly.ui.Streams
import org.json.JSONArray
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder

object RadioDirectory {
 /** Official discovery mechanism; fail over rather than depending on one hard-coded server. */
 fun search(name:String):JSONArray {
  require(name.trim().length in 2..100){"Escribe de 2 a 100 caracteres"}
  val hosts=InetAddress.getAllByName("all.api.radio-browser.info").map{it.canonicalHostName}.filter{it.endsWith(".api.radio-browser.info")}.distinct().shuffled()
  require(hosts.isNotEmpty()){ "No se localizaron servidores del directorio" }
  for(host in hosts.take(3)){
   val path="/json/stations/search?name="+URLEncoder.encode(name,"UTF-8")+"&limit=40&hidebroken=true&order=clickcount&reverse=true"
   try{return request(host,path)}catch(_:Exception){}
  };error("El directorio no respondió; puedes agregar una URL HTTPS manual")
 }
 private fun request(host:String,path:String):JSONArray {
  val c=URL("https://$host$path").openConnection() as javax.net.ssl.HttpsURLConnection
  c.connectTimeout=10000;c.readTimeout=10000;c.instanceFollowRedirects=false;c.setRequestProperty("User-Agent","AudioDeck/0.1")
  try{require(c.responseCode==200);return c.inputStream.use{JSONArray(String(Streams.readLimited(it,1_000_000),Charsets.UTF_8))}}finally{c.disconnect()}
 }
}
