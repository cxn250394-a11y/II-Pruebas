# F1 · Firma y respaldo de Calendario Familiar

Estado: diseño y pruebas de seguridad de respaldo; **firma persistente y migración en dispositivo no verificadas**. Sin cambiar credenciales, applicationId ni secretos.

## Estado y alcance

La v0.1 usa `mx.charly.calendario`; la beta Terra usa `mx.charly.calendario.terra`. Son instalaciones independientes, no una actualización in situ. El código de respaldo CFAM1 es común a ambas bases inspeccionadas: exporta ambas agendas, categorías, colores, eventos y tamaño de letra, y cifra con AES-GCM y una clave derivada de contraseña con PBKDF2-HMAC-SHA256. El sistema de respaldo automático de Android está desactivado. Este mecanismo manual no sincroniza teléfonos.

La restauración valida el contenido y lo reemplaza dentro de una transacción SQLite. El respaldo sustituye **ambas** agendas del destino; conservar una copia verificable de cada instalación antes de restaurar. No desinstalar la instalación original hasta probar que los datos necesarios se restauraron. La única copia cifrada y su contraseña deben guardarse separadas y fuera del repositorio público. Si se pierde la contraseña no hay recuperación implementada.

## Contrato propuesto de distribución privada (pendiente de autorización)

1. El dueño conserva fuera del repositorio una clave de firma, con respaldo seguro, control de acceso y plan de recuperación; no pegar el material en chat ni subirlo a Git.
2. Solo tras autorización expresa, configurar secretos de Actions mediante su interfaz segura. El workflow de distribución futura fallará de forma cerrada si faltan y no imprimirá claves o contraseñas. No se configuran secretos en esta entrega.
3. El flujo de entrega verificará `applicationId`, versión y huella SHA-256 del certificado con `apksigner`, SHA-256 del APK interno y commit fuente. El certificado y `applicationId` deben coincidir con los de una instalación previa para actualizarla conservando sus datos; comprobarlos antes de recomendar una actualización. Las firmas debug efímeras de runners pueden variar y no son una firma persistente.
4. Si no coinciden certificado o paquete, mantener ambas apps instaladas y usar respaldo manual cifrado. No alterar ni eliminar datos de origen antes de verificar la restauración. La copia permanece bajo control del usuario; retención y borrado son decisiones del dueño, sin subida automática.

## Migración y recuperación

El esquema SQLite actual es versión 1 y `onUpgrade` rechaza versiones sin migración. Para cambios de esquema o formato, definir migración incremental y respaldo/versionado explícitos, probar upgrade, interrupción y recuperación de la copia previa; no borrar datos para que arranque. No se afirma que exista ya una migración de esquema.

El siguiente gate requiere prueba con datos sintéticos: exportar desde APK origen, verificar lectura y contraseña del archivo, importar en APK destino instalado por separado y comparar ambos perfiles, categorías, colores, texto, horas, notas y fuentes; ensayar clave errónea, archivo truncado, manipulado y referencias cruzadas sin modificar datos previos. Repetir en teléfono autorizado y comprobar la experiencia real antes de declarar aptitud en dispositivo. El backup no sustituye esa prueba. La restauración reemplaza las agendas del destino, por lo que antes debe exportarse y verificarse también su contenido si se desea conservarlo.
