# TERRA 6.1 · MEGA MISIÓN DE PRODUCTOS ANDROID
Fecha: 2026-10-27 · Identificador: TERRA61-II-PRUEBAS-20261027

Repositorio autorizado: cxn250394-a11y/II-Pruebas (PÚBLICO). Coordinador: ChatGPT Work / Terra 6.1 o agente autorizado por el dueño. Revisión final: agente distinto del productor.
HEAD inmóvil de main al crear esta misión: 12cc1c7ea0d2717190ac9a2eed2a0bd9bc4ed4e1.

**META:** evolucionar siete aplicaciones hacia APK instalables y usables, sin fingir paridad comercial, datos Premium ajenos ni resultados que no estén medidos. Cada producto avanza en rama y Draft PR propios. No hacer merge, deploy, Releases públicas, reenvíos reales, apuestas, compras, acceso a cuentas, cambios en Super Bet, RS1000, Centro de Mando ni a otros repositorios.

## 1. Inventario verificable (cada PR es independiente de main)

| Línea | HEAD comprobado de su rama | PR | Estado inicial |
| --- | --- | --- | --- |
| Centro de Alertas LAB 0.2 | 123c355c51a730381c6c5fbc7a887093426b0a20 | #1 | Lector local; prueba real HyperOS pendiente |
| CanalDeck TDLib base | 613fb47d62793daaf28277fac58d3da764f0b865 | #4 | Cliente alternativo con login consentido |
| CanalDeck Personal 0.3.2 | 72db3f988523d21174fc8e9afd7856ad38a70d15 | #15 (base #14) | Carpetas, copia cifrada, OCR local; firma no estable |
| CanalDeck ↔ Pick$ | cadena #5 a #13 | #5–#13 | OFF / CONGELADO: no activar puentes |
| AudioDeck 0.2 | 368438fce0a4daced729ceb4907eb4810e3520f5 | #16 | Radio, timer, audio local, USB WAV experimental |
| StatsDeck 0.2 | 6377082f2e5f6a01ad0a6faec940eba912f58015 | #18 | Seguidos, historial de resultados observados, CSV |
| OddsDeck alfa | 98cff714738cbff71f67a3b4eac24dc7fe597ac5 | #3 | Cuotas manuales, CSV, EV/no-vig/ROI |
| OddsDeck misión Argon | 1df45ded714793d83cbd06d8903673578a69ab0f | #21 | Misión publicada, NO equivale a v0.2 terminada |
| Sandrift Karts Grok 0.2 | f6118bf9b449a760664f3b5d621735f273c00ddd | #19 | APK/JUnit verde, QA de controles/pausa no ratificada |
| Sandrift QA reforzada | 1c40a4ad5263033005ee0b1b90e9c5925819bda6 | #20 | CI emulador rojo, investigar causa real |
| Calendario Familiar 0.1 | 208b705cc5708bbb16d32b44ab66e8c4d87dceb7 | #22 | APK emulada, 2 perfiles, días coloreados y citas |

Todos son Draft PR independientes. Consultar HEAD y check runs actuales antes de trabajar; no heredar PASS de un commit diferente, no fusionar todas las ramas. Los títulos de PR no son una autoridad sobre la evidencia.

## 2. Reglas no negociables

- Cada cambio incluye: base SHA, rama de autor, HEAD exacto, productor, diff limitado, pruebas (PASS/FAIL/no ejecutadas), Lint, APK descargable, firma y SHA-256 **del APK interno**, capturas de la interfaz real y restricciones que sigan abiertas.
- Clasificar cada puerta: NO_INICIADO, CÓDIGO_SIN_VALIDAR, CI_APTO_EMULADOR, APTO_CON_LIMITES_OFFLINE, BLOQUEADO o APTO_DISPOSITIVO. Solo APTO_DISPOSITIVO con prueba del teléfono real.
- Nunca confundir una recomendación, etiqueta OCR, cuota anotada o cambio de score con evento confirmado, mensaje entregado, apuesta realizada o EV probado.
- Repositorio PÚBLICO: solo datos sintéticos. No subir sesiones, SMS, fotos de tu mamá, historial médico, mensajes reales, API keys, contraseñas, tokens, keystores o cuotas privadas.
- Evitar cambios no autorizados a los proyectos de apuestas activos, Torre, Info o RS1000. Nadie manda mensajes a chats de usuarios desde CI ni usa cuentas de Telegram reales.
- Firmas debug de runners distintos no son compatibles entre APK. **Mismo applicationId y mismo certificado** para actualizar manteniendo datos; si no, paquete aparte + exportación/importación cifrada probada. No recomendar desinstalar antes de un respaldo verificable.
- No construir otro motor que duplique uno existente sin una prueba de necesidad. No declarar equivalencia legal, funcional o de rendimiento con apps comerciales.

