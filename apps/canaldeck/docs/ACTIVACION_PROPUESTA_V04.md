# Propuesta para Torre — activación futura, NO autorizada

Esta propuesta no abre la puerta compilada ni autoriza una prueba real. Primero deben aprobarse GATE-B/C/D de la misión: CI del HEAD exacto, revisión ajena al autor y prueba del propietario con v0.3 en POCO/HyperOS.

1. Validar localmente cuenta, destino y fuentes reales elegidos por Charly; conservar los IDs y credenciales solo en el dispositivo. Documentar por privado la identidad de Fano y de cada familia Unico; distinguir Mazdita.
2. Resolver la evidencia de álbum completo en el proveedor real y el manejo de ACK asíncronos/UNKNOWN. Probar revocación de permisos y cierres de proceso con Fake antes de usar cuenta real. No abrir la puerta real como sustituto de esa revisión.
3. Crear un nuevo PR específico de activación. La salida permanecerá OFF para toda instalación sin permiso propio; no existirán activación remota, credenciales en CI ni cambio de permisos del servidor.
4. Proponer primero una ventana breve a un canal de prueba elegido por Charly. Como límites iniciales para revisión: hasta un evento por minuto y diez eventos en la ventana; un álbum cuenta como un evento, aunque implique varios mensajes originales. Registrar separadamente mensajes/eventos y coste cero. Esta tasa no está aprobada ni activa.
5. Para el canal Pick$ definitivo, Charly deberá dar otro OK con cuenta/destino/fuentes exactos, periodo, límite de tasa, permiso de texto/fotos/álbum, custodia y rollback. Revisar antes el solapamiento con el lector existente; esta misión no lo toca ni lo apaga.
6. Antes del primer evento, fijar baseline actual sin replay de históricos. Ante UNKNOWN o STOP, apagar salida; no reintentar, vaciar checkpoints o retransmitir backlog. Rollback: interruptor OFF, detener el conector móvil si es necesario y conservar ledger/UNKNOWN. Toda actuación física adicional requiere su OK correspondiente.

Formato de OK para completar **solo después** de las revisiones, con valores reales mantenidos en el registro privado del dueño:

> BORRADOR, NO OTORGADO: autorizo una prueba móvil limitada al HEAD ratificado [HEAD], cuenta local [identidad], destino [chat confirmado], fuentes y familias [identidades exactas], durante [inicio-fin CDMX], con [tasa/eventos máximos], texto y solo medios que Telegram permita por cada mensaje al entregar, coste y apuestas cero. Baseline nuevo, sin históricos. Ante permiso perdido, error o UNKNOWN: STOP sin reintento. Rollback limitado a [pasos expresos], conservando checkpoints. Esto no autoriza modificar Pick$ 0.2.0, Ferrari, RS1000, merge o deploy.

Hasta obtener ese OK y el nuevo PR ratificado: sent_real_messages=0 en las pruebas Fake; activación real BLOQUEADA.
