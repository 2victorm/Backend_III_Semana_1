package com.duoc.bancoxyz.decider;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ContinuidadDecider
 *
 * Esta clase se ejecuta justo despues del step que carga el CSV
 * y ANTES del step que arma el resumen/informe final.
 * Su trabajo es revisar como salio esa carga y decidir si vale la pena seguir.
 * 
 * El step de agregacion (resumen, informe) lee lo que haya quedado en la tabla.
 * Si la carga salio mal y quedaron pocos datos o ninguno: el resumen se genera
 * igual
 * con informacion incompleta y se ve como si fuera un resultado normal.
 * Nadie se daria cuenta de que algo fallo. Este decider evita eso.
 */
@Component
public class ContinuidadDecider implements JobExecutionDecider {

    public static final String CONTINUAR = "CONTINUAR";
    public static final String DEGRADADO = "DEGRADADO";
    public static final String SIN_DATOS = "SIN_DATOS";

    private final double umbralRechazo;

    public ContinuidadDecider(
            @Value("${banco.calidad.umbral-rechazo:0.5}") double umbralRechazo) {

        this.umbralRechazo = umbralRechazo;
    }

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {

        StepExecution ultimo = stepExecution != null
                ? stepExecution
                : ultimoStep(jobExecution);

        if (ultimo == null) {
            System.out.println("[DECIDER] No hay informacion del step previo -> SIN_DATOS");
            return new FlowExecutionStatus(SIN_DATOS);
        }

        long leidos = ultimo.getReadCount();
        long escritos = ultimo.getWriteCount();
        long descartados = Math.max(leidos - escritos, 0);

        if (escritos == 0) {

            System.out.printf(
                    "[DECIDER] Step '%s': 0 registros escritos de %d leidos -> %s "
                            + "(se detiene, no hay base para agregar)%n",
                    ultimo.getStepName(), leidos, SIN_DATOS);

            return new FlowExecutionStatus(SIN_DATOS);
        }

        double proporcion = leidos == 0 ? 0.0 : (double) descartados / leidos;

        if (proporcion > umbralRechazo) {

            System.out.printf(
                    "[DECIDER] Step '%s': %d de %d registros descartados (%.1f%%, umbral %.1f%%) -> %s%n",
                    ultimo.getStepName(), descartados, leidos,
                    proporcion * 100, umbralRechazo * 100, DEGRADADO);

            return new FlowExecutionStatus(DEGRADADO);
        }

        System.out.printf(
                "[DECIDER] Step '%s': %d de %d registros descartados (%.1f%%) -> %s%n",
                ultimo.getStepName(), descartados, leidos, proporcion * 100, CONTINUAR);

        return new FlowExecutionStatus(CONTINUAR);
    }

    private StepExecution ultimoStep(JobExecution jobExecution) {

        return jobExecution.getStepExecutions().stream()
                .reduce((primero, segundo) -> segundo)
                .orElse(null);
    }
}