## 3. Plan por fases (sin prometer terminar todo en una sola ejecución)

### F0. Auditoría de situación real — PR propio de evidencia
Leer los README, AndroidManifest, workflows, diffs y últimas dos ejecuciones de cada línea. Escribir coordinacion/evidencia/TERRA61-ESTADO-REAL.md con tabla de HEAD, producto, problema P0/P1/P2, prueba exacta, firma, status y siguiente gate. Reconciliar demos, datos reales y pruebas incompletas. Confirmar que main permanece inalterado.

### F1. Firma persistente, privacidad y respaldo para todas las APK
Diseñar distribución privada verificable con GitHub Actions Secrets, **sin pedir credenciales en chat o PR**. No crear ni modificar secrets sin aprobación explícita del dueño. Ensayar copia y restauración local cifrada antes de cualquier cambio de firma o ID. Plan de migración de DB, recuperación en error, retención y borrado. La compartición entre dos teléfonos no existe mágicamente: modo backup manual ahora; sync solo tras autorización, privacidad y diseño separado.

### F2. CALENDARIO FAMILIAR — utilidad y accesibilidad primero
Base PR #22, no tocar su HEAD original. Trabajar en rama nueva desde 208b705c.
- Días pintados al 100% de su celda. Eventos mostrados como **tarjetitas visibles, discretas, armoniosas**, etiqueta de categoría y texto parcial. No son bloques gigantes.
- Vista día con textos completos, horario/notas; modo semanal opcional para persona de unos 70 años con vista cansada. Tamaños de fuente grandes, contraste de texto mínimo AA cuando sea posible, dianas táctiles al menos 48dp y lectura TalkBack.
- Categorías con color propio, ejemplos IMSS 21/ISSSTE Balbuena, pero nada de citas médicas reales en tests o pantallas de evidencia.
- Pintar por lote días de oficina/trabajo/descanso con selección previa, rango acotado, confirmación y posibilidad de deshacer. No asumir días reales del turno del usuario.
- 24 colores pastel/vivos/neón y compatibilidad con icono de fecha 1–31. Probar cambio de icono en HyperOS; su caché puede retrasarlo. No pedir exact alarms solo por icono.
- Conservación de datos por actualización real o vía backup. No aprobar solo porque instala y muestra calendario vacío.

### F3. CANALDECK PERSONAL y ALERTAS, sin conectar puente Pick$
Partir de PR #15 y PR #1 en ramas distintas.
- Guardar favoritos/carpetas/orden entre actualizaciones, backup cifrado y firma estable; aislar cuentas/chats por ID. Revisar notificaciones y vista previa del mensaje (NO confundir “Conexión activa” con mensaje).
- Enlaces t.me públicos dentro del cliente mediante TDLib. Privados solamente cuando la cuenta los puede resolver legítimamente; no saltarse restricciones de guardado/captura. Screenshot configurable solo de chats no protegidos. OCR local bajo gesto del usuario, resultados candidatos no apuestas verificadas.
- Diagnóstico en POCO F7 / HyperOS 3: mensaje → proceso Telegram → NotificationListener → banner, Wi-Fi vs datos, pantalla bloqueada, clientes duales/Nicegram. No prometer que una app controla cuándo el sistema entrega una notificación.
- FUSIBLE OFF: no integrar PR #5–#13, Pick$, Ferrari ni reenvíos hasta misión separada.

### F4. STATSDECK + ODDSDECK, datos con fuente y reloj
StatsDeck base PR #18: ampliar ligas mediante fuentes abiertas/autorizadas, registrar cobertura por tipo de evento, timestamp, latencia, “N/D” si faltan córners/tarjetas/remates. Evitar 0 inventado y evitar llamar gol confirmado a mero cambio de marcador.
OddsDeck alfa base PR #3; misión detallada PR #21: comparador estricto por evento/mercado/periodo/línea/reglas/selección; precios más recientes y vigentes únicamente, historial por casa, EV condicionado a probabilidad aportada y validada, CLV que no confunde variación de precio con edge, ROI contabilizando ganada/perdida/nula/pendiente; importación CSV y OCR con confirmación humana. Sin APIs de pago obligatorias, scraping privado o acceso Premium ajeno.
Integración futura vía export/import documentado con consentimiento. No escribir a Super Bet o RS1000. PR de OddsDeck de Argon es entrega externa a revisar, no auto-aceptación.

