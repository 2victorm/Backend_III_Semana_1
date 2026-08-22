package com.duoc.bancoxyz.config;

import javax.sql.DataSource;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;

import com.duoc.bancoxyz.decider.ContinuidadDecider;
import com.duoc.bancoxyz.listener.JobListener;
import com.duoc.bancoxyz.listener.MetricasStepListener;
import com.duoc.bancoxyz.model.ResumenDiario;
import com.duoc.bancoxyz.model.Transaccion;
import com.duoc.bancoxyz.processor.TransaccionProcessor;
import com.duoc.bancoxyz.util.CampoCsv;
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

// Semana 2: El path CSV estaba fijo en el codigo "data/transacciones.csv"
// Ahora se arma con una property, para poder apuntar a data/ o a data-s2/
@Configuration
public class TransaccionesJobConfig {

        private static final String NOMBRE_JOB = "reporteTransaccionesDiariasJob";
        private static final String ARCHIVO = "transacciones.csv";

        // ------------------------------------------------------------------
        // STEP 1: transacciones.csv -> tabla transacciones
        // ------------------------------------------------------------------

        @Bean
        public FlatFileItemReader<Transaccion> transaccionReader(
                        @Value("${banco.data.directorio}") String directorio) {

                return new FlatFileItemReaderBuilder<Transaccion>()
                                .name("transaccionReader")
                                .resource(new ClassPathResource(directorio + "/" + ARCHIVO))
                                .encoding("UTF-8")
                                .linesToSkip(1)
                                .delimited()
                                .delimiter(",")
                                .names("id", "fecha", "monto", "tipo")
                                .fieldSetMapper(campos -> {

                                        Transaccion transaccion = new Transaccion();
                                        transaccion.setId(CampoCsv.entero(campos, "id"));
                                        transaccion.setFecha(ParseadorFechas.parsear(
                                                        CampoCsv.texto(campos, "fecha")));
                                        transaccion.setMonto(CampoCsv.decimal(campos, "monto"));
                                        transaccion.setTipo(CampoCsv.texto(campos, "tipo"));
                                        return transaccion;
                                })
                                .build();
        }

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

        // SEMANA 2: FabricaStepsCarga le agrega los 3 hilos, el chunk de 5,
        // la politica de omision, el retry y los listeners enn un solo lugar.
        @Bean
        public Step cargarTransaccionesStep(FabricaStepsCarga fabrica,
                        JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        FlatFileItemReader<Transaccion> transaccionReader,
                        TransaccionProcessor transaccionProcessor,
                        JdbcBatchItemWriter<Transaccion> transaccionWriter) {

                return fabrica.construir(
                                "cargarTransaccionesStep",
                                jobRepository,
                                transactionManager,
                                transaccionReader,
                                transaccionProcessor,
                                transaccionWriter,
                                NOMBRE_JOB,
                                ARCHIVO);
        }

        // ------------------------------------------------------------------
        // STEP 2: tabla transacciones -> tabla resumen_transacciones_diarias
        // ------------------------------------------------------------------

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
                        JdbcBatchItemWriter<ResumenDiario> resumenWriter,
                        MetricasStepListener metricasStepListener,
                        @Value("${banco.chunk.tamano:5}") int tamanoChunk) {

                return new StepBuilder("resumenTransaccionesStep", jobRepository)
                                .<ResumenDiario, ResumenDiario>chunk(tamanoChunk, transactionManager)
                                .reader(resumenReader)
                                .writer(resumenWriter)
                                .listener(metricasStepListener)
                                .build();
        }

        // ------------------------------------------------------------------
        // JOB
        // ------------------------------------------------------------------

        // SEMANA 2: ContinuidadDecider revisa que % de la carga de descarto y
        // decide si el job sigue normal, marcado o se detiene.
        @Bean
        public Job reporteTransaccionesDiariasJob(JobRepository jobRepository,
                        @Qualifier("cargarTransaccionesStep") Step cargarTransaccionesStep,
                        @Qualifier("resumenTransaccionesStep") Step resumenTransaccionesStep,
                        ContinuidadDecider continuidadDecider,
                        JobListener jobListener) {

                return new JobBuilder(NOMBRE_JOB, jobRepository)
                                .incrementer(new RunIdIncrementer())
                                .listener(jobListener)
                                .start(cargarTransaccionesStep)
                                .next(continuidadDecider)
                                .on(ContinuidadDecider.SIN_DATOS).fail()
                                .from(continuidadDecider)
                                .on("*").to(resumenTransaccionesStep)
                                .end()
                                .build();
        }
}