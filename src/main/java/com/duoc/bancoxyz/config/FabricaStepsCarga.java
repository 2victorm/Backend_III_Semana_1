package com.duoc.bancoxyz.config;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.SynchronizedItemStreamReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import com.duoc.bancoxyz.exception.FalloTransitorioSimulado;
import com.duoc.bancoxyz.listener.MetricasStepListener;
import com.duoc.bancoxyz.listener.RechazoSkipListener;
import com.duoc.bancoxyz.policy.PoliticaOmisionPersonalizada;
import com.duoc.bancoxyz.repository.RechazoRepository;
import com.duoc.bancoxyz.writer.WriterConFalloSimulado;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.TransientDataAccessException;

// Los Jobs neecsitan el step de carga configurado igual (mismos 3 hilos, tamano,
// reglas de omision y que se reintenta). En vez de repetir esta config 3 veces en 
// cada job, esta clase arma una sola para los 3 jobs.
@Component
public class FabricaStepsCarga {

    private final ThreadPoolTaskExecutor taskExecutor;
    private final MetricasStepListener metricasStepListener;
    private final RechazoRepository rechazoRepository;

    private final int tamanoChunk;
    private final long limiteOmisiones;
    private final int limiteReintentos;
    private final boolean simularFalloTransitorio;
    private final int fallosASimular;

    public FabricaStepsCarga(
            @Qualifier(TaskExecutorConfig.EXECUTOR) ThreadPoolTaskExecutor taskExecutor,
            MetricasStepListener metricasStepListener,
            RechazoRepository rechazoRepository,
            @Value("${banco.chunk.tamano:5}") int tamanoChunk,
            @Value("${banco.skip.limite:100}") long limiteOmisiones,
            @Value("${banco.retry.intentos:3}") int limiteReintentos,
            @Value("${banco.simular.fallo-transitorio:false}") boolean simularFalloTransitorio,
            @Value("${banco.simular.cantidad-fallos:2}") int fallosASimular) {

        this.taskExecutor = taskExecutor;
        this.metricasStepListener = metricasStepListener;
        this.rechazoRepository = rechazoRepository;
        this.tamanoChunk = tamanoChunk;
        this.limiteOmisiones = limiteOmisiones;
        this.limiteReintentos = limiteReintentos;
        this.simularFalloTransitorio = simularFalloTransitorio;
        this.fallosASimular = fallosASimular;
    }

    /**
     * Construye un step de carga paralelo, tolerante a fallos y auditado.
     *
     * @param nombreStep         nombre con el que aparece en el JobRepository y en
     *                           los logs
     * @param jobRepository      repositorio de metadatos
     * @param transactionManager gestor transaccional del chunk
     * @param reader             lector original, se envuelve para hacerlo
     *                           thread-safe
     * @param processor          validaciones y transformaciones
     * @param writer             destino final de los registros validos
     * @param nombreJob          job al que pertenece, se guarda en la auditoria
     * @param archivoOrigen      archivo de entrada, se guarda en la auditoria
     */
    public <I, O> Step construir(String nombreStep,
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemStreamReader<I> reader,
            ItemProcessor<I, O> processor,
            ItemWriter<O> writer,
            String nombreJob,
            String archivoOrigen) {

        ItemWriter<O> writerFinal = simularFalloTransitorio
                ? new WriterConFalloSimulado<>(writer, fallosASimular)
                : writer;

        return new StepBuilder(nombreStep, jobRepository)
                .<I, O>chunk(tamanoChunk, transactionManager)
                .reader(new SynchronizedItemStreamReader<>(reader))
                .processor(processor)
                .writer(writerFinal)
                .faultTolerant()
                .skipPolicy(new PoliticaOmisionPersonalizada(nombreStep, limiteOmisiones))
                .retryLimit(limiteReintentos)
                .retry(TransientDataAccessException.class)
                .retry(CannotAcquireLockException.class)
                .retry(DeadlockLoserDataAccessException.class)
                .retry(FalloTransitorioSimulado.class)
                .listener(new RechazoSkipListener<I, O>(
                        rechazoRepository, nombreJob, archivoOrigen))
                .listener(metricasStepListener)
                .taskExecutor(taskExecutor)
                .build();
    }

    public int getTamanoChunk() {
        return tamanoChunk;
    }
}
