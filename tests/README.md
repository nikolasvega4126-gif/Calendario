# Pruebas sin SDK Android

Desde el directorio raíz del proyecto:

```bash
mkdir -p tests/build
javac -encoding UTF-8 -d tests/build \
  app/src/main/java/com/vegadigital/turnosaldo/WorkEntry.java \
  app/src/main/java/com/vegadigital/turnosaldo/CurrencyTools.java \
  tests/TurnoSaldoTest.java
java -cp tests/build TurnoSaldoTest
```

Estas pruebas comprueban cálculo de turnos, preservación de la divisa por turno y catálogo ISO de monedas. No sustituyen la compilación Android ni las pruebas de interfaz en un dispositivo.
