package com.duoc.bancoxyz.listener;

import org.springframework.batch.core.listener.SkipListener;

import com.duoc.bancoxyz.repository.RechazoRepository;

/**
 * RechazoSkipListener
 *
 * Deja constancia de cada registro omitido por la politica de omision.
 *
 * Cuando un chunk falla, Spring Batch lo vuelve a procesar item por item para
 * aislar cual fue el registro culpable. Si el INSERT de auditoria estuviera en
 * el processor, ese reprocesamiento lo ejecutaria de nuevo y la tabla de
 * rechazos terminaria con filas duplicadas.
 *
 * El SkipListener, en cambio, se invoca una sola vez por item omitido y fuera
 * de la transaccion del chunk, que es justo lo que se necesita para auditar:
 * si el chunk hace rollback, el registro del error sobrevive.
 *
 * Se crea una instancia por step (no es un @Component) para que cada una sepa
 * a que job y a que archivo pertenece lo que esta auditando.
 */
public class RechazoSkipListener<I, O> implements SkipListener<I, O> {

    private final RechazoRepository rechazoRepository;
    private final String nombreJob;
    private final String archivoOrigen;

    public RechazoSkipListener(RechazoRepository rechazoRepository,
            String nombreJob,
            String archivoOrigen) {

        this.rechazoRepository = rechazoRepository;
        this.nombreJob = nombreJob;
        this.archivoOrigen = archivoOrigen;
    }

    @Override
    public void onSkipInRead(Throwable causa) {

        registrar("LECTURA", "(linea ilegible)", causa, null);
    }

    @Override
    public void onSkipInProcess(I item, Throwable causa) {

        registrar("PROCESO", identificar(item), causa, item);
    }

    @Override
    public void onSkipInWrite(O item, Throwable causa) {

        registrar("ESCRITURA", identificar(item), causa, item);
    }

    private void registrar(String etapa, String identificador, Throwable causa, Object item) {

        String motivo = etapa + ": " + mensajeUtil(causa);
        String datoOriginal = item == null ? null : recortar(item.toString(), 480);

        rechazoRepository.registrar(nombreJob, archivoOrigen, identificador, motivo, datoOriginal);

        System.out.printf("  [OMITIDO] [%s] %s -> %s%n",
                Thread.currentThread().getName(), identificador, motivo);
    }

    /**
     * Devuelve el mensaje mas especifico de la cadena de causas.
     */
    private String mensajeUtil(Throwable causa) {

        if (causa == null) {
            return "causa desconocida";
        }

        Throwable raiz = causa;
        int profundidad = 0;

        while (raiz.getCause() != null && profundidad < 10) {
            raiz = raiz.getCause();
            profundidad++;
        }

        String mensaje = raiz.getMessage();

        if (mensaje == null || mensaje.isBlank()) {
            mensaje = raiz.getClass().getSimpleName();
        }

        return recortar(mensaje, 200);
    }

    private String identificar(Object item) {

        if (item == null) {
            return "(sin identificador)";
        }

        return recortar(item.toString(), 60);
    }

    private String recortar(String texto, int maximo) {

        if (texto.length() <= maximo) {
            return texto;
        }

        return texto.substring(0, maximo - 3) + "...";
    }
}
