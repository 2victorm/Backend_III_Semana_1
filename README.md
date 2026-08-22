# Backend III — Banco XYZ (Experiencia 1, Semana 2)
 
**Asignatura:** Desarrollo Backend III (PBY2203) — Experiencia 1, Semana 2

## Objetivo
 
El Banco XYZ tiene tres procesos batch corriendo sobre un sistema legacy que
produce archivos CSV con datos sucios. Este proyecto los reescribe en **Spring
Batch**, validando y corrigiendo los datos antes de persistirlos en
**PostgreSQL**, y agrega escalamiento (chunks + multithreading) y tolerancia
a fallos (skip + retry) sobre la base construida en la semana 1.
 
Los tres procesos migrados son:
 
1. **Reporte de transacciones diarias** — procesa las transacciones del día,
   detecta anomalías y genera un resumen agrupado por fecha.
2. **Cálculo de intereses mensuales** — aplica la tasa correspondiente a cuentas
   de ahorro y préstamos, y calcula el saldo final.
3. **Estados de cuenta anuales** — compila los movimientos de cada cuenta y
   genera un informe anual para auditoría.
Los datos de entrada provienen de
[bank_legacy_data](https://github.com/KariVillagran/bank_legacy_data).
 
---
 
## Stack
 
| Componente   | Versión                  |
|--------------|---------------------------|
| Java         | 21                        |
| Spring Boot  | 4.0.7                     |
| Spring Batch | 6.x (incluido en Boot 4)  |
| PostgreSQL   | 16 o 17                   |
| Maven        | wrapper incluido (`mvnw`) |
 
---
 
## Estructura del código
 
```
src/main/java/com/duoc/bancoxyz/
    BancoXyzBatchApplication.java   Lanza los tres Jobs en orden y cierra el
                                     contexto explicitamente al terminar
    config/
        TransaccionesJobConfig.java    Job 1: readers, writers, steps y job
        InteresesJobConfig.java        Job 2
        CuentasAnualesJobConfig.java   Job 3
        FabricaStepsCarga.java         Construye los steps de carga (chunk +
                                        multithreading + skip + retry), comunes
                                        a los tres jobs
        TaskExecutorConfig.java        TaskExecutor de 3 hilos
    decider/ContinuidadDecider.java    Decide si el job continua, se marca
                                        DEGRADADO o se detiene, segun cuanto se
                                        descarto en el step de carga
    model/                          Objetos que viajan por el pipeline
        Transaccion.java            ResumenDiario.java
        Interes.java                InteresCalculado.java
        CuentaAnual.java            EstadoAnual.java
    processor/                      Toda la logica de validacion y limpieza
        TransaccionProcessor.java
        InteresProcessor.java
        CuentaAnualProcessor.java
    policy/PoliticaOmisionPersonalizada.java   Que se omite (dato malo) y que no
                                                 (fallo de infraestructura)
    listener/
        JobListener.java            Traza de ejecucion por consola
        MetricasStepListener.java   Throughput y reparto de trabajo por hilo
        RechazoSkipListener.java    Audita cada registro omitido (skip real)
    repository/RechazoRepository.java   Persiste los registros descartados
    exception/
        ValidacionDatosException.java
        FalloTransitorioSimulado.java  Simula un fallo transitorio para poder
                                         evidenciar el retry a demanda
    writer/WriterConFalloSimulado.java Envoltorio que activa el fallo simulado
    util/
        CampoCsv.java                Lectura tolerante de campos del CSV
        ParseadorFechas.java         Interpreta fechas en varios formatos
 
src/main/resources/
    application.properties           Config. base: conexion, escalado, tolerancia
                                      a fallos y tasas — se aplica siempre
    application-semana1.properties   Perfil que apunta a la data de la semana 1
    application-retry.properties     Perfil que fuerza fallos transitorios
    schema.sql                       DDL de las tablas de negocio
    data/                            CSV de la semana 1
    data-s2/                         CSV de la semana 2, con anomalias
                                      (default de esta actividad)
```
 
---
 
## Decisiones de diseño
 
### Un `application.properties` base + un archivo por perfil
 
`application.properties` trae la configuracion que se usa siempre (conexion,
pool, escalado, tolerancia a fallos, tasas). Los dos escenarios alternativos
(`semana1`, `retry`) viven cada uno en su propio
`application-<perfil>.properties`, que solo contiene lo que ese escenario
sobrescribe — el resto se hereda de la config base. Se activan con
`-Dspring-boot.run.profiles=<perfil>`.
 
Se eligio esta forma (archivos separados) en vez del formato multi-documento
de un solo archivo (bloques separados por `#---`) porque es el mecanismo mas
simple y menos propenso a errores. Cada perfil es un archivo independiente,
facil de ubicar y de revisar.
 
### Se mantienen los dos origenes de datos (`data` y `data-s2`)
 
`banco.data.directorio` controla de que carpeta se leen los tres CSV:
 
- `data` — archivos de la semana 1 (perfil `semana1`). 
- `data-s2` — archivos con datos sucios de la semana 2. Es el dataset de esta actividad.
No se reemplazo la data de la semana 1: se conservo como el perfil
`semana1`, de modo que el proyecto sigue pudiendo correr sobre esos datos
(evidencia de continuidad) ademas de sobre los datos de esta semana.
 
### Deteccion de duplicados segura ante reintentos
 
`TransaccionProcessor` e `InteresProcessor` detectan duplicados guardando la
"huella" del registro (los campos que lo identifican) junto con el **id del
registro que la reclamo primero**, en un `ConcurrentHashMap`. Con
el perfil `retry` activo, si un chunk falla y se reintenta, Spring Batch
vuelve a correr el `ItemProcessor` para los mismos items. Guardando tambien el id,
el processor distingue, misma huella + mismo id = el mismo registro
reprocesado (se deja pasar); misma huella + id distinto = duplicado real (se
rechaza).
 
---
 
## Tablas generadas
 
| Tabla                             | Contenido                                  |
|------------------------------------|---------------------------------------------|
| `transacciones`                    | transacciones diarias ya validadas          |
| `resumen_transacciones_diarias`    | **salida Job 1**: totales por fecha         |
| `intereses_calculados`             | **salida Job 2**: interes y saldo final por cuenta |
| `cuentas_anuales`                  | movimientos anuales ya validados            |
| `estados_cuenta_anuales`           | **salida Job 3**: informe por cuenta y año  |
| `registros_rechazados`             | auditoria: cada fila descartada con su motivo |
 
---
 
## Reglas de validacion
 
### Job 1 — Transacciones
 
- Fecha ilegible: se omite el registro (skip)
- Monto nulo, cero o negativo: se rechaza
- Tipo distinto de `debito` / `credito`: se rechaza
- Misma fecha + monto + tipo repetida: se rechaza por duplicado
- Tipo en minusculas: se normaliza a mayusculas
### Job 2 — Intereses
 
- Saldo nulo, cero o negativo: se rechaza
- Edad fuera de 18–100: se rechaza
- Tipo distinto de `ahorro` / `prestamo`: se rechaza
- Titular con mismos datos y otro `cuenta_id`: se rechaza por duplicado
Tasas aplicadas (configurables en `application.properties`):
ahorro **0,5 % mensual**, prestamo **1,5 % mensual**.
 
### Job 3 — Cuentas anuales
 
- Fecha ilegible: se omite el registro (skip)
- Monto nulo o cero: se rechaza
- Movimiento distinto de `deposito` / `retiro` / `compra`: se rechaza
- Descripcion vacia: se corrige con `SIN DESCRIPCION`
- Signo del monto: se normaliza — deposito suma, retiro y compra restan
---
 
## Escalamiento y tolerancia a fallos
 
- **Chunks de 5** registros y **3 hilos** en paralelo (`FabricaStepsCarga` +
  `TaskExecutorConfig`). El reader se envuelve en `SynchronizedItemStreamReader` 
  porque los readers de Spring Batch no son thread-safe.
- **Politica de omision propia** (`PoliticaOmisionPersonalizada`): omite datos
  invalidos (`ValidacionDatosException`, fechas o numeros ilegibles, filas
  duplicadas) pero nunca fallos de infraestructura, esos los maneja el retry.
- **Politica de reintento**: hasta `banco.retry.intentos` reintentos ante
  errores transitorios de base de datos (deadlock, perdida momentanea de
  conexion). Se puede forzar con el perfil `retry` para verlo en accion.
- **`ContinuidadDecider`**: entre el step de carga y el de agregacion decide si
  el job continua normalmente, sigue pero queda marcado `DEGRADADO` (se
  descarto mas del umbral configurado) o se detiene si no se cargo ningun
  registro (`SIN_DATOS`).
- **`MetricasStepListener`**: imprime duracion, throughput y cuantos registros
  proceso cada hilo, para poder ajustar chunk/hilos con datos reales.
---
 
## Manejo de errores
 
- **Dato malo pero legible** (monto negativo, tipo desconocido, duplicado): el
  `ItemProcessor` devuelve `null`. Spring Batch lo cuenta como *filtered*, no lo
  pasa al writer, y se deja constancia en `registros_rechazados`.
- **Dato ilegible** (fecha o numero que no se puede interpretar): se lanza una
  excepcion que la politica de omision reconoce como omitible (skip), y el
  `RechazoSkipListener` la audita.
- **Fallo de infraestructura** (deadlock, timeout de conexion): no se omite, se
  reintenta segun la politica de retry.
---
 
## Como ejecutar
 
### 1. Crear la base de datos
 
```sql
CREATE DATABASE banco_xyz;
```
 
### 2. Ajustar las credenciales
 
En `src/main/resources/application.properties`, seccion de base de datos.
 
### 3. Ejecutar
 
```bash
./mvnw spring-boot:run
```
 
Las tablas se crean solas en la ejecucion (`schema.sql` se ejecuta con
`spring.sql.init.mode=always`). El `schema.sql` hace `DROP TABLE IF EXISTS`
antes de crear, asi que cada ejecucion parte limpia y es reproducible.
 
### Perfiles disponibles
 
```bash
# Datos de la semana 1
./mvnw spring-boot:run "-Dspring-boot.run.profiles=semana1"
 
# Fuerza fallos transitorios en los primeros chunks para evidenciar el retry
./mvnw spring-boot:run "-Dspring-boot.run.profiles=retry"
```
 
---
 
## Evidencia de ejecucion
 
El `JobListener` imprime por consola el resumen de cada Job y Step (leidos,
filtrados, escritos, omitidos), y `MetricasStepListener` agrega duracion,
throughput y el reparto de registros por hilo. Las capturas de estas salidas
para cada uno de los tres jobs son la evidencia de ejecucion que pide la
actividad.
 