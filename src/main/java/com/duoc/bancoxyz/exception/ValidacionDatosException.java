package com.duoc.bancoxyz.exception;

/**
 * ValidacionDatosException
 * 
 * Validacion para registros danados. Los steps estan configurados con skip
 * para que se omita ese registro y pase al siguiente.
 */
public class ValidacionDatosException extends RuntimeException {

    public ValidacionDatosException(String mensaje) {
        super(mensaje);
    }
}
