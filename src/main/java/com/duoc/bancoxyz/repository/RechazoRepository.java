package com.duoc.bancoxyz.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 
 * RechazoRepository
 * Con esta clase se creara un historial del motivo por el
 * que el registro fue defectuoso/danado.
 * Con esta tabla el rechazo no se pierde, sino que queda
 * guardado para un posible proceso posterior.
 */
@Repository
public class RechazoRepository {

    private static final String SQL = """
            INSERT INTO registros_rechazados
                (job_name, archivo_origen, identificador, motivo, dato_original)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public RechazoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void registrar(String jobName,
            String archivoOrigen,
            String identificador,
            String motivo,
            String datoOriginal) {

        jdbcTemplate.update(SQL, jobName, archivoOrigen, identificador, motivo, datoOriginal);

        System.out.printf("  [RECHAZADO] %s -> %s (%s)%n", identificador, motivo, archivoOrigen);
    }
}
