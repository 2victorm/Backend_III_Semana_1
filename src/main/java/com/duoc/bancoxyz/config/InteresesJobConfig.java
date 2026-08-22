package com.duoc.bancoxyz.config;

import javax.sql.DataSource;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.infrastructure.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
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
import com.duoc.bancoxyz.model.Interes;
import com.duoc.bancoxyz.model.InteresCalculado;
import com.duoc.bancoxyz.processor.InteresProcessor;
import com.duoc.bancoxyz.util.CampoCsv;

/**
 * 
 * InteresesJobConfig
 * Job 2 - Calculo de intereses mensuales.
 * Step 1 (unico). lee intereses.csv, aplica la tasa segun el tipo
 * y guarda el resultado (con saldo actualizado) en la tabla
 * intereses_calculados.
 */

// CAMBIO SEMANA 2: mismo cambio que en Transacciones -- el path ahora
// sale de una property en vez de estar escrito fijo en el codigo.
@Configuration
public class InteresesJobConfig {

    private static final String NOMBRE_JOB = "calculoInteresesMensualesJob";
    private static final String ARCHIVO = "intereses.csv";

    /**
     * Lector del CSV de cuentas.
     *
     * El archivo de esta semana trae una fila sin saldo y otra sin edad. Leer
     * esos campos con readBigDecimal()/readInt() reventaria el mapper; CampoCsv
     * devuelve null y deja que el processor decida y documente el rechazo.
     */
    @Bean
    public FlatFileItemReader<Interes> interesReader(
            @Value("${banco.data.directorio}") String directorio) {

        return new FlatFileItemReaderBuilder<Interes>()
                .name("interesReader")
                .resource(new ClassPathResource(directorio + "/" + ARCHIVO))
                .encoding("UTF-8")
                .linesToSkip(1)
                .delimited()
                .delimiter(",")
                .names("cuentaId", "nombre", "saldo", "edad", "tipo")
                .fieldSetMapper(campos -> {

                    Interes interes = new Interes();
                    interes.setCuentaId(CampoCsv.entero(campos, "cuentaId"));
                    interes.setNombre(CampoCsv.texto(campos, "nombre"));
                    interes.setSaldo(CampoCsv.decimal(campos, "saldo"));
                    interes.setEdad(CampoCsv.enteroCorto(campos, "edad"));
                    interes.setTipo(CampoCsv.texto(campos, "tipo"));
                    return interes;
                })
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<InteresCalculado> interesWriter(DataSource dataSource) {

        return new JdbcBatchItemWriterBuilder<InteresCalculado>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO intereses_calculados
                            (cuenta_id, nombre, tipo, saldo_inicial, tasa_aplicada,
                             interes_mensual, saldo_final)
                        VALUES
                            (:cuentaId, :nombre, :tipo, :saldoInicial, :tasaAplicada,
                             :interesMensual, :saldoFinal)
                        """)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .build();
    }

    // CAMBIO SEMANA 2: igual que en Transacciones, el step se arma con
    // FabricaStepsCarga en vez de a mano.
    @Bean
    public Step calcularInteresesStep(FabricaStepsCarga fabrica,
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<Interes> interesReader,
            InteresProcessor interesProcessor,
            JdbcBatchItemWriter<InteresCalculado> interesWriter) {

        return fabrica.construir(
                "calcularInteresesStep",
                jobRepository,
                transactionManager,
                interesReader,
                interesProcessor,
                interesWriter,
                NOMBRE_JOB,
                ARCHIVO);
    }

    // CAMBIO SEMANA 2: el decider aca solo decide entre
    // "termina bien" o "no se caragaron datos, se detiene"
    @Bean
    public Job calculoInteresesMensualesJob(JobRepository jobRepository,
            @Qualifier("calcularInteresesStep") Step calcularInteresesStep,
            ContinuidadDecider continuidadDecider,
            JobListener jobListener) {

        return new JobBuilder(NOMBRE_JOB, jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobListener)
                .start(calcularInteresesStep)
                .next(continuidadDecider)
                .on(ContinuidadDecider.SIN_DATOS).fail()
                .from(continuidadDecider)
                .on("*").end()
                .end()
                .build();
    }
}