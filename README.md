# Calendario Laboral · TurnoSaldo V2 para Android

**Marca:** Vega Digital Technologies  
**Tipo:** aplicación nativa para Android, desarrollada en Java 17, sin cuentas ni conexión obligatoria.  
**Estado:** proyecto fuente preparado para abrir y compilar en Android Studio. **No incluye APK** ni se ha probado en un dispositivo Android en este entorno.

## Diseño de la versión 2

Basado en la captura de referencia del usuario: cabecera azul-turquesa, calendario mensual blanco, jornadas señaladas en verde, fecha elegida en azul, cuatro tarjetas de horas e ingresos, acciones rápidas, gráfico de ingresos de lunes a domingo y navegación inferior.

Cinco pantallas funcionales:

- **Inicio:** calendario, horas del día seleccionado, dinero del día, de la semana y del mes, accesos rápidos y gráfico semanal.
- **Calendario:** navegación por meses, selección de fechas, altas de uno o varios turnos y totales diarios.
- **Reportes:** resumen mensual, tarifa media, ingresos semanales y exportación CSV.
- **Historial:** turnos guardados y apertura del detalle.
- **Ajustes:** moneda y tarifa por hora específica de esa moneda.

Cada turno muestra un **detalle individual** con fecha, horario, descanso, horas, salario, notas y acciones para editar o eliminar.

## Monedas internacionales y gestión segura de importes

La aplicación utiliza `java.util.Currency.getAvailableCurrencies()` de Android, con códigos **ISO 4217** y búsqueda por código o nombre. Esto cubre las divisas reconocidas por la versión de Android instalada (la cifra exacta varía según el dispositivo), entre otras **COP, MXN, USD, EUR, PLN, GBP, BRL, ARS, CLP, PEN, JPY**.

- Se selecciona una moneda inicial según la configuración regional del dispositivo; al actualizar desde V1, se conserva PLN si ya estaba configurada la tarifa V1.
- La moneda se puede cambiar desde **Ajustes**, y cada turno también puede tener una moneda específica.
- Las tarifas predeterminadas se guardan **por moneda**. Los turnos anteriores conservan su tarifa y código monetario.
- Los resúmenes y exportaciones se **filtran por moneda seleccionada**, sin sumar divisas incompatibles.
- **No se realiza ninguna conversión de divisas**. Cambiar la selección muestra los registros de esa moneda, no reetiqueta sus importes.
- Utiliza el formato decimal del dispositivo y la cantidad de decimales establecida para la divisa, cuando está disponible.
- La interfaz de V2 está en español. Traducirla a más idiomas es un paso posterior.

## Cómo compilar la app

1. Descomprime la carpeta y abre **TurnoSaldo_Android** desde **Android Studio > Open** (o la carpeta raíz que contiene `settings.gradle`).
2. Instala el **Android SDK Platform 36** y utiliza **Java 17**; se configura Android Gradle Plugin **8.13.2** y Gradle **8.13**. Necesitarás conexión para descargar dependencias inicialmente.
3. Este ZIP **no incluye Gradle Wrapper binario**. Si Android Studio solicita Gradle, usa una instalación local de Gradle 8.13 o genera el wrapper con `gradle wrapper --gradle-version 8.13`.
4. Sincroniza Gradle, conecta un dispositivo Android 8.0 o superior (API 26+) o abre un emulador y ejecuta la aplicación.
5. Para crear el APK de prueba, elige **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
6. Para Play Console, construye un **Android App Bundle (.aab)** firmado y realiza las pruebas requeridas por Google Play.

**Paquete Android:** `com.vegadigital.turnosaldo` (igual que V1 para permitir actualización compatible). `versionCode 2`, `versionName 2.0.0`. Si el paquete se ha publicado bajo otra firma, deberás respetar la firma original para actualizarlo.

## Cálculos y turnos

`(minutos totales del turno - descanso) × tarifa por hora / 60`.

Se permite que el turno termine al día siguiente, siempre que la hora de salida sea anterior a la de entrada. Si ambas horas son iguales se rechaza el turno (no presupone una jornada de 24 h). El descanso no puede igualar o superar la duración total del turno. Las cantidades son **estimaciones brutas**, no cálculos tributarios ni nóminas oficiales; no incluyen horas extra o recargos automáticos por festivos.

**Nota sobre turnos nocturnos:** la jornada se contabiliza en la fecha de inicio del turno, sin prorratearla entre los dos días.

## Privacidad y migración

- Persistencia local con SQLite (`turnosaldo.db`), sin permisos de internet, GPS o cuenta.
- Migración de la base de datos **versión 1 a 2 sin borrar registros**, asignando **PLN** a los turnos anteriores.
- **Desinstalar la app borra los registros**, salvo si el sistema hace una copia de seguridad que pueda restaurarse. No se debe depender de ello: exporta CSV antes de cambiar de equipo.
- El CSV es un respaldo de lectura para Excel; la app V2 aún **no implementa importación** para restaurar turnos desde ese CSV.

## Comprobaciones realizadas

- Código Java no Android (`WorkEntry` y `CurrencyTools`) compilado y ejecutado con `javac`.
- Pruebas de horarios normales y nocturnos, preservación de monedas y formatos.
- Se simuló el SQL de migración con SQLite y se confirmó la conservación de registros en PLN.
- Se verificó la sintaxis Java de los archivos con el parser del JDK.
- **Pendiente:** build completo con Android SDK, revisión visual real y pruebas de dispositivos pequeños/grandes.
