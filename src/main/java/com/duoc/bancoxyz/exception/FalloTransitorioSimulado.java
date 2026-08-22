package com.duoc.bancoxyz.exception;

import org.springframework.dao.TransientDataAccessException;

/**
 * FalloTransitorioSimulado
 *
 * Esta clase simula un fallo transitorio real (ej. Postgres
 * se cae un instante) para poder activarlo cuando queramos y
 * mostrar que el reintento funciona.
 *
 * Solo se dispara si banco.simular.fallo-transitorio=true.
 */
public class FalloTransitorioSimulado extends TransientDataAccessException {

    public FalloTransitorioSimulado(String mensaje) {
        super(mensaje);
    }
}
