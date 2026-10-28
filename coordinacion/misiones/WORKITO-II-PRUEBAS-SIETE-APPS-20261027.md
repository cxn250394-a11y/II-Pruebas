# WORKITO · MISIÓN INTEGRAL DE LAS SIETE APPS ANDROID
**Control:** WORKITO-II-PRUEBAS-20261027 · **Fecha:** 27-oct-2026
**Ejecutor principal:** ChatGPT Work ("Workito"), con ejecución real de código/Android/CI y uso de subagentes solo si están disponibles.
**Coordinador y auditor externo:** ChatGPT de esta conversación; no sustituir una auditoría independiente por autoverificación del ejecutor.
**Repositorio autorizado ÚNICO:** https://github.com/cxn250394-a11y/II-Pruebas · repositorio PÚBLICO.
**Main comprobado al emitir este relevo:** 12cc1c7ea0d2717190ac9a2eed2a0bd9bc4ed4e1.
**Esta rama:** mission/workito-siete-android-20261027 es exclusivamente de coordinación. No convertirla en una rama de producto.

## 0. Mandato y límites

El usuario quiere **las siete apps funcionales y APK instalables**, no otra investigación interminable ni solo documentos: 1) CanalDeck tipo Nicegram; 2) Calendario Familiar; 3) Sandrift Karts; 4) StatsDeck; 5) OddsDeck; 6) AudioDeck; 7) Centro de Alertas. Workito es el desarrollador principal de todas; ejecutar por etapas, continuar con lo siguiente cuando una etapa alcanza su puerta, y dejar commits verificables de cada sesión.

Ninguna sesión puede prometer terminar mágicamente siete productos complejos en una sola ejecución. Haz **todo el trabajo posible dentro de la sesión**, documenta PARCIAL_RECUPERABLE con HEAD, pruebas y bloqueo cuando corresponda, y deja relevo preciso. Si una dependencia necesita autorización del dueño o un dispositivo real, avanza otros componentes independientes sin inventar resultados.

**Prohibido sin nueva instrucción expresa:** merge, push a main, Release pública, deploy, apuesta real, compra, reenvío a Telegram o Pick$, lectura de cuentas/datos privados del dueño, usar credenciales reales, tocar otros repositorios, Super Bet 3.0, RS1000, Centro de Mando o Info-para-SB-G-CGPT. No instalar conectores ni cambiar visibilidad del repositorio o secretos sin aprobación. No subir sesiones TDLib, capturas privadas, historiales de apuestas, datos médicos familiares ni keystores al repositorio público, artefactos públicos o logs. Datos sintéticos en CI.

Funciones "Premium" PROPIAS sí: mejores herramientas, organización, UX, estadísticas y análisis implementados legítimamente. NO desbloquear suscripciones, DRM, bibliotecas comerciales, APIs privadas ni funciones de servidor que requieran pagar. Diseños, recursos y personajes originales y sin copiar código/arte ajenos.

## 1. Fuentes que DEBES leer antes de editar (P0)

1. Mega misión Terra original: coordinacion/misiones/TERRA61-II-PRUEBAS-MEGA-20261027.md en rama mission/terra61-mega-control-20261027, commit aa9b0f1a6876f70c03d4030ad4ab5cf6e106e44d, Draft PR #23.
2. Inventario independiente Terra F0: coordinacion/evidencia/TERRA61-ESTADO-REAL.md en PR #28, HEAD 25a03fd05991347560d4d27a0c266a7bc04ac0aa.
3. Comentarios de AUDITORÍA EXTERNA recientes en PR #26, #27, #28 y #30. Son prioridades P1/P2 explícitas que deben convertirse en tests y fixes.
4. **Inspeccionar por GitHub los HEAD, los diffs, el código, los reportes, las APK y los workflows vigentes**; no confiar en resúmenes del productor ni en un test verde de otra rama.
5. Confirmar que main sigue en el commit esperado antes de trabajar. Inspeccionar permisos Android, claves/secretos y dependencias. No mover main.

