package com.duoc.bancoxyz.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * ParseadorFechas
 * Los sistemas legacy no siempre vienen con el mismo formato de fecha.
 * Con esta clase se probaran varios formatos antes de dar por defectuoso/danado
 * el dato. Devolvera null si ninguno funciona.
 */
public final class ParseadorFechas {

    private static final DateTimeFormatter[] FORMATOS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    };

    private ParseadorFechas() {
    }

    public static LocalDate parsear(String valor) {

        if (valor == null || valor.isBlank()) {
            return null;
        }

        String limpio = valor.trim();

        for (DateTimeFormatter formato : FORMATOS) {
            try {
                return LocalDate.parse(limpio, formato);
            } catch (Exception e) {

            }
        }

        return null;
    }
}
