package com.duoc.bancoxyz;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 
 * BancoXyzBatchApplication
 * Punto de entrada de la aplicacion.
 */
@SpringBootApplication
public class BancoXyzBatchApplication implements CommandLineRunner {

    private final JobLauncher jobLauncher;
    private final Job reporteTransaccionesDiariasJob;
    private final Job calculoInteresesMensualesJob;
    private final Job estadosCuentaAnualesJob;

    public BancoXyzBatchApplication(
            JobLauncher jobLauncher,
            @Qualifier("reporteTransaccionesDiariasJob") Job reporteTransaccionesDiariasJob,
            @Qualifier("calculoInteresesMensualesJob") Job calculoInteresesMensualesJob,
            @Qualifier("estadosCuentaAnualesJob") Job estadosCuentaAnualesJob) {

        this.jobLauncher = jobLauncher;
        this.reporteTransaccionesDiariasJob = reporteTransaccionesDiariasJob;
        this.calculoInteresesMensualesJob = calculoInteresesMensualesJob;
        this.estadosCuentaAnualesJob = estadosCuentaAnualesJob;
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext contexto = SpringApplication.run(
                BancoXyzBatchApplication.class, args);
        System.exit(SpringApplication.exit(contexto));
    }

    @Override
    public void run(String... args) throws Exception {

        JobParameters parametros = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(reporteTransaccionesDiariasJob, parametros);
        jobLauncher.run(calculoInteresesMensualesJob, parametros);
        jobLauncher.run(estadosCuentaAnualesJob, parametros);
    }
}