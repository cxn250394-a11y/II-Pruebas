# WORKITO — Estado y decisiones

Fuente de misión: 0aa77bfad255be6ef465521422cf69d5212292c6, PR #31. Productor: Workito. Repositorio único II-Pruebas, público. Prevuelo: main consultado por git ls-remote = 12cc1c7ea0d2717190ac9a2eed2a0bd9bc4ed4e1; no se mueve main. Se leyeron misión #31, Terra #23, inventario #28 y auditorías externas #26/#27/#28/#30.

| App | HEAD base | Prioridad / objetivo | Evidencia de partida | Gate pendiente |
|---|---|---|---|---|
| CanalDeck | 14d59e7aed9d2de8c83bebbca82256afdbe51db4 (#30) | P1 notificación sin cuenta/sesión, colisiones PendingIntent; privacidad de transición | Auditoría #30: 100 JVM y 6 instrumentadas, CI 40467159058; P1 abierto | RED/GREEN de aviso antiguo con cuenta distinta, mismo chat; Android real sintético y dictamen externo |
| Calendario | 0c8a61e69ac16ce11978fba94ab5ca247817e293 (#26) | Restore v0.1→v0.2 paralelo, accesibilidad y datos | Auditoría #26 y CI 40468024200; cita sintética persiste; icono dinámico apagado | Dos APK, backup cifrado por UI; icono seguro y físico pendientes |
| Sandrift | 8b9f037b257b44ea8baf8156bd571bb3b7984bf5 (#27) | BACK no pausa; estado tras fondo/rotación | Auditoría #27 y CI 40463857960; pausa táctil válida, BACK rojo | BACK y conservación de carrera, 2 aspectos; FPS POCO NOT_RUN |
| StatsDeck | 6377082f2e5f6a01ad0a6faec940eba912f58015 (#18) | Fuente/reloj, cobertura N/D y búsqueda | Inventario #28; CI 40025268638 previo | Incremento aislado con tests y emulador, datos externos autorizados |
| OddsDeck | 98cff714738cbff71f67a3b4eac24dc7fe597ac5 (#3) | Comparación estricta/TTL, tracking honesto | #21 es misión, sin implementación ratificada; CI alfa 39684481482 | Verificar ramas Argon antes de editar, luego incremento probado |
| AudioDeck | 368438fce0a4daced729ceb4907eb4810e3520f5 (#16) | Funciones locales/radio, persistencia, modos honestos | CI previo 40021310468 | Build/UI del incremento; USB físico y TIDAL/Qobuz bloqueados sin autorización |
| Alertas LAB | 123c355c51a730381c6c5fbc7a887093426b0a20 (#1) | Identidad/duplicados, privacidad y tiempos observados | CI previo 39632897569 sin emulador | Consentimiento simulado y UI lab; HyperOS físico NOT_RUN |

## Decisiones

- Código por producto en rama y Draft nuevo desde SHA exacto; no modificar ramas Terra/Grok/Argon, no force push, merge, deploy o Release. #5–#13 Pick$ OFF. Sin apuestas, compras, cuentas ni datos privados; otros repositorios intactos.
- Los PASS previos pertenecen solo a su HEAD. Este documento es prevuelo, no dictamen de los cambios de Workito ni cierre de auditoría externa.
- Orden de ejecución C1→C2/C3→C4→C5→C6→C7; preparación independiente en paralelo. Si un gate requiere dispositivo/permiso, registrar NOT_RUN/BLOQUEADO y avanzar trabajo independiente.
- Firma debug de laboratorio: comprobar applicationId, certificado y SHA interno de cada APK descargada y SOURCE-COMMIT. No asumir actualización compatible. No desinstalar apps con datos. Paralelo con ID de laboratorio o restore cifrado probado; la firma privada estable requiere aprobación expresa y nunca se publica una clave.
- C1 de identidad no cierra C1b compositor/FLAG_SECURE automáticamente. C2 no restaura icono dinámico por probar backup. C3 no acredita rendimiento POCO con SwiftShader.

Estado inicial: PARCIAL_RECUPERABLE. Cada Draft de producto contendrá HEAD, base, pruebas negativas, CI, APK bytes/SHA/cert, capturas, límites y siguiente gate. La matriz se actualizará con los resultados concretos al cierre.
