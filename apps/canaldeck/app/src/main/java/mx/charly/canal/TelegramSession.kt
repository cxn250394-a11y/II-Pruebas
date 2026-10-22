package mx.charly.canal

import android.content.Context
import android.os.Build
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList

/** One explicit account session. No bots, proxy service, contact upload or borrowed credentials. */
class TelegramSession private constructor(private val context: Context) {
    companion object {
        @Volatile var current: TelegramSession? = null; private set
        fun start(context: Context): TelegramSession = current ?: TelegramSession(context.applicationContext).also { current = it; it.begin() }
        fun preview(c: MessageContent?): String = when (c) {
            is MessageText -> c.text.text
            is MessagePhoto -> "▧ Foto · ${c.caption.text}"
            is MessageVideo -> "▶ Video · ${c.caption.text}"
            is MessageDocument -> "Archivo · ${c.document.fileName} · ${c.caption.text}"
            is MessageAudio -> "Audio · ${c.audio.title} · ${c.caption.text}"
            is MessageVoiceNote -> "Nota de voz · ${c.caption.text}"
            is MessageSticker -> "Sticker ${c.sticker.emoji}"
            null -> ""
            else -> "Contenido no representado por esta versión"
        }
        /** Only visible caption/text: never OCR or download protected images to infer authorship. */
        fun pickText(c: MessageContent?): String = when (c) {
            is MessageText -> c.text.text
            is MessagePhoto -> c.caption.text
            is MessageVideo -> c.caption.text
            is MessageDocument -> c.caption.text
            else -> ""
        }
        fun errorText(code: Int, raw: String): String = when {
            raw.contains("PHONE_NUMBER_INVALID") -> "Revisa el teléfono con código de país, por ejemplo +52…"
            raw.contains("PHONE_CODE_INVALID") || raw.contains("EMAIL_CODE_INVALID") -> "El código no es correcto. Revisa el último código recibido."
            raw.contains("PHONE_CODE_EXPIRED") -> "El código caducó. Solicita otro desde esta pantalla."
            raw.contains("PASSWORD_HASH_INVALID") -> "La contraseña de verificación en dos pasos no es correcta."
            raw.contains("API_ID_INVALID") -> "La identidad de aplicación no es válida. Revisa api_id y api_hash."
            code == 429 || raw.startsWith("FLOOD_WAIT") -> "Telegram pide esperar antes de reintentar. No repitas el envío de códigos."
            raw.contains("CHAT_WRITE_FORBIDDEN") || raw.contains("CHAT_SEND") -> "No tienes permiso para escribir en esta conversación."
            raw.contains("PAYMENT") || raw.contains("STARS") -> "El envío requiere pago o condiciones adicionales. CanalDeck no autoriza gastos."
            else -> "Telegram no pudo completar esta operación (código $code)."
        }
    }
    private val errorHandler = CoroutineExceptionHandler { _, _ -> status = "La conexión encontró un error. Desconecta y vuelve a conectar."; notifyUi() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + errorHandler)
    private val client = TdlClient.create()
    val observers = CopyOnWriteArrayList<() -> Unit>()
    val messageObservers = CopyOnWriteArrayList<(Long) -> Unit>()
    val chats = linkedMapOf<Long, Chat>()
    val recent = linkedMapOf<Pair<Long, Long>, Message>()
    val removed = linkedSetOf<Pair<Long, Long>>()
    var state: AuthorizationState? = null; private set
    var status = "Iniciando conexión"; private set
    var network = "Conectando"; private set
    var accountId = 0L; private set
    var accountName = ""; private set
    var ready = false; private set
    var closing = false; private set
    var authBusy = false; private set
    var visibleChat = 0L; private set
    var onMessage: ((Message) -> Unit)? = null
    var onRead: ((Long) -> Unit)? = null
    private var configured = false
    private var refreshJob: Job? = null
    private val pendingChats = linkedSetOf<Long>()
    private var pendingJob: Job? = null

