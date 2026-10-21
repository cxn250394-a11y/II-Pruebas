# Auditoría inicial de cliente y kart — 2026-10-21

## Cliente Telegram

Consultados `nicegram/Nicegram-Android/README.md`, estructura raíz y `build.gradle` en main. El Gradle leído tiene blob `e1a87e9f5c0599822f7e8ae67ee2ead523463835`.

Hallazgo concreto: no es una app mínima a la que solo cambiar el nombre. La configuración incorpora módulos Nicegram, Compose/Hilt, Google/Huawei/Crashlytics y un repositorio de `trustwallet/wallet-core` que carga `githubPackagesUser`/`githubPackagesToken` de `local.properties`. No se leyeron valores de credenciales ni se descargaron sesiones. El README conserva instrucciones antiguas que no corresponden a todas las versiones de herramientas del build actual: no usarlo como receta comprobada de compilación.

Decisión de trabajo: comparar un fork del cliente Telegram Android con una extracción acotada de funciones útiles de Nicegram; priorizar mensajería, carpetas y multi-cuenta. Excluir de nuestra primera versión wallet/IA/backend comercial que el usuario no pidió. Auditar qué se puede retirar sin romper las funciones reales antes de importar. No copiar firmas ni claves de upstream. No se ha compilado ni creado un cliente propio aún.

Dependencias de aceptación: api_id/api_hash propios privados; consentimiento para login en el dispositivo; conservar licencia y procedencia; probar mensajería, multimedia, notificaciones, reconexión y cierre de sesión. No activar ni enviar mensajes de prueba reales sin autorización. `Centro de Alertas` no resuelve esas puertas.

Fuentes: https://github.com/nicegram/Nicegram-Android ; https://core.telegram.org/api/obtaining_api_id ; https://core.telegram.org/api/terms

## Recuperación BrisaKart / Sundrift

Recuperados desde archivos previos del usuario:
- `BrisaKart-proyecto-completo.zip`, 2,904,265 bytes.
- `sundrift_kart_64_android_apk_project.zip`, 235,540 bytes.

La copia se inspeccionó en un contenedor de desarrollo aislado, no en RS1000 ni en el ordenador del usuario. No se ha importado todavía el archivo completo en este repositorio.

### Ejecución repetida en esta sesión

- `python -m unittest discover -s tests -v`: **38 pruebas PASS**, 0 fallos.
- `python tools/replay_check.py`: **4/4 casos PASS**, 1,400 pasos por pista, 8 corredores, campos x/z/speed/progress/rank/lap; error absoluto máximo `1.0755911716842093e-09`, tolerancia `1e-06`. Python 3.13.5 / Node v22.16.0.
- `python tools/browser_checks.py`: **NO COMPLETADA**, timeout de arranque. Diagnóstico independiente: Chromium del contenedor no creó contexto WebGL con la configuración SwiftShader utilizada (`getContext('webgl') == false`); el juego muestra el aviso de falta de aceleración. No se presenta como pase visual ni como medición Android. Los informes visuales de la entrega anterior no son resultados de hoy.

### Estado del código recuperado

La simulación Python se transpila a JavaScript; el renderizado es WebGL dentro de WebView. La APK experimental anterior usó un ensamblador binario específico; no es la reconstrucción por SDK oficial que necesitamos aceptar. Existe un script alternativo `tools/build_android_sdk.py` (javac/d8/aapt2/zipalign/apksigner), pendiente de ejecución aquí.

La alfa no acredita calidad similar a Beach Buggy ni 60 FPS. Preserva una base de físicas, objetos, rivales y copas, pero faltan la reconstrucción Android validada, pruebas físicas de control/inclinación, mejora de presentación/animación/cámara y aceptación del usuario de una carrera completa. No migrar silenciosamente a GDScript llamándolo Python.

### Siguiente tramo verificable

Importar fuente revisada sin claves/artefactos viejos como entregables nuevos; construir con SDK oficial; ejecutar e inspeccionar carrera completa en Android; corregir manejo/cámara/colisiones y arte antes de añadir volumen de contenido. Las mediciones de 60 FPS requieren el POCO, resolución y estado térmico identificados. El objetivo funcional y visual cercano al referente se mantiene, con recursos propios.
