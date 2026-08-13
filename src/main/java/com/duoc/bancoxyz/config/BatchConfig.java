package com.duoc.bancoxyz.config;

import com.duoc.bancoxyz.model.Transaccion;
import com.duoc.bancoxyz.processor.TransaccionProcessor;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;

import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;

import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class BatchConfig {

        @Bean
                public FlatFileItemReader<Transaccion> transaccionReader() {

        return new FlatFileItemReaderBuilder<Transaccion>()
                .name("transaccionReader")
                .resource(new ClassPathResource("data/transacciones.csv"))
                .delimited()
                .names("id", "fecha", "monto", "tipo")
                .fieldSetMapper(fieldSet -> {
                        Transaccion transaccion = new Transaccion();

                        transaccion.setId(fieldSet.readLong("id"));
                        transaccion.setFecha(
                                java.time.LocalDate.parse(fieldSet.readString("fecha"))
                        );
                        transaccion.setMonto(
                                fieldSet.readBigDecimal("monto")
                        );
                        transaccion.setTipo(
                                fieldSet.readString("tipo")
                        );

                        return transaccion;
                })
                .linesToSkip(1)
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
                .itemSqlParameterSourceProvider(BeanPropertySqlParameterSource::new)
                .build();
    }

    @Bean
    public Step transaccionesStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<Transaccion> transaccionReader,
            TransaccionProcessor transaccionProcessor,
            JdbcBatchItemWriter<Transaccion> transaccionWriter) {

        return new StepBuilder("transaccionesStep", jobRepository)
                .<Transaccion, Transaccion>chunk(10, transactionManager)
                .reader(transaccionReader)
                .processor(transaccionProcessor)
                .writer(transaccionWriter)
                .build();
    }

    @Bean
    public Job transaccionesJob(
            JobRepository jobRepository,
            Step transaccionesStep) {

        return new JobBuilder("transaccionesJob", jobRepository)
                .start(transaccionesStep)
                .build();
    }
}