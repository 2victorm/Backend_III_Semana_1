package com.duoc.bancoxyz.processor;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.duoc.bancoxyz.exception.ValidacionDatosException;
import com.duoc.bancoxyz.model.Transaccion;
import com.duoc.bancoxyz.repository.RechazoRepository;

/**
 * 
 * TransaccionProcessor
 * Se validan las transacciones antes de guardarlas.
 * Se consideran las fechas, montos, los tipos y duplicados.
 */
@Component
public class TransaccionProcessor implements ItemProcessor<Transaccion, Transaccion> {

    private static final String JOB = "reporteTransaccionesDiariasJob";
    private static final String ARCHIVO = "transacciones.csv";

    private final RechazoRepository rechazoRepository;

    private final Set<String> huellasVistas = new HashSet<>();

    public TransaccionProcessor(RechazoRepository rechazoRepository) {
        this.rechazoRepository = rechazoRepository;
    }

    @Override
    public Transaccion process(Transaccion transaccion) {

        String id = String.valueOf(transaccion.getId());

        // Validacion fecha
        if (transaccion.getFecha() == null) {
            throw new ValidacionDatosException(
                    "Transaccion " + id + ": la fecha no pudo interpretarse");
        }
        // Validacion montos
        if (transaccion.getMonto() == null
                || transaccion.getMonto().compareTo(BigDecimal.ZERO) <= 0) {

            rechazoRepository.registrar(JOB, ARCHIVO, id,
                    "Transacción descartada: ID " + transaccion.getId()
                            + " - monto inválido: " + transaccion.getMonto(),
                    transaccion.toString());

            return null;
        }
        // Validacion de tipo (credito, debito)
        String tipo = transaccion.getTipo() == null
                ? ""
                : transaccion.getTipo().trim().toUpperCase();

        if (!tipo.equals("DEBITO") && !tipo.equals("CREDITO")) {

            rechazoRepository.registrar(JOB, ARCHIVO, id,
                    "Tipo de transaccion no reconocido: '" + tipo + "'", transaccion.toString());
            return null;
        }

        transaccion.setTipo(tipo);

        // Verificacion de duplicados
        String huella = transaccion.getFecha() + "|" + transaccion.getMonto() + "|" + tipo;

        if (!huellasVistas.add(huella)) {

            rechazoRepository.registrar(JOB, ARCHIVO, id,
                    "Registro duplicado (misma fecha, monto y tipo)", transaccion.toString());
            return null;
        }

        return transaccion;
    }
}