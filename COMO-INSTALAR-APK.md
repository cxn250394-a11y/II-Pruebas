# APK de Centro de Alertas: descarga simple

## Después de que se haya publicado II-Pruebas en GitHub

1. Abre **https://github.com/cxn250394-a11y/II-Pruebas/actions** e inicia sesión en GitHub.
2. Comprueba que el flujo **Centro de Alertas | Pruebas y APK** esté verde.
3. Si el repositorio es **privado**, abre **https://github.com/cxn250394-a11y/II-Pruebas/releases** y selecciona la última versión preliminar **Centro de Alertas prueba #...**. Si permanece público, no se publican Releases y la APK queda dentro del ZIP de artefactos de GitHub Actions.
4. En **Assets**, pulsa directamente el archivo **Centro-de-Alertas-v0.1.apk**. No hay que descomprimir ningún ZIP.
5. Abre el archivo descargado en tu POCO F7 y sigue el aviso de instalación desde esa fuente. No deshabilites la protección de Android de forma global.
6. Abre Centro de Alertas y selecciona las aplicaciones permitidas. Concede el permiso de **Acceso a notificaciones** solo a la app que has instalado voluntariamente.

Si el flujo falla, todavía **NO** existe una APK válida de esta versión. Se deben revisar los errores en **Actions** antes de intentar instalar nada.

**Privacidad:** El permiso de notificaciones puede revelar el contenido de tus mensajes. Activa solo las fuentes que realmente quieres archivar. No recomendamos añadir notificaciones bancarias ni códigos de verificación.

**Actualizaciones de prueba:** Las APK compiladas de manera independiente pueden utilizar certificados debug diferentes, por lo que Android podría exigir desinstalar una versión anterior y perder el historial local. Una firma de actualización estable se incorporará en una fase posterior.

## Estado actual del proyecto

Este archivo describe la forma de instalación después de que un flujo de GitHub Actions finalice correctamente. No interpretes el archivo de instrucciones como evidencia de que la APK ya existe; confirma un run verde y el asset firmado antes de instalar.
