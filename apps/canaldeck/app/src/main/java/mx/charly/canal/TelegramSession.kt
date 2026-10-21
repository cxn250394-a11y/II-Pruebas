package mx.charly.canal

import android.content.Context
import android.os.Build
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList

/** Real TDLib session, not a notification reader. User-initiated login only. */
class TelegramSession private constructor(private val context:Context){
 companion object {
  var current:TelegramSession?=null;private set
  fun start(context:Context):TelegramSession {return current?:TelegramSession(context.applicationContext).also{current=it;it.begin()}}
  fun preview(content:MessageContent?):String=when(content){is MessageText->content.text.text;is MessagePhoto->"[Foto] "+content.caption.text;is MessageVideo->"[Video] "+content.caption.text;is MessageDocument->"[Archivo] "+content.caption.text;null->"";else->"[${content.javaClass.simpleName.removePrefix("Message")}]"}
 }
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private val client=TdlClient.create()
 val observers=CopyOnWriteArrayList<()->Unit>()
 var state:AuthorizationState?=null;private set
 var status="Conectando a Telegram";private set
 var ready=false;private set
 val chats=linkedMapOf<Long,Chat>()
 var onMessage:((Message)->Unit)?=null
 private var configured=false
 private fun notifyUi(){observers.forEach{it()}}
 private fun begin(){
  scope.launch{client.authorizationStateUpdates.collect{u->state=u.authorizationState;ready=state is AuthorizationStateReady
   status=when(state){is AuthorizationStateWaitTdlibParameters->"Preparando sesión cifrada";is AuthorizationStateWaitPhoneNumber->"Introduce tu teléfono";is AuthorizationStateWaitCode->"Introduce el código recibido en Telegram";is AuthorizationStateWaitPassword->"Introduce tu contraseña de verificación en dos pasos";is AuthorizationStateReady->"Conectado a Telegram";is AuthorizationStateClosed->"Sesión cerrada";is AuthorizationStateLoggingOut->"Cerrando sesión";else->"Estado requerido: ${state?.javaClass?.simpleName}"}
   if(state is AuthorizationStateWaitTdlibParameters&&!configured)configure()
   if(ready)refresh()
   notifyUi()
  }}
  scope.launch{client.allUpdates.collect{u->when(u){
   is UpdateNewChat->{chats[u.chat.id]=u.chat;notifyUi()}
   is UpdateNewMessage->{onMessage?.invoke(u.message);refreshChat(u.message.chatId)}
   else->Unit
  }}}
  scope.launch{client.setLogVerbosityLevel(0);client.getAuthorizationState()}
 }
 private suspend fun configure(){configured=true
  try{val v=Vault(context);val id=v.get("api-id")?.toIntOrNull()?:error("Falta api_id");val hash=v.get("api-hash")?:error("Falta api_hash")
   handle(client.setTdlibParameters(useTestDc=false,databaseDirectory="${context.filesDir}/telegram/db",filesDirectory="${context.filesDir}/telegram/files",databaseEncryptionKey=v.databaseKey(),useFileDatabase=true,useChatInfoDatabase=true,useMessageDatabase=true,useSecretChats=false,apiId=id,apiHash=hash,systemLanguageCode="es",deviceModel=Build.MODEL,systemVersion="Android ${Build.VERSION.RELEASE}",applicationVersion="CanalDeck 0.1 alpha"))
  }catch(_:Exception){status="No se pudo abrir la configuración cifrada. Revisa las credenciales en esta app.";configured=false;notifyUi()}
 }
 private fun handle(r:TdlResult<*>){if(r is TdlResult.Failure){status="Telegram: ${r.code} · ${r.message.take(160)}";notifyUi()}}
 fun authenticate(value:String){scope.launch{try{val result=when(state){is AuthorizationStateWaitPhoneNumber->client.setAuthenticationPhoneNumber(phoneNumber=value,settings=null);is AuthorizationStateWaitCode->client.checkAuthenticationCode(value);is AuthorizationStateWaitPassword->client.checkAuthenticationPassword(value);else->null};if(result!=null)handle(result)}catch(_:Exception){status="No se pudo completar la autenticación";notifyUi()}}}
 fun refresh(limit:Int=100){if(!ready)return;scope.launch{try{client.loadChats(chatList=ChatListMain(),limit=limit)
  when(val r=client.getChats(chatList=ChatListMain(),limit=limit)){is TdlResult.Success->{for(id in r.result.chatIds){when(val c=client.getChat(id)){is TdlResult.Success->chats[id]=c.result;else->Unit}};notifyUi()};is TdlResult.Failure->handle(r)}
 }catch(_:Exception){status="No se pudo actualizar la lista";notifyUi()}}}
 private fun refreshChat(id:Long){scope.launch{when(val c=client.getChat(id)){is TdlResult.Success->{chats[id]=c.result;notifyUi()};else->Unit}}}
 fun history(id:Long,before:Long=0,callback:(List<Message>?,String?)->Unit){scope.launch{try{when(val r=client.getChatHistory(chatId=id,fromMessageId=before,offset=0,limit=40,onlyLocal=false)){
  is TdlResult.Success->callback(r.result.messages.filterNotNull(),null);is TdlResult.Failure->callback(null,"${r.code}: ${r.message.take(120)}")
 }}catch(_:Exception){callback(null,"No se pudo obtener el historial")}}}
 fun photo(fileId:Int,callback:(String?)->Unit){scope.launch{try{when(val r=client.downloadFile(fileId=fileId,priority=1,offset=0,limit=0,synchronous=true)){
  is TdlResult.Success->{val f=r.result;callback(if(f.size<=20_000_000&&f.local.isDownloadingCompleted)f.local.path else null)};else->callback(null)
 }}catch(_:Exception){callback(null)}}}
 fun logout(){scope.launch{handle(client.logOut())}}
 fun stop(){onMessage=null;current=null;observers.clear();scope.launch{try{client.close()}finally{scope.cancel()}}}
}