    private fun notifyUi() { observers.forEach { it() } }
    private fun notifyMessages(chat: Long) { messageObservers.forEach { it(chat) } }
    private fun record(m: Message) { recent[m.chatId to m.id] = m; while (recent.size > 256) recent.remove(recent.keys.first()); notifyMessages(m.chatId) }
    private fun forget(chat: Long, ids: LongArray) { ids.forEach { removed.add(chat to it); recent.remove(chat to it) }; while (removed.size > 512) removed.remove(removed.first()); notifyMessages(chat) }
    private fun begin() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.allUpdates.collect { u ->
                when (u) {
                    is UpdateAuthorizationState -> authorization(u.authorizationState)
                    is UpdateConnectionState -> {
                        network = when (u.state) { is ConnectionStateReady -> "En línea"; is ConnectionStateWaitingForNetwork -> "Sin red"; is ConnectionStateUpdating -> "Sincronizando"; else -> "Reconectando" }; notifyUi()
                    }
                    is UpdateNewChat -> { chats[u.chat.id] = u.chat; notifyUi() }
                    is UpdateNewMessage -> {
                        record(u.message); refreshChat(u.message.chatId)
                        if (ready) onMessage?.invoke(u.message)
                    }
                    is UpdateChatLastMessage -> refreshChat(u.chatId)
                    is UpdateChatTitle -> refreshChat(u.chatId)
                    is UpdateChatReadInbox -> { refreshChat(u.chatId); if (u.unreadCount == 0) onRead?.invoke(u.chatId) }
                    is UpdateChatReadOutbox -> refreshChat(u.chatId)
                    is UpdateMessageContent -> scope.launch {
                        val r = client.getMessage(chatId = u.chatId, messageId = u.messageId)
                        if (r is TdlResult.Success) record(r.result)
                    }
                    is UpdateDeleteMessages -> if (u.isPermanent) forget(u.chatId, u.messageIds)
                    is UpdateMessageSendSucceeded -> { forget(u.message.chatId, longArrayOf(u.oldMessageId)); record(u.message); refreshChat(u.message.chatId) }
                    is UpdateMessageSendFailed -> { forget(u.message.chatId, longArrayOf(u.oldMessageId)); record(u.message); status = errorText(u.error.code, u.error.message); notifyUi() }
                    else -> Unit
                }
            }
        }
        scope.launch { client.setLogVerbosityLevel(0); client.getAuthorizationState() }
    }
    private suspend fun authorization(s: AuthorizationState) {
        state = s; authBusy = false; ready = false
        status = when (s) {
            is AuthorizationStateWaitTdlibParameters -> "Preparando sesión cifrada"
            is AuthorizationStateWaitPhoneNumber -> "Introduce tu teléfono"
            is AuthorizationStateWaitCode -> "Introduce el código de acceso"
            is AuthorizationStateWaitPassword -> "Verificación en dos pasos"
            is AuthorizationStateWaitEmailAddress -> "Telegram solicita tu correo de acceso"
            is AuthorizationStateWaitEmailCode -> "Introduce el código enviado a tu correo"
            is AuthorizationStateWaitOtherDeviceConfirmation -> "Confirma el acceso desde otro dispositivo"
            is AuthorizationStateReady -> "Cargando tu cuenta"
            is AuthorizationStateClosing -> "Desconectando"
            is AuthorizationStateClosed -> "Desconectado"
            is AuthorizationStateLoggingOut -> "Cerrando esta sesión"
            is AuthorizationStateWaitRegistration -> "Esta versión requiere una cuenta ya creada en Telegram oficial"
            else -> "Telegram solicita un paso adicional no implementado en esta versión"
        }
        notifyUi()
        when (s) {
            is AuthorizationStateWaitTdlibParameters -> if (!configured) configure()
            is AuthorizationStateReady -> scope.launch {
                when (val me = client.getMe()) {
                    is TdlResult.Success -> { if (closing) return@launch; accountId = me.result.id; accountName = me.result.firstName; ready = true; status = "Sesión iniciada"; notifyUi(); refresh() }
                    is TdlResult.Failure -> failure(me)
                }
            }
            is AuthorizationStateClosed -> { closing = true; accountId = 0; chats.clear(); recent.clear(); removed.clear(); if (current === this) current = null; notifyUi(); scope.cancel() }
            else -> Unit
        }
    }
    private suspend fun configure() {
        configured = true
        try {
            val v = Vault(context); val id = v.get("api-id").orEmpty(); val hash = v.get("api-hash").orEmpty()
            require(Vault.validApi(id, hash))
            val r = client.setTdlibParameters(useTestDc = false, databaseDirectory = "${context.filesDir}/telegram/db",
                filesDirectory = "${context.filesDir}/telegram/files", databaseEncryptionKey = v.databaseKey(),
                useFileDatabase = true, useChatInfoDatabase = true, useMessageDatabase = true, useSecretChats = false,
                apiId = id.toInt(), apiHash = hash, systemLanguageCode = "es", deviceModel = Build.MODEL,
                systemVersion = "Android ${Build.VERSION.RELEASE}", applicationVersion = "CanalDeck 0.2 beta")
            if (r is TdlResult.Failure) { configured = false; failure(r) }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { configured = false; status = "No se pudo abrir la configuración cifrada. Revisa tu identidad de aplicación."; notifyUi() }
    }
    private fun failure(r: TdlResult.Failure) { status = errorText(r.code, r.message); notifyUi() }
    fun authenticate(value: String) {
        if (authBusy || closing || value.isBlank()) return
        authBusy = true; notifyUi()
        scope.launch {
            try {
                val r = when (state) {
                    is AuthorizationStateWaitPhoneNumber -> client.setAuthenticationPhoneNumber(phoneNumber = value.trim(), settings = null)
                    is AuthorizationStateWaitCode -> client.checkAuthenticationCode(value.trim())
                    is AuthorizationStateWaitPassword -> client.checkAuthenticationPassword(value)
                    is AuthorizationStateWaitEmailAddress -> client.setAuthenticationEmailAddress(value.trim())
                    is AuthorizationStateWaitEmailCode -> client.checkAuthenticationEmailCode(EmailAddressAuthenticationCode(code = value.trim()))
                    else -> null
                }
                if (r is TdlResult.Failure) failure(r)
            } finally { authBusy = false; notifyUi() }
        }
    }
    fun resendCode() { if (!authBusy && state is AuthorizationStateWaitCode) scope.launch { val r = client.resendAuthenticationCode(); if (r is TdlResult.Failure) failure(r) } }
    fun refresh(limit: Int = 200, archive: Boolean = false) {
        if (!ready || closing || refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            val list: ChatList = if (archive) ChatListArchive() else ChatListMain()
            client.loadChats(chatList = list, limit = limit.coerceIn(1,1000))
            when (val r = client.getChats(chatList = list, limit = limit.coerceIn(1,1000))) {
                is TdlResult.Success -> { for (id in r.result.chatIds) { val c = client.getChat(id); if (c is TdlResult.Success) chats[id] = c.result }; notifyUi() }
                is TdlResult.Failure -> failure(r)
            }
        }
    }
    private fun refreshChat(id: Long) {
        if (!ready || closing) return
        pendingChats.add(id)
        if (pendingJob?.isActive == true) return
        pendingJob = scope.launch {
            delay(150)
            while (pendingChats.isNotEmpty()) {
                val ids = pendingChats.toList(); pendingChats.clear()
                for (chat in ids) { val r = client.getChat(chat); if (r is TdlResult.Success) chats[chat] = r.result }
                notifyUi()
            }
        }
    }
    /** Obtain the original URL through TDLib; never manufacture a t.me/c link from 64-bit IDs. */
    fun linkToOriginal(message: Message, callback: (String?) -> Unit) {
        if (!ready || closing || message.sendingState != null ||
            chats[message.chatId]?.type !is ChatTypeSupergroup) {
            callback(null)
            return
        }
        scope.launch {
            var link: String? = null
            try {
                val p = client.getMessageProperties(chatId = message.chatId, messageId = message.id)
                if (p is TdlResult.Success && p.result.canGetLink) {
                    val r = client.getMessageLink(
                        chatId = message.chatId, messageId = message.id,
                        mediaTimestamp = 0, checklistTaskId = 0, pollOptionId = "",
                        forAlbum = false, inMessageThread = false
                    )
                    if (r is TdlResult.Success) link = PickWatchRules.checkedLink(r.result.link)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Unknown source URL -> No disponible, not a fabricated link. */ }
            if (!closing && ready) callback(link)
        }
    }
    fun history(id: Long, before: Long = 0, callback: (List<Message>?, String?) -> Unit) {
        if (!ready || closing) { callback(null,"La sesión no está conectada"); return }
        scope.launch {
            when (val r = client.getChatHistory(chatId = id, fromMessageId = before, offset = 0, limit = 40, onlyLocal = false)) {
                is TdlResult.Success -> callback(r.result.messages.filterNotNull(), null)
                is TdlResult.Failure -> callback(null,errorText(r.code,r.message))
            }
        }
    }
    fun searchMessages(id: Long, query: String, callback: (List<Message>?, String?) -> Unit) {
        if (!ready || closing) return
        scope.launch { when (val r = client.searchChatMessages(chatId=id,query=query,fromMessageId=0,offset=0,limit=50)) {
            is TdlResult.Success -> callback(r.result.messages.filterNotNull(),null)
            is TdlResult.Failure -> callback(null,errorText(r.code,r.message))
        } }
    }
    fun showChat(id: Long) {
        if (visibleChat == id) return
        val old = visibleChat; visibleChat = id
        if (!ready || closing) return
        scope.launch { if (old != 0L) client.closeChat(old); if (id != 0L) client.openChat(id) }
    }
    fun viewed(id: Long, messages: LongArray) {
        if (!ready || closing || visibleChat != id || messages.isEmpty()) return
        scope.launch { client.viewMessages(chatId=id,messageIds=messages,source=MessageSourceChatHistory(),forceRead=false) }
    }
    fun sendText(id: Long, text: String, reply: Message?, callback: (Message?, String?) -> Unit) {
        if (!ready || closing || !DeckRules.validMessage(text)) { callback(null,"Escribe entre 1 y 4,096 caracteres"); return }
        scope.launch {
            val options = MessageSendOptions(suggestedPostInfo=null,disableNotification=false,fromBackground=false,
                protectContent=false,allowPaidBroadcast=false,paidMessageStarCount=0,updateOrderOfInstalledStickerSets=false,
                schedulingState=null,effectId=0,sendingId=0,onlyPreview=false)
            val target = reply?.let { InputMessageReplyToMessage(messageId=it.id,quote=null,checklistTaskId=0,pollOptionId="") }
            when (val r = client.sendMessage(chatId=id,topicId=reply?.topicId,replyTo=target,options=options,
                inputMessageContent=InputMessageText(text=FormattedText(text=text,entities=emptyArray()),linkPreviewOptions=null,clearDraft=false))) {
                is TdlResult.Success -> { record(r.result); callback(r.result,null) }
                is TdlResult.Failure -> callback(null,errorText(r.code,r.message))
            }
        }
    }
    fun media(fileId: Int, maxBytes: Long, callback: (String?) -> Unit) {
        if (!ready || closing) { callback(null); return }
        scope.launch {
            when (val r = client.downloadFile(fileId=fileId,priority=1,offset=0,limit=maxBytes+1,synchronous=true)) {
                is TdlResult.Success -> {
                    val f = r.result
                    callback(if (f.local.isDownloadingCompleted && f.size <= maxBytes && f.local.downloadedSize <= maxBytes) f.local.path else null)
                }
                else -> callback(null)
            }
        }
    }
    fun logout() { if (!closing) scope.launch { val r = client.logOut(); if (r is TdlResult.Failure) failure(r) } }
    fun stop() {
        if (closing) return
        closing = true; ready = false; onMessage = null; onRead = null; visibleChat = 0; status = "Desconectando"; notifyUi()
        scope.launch { client.close() }
    }
}
