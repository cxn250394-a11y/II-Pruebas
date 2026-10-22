# CanalDeck v0.3 beta → avisos Pick$ (primera integración)

**Estado: CANDIDATO, pendiente de CI/Android real.** No supone puesta en producción, modificación de Pick$ 0.2.0, acceso al servidor RS1000 ni envío automático a Pick$. Rama hija de CanalDeck v0.2 Draft #4; se desarrolla sin afectar su APK entregada.

## Cambios implementados

1. Selección MANUAL por `chat.id` y `accountId` real en CanalDeck: mantener pulsada una conversación → **Avisos de picks · Canal/Tipster** → Desactivado / Canal directo / Grupo de reventa. Estado **OFF** por defecto, no se habilita por título.
2. **Canal directo**, por ejemplo Fano Futbol: publicación de texto, foto o foto con caption, sin exigir nombre de tipster. La etiqueta se obtiene del chat seleccionado y cargado desde TDLib; no se inventa un ID de Fano a partir de su nombre. Alertas locales en Android.
3. **Grupo de reventa** (cuatro canales UnicoRey que Charly identificó en el registro de Info): reconoce únicamente líneas con autor atribuido mediante el conjunto de 12 tipsters autorizado por Charly. Una mención casual no basta; si el grupo publica solo una imagen sin caption/autor explícito, queda sin aviso hasta identificarla con evidencia.
4. **Hora original**: campo `Message.date` del evento emitido en el canal/grupo de origen (convertido a CDMX), no hora de notificación ni de procesamiento. Un eventual mensaje reenviado por un revendedor conserva la fecha del POST del revendedor; la fecha anterior del creador no se infiere si Telegram no la acredita.
5. **Link original**: consulta `getMessageLink` a TDLib en la misma sesión para el `chatId/messageId`; se acepta únicamente HTTPS t.me/telegram.me y se muestra `No disponible` si falla. No construimos `t.me/c/<ID>/<mensaje>` a mano. TDLib solo emite enlaces si `messageProperties` los permite.
6. Aviso local con exactamente tres campos: `Canal: X` **o** `Tipster: X` / `Hora original` / `Link`, más título de notificación sin añadir un segundo encabezado de atribución. La configuración general de vista previa de otras conversaciones no se cambia; en modo Picks la notificación Android privada contiene solo los tres metadatos, no el cuerpo ni imagen del post.
7. Vista de mensaje → **Compartir referencia Pick$**: solo cuando el chat fue marcado por el usuario. Previsualiza el texto, y abre el Sharesheet Android **tras tocar Compartir**; tú eliges el destinatario final. No manda un mensaje ni una imagen a Pick$ automáticamente, ni comparte el caption protegido. Puede quedar sin enlace cuando TDLib no permite recuperarlo.
8. Agrupación de fotos en una misma publicación mediante `Message.mediaAlbumId` en el deduplicador de notificaciones (hasta reconectar/cerrar el servicio). Los cambios o ediciones no se tratan como picks nuevos; estado de lectura y red permanece como en la app v0.2.
9. No se agrega otro motor Telegram, otra sesión TDLib, bot, token, puerto, servicio de RS1000, ni integración con Ferrari Beta. Sigue `STAKE=0; BET_ENABLED=False; authorizes_market=false`.

## QA verificable

- Clase pura `PickBridgeRules.kt` sin Android ni red, pruebas `PickBridgeRulesTest.kt` (16 casos independientes, cada uno con JUnit).
- Compilación y Android Emulator mediante workflow `.github/workflows/canaldeck.yml` del PR hijo, con API 35, ejecución nativa y APK.
- Falta comprobar: inicio de sesión Telegram real de Charly en CanalDeck, que el usuario identifique en pantalla la fuente Fano/Unico exacta, correcta atribución de los revendedores con muestras reales permitidas, enlaces de mensaje privado, foto real y álbum, avisos con pantalla bloqueada/HyperOS, consumo de batería. **Una CI verde con fixtures no cierra esos puntos**.
- Si `getMessageLink` o la API de TDLib no compilan con la dependencia fijada, corregir el candidato en la rama, sin cambiar los permisos ni descargar SDK de terceros.
- La APK v0.3 es de **firma debug CI no persistente**; no prometemos actualización sobre v0.2 instalada con otra firma. Conservar respaldos/credenciales locales según instrucciones del usuario antes de sustituir APK, sin extraer sesiones de Telegram.

## Etapa pendiente para avisos automáticos en Pick$

Para entrega automática a la conversación de Telegram Pick$, no basta el Sharesheet: hay que decidir la ruta de salida, el permiso exacto del propietario, deduplicación persistente, permiso de medios y anti-bucle, así como si será el dispositivo Android o el único lector RS1000 quien emita. **No añadir un segundo listener sobre la sesión TDLib del servidor**. El código de aquí no habilita envíos automáticos, que requieren OK operativo propio y aceptación independiente. En contenido protegido se mantendría *solo aviso + enlace*.

## Relación con Info

- [Decisión de Charly 22-oct](https://github.com/cxn250394-a11y/Info-para-SB-G-CGPT/blob/main/coordinacion/decisiones/CHARLY-PICKS-LISTA-FANO-Y-FORMATO-20261022.md)
- [Draft Pick$ #33](https://github.com/cxn250394-a11y/Info-para-SB-G-CGPT/pull/33): registro offline y parser Python; esta entrega es un **cliente Android** y conserva reglas equivalentes.
- No atribuir a CanalDeck captura automática o monitoreo persistente de UnicoRey cuando la aplicación Android/HyperOS esté apagada.
