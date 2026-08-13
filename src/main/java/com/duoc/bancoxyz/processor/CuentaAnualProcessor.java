package com.duoc.bancoxyz.processor;

import java.math.BigDecimal;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.duoc.bancoxyz.exception.ValidacionDatosException;
import com.duoc.bancoxyz.model.CuentaAnual;
import com.duoc.bancoxyz.repository.RechazoRepository;

/**
 * 
 * CuentaAnualProcessor
 * Limpia los movimientos anuales antes de guardarlos.
 * Se validan las fechas, los montos, el tipo de movimiento, la descripcion,
 * signo del monto.
 * Para Descripcion no se rechaza, se corrige.
 * Para Signo del monto se normaliza segun el movimiento.
 */
@Component
public class CuentaAnualProcessor implements ItemProcessor<CuentaAnual, CuentaAnual> {

    private static final String JOB = "estadosCuentaAnualesJob";
    private static final String ARCHIVO = "cuentas_anuales.csv";

    private final RechazoRepository rechazoRepository;

    public CuentaAnualProcessor(RechazoRepository rechazoRepository) {
        this.rechazoRepository = rechazoRepository;
    }

    @Override
    public CuentaAnual process(CuentaAnual movimiento) {

        String id = "cuenta " + movimiento.getCuentaId();

        // Validacion de fechas
        if (movimiento.getFecha() == null) {
            throw new ValidacionDatosException(
                    id + ": la fecha no pudo interpretarse");
        }

        // Validacion de montos
        if (movimiento.getMonto() == null
                || movimiento.getMonto().compareTo(BigDecimal.ZERO) == 0) {

            rechazoRepository.registrar(JOB, ARCHIVO, id,
                    "Monto invalido (nulo o cero)", movimiento.toString());
            return null;
        }

        // Validacion de tipo de movimiento
        String tipo = movimiento.getTransaccion() == null
                ? ""
                : movimiento.getTransaccion().trim().toUpperCase();

        if (!tipo.equals("DEPOSITO") && !tipo.equals("RETIRO") && !tipo.equals("COMPRA")) {

            rechazoRepository.registrar(JOB, ARCHIVO, id,
                    "Tipo de movimiento no reconocido: '" + tipo + "'", movimiento.toString());
            return null;
        }

        movimiento.setTransaccion(tipo);

        // Validacion de descripcion
        if (movimiento.getDescripcion() == null || movimiento.getDescripcion().isBlank()) {
            movimiento.setDescripcion("SIN DESCRIPCION");
        } else {
            movimiento.setDescripcion(movimiento.getDescripcion().trim());
        }

        // Verificacion de signo segun el tipo de movimiento
        BigDecimal montoAbsoluto = movimiento.getMonto().abs();

        if (tipo.equals("DEPOSITO")) {
            movimiento.setMonto(montoAbsoluto);
        } else {
            movimiento.setMonto(montoAbsoluto.negate());
        }

        return movimiento;
    }
}