| Producto | HEAD fuente verificado 27-oct-2026 | PR y CI conocidos | Punto crítico |
| --- | --- | --- | --- |
| CanalDeck Personal: cadena #15 → #29 → #30 | #30: 14d59e7aed9d2de8c83bebbca82256afdbe51db4 | #30 Draft; CI 40467159058 verde | **P1**: PendingIntent de aviso trae chatId pero no cuenta de origen; puede abrir otro chat tras cambio de sesión. P1 FLAG_SECURE/compositor sin cerrar. |
| Calendario Familiar | #26: 0c8a61e69ac16ce11978fba94ab5ca247817e293 | #26 Draft; CI 40468024200 verde | Imagen icono de fecha **desactivada** por riesgo de retornar HOME. App ID separado, restaure de v0.1 no comprobado entre dos APK; accesibilidad real pendiente. |
| Sandrift Karts | #27: 8b9f037b257b44ea8baf8156bd571bb3b7984bf5 | #27 Draft; CI 40463857960 verde | PAUSA táctil/reanudar comprobadas, **BACK no pausa**; fondo/rotación solo liveness, FPS POCO no medidos. |
| StatsDeck | #18: 6377082f2e5f6a01ad0a6faec940eba912f58015 | CI 40025268638 verde | Fuente OpenLigaDB, seguidos, cambios observados; córners/tarjetas y push vivo sin proveedor comprobado. |
| OddsDeck | alfa #3: 98cff714738cbff71f67a3b4eac24dc7fe597ac5 | #21 es MISIÓN de Argon, no implementación 0.2; alfa CI 39684481482 | Comparador y EV/CLV/ROI requieren evidencia, caducidad y normalización estricta; si Argon no entregó cambios, implementa desde alfa. |
| AudioDeck | #16: 368438fce0a4daced729ceb4907eb4810e3520f5 | CI 40021310468 verde | Radio/favoritos/timer, USB WAV experimental. NO bit-perfect certificado; TIDAL/Qobuz bajo autorización. |
| Centro de Alertas LAB | #1: 123c355c51a730381c6c5fbc7a887093426b0a20 | CI 39632897569 verde; no prueba física | HyperOS, dual Telegram, títulos duplicados, privacidad y retraso de entrega no verificados. |

**Otras ramas, NO mezclar a ciegas:** #24/#25 fueron intentos preliminares de ChatGPT; #19 código Grok, #20 QA estricta, #22 Calendar v0.1, #5–#13 puente Pick$ congelado. Los números son referencias, no autorización para mezclar esos cambios.

## 2. Forma de ejecución obligatoria

**E0 — Prevuelo de evidencia, breve:** revisar fuente exacta, CI y bug reproducible; crear coordinacion/evidencia/WORKITO-ESTADO-Y-DECISIONES.md en rama SOLO de coordinación, con matriz por app: objetivo, HEAD, bloqueo P0/P1/P2, estado de APK, evidencia, prioridad, pruebas futuras y contrato de firma. No gastar toda la sesión en inventarios: empieza código inmediatamente después.

**E1 — Un producto = una rama + un Draft PR nuevo**, partiendo de su HEAD verificado, no desde main cuando el código vive en PR apilado. Mantener bases PR coherentes. No force push ni modificar la rama del autor Terra/Grok/Argon. Si un producto requiere biblioteca común, documentar diffs cruzados y prueba de regresión; no acoplar apps por defecto.

**E2 — Pruebas que detecten defectos**, comenzar con casos rojos donde sea práctico, después reparar y demostrar verde. No borrar aserciones, pruebas negativas, checks de permisos o telemetría para lograr verde. Debe haber UI emulada y screenshots reales (sin overlays) y prueba de conservación de datos con force-stop/restart.

**E3 — APK entregable:** Gradle Android build, JUnit, Android Lint, emulador con navegación real y ausencia de crash. Firmar debug para laboratorio (apksigner verify), SHA-256 del APK INTERNO, SOURCE-COMMIT exacto, APK individual fácil de instalar, tests y capturas en artefactos separados. Comprobar el archivo descargado y su hash, no atribuir la firma de una versión a otra. No confundir ZIP artifact con la propia APK.

**E4 — Persistencia y compatibilidad:** para actualizar una APK debe coincidir applicationId y certificado; la firma debug de GitHub Actions suele variar entre runners. Hasta contar con firma privada estable aprobada, instalar paralelo con ID diferente y backup/restore cifrado testeado. NO desinstalar la instalación de mamá ni otras con datos. Nunca publicar ni pasar en chat la clave privada. Planificar secretos/actions, pero configuración solo tras aprobación del dueño.

**E5 — Auditar y entregar por lote:** al finalizar cada módulo documentar qué se hizo, qué NO, bugs pendientes, alcance exacto del CI, instrucciones de instalación/probar y siguiente tarea. Conservar todos los Draft PR abiertos. Cuando un módulo está bloqueado por teléfono/acceso a plataforma, marca ese gate y avanza otro. El usuario quiere resultados reales, no planes sin código.

## 3. Orden de trabajo prioritario para Workito

### C1 — CanalDeck Personal: seguridad P1 primero (INICIAR AQUÍ)
Base: PR #30 HEAD 14d59e7aed9d2de8c83bebbca82256afdbe51db4. Objetivo de primer PR: corregir el clic sobre notificación antigua tras cambiar de cuenta y el enrutamiento de sesiones, con test de dos cuentas sintéticas, mismo chatId y PendingIntent obsoleto; cerrar también colisiones de identidad/reutilización de PendingIntent (URI/requestCode/flags) y limpieza segura de avisos de la cuenta anterior. Un aviso nunca debe abrir un chat de otra sesión.