### F5. AUDIODECK, alta fidelidad con medición
Base PR #16. Reforzar radio/favoritos, álbumes, biblioteca FLAC/WAV y controles, listas, ReplayGain y UX. Diferenciar ruta NORMAL / DSP-EQ / USB BIT-PERFECT. Con EQ el audio no es bit-perfect. Validar USB con DAC y dispositivo real antes de afirmar bit-perfect, no solo porque Android acepta un flag. TIDAL/Qobuz: SDK/API y suscripción *autorizada* cuando corresponda, sin DRM/paywall bypass. Si no hay autorización, estado BLOQUEADO de esa integración y terminar las funciones locales independientes.

### F6. SANDRIFT KARTS: pruebas rojas, juego original
PR #19: Grok entregó motor y APK, pero el emulador QA estricto PR #20 falla. Analizar logs 40036035578 y 40039666002 (menu-tap-did-not-enter-race y pause-did-not-stop-race); el popup de Android “Viewing full screen” invalida algunas pruebas superficiales. No eliminar aserciones para obtener verde. Validar entrar a carrera por toque real, pausa, reanudar, fondo, rotación, dos relaciones de aspecto, 40s de telemetría. Si se requiere instrumentación, hacerla explícita solo en debuggable y probar estados.
Más adelante sonido, menús, circuitos y recursos procedurales/originales; no usar personajes, mapas, música o marcas copiadas de Beach Buggy Racing o Mario Kart. 60 FPS objetivo, NO probado en POCO; reportar avg/p95/peor y temperatura/consumo cuando exista teléfono autorizado.

## 4. Gates y entregables obligatorios

G0 PREVUELO (HEAD, diff, fuentes, riesgos) → G1 DISEÑO (criterios observables) → G2 PRUEBAS ROJAS Y VERDES (regresiones) → G3 BUILD/JUNIT/LINT/APK (firma y SHA) → G4 EMULADOR (instalación real, UI, capturas, ausencia de overlay/cierre) → G5 DISPOSITIVO (POCO/Mamá, si hay acceso autorizado) → G6 DICTAMEN INDEPENDIENTE.
Un RUN exitoso no demuestra Android en un POCO, sensor de giro, continuidad de sesiones TDLib, entrega puntual de notificaciones, ni bit-perfect real.
Documentar resultados en el PR Draft, con links de Actions artifacts y APK individual si es accesible por el usuario, SHA del APK interno y SOURCE-COMMIT. Sin merges ni deploys. Cada versión tiene propio changelog.
Pruebas críticas: falsos positivos, datos vencidos, respaldo corrupto, perfiles cruzados, medios protegidos, OCR incierto, permisos y privacidad, bordes de motor y pausa, UI 70 años.

## 5. Asignación sin pisarse

- Terra 6.1 / Work: F0 y F1; **primeros cambios de código** en Calendario Familiar v0.2 y QA Sandrift, cada uno en una rama y Draft PR separados.
- Grok: implementación de Kart solo en rama propia, revisión de QA por tercero.
- Gemini Argon/Jules: OddsDeck v0.2 a partir de base exacta; no declarar PR/APK sin publicación real.
- Workito: experimentos de CanalDeck→Pick$ OFF; no activar por delegación implícita.
- Dueño: autoriza firma persistente y prueba de teléfonos; no revelar secretos en repo público.

## 6. Primera ejecución exigida a Terra (hacer, no planear eternamente)

1. Leer esta misión completa; auditar HEAD reales de las siete líneas y escribir coordinacion/evidencia/TERRA61-ESTADO-REAL.md (brechas P0/P1/P2; firmas y riesgos).
2. Elegir **un defecto concreto de Calendario** y **uno de Sandrift QA**, documentar reproducción previa, arreglar en branches separados, escribir pruebas y construir APK(s) cuando aplique. No crear otra web ni otro motor.
3. Entregar Draft PR por producto, con CI y capturas de laboratorio, log de fuente/productor exacto, límite offline y relevo.
4. Si algo no puede terminarse en esa sesión, dejar **PARCIAL_RECUPERABLE** con commit y error preciso, no afirmar que trabaja en segundo plano.
5. No tocar main, apuestas, mensajes ni apps externas. No autoauditar una reparación crítica como APTO_DISPOSITIVO.

**Formato de cierre obligatorio:** Producto | rama+HEAD | productor | base | cambios | pruebas+Lint+capturas | APK+SHA | riesgos | dictamen | siguiente gate. Reportar datos vencidos y “sin evidencia” explícitamente. No heredar éxito entre HEAD diferentes.
