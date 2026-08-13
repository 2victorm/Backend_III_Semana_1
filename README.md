# \# Backend\_III\_Semana\_1 - Banco XYZ

# \*\*Asignatura:\*\* Desarrollo Backend III (PBY2203) — Experiencia 1, Semana 1

# 

# \## Objetivo

# El Banco XYZ tiene tres procesos batch corriendo sobre un sistema legacy que

# produce archivos CSV con datos sucios. Este proyecto los reescribe en \*\*Spring

# Batch\*\*, validando y corrigiendo los datos antes de persistirlos en

# \*\*PostgreSQL\*\*.

# 

# Los tres procesos migrados son:

# 

# 1\. \*\*Reporte de transacciones diarias\*\* — procesa las transacciones del día,

# &#x20;  detecta anomalías y genera un resumen agrupado por fecha.

# 2\. \*\*Cálculo de intereses mensuales\*\* — aplica la tasa correspondiente a cuentas

# &#x20;  de ahorro y préstamos, y calcula el saldo final.

# 3\. \*\*Estados de cuenta anuales\*\* — compila los movimientos de cada cuenta y

# &#x20;  genera un informe anual para auditoría.

# 

# Los datos de entrada provienen de

# \[bank\_legacy\_data](https://github.com/KariVillagran/bank\_legacy\_data)

# (carpeta `data/semana\_1`).

# 

# \---

# 

# \## Stack

# 

# | Componente | Versión |

# |---|---|

# | Java | 21 |

# | Spring Boot | 4.0.7 |

# | Spring Batch | 6.x (incluido en Boot 4) |

# | PostgreSQL | 16 o 17 |

# | Maven | wrapper incluido (`mvnw`) |

# 

# \---

# 

# \## Estructura del código

# 

# ```

# src/main/java/com/duoc/bancoxyz/

# BancoXyzBatchApplication.java   Lanza los tres Jobs en orden

# &#x20;   config/

# &#x20;       TransaccionesJobConfig.java   Job 1: readers, writers, steps y job

# &#x20;       InteresesJobConfig.java       Job 2

# &#x20;       CuentasAnualesJobConfig.java  Job 3

# &#x20;   model/                          Objetos que viajan por el pipeline

# &#x20;       Transaccion.java            ResumenDiario.java

# &#x20;       Interes.java                InteresCalculado.java

# &#x20;       CuentaAnual.java            EstadoAnual.java

# &#x20;   processor/                      Toda la lógica de validación y limpieza

# &#x20;       TransaccionProcessor.java

# &#x20;       InteresProcessor.java

# &#x20;       CuentaAnualProcessor.java

# &#x20;   listener/JobListener.java           Traza de ejecución por consola

# &#x20;   repository/RechazoRepository.java   Persiste los registros descartados

# &#x20;   exception/ValidacionDatosException.java

# &#x20;   util/ParseadorFechas.java           Interpreta fechas en varios formatos

# 

# &#x20;   src/main/resources/

# &#x20;   application.properties          Conexión, tasas y rangos configurables

# &#x20;   schema.sql                      DDL de las tablas de negocio

# &#x20;   data/                           Los tres CSV de entrada

# &#x20;   ```

# \---

# 

# \## Tablas generadas

# 

# | Tabla | Contenido |

# |---|---|

# | `transacciones` | transacciones diarias ya validadas |

# | `resumen\_transacciones\_diarias` | \*\*salida Job 1\*\*: totales por fecha |

# | `intereses\_calculados` | \*\*salida Job 2\*\*: interés y saldo final por cuenta |

# | `cuentas\_anuales` | movimientos anuales ya validados |

# | `estados\_cuenta\_anuales` | \*\*salida Job 3\*\*: informe por cuenta y año |

# | `registros\_rechazados` | auditoría: cada fila descartada con su motivo |

# 

# \---

# 

# \## Reglas de validación

# 

# \### Job 1 — Transacciones

# 

# Fecha ilegible: se omite el registro (skip)

# Monto nulo, cero o negativo: se rechaza 

# Tipo distinto de `debito` / `credito`: se rechaza 

# Misma fecha + monto + tipo repetida: se rechaza por duplicado 

# Tipo en minúsculas: se normaliza a mayúsculas 

# 

# \### Job 2 — Intereses

# 

# Saldo nulo, cero o negativo: se rechaza 

# Edad fuera de 18–100: se rechaza 

# Tipo distinto de `ahorro` / `prestamo`: se rechaza 

# Titular con mismos datos y otro `cuenta\_id`: se rechaza por duplicado 

# 

# Tasas aplicadas (configurables en `application.properties`):

# ahorro \*\*0,5 % mensual\*\*, préstamo \*\*1,5 % mensual\*\*.

# 

# \### Job 3 — Cuentas anuales

# 

# Fecha ilegible: se omite el registro (skip) 

# Monto nulo o cero: se rechaza 

# Movimiento distinto de `deposito` / `retiro` / `compra`: se rechaza 

# Descripción vacía: se corrige con `SIN DESCRIPCION` 

# Signo del monto: se normaliza depósito suma, retiro y compra restan 

# 

# \---

# 

# \## Manejo de errores

# 

# \- \*\*Dato malo pero legible\*\* (monto negativo, tipo desconocido, duplicado): el

# &#x20; `ItemProcessor` devuelve `null`. Spring Batch lo cuenta como \*filtered\*, no lo

# &#x20; pasa al writer, y nosotros dejamos constancia en `registros\_rechazados`.

# 

# \---

# 

# \## Cómo ejecutar

# 

# \### 1. Crear la base de datos

# ```sql

# CREATE DATABASE banco\_xyz;

# ```

# 

# \### 2. Ajustar las credenciales

# 

# En `src/main/resources/application.properties`:

# 

# \### 3. Ejecutar

# 

# ```bash

# ./mvnw spring-boot:run

# ```

# 

# \---

# 

# Las tablas se crean solas en el arranque (`schema.sql` se ejecuta con

# `spring.sql.init.mode=always`). El `schema.sql` hace `DROP TABLE IF EXISTS`

# antes de crear, así que cada ejecución parte limpia y es reproducible.

# 

# \## Evidencia de ejecución

# 

# El `JobListener` imprime por consola el resumen de cada Job y Step.

# 