Revisar C1b PRIVACIDAD antes de declarar APTO: FLAG_SECURE ya activo ANTES del primer frame sensible al cambiar cuenta, diálogo, backup, búsqueda, OCR y media que cambian de política; sin extraer contenido protegido. Metadatos desconocidos ⇒ texto oculto por defecto. Probar rotación, background, muerte/reinicio, apertura en cola, transición entre cuentas; usar mocks sintéticos y dispositivo de laboratorio cuando sea posible. El demo no sustituye cuentas reales autorizadas. Prohibido conectar Pick$.

Mejorar mensajes/notificaciones solo cuando no provoquen filtración: separar tarjeta «Conexión activa» del contenido de mensaje; organización carpetas, favoritos y backup de usuario deben persistir por identidad. Sin asumir que controlamos entrega del sistema HyperOS. El usuario quiere un Nicegram PROPIO, no un espejo parcial ni un bypass de Telegram Premium de servidor.

**Gate C1:** P1 cuenta-cruzada demostrado RED antes y PASS después, sin leaks, APK/CI del mismo HEAD y Auditor externo; NO pedir iniciar sesión real hasta pasar las pruebas críticas.

### C2 — Calendario Familiar: datos de Mamá y UX
Base: PR #26 HEAD 0c8a61e69ac16ce11978fba94ab5ca247817e293. Mantener dos agendas Yo/Mamá, días totalmente coloreados, **tarjetitas de evento visibles, armoniosas y con texto**, paletas pastel/neón, categorías IMSS 21/ISSSTE Balbuena editables, vista día/semana letra grande, objetivos de toque ≥48dp y TalkBack. No llenar días con eventos ficticios en uso real.

Primero probar exportar backup de v0.1 e importar a APK v0.2 instalada en paralelo, y comprobar colores, citas, perfil, categorías y notas; fallo de contraseña/truncado/referencia cruzada no destruye datos. Resolver icono que muestra el día del mes SIN cerrar inesperadamente la app (alternativa launcher/shortcut segura si los aliases dinámicos no funcionan en HyperOS); la v0.2 de Terra lo dejó genérico, no declarar cerrado. Permitir deshacer pintura por lote de forma fiable. La vista mensual no debe quedar microscópica para una persona de 70 años. Capturas synthetic con fuentes grandes.

**Gate C2:** instalación cohabitante, restore real entre dos APK en emulador, TalkBack/escala en laboratorio, no crash de icono, y prueba con persona/dispositivo autorizados solo después.

### C3 — Sandrift Karts: superar problemas reales
Base: PR #27 HEAD 8b9f037b257b44ea8baf8156bd571bb3b7984bf5, comparar contra Grok #19 y QA #20; no quitar pruebas buenas. Corregir BACK (actualmente NO pausa); confirmar que PAUSA, CONTINUAR, cambio de fondo, rotación y ciclo de carrera realmente preservan posición/velocidad/tiempo/objetos o se reinician claramente (no solo PID vivo). Instrumentación solo en debug. Emulador sin alertas de pantalla completa bloqueando controles.

Añadir mejoras originales de manejo, jugabilidad, circuitos, rivales, efectos, audio y menú con recursos originales/licencias claras, sin copiar Beach Buggy Racing ni Mario Kart 64. FPS 60 es OBJETIVO NO VALIDADO: medir promedio/p95/1% low y temperatura en el POCO F7 cuando el dueño pueda probar. No afirmar AAA ni estabilidad de 60 FPS por SwiftShader.

**Gate C3:** menú→carrera, pausa y reanudación por botón y BACK, background/rotation, dos aspectos, screenshots y hashes, APK jugable en emulador sin falsos PASS.

### C4 — StatsDeck
Base: PR #18. Extender cobertura, búsqueda, cuadros de liga/equipo, favoritos, historia comparativa y avisos basados en fuente con reloj. Catalogar datos disponibles por liga/evento y licencia. Córners, tarjetas, tiros, alineaciones, xG y eventos live solo cuando exista fuente legítima verificable; **N/D ≠ 0**. Definir actualizaciones observadas vs confirmación, expiración y latencia. Sin APIs de pago obligatorias, sin datos inventados. APK con datos DEMO rotulados y fuente real cuando esté disponible.

### C5 — OddsDeck
Base alfa PR #3, consultar PR #21 misión Argon y verificar si entregó ZIP/branch nuevos; si no, NO esperar a Argon para programar. Herramientas propias OddsJam/Action/BetGPT: mercado exacto con evento/período/línea/reglas/selección, fuente y hora, histórico de cada cuota, TTL, mejor cuota de observaciones vigentes, calculadoras no-vig justificadas, EV con probabilidad aportada y documentada, CLV honesto y tracking de boletos/ROI por estado y procedencia. CSV/OCR → borrador editable explícitamente confirmado; sin apostar ni enviar a SB/RS1000. Premium propio ≠ acceso a datos de pago ajenos.

