# CanalDeck: identidad, datos y variantes

Propuesta de autor conforme a Info main
`91993e46bb1c489626e683ed31cb02da465ae693`. Parte de PR #10
`6eb560ab5593e9c9c05e7a38d162fdd6b8553bdb`; se conservan congelados #8, #9,
#10 e Info #33. No modifica sus HEAD ni los dictámenes en curso.

**Ninguna acción del dueño es necesaria ahora.** La preparación se limita a
código, pruebas y evidencia de CI. No se solicita instalar, conectar una cuenta,
introducir credenciales o ejecutar un procedimiento físico mientras trabaja.

## Problema que resuelve el cambio

El laboratorio de #9 y #10 sustituyó `defaultConfig.applicationId` por
`mx.charly.canal.offline` y retiró Internet para toda la aplicación. Era válido
como rama aislada de ensayo, pero integrar ese tronco tal cual cambiaría la
identidad y conectividad de CanalDeck principal: P2-9A de la decisión de Torre.

Este candidato introduce la dimensión de flavors `mode`, con `normal` y
`offline`. La identidad base vuelve a `mx.charly.canal`, la que ya usaba PR #5
`8c385906476c627da51f040f899620088c968ce5`. Solo `offline` añade el sufijo
`.offline`. El namespace de clases sigue siendo `mx.charly.canal`; no es el
identificador que Android usa para aislar la instalación y sus datos.

La separación es funcional: los manifiestos, `BuildConfig` y paquetes resultantes
deben diferir según la matriz. No se añade otro motor Fake, cliente TDLib ni
colector de updates. Los controladores de referencias y fotos individuales no
cambian en este carril.

## Matriz de compilación que debe verificar CI

| Variante | applicationId | Internet | Inicio de sesión del cliente | Salida automática a Pick$ |
|---|---|---|---|---|
| `normalDebug` | `mx.charly.canal` | Presente | Capacidad del cliente base; no se ejerce en pruebas | `PICK_RELAY_REAL_ENABLED=false` |
| `normalRelease` | `mx.charly.canal` | Presente | Capacidad del cliente base; no se ejerce en pruebas | `PICK_RELAY_REAL_ENABLED=false` |
| `offlineDebug` | `mx.charly.canal.offline` | Ausente | Bloqueado por `OFFLINE_REHEARSAL_ONLY=true` | `PICK_RELAY_REAL_ENABLED=false` |
| `offlineRelease` | `mx.charly.canal.offline` | Ausente | Bloqueado por `OFFLINE_REHEARSAL_ONLY=true` | `PICK_RELAY_REAL_ENABLED=false` |

`OFFLINE_REHEARSAL_ONLY=false` pertenece a `normal`. No concede autorización para
usar una cuenta. Internet y el acceso base son capacidades de esa variante;
ninguna bandera de usuario abre el fusible compilado de relay.

El manifiesto común conserva backups desactivados y componentes privados. El
manifiesto de `normal` añade Internet. El de `offline` exige eliminarlo mediante
el merger, incluso si una dependencia lo añadiera. Las dos etiquetas distinguen
CanalDeck de CanalDeck Offline. No hay sufijo de identidad adicional entre debug
y release: cada flavor conserva su identidad en ambos tipos de build.

La evidencia del nuevo PR debe vincular el HEAD exacto con los manifiestos
fusionados y los APK de las cuatro variantes. Debe comprobar también que ambos
tipos de build mantienen relay OFF; un manifiesto debug correcto no acredita
por sí solo release.

El par debug de emulador permite comprobar coexistencia y datos sin cuenta.
Release unsigned se compila e inspecciona como artefacto; no se instala ni se
convierte una prueba debug en prueba de ejecución release. El recibo debe
distinguir esas dos clases de evidencia.

## Datos y continuidad de instalación

Los nombres de preferencias, SQLite, Vault y directorios TDLib pueden ser iguales
en el código compartido. Se resuelven dentro del directorio privado de cada
applicationId. No se configura `sharedUserId`, acceso de archivos entre variantes,
importación de sesiones, copia de bases, migración automática ni borrado.

Las pruebas de aislamiento deben usar dos instalaciones de emulador con fixtures
propios: comprobar paquetes, UID y directorios distintos; persistencia al reabrir
en cada variante; ausencia de acceso a datos privados de la otra. Solo deben
crear y limpiar fixtures de prueba. No usar una cuenta, credenciales o datos del
dueño para demostrar la separación.

Un resultado del emulador acredita esas condiciones de laboratorio. No demuestra
qué firma, versión, UID o datos tiene hoy la instalación del POCO, ni autoriza
copiar su sesión a la variante offline.

