package com.duoc.bancoxyz.config;

import javax.sql.DataSource;

import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder;
import org.springframework.batch.infrastructure.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;

import com.duoc.bancoxyz.exception.ValidacionDatosException;
import com.duoc.bancoxyz.listener.JobListener;
import com.duoc.bancoxyz.model.CuentaAnual;
import com.duoc.bancoxyz.model.EstadoAnual;
import com.duoc.bancoxyz.processor.CuentaAnualProcessor;
import com.duoc.bancoxyz.util.ParseadorFechas;

/**
 * 
 * CuentasAnualesJobConfig
 * Job 3 - Generacion de estados de cuenta anuales.
 * Step 1: cargarCuentasAnualesStep lee cuentas_anuales.csv, lo valida y
 * lo guarda en la tabla cuentas_anuales.
 * Step 2: generarEstadosAnualesStep agrupa la tabla y escribe el informe
 * en estados_cuenta_anuales.
 * 
 */
@Configuration
public class CuentasAnualesJobConfig {

    // STEP 1: CSV -> tabla cuentas_anuales
    @Bean
    public FlatFileItemReader<CuentaAnual> cuentaAnualReader() {

        return new FlatFileItemReaderBuilder<CuentaAnual>()
                .name("cuentaAnualReader")
                .resource(new ClassPathResource("data/cuentas_anuales.csv"))
                .encoding("UTF-8")
                .linesToSkip(1)
                .delimited()
                .delimiter(",")
                .names("cuentaId", "fecha", "transaccion", "monto", "descripcion")
                .fieldSetMapper(fieldSet -> {

                    CuentaAnual movimiento = new CuentaAnual();
                    movimiento.setCuentaId(fieldSet.readLong("cuentaId"));
                    movimiento.setFecha(ParseadorFechas.parsear(fieldSet.readString("fecha")));
                    movimiento.setTransaccion(fieldSet.readString("transaccion"));
                    movimiento.setMonto(fieldSet.readBigDecimal("monto"));
                    movimiento.setDescripcion(fieldSet.readString("descripcion"));
                    return movimiento;
                })
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<CuentaAnual> cuentaAnualWriter(DataSource dataSource) {

        return new JdbcBatchItemWriterBuilder<CuentaAnual>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO cuentas_anuales (cuenta_id, fecha, transaccion, monto, descripcion)
                        VALUES (:cuentaId, :fecha, :transaccion, :monto, :descripcion)
                        """)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .build();
    }

    @Bean
    public Step cargarCuentasAnualesStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<CuentaAnual> cuentaAnualReader,
            CuentaAnualProcessor cuentaAnualProcessor,
            JdbcBatchItemWriter<CuentaAnual> cuentaAnualWriter) {

        return new ChunkOrientedStepBuilder<CuentaAnual, CuentaAnual>(
                "cargarCuentasAnualesStep", jobRepository, 10)
                .reader(cuentaAnualReader)
                .processor(cuentaAnualProcessor)
                .writer(cuentaAnualWriter)
                .transactionManager(transactionManager)
                .faultTolerant()
                .skip(ValidacionDatosException.class)
                .skipLimit(20)
                .build();
    }

    // STEP 2: tabla cuentas_anuales -> tabla estados_cuenta_anuales
    @Bean
    public JdbcCursorItemReader<EstadoAnual> estadoAnualReader(DataSource dataSource) {

        return new JdbcCursorItemReaderBuilder<EstadoAnual>()
                .name("estadoAnualReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT cuenta_id,
                               CAST(EXTRACT(YEAR FROM fecha) AS INTEGER) AS anio,
                               COUNT(*) AS cantidad_movimientos,
                               COALESCE(SUM(CASE WHEN transaccion = 'DEPOSITO' THEN monto      ELSE 0 END), 0) AS total_depositos,
                               COALESCE(SUM(CASE WHEN transaccion = 'RETIRO'   THEN ABS(monto) ELSE 0 END), 0) AS total_retiros,
                               COALESCE(SUM(CASE WHEN transaccion = 'COMPRA'   THEN ABS(monto) ELSE 0 END), 0) AS total_compras,
                               COALESCE(SUM(monto), 0) AS saldo_neto
                          FROM cuentas_anuales
                         GROUP BY cuenta_id, EXTRACT(YEAR FROM fecha)
                         ORDER BY cuenta_id
                        """)
                .rowMapper((rs, filaNumero) -> {

                    EstadoAnual estado = new EstadoAnual();
                    estado.setCuentaId(rs.getLong("cuenta_id"));
                    estado.setAnio(rs.getInt("anio"));
                    estado.setCantidadMovimientos(rs.getInt("cantidad_movimientos"));
                    estado.setTotalDepositos(rs.getBigDecimal("total_depositos"));
                    estado.setTotalRetiros(rs.getBigDecimal("total_retiros"));
                    estado.setTotalCompras(rs.getBigDecimal("total_compras"));
                    estado.setSaldoNeto(rs.getBigDecimal("saldo_neto"));
                    return estado;
                })
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<EstadoAnual> estadoAnualWriter(DataSource dataSource) {

        return new JdbcBatchItemWriterBuilder<EstadoAnual>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO estados_cuenta_anuales
                            (cuenta_id, anio, cantidad_movimientos, total_depositos,
                             total_retiros, total_compras, saldo_neto)
                        VALUES
                            (:cuentaId, :anio, :cantidadMovimientos, :totalDepositos,
                             :totalRetiros, :totalCompras, :saldoNeto)
                        """)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .build();
    }

    @Bean
    public Step generarEstadosAnualesStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcCursorItemReader<EstadoAnual> estadoAnualReader,
            JdbcBatchItemWriter<EstadoAnual> estadoAnualWriter) {

        return new ChunkOrientedStepBuilder<EstadoAnual, EstadoAnual>(
                "generarEstadosAnualesStep", jobRepository, 10)
                .reader(estadoAnualReader)
                .writer(estadoAnualWriter)
                .transactionManager(transactionManager)
                .build();
    }

    // JOB
    @Bean
    public Job estadosCuentaAnualesJob(JobRepository jobRepository,
            @Qualifier("cargarCuentasAnualesStep") Step cargarCuentasAnualesStep,
            @Qualifier("generarEstadosAnualesStep") Step generarEstadosAnualesStep,
            JobListener jobListener) {

        return new JobBuilder("estadosCuentaAnualesJob", jobRepository)
                .start(cargarCuentasAnualesStep)
                .next(generarEstadosAnualesStep)
                .listener(jobListener)
                .build();
    }
}
