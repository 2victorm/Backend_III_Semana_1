package com.duoc.bancoxyz.policy;

import java.time.format.DateTimeParseException;

import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;

import com.duoc.bancoxyz.exception.ValidacionDatosException;

/**
 * PoliticaOmisionPersonalizada
 *
 * Esta clase decide, cada vez que algo falla durante el step, si ese fallo
 * se OMITE (se descarta ese registro y se sigue con el siguiente) o si el
 * step se debe detener.
 * 
 */
public class PoliticaOmisionPersonalizada implements SkipPolicy {

    private final String nombreStep;
    private final long limiteOmisiones;

    public PoliticaOmisionPersonalizada(String nombreStep, long limiteOmisiones) {
        this.nombreStep = nombreStep;
        this.limiteOmisiones = limiteOmisiones;
    }

    @Override
    public boolean shouldSkip(Throwable causa, long omitidosHastaAhora)
            throws SkipLimitExceededException {

        // Fallo transitorio de infraestructura: lo maneja el retry, no el skip.
        if (esTransitorio(causa)) {
            return false;
        }

        if (!esOmitible(causa)) {
            return false;
        }

        if (omitidosHastaAhora >= limiteOmisiones) {

            System.out.printf(
                    "[POLITICA] Step '%s': limite de omisiones alcanzado (%d). "
                            + "Se detiene el step para no generar un resultado incompleto.%n",
                    nombreStep, limiteOmisiones);

            throw new SkipLimitExceededException(limiteOmisiones, causa);
        }

        return true;
    }

    private boolean esOmitible(Throwable causa) {

        return contiene(causa, ValidacionDatosException.class)
                || contiene(causa, FlatFileParseException.class)
                || contiene(causa, DateTimeParseException.class)
                || contiene(causa, NumberFormatException.class)
                || contiene(causa, DataIntegrityViolationException.class);
    }

    private boolean esTransitorio(Throwable causa) {
        return contiene(causa, TransientDataAccessException.class);
    }

    /**
     * Spring Batch envuelve las excepciones (por ejemplo, un NumberFormatException
     * viaja dentro de un FlatFileParseException). Hay que recorrer la cadena de
     * causas o la politica no reconoceria nada.
     */
    private boolean contiene(Throwable causa, Class<? extends Throwable> tipo) {

        Throwable actual = causa;
        int profundidad = 0;

        while (actual != null && profundidad < 10) {

            if (tipo.isInstance(actual)) {
                return true;
            }

            actual = actual.getCause();
            profundidad++;
        }

        return false;
    }
}
