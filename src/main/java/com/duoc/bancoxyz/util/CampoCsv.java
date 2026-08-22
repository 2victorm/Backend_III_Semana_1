package com.duoc.bancoxyz.util;

import java.math.BigDecimal;

import org.springframework.batch.infrastructure.item.file.transform.FieldSet;

// Si el campo esta mal, en vez de explotar devuelve null, y deja que el ItemProcessor
// sea quien decida que hacer (rechazar, o corregir).
public final class CampoCsv {

    private CampoCsv() {
    }

    /** Devuelve el texto sin espacios sobrantes, o null si viene vacio. */
    public static String texto(FieldSet campos, String nombre) {

        String valor = campos.readString(nombre);

        if (valor == null || valor.isBlank()) {
            return null;
        }

        return valor.trim();
    }

    /** Devuelve el texto normalizado a MAYUSCULAS, o null si viene vacio. */
    public static String textoMayuscula(FieldSet campos, String nombre) {

        String valor = texto(campos, nombre);
        return valor == null ? null : valor.toUpperCase();
    }

    /** Devuelve el texto normalizado a minusculas, o null si viene vacio. */
    public static String textoMinuscula(FieldSet campos, String nombre) {

        String valor = texto(campos, nombre);
        return valor == null ? null : valor.toLowerCase();
    }

    /** Devuelve el valor como Long, o null si viene vacio o no es numerico. */
    public static Long entero(FieldSet campos, String nombre) {

        String valor = texto(campos, nombre);

        if (valor == null) {
            return null;
        }

        try {
            return Long.valueOf(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Devuelve el valor como Integer, o null si viene vacio o no es numerico. */
    public static Integer enteroCorto(FieldSet campos, String nombre) {

        String valor = texto(campos, nombre);

        if (valor == null) {
            return null;
        }

        try {
            return Integer.valueOf(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Devuelve el valor como BigDecimal, o null si viene vacio o no es numerico.
     */
    public static BigDecimal decimal(FieldSet campos, String nombre) {

        String valor = texto(campos, nombre);

        if (valor == null) {
            return null;
        }

        try {
            return new BigDecimal(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
