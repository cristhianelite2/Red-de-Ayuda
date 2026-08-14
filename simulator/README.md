# Simulador de nodos

El simulador JVM vive en el módulo Gradle [`../android/simulator`](../android/simulator)
para compartir el Emergency Core.

```bash
cd android
.\gradlew :simulator:run :simulator:test
```

Escenario: A (SOS) → B (repetidor) → C (repetidor) → D (rescatista), sin BLE.
