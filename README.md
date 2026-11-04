# SmartWorker

Proyecto Android desarrollado con Kotlin y Jetpack Compose para consultar lecturas ambientales simuladas y compartirlas entre trabajador y supervisor mediante Firebase.

## Funcionalidades

- Registro e inicio de sesión con Firebase Authentication.
- Perfiles de usuarios e historial de lecturas en Cloud Firestore.
- Última lectura y actualizaciones mediante Realtime Database.
- Vistas de trabajador y supervisor, con asignación administrativa de trabajadores.

El paquete Android es `cl.smartworker.app`. La aplicación todavía no recibe mediciones del Arduino Uno y DHT11; las lecturas actuales son simuladas.

## Abrir en Android Studio

1. Clonar o descargar este repositorio y abrir su carpeta raíz en Android Studio.
2. Seleccionar **JDK 21** para Gradle e instalar Android SDK 35 si Android Studio lo solicita. El proyecto utiliza Gradle 8.9.
3. Obtener `google-services.json` desde la configuración del proyecto Firebase **SmartWorker** (`smartworker-bb004`), para la aplicación Android `cl.smartworker.app`.
4. Copiar ese archivo dentro de `app/`. Se excluye del repositorio; el archivo original permanece en el proyecto local del equipo.
5. Sincronizar el proyecto con Gradle y ejecutar el módulo `app` en un Android compatible (Android 8.0/API 26 o posterior).

La configuración de Realtime Database está en `Repo.kt`. Si se utiliza otro proyecto Firebase, también hay que actualizar esa dirección y la configuración de Firebase CLI.

## Firebase y cuentas

Habilitar Correo electrónico/Contraseña en Authentication y disponer de Cloud Firestore y Realtime Database. Las cuentas se crean desde la aplicación; no se incluyen contraseñas de usuarios reales.

Las cuentas nuevas tienen rol `TRABAJADOR`. Para preparar el acceso del supervisor desde la consola de Firebase:

1. Registrar las cuentas del trabajador y del supervisor desde la aplicación.
2. En Firestore, cambiar `usuarios/{UID_SUPERVISOR}/rol` a `SUPERVISOR`.
3. En el documento `usuarios/{UID_TRABAJADOR}`, establecer `supervisorUid` con el UID del supervisor.
4. Si se utilizan las reglas de Realtime Database incluidas, establecer `permisos/{UID_TRABAJADOR}/supervisorUid` con ese mismo UID.
5. Ingresar como trabajador y guardar una lectura desde Métricas. Luego ingresar como supervisor para consultar al trabajador asignado.

Los archivos `firestore.rules` y `database.rules.json` contienen las reglas preparadas para el proyecto. Subirlos a GitHub no los publica en Firebase ni demuestra qué reglas están actualmente activas en la consola.

## Pruebas locales de Firebase

Con Node.js y JDK 21 instalados:

```sh
cd firebase-tests
npm ci
npm test
```

Estas pruebas utilizan emuladores y el proyecto `demo-smartworker`.
