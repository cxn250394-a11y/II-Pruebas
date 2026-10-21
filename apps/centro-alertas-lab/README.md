# Centro de Alertas LAB 0.2

APK independiente (`com.charly.alertaslab`) para probar organización y radar local sin reemplazar Centro de Alertas v0.1. El proyecto original no se modifica.

## Arquitectura

`LabListener` (opt-in por fuente, cola acotada, no resúmenes de grupo) → `NotificationParts` (MessagingStyle o texto entregado por Android) → `LabStore` (SQLite privado, idempotencia por evento, 2,000 registros/14 días) → `LabActivity` (inicio, bandeja, canales, radar, ajustes).

`shared/alerts-core` aporta reglas puras, separación por paquete/perfil, orden cronológico y clasificación conservadora. No tiene APIs ni credenciales. Los identificadores de evento incorporan instante y ocurrencia para no borrar dos mensajes iguales legítimos. Aun así, una app que no exponga un ID real o tiempo distinguible puede producir ambigüedad; no se promete dedupe perfecto entre clientes.

## Interfaz y límites

- Inicio con tres pasos y estados distintos: fuentes desactivadas, permiso pendiente, lector sin conexión, espera de aviso nuevo y captura pausada.
- Demo ficticio en memoria: sin permiso, sin escritura en DB ni efecto en cuentas.
- Búsqueda en hasta 2,000 registros, nuevos primero; estados leído/favorito exclusivamente locales.
- Canales agrupados por título, aplicación y perfil. No identifica cuentas; no fusiona automáticamente Nicegram y Telegram. Chats homónimos pueden agruparse.
- Carpetas locales con creación y asignación. No modifica carpetas de Telegram.
- Radar: **menciones**, no marcadores oficiales, probabilidades o eventos verificados. Filtra publicidad/predicciones frecuentes y mantiene VAR/correcciones diferenciados. Los formatos nuevos pueden clasificarse mal o no clasificarse.
- No notificaciones propias, no cancelación de avisos originales, no importación de históricos activos ni lectura de chats completos, no cuenta Telegram.
- Android/HyperOS decide si entrega notificaciones de aplicaciones duales y qué texto permite ver.
- Textos privados en almacenamiento privado, sin cifrado adicional de filas; respaldo desactivado, sin INTERNET ni permisos runtime. La redacción OTP es heurística, no una garantía.
- Pausar/revocar una fuente impide nuevas inserciones; borrar historial pausa la captura. Borrar no cambia otras apps. No existe exportación.

## Compilación y evidencia

Java 17, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, SDK 35. Desde esta carpeta:

```bash
gradle --no-daemon :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Pruebas puras sin SDK desde la raíz del repositorio:

```bash
bash scripts/test-lab-core.sh
python3 scripts/check-lab.py
```

CI en `.github/workflows/alertas-lab.yml`; adjunta APK debug firmada, SHA-256, verificación y commit de origen; no publica Releases ni despliega. Las pruebas Android JVM utilizan Robolectric (API 28 + smoke API 35), **no un POCO físico**. Consulta el resultado real de la ejecución del PR; este README no afirma que una ejecución futura haya pasado.

La clave debug del runner no se conserva ni se publica. La primera APK LAB coexiste con v0.1; para actualizaciones LAB futuras sigue pendiente configurar firma persistente privada.
