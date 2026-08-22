package com.duoc.bancoxyz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import jakarta.annotation.PreDestroy;

// Esta clase arma los 3 hilos que van a procesar los
// datos en paralelo, en vez de que todo lo haga un solo hilo uno por uno.
@Configuration
public class TaskExecutorConfig {

    // Nombre con el que las otras clases (FabricaStepsCarga, MetricasStepListener)
    // van a pedir este mismo pool de hilos para que todos usen el mismo.
    public static final String EXECUTOR = "batchTaskExecutor";

    private ThreadPoolTaskExecutor executor;

    @Bean(EXECUTOR)
    public ThreadPoolTaskExecutor taskExecutor(
            @Value("${banco.paralelismo.hilos:3}") int hilos,
            @Value("${banco.paralelismo.cola:25}") int capacidadCola,
            @Value("${banco.paralelismo.prefijo:Batch-Thread-}") String prefijo) {

        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(hilos);
        executor.setMaxPoolSize(hilos);
        executor.setQueueCapacity(capacidadCola);
        executor.setThreadNamePrefix(prefijo);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();

        System.out.printf(
                "[POOL] TaskExecutor iniciado -> corePoolSize=%d | maxPoolSize=%d | queueCapacity=%d | prefijo='%s'%n",
                hilos, hilos, capacidadCola, prefijo);

        return executor;
    }

    // Se ejecuta automaticamente cuando la app se esta cerrando.
    // Sine sto, los 3 hilos quedarian ejecutandose y el programa nunca terminaria
    // solo.
    @PreDestroy
    public void cerrarPool() {

        if (executor != null) {
            System.out.println("[POOL] Cerrando TaskExecutor y liberando hilos.");
            executor.shutdown();
        }
    }

    // Metodo que le pregunta al pool cuantos huilos esta usando
    // para poder mostrarlo en los logs (en MetricasStepListener).
    public static String resumen(ThreadPoolTaskExecutor executor) {

        return String.format(
                "hilos activos=%d | tamano pool=%d | tareas en cola=%d",
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getThreadPoolExecutor().getQueue().size());
    }
}