# Ensayo mínimo en POCO / HyperOS

Este documento propone una prueba del candidato aislado de álbumes y ACK. El
trabajo autorizado ahora es desarrollo y CI offline. **Todavía no se ha ejecutado
esta prueba en el teléfono del propietario.** No habilita Telegram ni cambia la
configuración de Pick$ productivo, Ferrari o los PR congelados #8 y #33.

## A. Probar con datos ficticios, sin conectar una cuenta

La Torre entregará el APK exacto del nuevo candidato, con su commit, CI y SHA-256.
El APK antiguo de PR #8 no contiene este ensayo nuevo. Una CI verde no equivale a
una prueba con el POCO ni a permiso para enviar picks reales.

1. Conserva CanalDeck principal y sus datos anteriores. El nuevo candidato usa
   la instalación separada `mx.charly.canal.offline`: verifica con la Torre que
   ese sea el paquete del APK entregado. No sustituye la app principal. Si Android
   rechaza la firma, **no desinstales ni borres datos para forzar la instalación**:
   avisa a la Torre. Los checkpoints y entregas inciertas deben conservarse.
2. Para este ensayo deja el teléfono en modo avión, con Wi-Fi y datos móviles
   apagados. Abre CanalDeck y toca **«Ensayo offline · sin cuenta»**. No toques
   un acceso de conexión ni introduzcas identidad de aplicación, teléfono o códigos.
   Esta versión oculta el alta de cuenta y carece del permiso de Internet. Si ves
   un botón para conectar una cuenta, detén A y comprueba el APK con la Torre.
   Esta instalación no apaga un servicio de otra app conectado previamente.
3. La pantalla debe indicar **«Picks ficticios · 0 envíos reales»**. Las tarjetas
   representan un álbum inventado; no son fotografías de ningún canal ni abren
   enlaces. La pantalla no inicia TDLib, un lector, una cuenta ni un servicio.
4. Toca **«Nuevo ensayo ficticio»** y sigue el botón principal de cada paso.
   El resultado esperado es:

| Momento del ensayo | Lo que debe verse |
|---|---|
| Solo parte del álbum | Retenido; no hay solicitud de encabezado ni de fotos. |
| Álbum ficticio completo | Encabezado pendiente; aún no se solicita reenviar fotos. |
| Acuse rápido del encabezado | Sigue pendiente; un acuse rápido no es confirmación final. |
| Confirmación final del encabezado | Se solicitan las dos fotos ficticias; la entrega sigue pendiente. |
| Confirmación final de una foto | La entrega continúa pendiente: falta la otra. |
| Confirmación final de ambas | Escenario ficticio confirmado. El contador de envíos reales sigue en cero. |

5. Haz otro ensayo y toca **«Simular corte · conservar incierto»** antes de
   completar las confirmaciones. Debe
   quedar **UNKNOWN / incierto**. Comprueba la reapertura: conserva ese estado y
   no crea otra solicitud para el mismo escenario. Un nuevo ensayo utiliza una
   identidad ficticia distinta; no borra el anterior.
   El botón **«Comprobar reapertura · sin reintentar»** cierra y vuelve a abrir
   la base local del ensayo, sin transporte ni solicitudes nuevas. También puede
   usarse con una entrega pendiente: queda incierta al recuperar la base.
6. Cierra y vuelve a abrir la pantalla. No debe iniciar sesión, enviar Telegram,
   vaciar checkpoints ni reanudar un escenario incierto. Puedes dejar de ensayar
   simplemente volviendo atrás o cerrando la app.

Anota el modelo POCO, versión Android/HyperOS, versión del APK y los resultados
de los pasos anteriores. Una captura de la pantalla ficticia basta para describir
un fallo; no incluyas conversaciones, IDs privados, fotos de tipsters ni secretos.

**Aceptación de A:** el ensayo funciona sin red ni cuenta, respeta las fases del
álbum/ACK, conserva UNKNOWN y muestra cero envíos reales. Esto solo acredita una
simulación en ese teléfono. No demuestra cardinalidad de álbum real, entrega real
a Pick$, cobertura 24/7 ni funcionamiento con pantalla apagada.

## B. Observar fuentes reales: pendiente de su propio OK

La observación con una cuenta real es otra fase. La Torre debe acordar con Charly
un OK específico que delimite cuenta, fuentes elegidas en la app, periodo, solo
lectura, alcance de métricas y forma de detenerse. No se deriva del OK de Ferrari,
del ensayo A ni del pedido de construir la función.

Cuando esa fase sea autorizada, el propietario seleccionará desde su propia
sesión los chats reales de Fano y cada fuente UnicoRey, separados de Mazdita. Los
nombres no sustituyen a las identidades reales. Los doce tipsters siguen siendo
la lista permitida, sin ampliar autores por similitud o por aparecer en un texto.
La cuenta y los permisos se verifican en el teléfono; no se pegan credenciales ni
IDs privados en un PR o en el chat.

Se registrarán solo contadores privados de lo que CanalDeck observó durante la
ventana conectada. No llamarlos «total de publicaciones de Telegram». La salida
continúa OFF. Los álbumes cuya cardinalidad real no pueda demostrarse permanecen
retenidos. No descargar ni reconstruir imágenes protegidas y no convertir un
timeout o un periodo sin mensajes en prueba de álbum completo.

En HyperOS se comprobará después, dentro del OK, qué ocurre al apagar la pantalla,
perder red y volver a abrir la app. El servicio visible puede ser suspendido por
el sistema. Inicio automático y batería se ajustan solo por decisión del dueño;
esta guía no promete entrega instantánea ni persistencia continua.

## C. Un envío real controlado: futuro y separado

Antes de enviar a Pick$ hacen falta revisión ajena por el HEAD exacto, decisión de
la Torre, evidencia suficiente de B y un **nuevo OK literal de Charly**. Ese OK
debe nombrar destino y fuentes reales, duración, límites de tasa, contenido y
atribución, permisos por chat y por mensaje, custodia, STOP y rollback. No debe
autorizar apuestas ni gastos.

La compilación actual conserva el interruptor real sellado en `false`; la pantalla
offline no lo cambia. Una futura habilitación requiere otro cambio revisable y
sus gates. Que una solicitud vuelva con IDs temporales o con un acuse rápido no
autoriza la siguiente fase: se exige confirmación final correlacionada de cada
mensaje. Si el resultado es incierto, se conserva UNKNOWN y no se retransmite por
cuenta propia.

**Estado de esta entrega:** guía y candidato de autor, sujetos a CI y revisión
independiente. Sin prueba física del POCO, sesión real, activación, merge, deploy,
RS1000 ni envíos a Telegram realizados por este documento.
