# CanalDeck ↔ Pick$ · etapa 1: alertas locales seleccionadas (22-oct-2026)

**ESTADO: CANDIDATO EN DRAFT; NO DESPLEGADO; NO ENVÍA NADA A TELEGRAM NI A PICK$.**

## Qué aprovecha del cliente CanalDeck

CanalDeck 0.2 beta ya tiene una única sesión TDLib de Android, visible y activada manualmente, y recibe `UpdateNewMessage` de las conversaciones del usuario. Esta etapa agrega una política de avisos **locales Android** sobre ese evento existente, sin crear una segunda sesión ni usar notificaciones interceptadas de otras apps.

### Activación después de instalar una APK compilada y probar inicio de sesión

1. Mantén pulsado cada conversación real en CanalDeck → **Avisos de picks (Canal / Tipster)**. Por defecto todo sigue apagado y no se detecta ninguna fuente por nombre parecido.
2. Elige **Canal directo** para fuentes propias como Fano Futbol, Premium Privé, Rayo Free Picks, JC FOOTY TIPS y Jtsfootytips, solo cuando el chat esté identificado. El aviso usa `Canal: <nombre real del chat>`.
3. Elige **Grupo revendedor** para los cuatro grupos UnicoRey «- OCTUBRE» identificados por chat real. Solo detecta publicaciones con etiqueta explícita `Tipster: X` o línea completa X entre los doce nombres que Charly autorizó. No hereda la allowlist para Mazdita; esta etapa no altera sus filtros antiguos.
4. La app ofrece un aviso del sistema con tres líneas: **Canal o Tipster**, **Hora original** del mensaje publicado en el canal/grupo monitoreado (CDMX) y **Link** devuelto por la función oficial TDLib `getMessageLink`. Si TDLib no permite obtenerlo: `No disponible`, jamás un enlace inventado.
5. Tocando la notificación se abre la conversación dentro de CanalDeck. Las imágenes originales **no** se reenvían, descargan ni copian automáticamente. Fano con foto/caption puede producir un aviso; si viene imagen sola de revendedor sin etiqueta reconocible, permanece sin clasificar en lugar de inventar tipster.

## Restricciones deliberadas

- Esta rama **NO implementa** un enlace automático al canal privado Pick$, envío Bot API, forwarding de Telegram, scraping de Nicegram ni importación de archivos. Es el primer paso móvil y necesitará una segunda etapa de integración con el único lector Pick$ existente en RS1000, con autorización específica y una prueba viva reversible.
- Solo funciona si la cuenta de CanalDeck tiene sesión real activa y el servicio visible sigue conectado; Android/HyperOS puede limitar la ejecución en segundo plano. La APK base v0.2 tampoco tiene paridad completa con Nicegram.
- Chats protegidos y autodestrucción: nunca exportar contenido protegido ni evadir restricciones. El aviso solo muestra metadatos aprobados. Para obtener link se comprueban las propiedades TDLib; ni el cliente ni el filtro fabrican `t.me/c/`.
- No se almacenan textos ni imágenes en preferencias de estas alertas: por chat/cuenta solo se guardan tipo elegido, baseline, último message ID y album ID. Los avisos nuevos posteriores a la activación evitan replay de historial anterior. Se conserva la configuración privada per-account del propio cliente.
- Los álbumes solo muestran un candidato por mediaAlbumId en el mismo chat. Si aparece primero una foto sin caption en un revendedor, no se asocia a otra publicación por proximidad: no se inventa identidad. Si la fecha del mensaje no está disponible o es anterior a su activación, se omite.
- Los doce nombres están confirmados, pero no se conocen automáticamente los IDs de fuentes adicionales ni la firma específica de cada tipster. La selección manual explícita por chat real mitiga homónimos. No prueba que todas las publicaciones se identifiquen o que estén completas.

## Diferencia de volumen Mazdita / UnicoRey

No se puede atribuir al número de publicaciones: la instalación productiva Pick$ 0.2.0 tenía únicamente las fuentes Mazdita y Premium Privé y el candidato Unico del PR #32 aún no estaba integrado en su sesión. Esta etapa no mide el volumen total publicado cuando CanalDeck está cerrado. Para concluir hace falta auditoría agregada de los eventos recibidos/filtrados/enviados por chat ID y ventana comparable, con permisos separados. No equiparar `0 avisos` con `0 mensajes`.

## Verificación

- Código: `app/src/main/java/mx/charly/canal/PickAlertPolicy.kt` (política pura), `Organization.kt` (preferencias por chat/account), `TelegramSession.kt` (enlace de mensaje oficial), `ConnectionService.kt` (avisos Android), `CanalActivity.kt` (selector por chat).
- Pruebas: `app/src/test/java/mx/charly/canal/PickAlertPolicyTest.kt`, junto con suites heredadas de CanalDeck.
- CI esperada: `.github/workflows/canaldeck.yml`; gradle `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` más emulador. El PASS real se certificará únicamente si termina el run correspondiente al HEAD del PR.

**No alterar** II-Pruebas main, Pick$ productivo 0.2.0, RS1000, Ferrari Beta G2, sesión TDLib de producción, ni disparar G4/Telegram real de Ferrari. STAKE=0; BET_ENABLED=False; authorizes_market=false.
