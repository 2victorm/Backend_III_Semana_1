package com.duoc.bancoxyz.processor;

import java.math.BigDecimal;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.duoc.bancoxyz.model.Transaccion;

@Component
public class TransaccionProcessor implements ItemProcessor<Transaccion, Transaccion> {

    @Override
    public Transaccion process(Transaccion transaccion) {

        if (transaccion.getMonto() == null ||
                transaccion.getMonto().compareTo(BigDecimal.ZERO) <= 0) {

            System.out.println(
                    "Transacción descartada: ID " + transaccion.getId()
                    + " - monto inválido: " + transaccion.getMonto()
            );

            return null;
        }

        return transaccion;
    }
}