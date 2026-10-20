# II Pruebas

**Repositorio de laboratorio independiente** para aplicaciones Android experimentales. Ningún componente de este repositorio debe acceder a repositorios, servicios, claves o infraestructura de Super Bet, Super Investment, Centro de Mando o RS1000.

## Primera aplicación: Centro de Alertas v0.1

**Carpeta:** [`apps/centro-de-alertas/`](apps/centro-de-alertas/)

Bandeja privada para Android/HyperOS que organiza notificaciones **ya recibidas por el teléfono**. Reconoce Telegram, Nicegram y varias aplicaciones deportivas; permite seleccionar las fuentes de manera explícita, buscar y priorizar palabras. No sustituye a Telegram ni obtiene automáticamente eventos deportivos no notificados.

### Conseguir una APK instalable

Al subir los archivos a la rama `main` de `cxn250394-a11y/II-Pruebas`, GitHub Actions compila la aplicación y ejecuta pruebas. **Solo si todo sale bien y el repositorio es privado**, publica una **pre-release privada** con un archivo directamente descargable:

- GitHub → repositorio **II-Pruebas** → **Releases** → última versión `Centro de Alertas prueba` → sección **Assets** → `Centro-de-Alertas-v0.1.apk`.
- Descarga y abre la APK en tu POCO F7. Android puede solicitar permitir instalación desde la app usada para abrirla.
- En Centro de Alertas, activa únicamente las fuentes que quieras guardar y concede el acceso a notificaciones de Android.

La APK es **debug**, para pruebas personales, no para distribución pública. Si el repositorio permanece público, el flujo **no crea Releases**, pero sí genera un artefacto de Actions con la APK para revisión. Convierte el repositorio a **privado** y vuelve a ejecutar la compilación para obtener una Release descargable directamente. **No instala nada remotamente**, no despliega sistemas ni toca producción. Los APK debug de compilaciones diferentes pueden tener firmas diferentes: puede ser necesario desinstalar una compilación anterior y perder su historial local; pendiente implementar firma persistente segura.

### Estado verificable

- Código Kotlin, manifiesto y flujo de GitHub Actions preparados.
- Se ejecutan pruebas JVM del motor y comprobaciones estáticas sin Android SDK.
- **Todavía no existe APK compilada de esta versión** hasta que GitHub Actions complete exitosamente tras publicarse en el repositorio.
- No se han hecho pruebas físicas en POCO F7.

### Seguridad y privacidad

La app exige consentimiento para el permiso `NotificationListenerService`, no solicita Internet, y de fábrica todas las fuentes de captura están desactivadas. Al habilitarla, **puede guardar texto sensible presente en notificaciones permitidas**. La protección por códigos OTP es heurística e incompleta. No se debe habilitar una fuente que envíe secretos, datos financieros o personales que no quieras almacenar. Si el teléfono está desbloqueado, alguien con acceso físico a esta app puede leer el historial.

## Organización prevista

```text
II-Pruebas/
  apps/
    centro-de-alertas/   # proyecto Kotlin/Android
    telegram/             # futuro, no creado
    sports-radar/         # futuro, no creado
    kart-racing/          # futuro, no creado
  .github/workflows/     # prueba, compilación y APK privada
```

**Ningún módulo de Super Bet, RS1000 u otros repositorios fue importado ni modificado.**
