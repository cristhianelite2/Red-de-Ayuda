# Android — Red de Ayuda (prototipo)

API mínima 26. Kotlin nativo. Abrir **esta carpeta** `android/` en Android Studio.

## Núcleo y simulador (sin teléfono, JDK 17)

```bat
set JAVA_HOME=..\ .tools\jdk-17.0.20+8
gradlew.bat :domain:test :simulator:run
```

Si ya tienes JDK 17:

```bat
gradlew.bat :domain:test :simulator:test :simulator:run
```

El simulador demuestra A→B→C→D (SOS, repetidores silenciosos, ACK, RESCUE_PING) **sin BLE**.

## App en teléfonos

1. Android Studio descarga el SDK si falta.
2. `Build > Build APK` e instala en 3 teléfonos.
3. Sigue [`../docs/testing/field-test-abc.md`](../docs/testing/field-test-abc.md).

Módulos: `:domain` `:data` `:transport-ble` `:platform` `:app` `:simulator`.

Los módulos Android solo se incluyen si Gradle detecta el SDK (`local.properties` o `%LOCALAPPDATA%\Android\Sdk`).
