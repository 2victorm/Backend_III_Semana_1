package com.duoc.bancoxyz.config;

import javax.sql.DataSource;

import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder;
import org.springframework.batch.infrastructure.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
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
import com.duoc.bancoxyz.model.ResumenDiario;
import com.duoc.bancoxyz.model.Transaccion;
import com.duoc.bancoxyz.processor.TransaccionProcessor;
import com.duoc.bancoxyz.util.ParseadorFechas;

/**
 * 
 * TransaccionesJobConfig
 * Job 1 - Reporte de transacciones diarias.
 * Step 1. cargarTransaccionesStep lee transacciones.csv, lo valida y
 * lo guarda en la tabla transacciones.
 * Step 2. resumenTransaccionesStep lee esa tabla y escribe el resumen en
 * la tabla resumen_transacciones_diarias.
 */
@Configuration
public class TransaccionesJobConfig {

    // Step 1: Convierte CSV transacciones a tabla transacciones.
    // Lee el CSV y arma un objeto por cada una.
    @Bean
    public FlatFileItemReader<Transaccion> transaccionReader() {

        return new FlatFileItemReaderBuilder<Transaccion>()
                .name("transaccionReader")
                .resource(new ClassPathResource("data/transacciones.csv"))
                .encoding("UTF-8")
                .linesToSkip(1)
                .delimited()
                .delimiter(",")
                .names("id", "fecha", "monto", "tipo")
                .fieldSetMapper(fieldSet -> {

                    Transaccion transaccion = new Transaccion();
                    transaccion.setId(fieldSet.readLong("id"));
                    transaccion.setFecha(ParseadorFechas.parsear(fieldSet.readString("fecha")));
                    transaccion.setMonto(fieldSet.readBigDecimal("monto"));
                    transaccion.setTipo(fieldSet.readString("tipo"));
                    return transaccion;
                })
                .build();
    }

    // Inserta casa transaccion a la tabla.
    @Bean
    public JdbcBatchItemWriter<Transaccion> transaccionWriter(DataSource dataSource) {

        return new JdbcBatchItemWriterBuilder<Transaccion>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO transacciones (id, fecha, monto, tipo)
                        VALUES (:id, :fecha, :monto, :tipo)
                        """)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .build();
    }

    @Bean
    public Step cargarTransaccionesStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<Transaccion> transaccionReader,
            TransaccionProcessor transaccionProcessor,
            JdbcBatchItemWriter<Transaccion> transaccionWriter) {

        return new ChunkOrientedStepBuilder<Transaccion, Transaccion>(
                "cargarTransaccionesStep", jobRepository, 10)
                .reader(transaccionReader)
                .processor(transaccionProcessor)
                .writer(transaccionWriter)
                .transactionManager(transactionManager)
                .faultTolerant()
                .skip(ValidacionDatosException.class)
                .skipLimit(20)
                .build();
    }

    // Step 2: tabla transacciones -> tabla resumen_transacciones_diarias.
    @Bean
    public JdbcCursorItemReader<ResumenDiario> resumenReader(DataSource dataSource) {

        return new JdbcCursorItemReaderBuilder<ResumenDiario>()
                .name("resumenReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT fecha,
                               COUNT(*) AS cantidad_transacciones,
                               COALESCE(SUM(CASE WHEN tipo = 'DEBITO'  THEN monto ELSE 0 END), 0) AS total_debitos,
                               COALESCE(SUM(CASE WHEN tipo = 'CREDITO' THEN monto ELSE 0 END), 0) AS total_creditos,
                               MAX(monto) AS monto_mayor
                          FROM transacciones
                         GROUP BY fecha
                         ORDER BY fecha
                        """)
                .rowMapper((rs, filaNumero) -> {

                    ResumenDiario resumen = new ResumenDiario();
                    resumen.setFecha(rs.getDate("fecha").toLocalDate());
                    resumen.setCantidadTransacciones(rs.getInt("cantidad_transacciones"));
                    resumen.setTotalDebitos(rs.getBigDecimal("total_debitos"));
                    resumen.setTotalCreditos(rs.getBigDecimal("total_creditos"));
                    resumen.setMontoMayor(rs.getBigDecimal("monto_mayor"));
                    return resumen;
                })
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<ResumenDiario> resumenWriter(DataSource dataSource) {

        return new JdbcBatchItemWriterBuilder<ResumenDiario>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO resumen_transacciones_diarias
                            (fecha, cantidad_transacciones, total_debitos, total_creditos, monto_mayor)
                        VALUES
                            (:fecha, :cantidadTransacciones, :totalDebitos, :totalCreditos, :montoMayor)
                        """)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .build();
    }

    @Bean
    public Step resumenTransaccionesStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcCursorItemReader<ResumenDiario> resumenReader,
            JdbcBatchItemWriter<ResumenDiario> resumenWriter) {

        return new ChunkOrientedStepBuilder<ResumenDiario, ResumenDiario>(
                "resumenTransaccionesStep", jobRepository, 10)
                .reader(resumenReader)
                .writer(resumenWriter)
                .transactionManager(transactionManager)
                .build();
    }

    // Job: se encadenan los dos steps anteriores.
    @Bean
    public Job reporteTransaccionesDiariasJob(JobRepository jobRepository,
            @Qualifier("cargarTransaccionesStep") Step cargarTransaccionesStep,
            @Qualifier("resumenTransaccionesStep") Step resumenTransaccionesStep,
            JobListener jobListener) {

        return new JobBuilder("reporteTransaccionesDiariasJob", jobRepository)
                .start(cargarTransaccionesStep)
                .next(resumenTransaccionesStep)
                .listener(jobListener)
                .build();
    }
}