La continuidad de una actualización principal exige comprobar al menos paquete,
certificado de firma, versionCode y compatibilidad de esquema con la instalación
existente. La firma debug de CI es temporal y puede diferir entre ejecuciones.
Release no tiene firma de distribución configurada en esta propuesta. Un APK
release unsigned es evidencia de compilación, no una actualización instalable
aprobada para el teléfono.

Si firma o compatibilidad no coinciden, conservar los datos y detener la
actualización. No desinstalar, forzar downgrade, borrar reservas o restaurar una
base antigua para conseguir instalar. El retorno a otra versión también requiere
comprobar compatibilidad; el paquete correcto por sí solo no basta.

## Ruta mínima posterior, sin ejecutarla ahora

1. **Revisión de este delta.** Torre y revisor ajeno comprueban la separación de
   variantes por HEAD, CI y artefactos. El autor no cierra P2-9A por sí mismo.
   No integrar las ramas de laboratorio anteriores tal cual.
2. **Laboratorio offline, si el dueño decide probarlo después.** Solo el artefacto
   `offline` revisado, sin red ni cuenta, con firma e identidad comprobadas. Su
   instalación no sustituye la app principal ni traslada sus datos. La pantalla
   heredada y las pruebas automáticas conservan el alcance Fake indicado en #10.
   Esta preparación no solicita esa prueba física ahora.
3. **Observación futura con salida OFF.** Requiere un OK específico y una versión
   `normal` revisada que conserve la identidad, firma y datos de CanalDeck
   principal. Se reutiliza su único cliente/sesión existente; no iniciar una
   segunda cuenta en el laboratorio `.offline`. Verificar privadamente fuentes y
   destino elegidos por ID, permisos, baseline sin backlog, alcance de métricas,
   ventana CDMX y STOP. Que `normal` tenga Internet no concede ese OK.
4. **Prueba supervisada de referencia y foto individual.** Requiere revisión y
   otro OK literal que fije HEAD, cuenta/sesión, destino, fuentes, tipos de
   contenido, ventana, tasa, máximos, custodia y reversa. El fusible de esta entrega
   sigue OFF: un envío real necesita otro cambio revisable. No se concede aquí
   permiso para abrirlo.

Texto se prepara como referencia con atribución, fecha original y enlace válido.
Una foto con caption solo puede preparar un original permitido después de la
confirmación final del encabezado y la revalidación de permisos. Fano foto sola
requiere consentimiento específico y aviso de imagen sin clasificar; el control
de UI correspondiente sigue pendiente. UnicoRey sin tipster verificable sigue
HOLD. Las cuatro familias UnicoRey permanecen separadas de Mazdita y no amplían
la lista de doce tipsters.

**Álbumes vivos HOLD.** No hay prueba autoritativa de completitud en el carril
fijado; timeout, silencio, diez miembros o historial parcial no los liberan.
Separar flavors no modifica esa condición.

## Gates que esta separación no cierra

- **P2-9B:** borrar el encabezado final antes de la fase de foto puede dejar
  originales huérfanos. Se mantiene abierto y debe resolverse en el carril vivo
  antes de permitir esa prueba; los manifiestos no corrigen el protocolo ACK.
- **P2-9C:** el buffer temprano puede saturarse con tráfico ajeno. Sigue abierto;
  priorizar destino dedicado y demostrar el tratamiento acotado antes de activar.
- Los P2 de recaps/resultados, falsos negativos, paridad Kotlin/Python,
  atribución/familia, cobertura y procedencia de enlaces mantienen su propio
  criterio de cierre antes de automatización desatendida. Esta entrega no los
  declara resueltos.
- ACK Telegram real, recepción en Pick$, continuidad 24/7 y comportamiento físico
  POCO/HyperOS continúan sin verificar. Un PASS de build no es un PASS operativo.

La futura reversa conserva el procedimiento de
[activación y STOP de singles](SINGLE_RELAY_ACTIVACION_Y_REVERSA.md): cerrar salida,
revocar consentimiento de foto sola, invalidar pendientes como UNKNOWN, preservar
SQLite y no reintentar. No borra mensajes reales ni revierte apuestas.

## Recibo de entrega requerido

El nuevo PR, si aporta el cambio funcional y las pruebas, debe publicar HEAD,
base, lista de cambios, CI exacta, resultados por variante y tipo de build,
manifiestos, hashes de APK/ZIP separados y estado de firma. Las pruebas Android
deben identificar qué variantes se instalaron en el emulador y qué aislamiento
se comprobó. No reutilizar los 322/25 de #10 como validación del delta actual ni
anticipar PASS en este documento.

Código y evidencia de autor; revisión independiente pendiente. Sin pruebas de
cuenta, envíos reales, merge, deploy, RS1000 o apuestas. No se repiten M1 o G2.

`STAKE=0; BET_ENABLED=False; authorizes_market=false; E2E-A1=BLOCKED;
F3_NO_HABILITADO; T0=NO_SELLADO; T7=0h`.
