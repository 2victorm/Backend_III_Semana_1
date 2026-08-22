package com.duoc.bancoxyz.writer;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

import com.duoc.bancoxyz.exception.FalloTransitorioSimulado;

/**
 * WriterConFalloSimulado
 *
 * Esta clase es un "disfraz" que se pone ENCIMA del writer real,
 * para poder DEMOSTRAR que el reintento funciona.
 * 
 * Un fallo transitorio de verdad no se puede pedir que ocurra.
 * Sin esta clase, la politica de retry quedaria configurada en el codigo pero
 * nunca se veria disparada en la practica.
 * 
 */
public class WriterConFalloSimulado<T> implements ItemWriter<T> {

    private final ItemWriter<T> writerReal;
    private final int fallosAProvocar;
    private final AtomicInteger fallosProvocados = new AtomicInteger();

    public WriterConFalloSimulado(ItemWriter<T> writerReal, int fallosAProvocar) {
        this.writerReal = writerReal;
        this.fallosAProvocar = fallosAProvocar;
    }

    @Override
    public void write(Chunk<? extends T> chunk) throws Exception {

        if (fallosProvocados.get() < fallosAProvocar) {

            int numero = fallosProvocados.incrementAndGet();

            System.out.printf("  [FALLO SIMULADO] [%s] intento %d/%d sobre un chunk de %d registros%n",
                    Thread.currentThread().getName(), numero, fallosAProvocar, chunk.size());

            throw new FalloTransitorioSimulado(
                    "Fallo transitorio simulado numero " + numero + " (deadlock ficticio)");
        }

        writerReal.write(chunk);
    }
}
