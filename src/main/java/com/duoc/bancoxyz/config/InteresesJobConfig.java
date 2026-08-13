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
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;

import com.duoc.bancoxyz.exception.ValidacionDatosException;
import com.duoc.bancoxyz.listener.JobListener;
import com.duoc.bancoxyz.model.Interes;
import com.duoc.bancoxyz.model.InteresCalculado;
import com.duoc.bancoxyz.processor.InteresProcessor;

/**
 * 
 * InteresesJobConfig
 * Job 2 - Calculo de intereses mensuales.
 * Step 1 (unico). lee intereses.csv, aplica la tasa segun el tipo
 * y guarda el resultado (con saldo actualizado) en la tabla
 * intereses_calculados.
 */
@Configuration
public class InteresesJobConfig {
    @Bean
    public FlatFileItemReader<Interes> interesReader() {

        return new FlatFileItemReaderBuilder<Interes>()
                .name("interesReader")
                .resource(new ClassPathResource("data/intereses.csv"))
                .encoding("UTF-8")
                .linesToSkip(1)
                .delimited()
                .delimiter(",")
                .names("cuentaId", "nombre", "saldo", "edad", "tipo")
                .fieldSetMapper(fieldSet -> {

                    Interes interes = new Interes();
                    interes.setCuentaId(fieldSet.readLong("cuentaId"));
                    interes.setNombre(fieldSet.readString("nombre"));
                    interes.setSaldo(fieldSet.readBigDecimal("saldo"));
                    interes.setEdad(fieldSet.readInt("edad"));
                    interes.setTipo(fieldSet.readString("tipo"));
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

    /**
     * El reader entrega Intereses.
     * El procesor devuelve InteresCalculado.
     * El writer recibe InteresCalculado.
     */
    @Bean
    public Step calcularInteresesStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<Interes> interesReader,
            InteresProcessor interesProcessor,
            JdbcBatchItemWriter<InteresCalculado> interesWriter) {

        return new ChunkOrientedStepBuilder<Interes, InteresCalculado>(
                "calcularInteresesStep", jobRepository, 10)
                .reader(interesReader)
                .processor(interesProcessor)
                .writer(interesWriter)
                .transactionManager(transactionManager)
                .faultTolerant()
                .skip(ValidacionDatosException.class)
                .skipLimit(20)
                .build();
    }

    @Bean
    public Job calculoInteresesMensualesJob(JobRepository jobRepository,
            @Qualifier("calcularInteresesStep") Step calcularInteresesStep,
            JobListener jobListener) {

        return new JobBuilder("calculoInteresesMensualesJob", jobRepository)
                .start(calcularInteresesStep)
                .listener(jobListener)
                .build();
    }
}
