package com.duoc.bancoxyz;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BancoXyzBatchApplication implements CommandLineRunner {

    private final JobLauncher jobLauncher;
    private final Job transaccionesJob;

    public BancoXyzBatchApplication(
            JobLauncher jobLauncher,
            Job transaccionesJob) {

        this.jobLauncher = jobLauncher;
        this.transaccionesJob = transaccionesJob;
    }

    public static void main(String[] args) {
        SpringApplication.run(BancoXyzBatchApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

        JobParameters parametros = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(transaccionesJob, parametros);
    }
}