# Evidencia y estado de construcción

Fecha: 2026-10-20 (CDMX)

## Resultado observado

- `kotlinc -version`: **1.9.0** (JVM local para reglas aisladas).
- `bash scripts/test-core.sh`: **20/20 PASS** en motor independiente.
- `bash scripts/check-permissions.sh`: **PASS**.
- Gradle: **no disponible** en entorno de ejecución.
- Android SDK (`android.jar`, `aapt2`, `sdkmanager`): **no disponible** en entorno de ejecución.
- Acceso de red local a repositorios de SDK: **no disponible** (DNS de dl.google.com falló).
- Equipo Desktop Commander autorizado `ChArly`: **offline** durante esta sesión; no se usó ni se modificó.
- GitHub: cuatro repositorios accesibles, todos con responsabilidades existentes. El conector no expone creación de repositorios nuevos. **No se escribió en ninguno**.

## Estado de la APK

**NO COMPILADA. No existe un archivo APK válido en esta entrega.**

Se preparó `.github/workflows/android.yml` para que, al subir los archivos a un nuevo repositorio privado con Actions, un runner configure SDK 35, Gradle 8.9, ejecute JUnit/lint y compile un debug APK, publicándolo como artefacto. **No se ha ejecutado esa CI**.

No confundir pruebas JVM del motor con compilación o prueba de instalación real en Android.

## II-Pruebas (nuevo contenedor)

Se organizó el proyecto bajo `apps/centro-de-alertas/` con un workflow raíz para publicación directa de `.apk` como *pre-release privada*. La compilación requiere que el usuario cree el repositorio `cxn250394-a11y/II-Pruebas` y se publiquen estos archivos. **La APK todavía no ha sido compilada; el flujo de release no ha sido ejecutado.**
