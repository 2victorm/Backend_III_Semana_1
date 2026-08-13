
DROP TABLE IF EXISTS resumen_transacciones_diarias;
DROP TABLE IF EXISTS transacciones;
DROP TABLE IF EXISTS intereses_calculados;
DROP TABLE IF EXISTS estados_cuenta_anuales;
DROP TABLE IF EXISTS cuentas_anuales;
DROP TABLE IF EXISTS registros_rechazados;

-- JOB 1: Reporte de transacciones diarias

CREATE TABLE transacciones (
    id     BIGINT         PRIMARY KEY,
    fecha  DATE           NOT NULL,
    monto  NUMERIC(15, 2) NOT NULL,
    tipo   VARCHAR(20)    NOT NULL
);

CREATE TABLE resumen_transacciones_diarias (
    fecha                  DATE           PRIMARY KEY,
    cantidad_transacciones INTEGER        NOT NULL,
    total_debitos          NUMERIC(15, 2) NOT NULL,
    total_creditos         NUMERIC(15, 2) NOT NULL,
    monto_mayor            NUMERIC(15, 2) NOT NULL
);

-- JOB 2: Calculo de intereses mensuales

CREATE TABLE intereses_calculados (
    cuenta_id       BIGINT         PRIMARY KEY,
    nombre          VARCHAR(150)   NOT NULL,
    tipo            VARCHAR(20)    NOT NULL,
    saldo_inicial   NUMERIC(15, 2) NOT NULL,
    tasa_aplicada   NUMERIC(6, 4)  NOT NULL,
    interes_mensual NUMERIC(15, 2) NOT NULL,
    saldo_final     NUMERIC(15, 2) NOT NULL
);

-- JOB 3: Generacion de Estados de Cuenta Anuales

CREATE TABLE cuentas_anuales (
    id          BIGSERIAL      PRIMARY KEY,
    cuenta_id   BIGINT         NOT NULL,
    fecha       DATE           NOT NULL,
    transaccion VARCHAR(20)    NOT NULL,
    monto       NUMERIC(15, 2) NOT NULL,
    descripcion VARCHAR(250)
);

CREATE TABLE estados_cuenta_anuales (
    cuenta_id            BIGINT         NOT NULL,
    anio                 INTEGER        NOT NULL,
    cantidad_movimientos INTEGER        NOT NULL,
    total_depositos      NUMERIC(15, 2) NOT NULL,
    total_retiros        NUMERIC(15, 2) NOT NULL,
    total_compras        NUMERIC(15, 2) NOT NULL,
    saldo_neto           NUMERIC(15, 2) NOT NULL,
    PRIMARY KEY (cuenta_id, anio)
);

-- Auditoria de errores (compartida por los tres Jobs)

CREATE TABLE registros_rechazados (
    id             BIGSERIAL    PRIMARY KEY,
    job_name       VARCHAR(60)  NOT NULL,
    archivo_origen VARCHAR(60)  NOT NULL,
    identificador  VARCHAR(60),
    motivo         VARCHAR(250) NOT NULL,
    dato_original  VARCHAR(500),
    fecha_registro TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);