### C6 — AudioDeck
Base PR #16. Mejorar reproducción local Hi-Res, FLAC/WAV/formatos soportados realmente, biblioteca, carátulas, listas, radio/estaciones favoritas, temporizador, UX y modos NORMAL/EQ/USB bit-perfect diferenciados. No confundir un indicador de frecuencia con medir salida de bits: prueba en DAC físico para certificar. TIDAL/Qobuz SOLO SDK y derechos/plan oficial autorizados; si falta integración, marcar BLOCKED y entregar funciones locales/radio completas. No intentar desbloquear streaming Premium/DRM.

### C7 — Centro de Alertas
Base PR #1. Mejorar agrupación de Telegram/Nicegram, filtrado de duplicados por paquetes y perfil dual, canal vs mensaje, prioridad y cronología. Exigir consentimiento Android NotificationListenerService, almacenamiento mínimo privado, borrado y no subir mensajes a servidores. Diagnóstico de timestamps: recibido por app vs notificación que publica Android vs presentación por sistema; no afirmar tiempos de servidor desconocidos ni corregir retrasos de HyperOS sin prueba. Notificaciones de goles/córners/tarjetas solo reflejan hechos recibidos por fuentes compatibles, no inventar mensajes nunca emitidos.

## 4. Requisitos comunes de aceptación

| Etapa | Prueba |
| --- | --- |
| P0 | Branch/HEAD y límites, PR matriz de estado actualizado, evidencia de bug reproducible |
| P1 | Código funcional sin acceso indebido, pruebas críticas negativas, persistencia validada |
| P2 | CI JUnit/Lint/assemble del HEAD exacto + firma/hash APK descargada |
| P3 | Emulador con navegación REAL, reinicio, capturas sin banners/modal ajenos, prueba de privacidad y 2 aspectos si aplican |
| P4 | APK individual para usuario con guía sencilla y migración de datos segura |
| P5 | Validación POCO F7/HyperOS 3, teléfono de mamá o DAC SOLO si hay acceso legítimo y permiso del usuario; distinguir NOT_RUN de FAIL |
| P6 | Dictamen de auditor externo antes de recomendar merge o sustitución de app |

Por cada PR incluir tabla:
Producto | branch+HEAD | base | cambios concretos | tests/lint | emulador+capturas | APK bytes + SHA-256 + cert | riesgos P0/P1/P2 | aceptación | siguiente gate.

Si la APK no existe, decir NO_APK y explicar el error. Si Android Lint es verde, no inferir seguridad total. Nunca cerrar un P1 por tener más tests sin reproducir específicamente el defecto. Un PR Draft puede seguir con código válido y bloqueos activos.

## 5. Primera sesión: trabajo OBLIGATORIO, no solo inventario

1. Leer la mega misión Terra #23, F0 #28, comentarios de auditoría en #26/#27/#30 y este archivo. Confirmar main/HEADs y elegir rama según producto.
2. Publicar matriz WORKITO-ESTADO (reutilizar información ya disponible, NO rehacer largas auditorías históricas).
3. **Atacar el P1 de CanalDeck de avisos de cuentas cruzadas inmediatamente**: codificar y escribir test rojo/verde. Cerrar P1 solo con evidencia correcta. Si el cierre exige cambios mayores, dejar PR funcional acotado con resto P1 pendiente.
4. Con el tiempo disponible, continuar C2 (backup/install entre APK del calendario) y C3 (BACK de Sandrift), en PRs distintos. No tocar código de Argon, Grok o Terra en sus ramas.
5. Construir APK, ejecutar CI y revisar artefactos; entregar pruebas concretas y estados, sin prometer trabajo futuro como si se ejecutara automáticamente.
6. Continuar con C4–C7 cuando estén los primeros gates seguros. Avanzar de verdad en todas las apps a través de sesiones/etapas, sin modificar main. Si una fase queda bloqueada, tomar el siguiente producto y documentar.

## 6. Relevo y cierre obligatorio

Entregar un resumen en español claro, con links de cada Draft PR, commits exactos, CI status, enlace a **cada APK descargable individual** y capturas/test artifacts. Priorizar un diagnóstico sincero de lo que NO pasó. Si quedan más de siete tareas por ejecutar, adjuntar orden concreto de siguiente lote y qué no debe repetirse. Si una sesión termina antes, marcar PARCIAL_RECUPERABLE, no simular labor en segundo plano.

**NO MERGE, NO RELEASE, NO DEPLOY, NO DATOS PRIVADOS, NO APUESTAS, NO SUBSCRIPCIONES AJENAS, NO PRODUCCIÓN**.
