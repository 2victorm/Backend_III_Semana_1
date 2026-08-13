package com.duoc.bancoxyz.processor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.duoc.bancoxyz.model.Interes;
import com.duoc.bancoxyz.model.InteresCalculado;
import com.duoc.bancoxyz.repository.RechazoRepository;

/**
 * 
 * InteresProcessor
 * Aplica interes mensual a cuentas de ahorro y prestamos.
 * Se valida y transforma el dato en un resultado calculado.
 * 
 * Se rechazan saldos nulos, negativos o en 0.
 * Se rechazan edades fueran de un rango logico.
 * Se rechazan tipos que no sean ahorro o prestamo.
 * Se rechazan cuentas duplicadas.
 * 
 */
@Component
public class InteresProcessor implements ItemProcessor<Interes, InteresCalculado> {

    private static final String JOB = "calculoInteresesMensualesJob";
    private static final String ARCHIVO = "intereses.csv";

    private final RechazoRepository rechazoRepository;
    private final BigDecimal tasaAhorro;
    private final BigDecimal tasaPrestamo;
    private final int edadMinima;
    private final int edadMaxima;

    private final Set<String> huellasVistas = new HashSet<>();

    public InteresProcessor(RechazoRepository rechazoRepository,
            @Value("${banco.tasa.ahorro}") BigDecimal tasaAhorro,
            @Value("${banco.tasa.prestamo}") BigDecimal tasaPrestamo,
            @Value("${banco.edad.minima}") int edadMinima,
            @Value("${banco.edad.maxima}") int edadMaxima) {

        this.rechazoRepository = rechazoRepository;
        this.tasaAhorro = tasaAhorro;
        this.tasaPrestamo = tasaPrestamo;
        this.edadMinima = edadMinima;
        this.edadMaxima = edadMaxima;
    }

    @Override
    public InteresCalculado process(Interes interes) {

        String id = String.valueOf(interes.getCuentaId());

        // Validacion de saldos
        if (interes.getSaldo() == null
                || interes.getSaldo().compareTo(BigDecimal.ZERO) <= 0) {

            rechazoRepository.registrar(JOB, ARCHIVO, id,
                    "Saldo invalido (nulo, cero o negativo)", interes.toString());
            return null;
        }

        // Validacion de edades
        if (interes.getEdad() == null
                || interes.getEdad() < edadMinima
                || interes.getEdad() > edadMaxima) {

            rechazoRepository.registrar(JOB, ARCHIVO, id,
                    "Edad fuera de rango (" + edadMinima + "-" + edadMaxima + "): " + interes.getEdad(),
                    interes.toString());
            return null;
        }

        // Validacion de tipo (ahorro, prestamo)
        String tipo = interes.getTipo() == null
                ? ""
                : interes.getTipo().trim().toLowerCase();

        BigDecimal tasa;

        if (tipo.equals("ahorro")) {
            tasa = tasaAhorro;
        } else if (tipo.equals("prestamo")) {
            tasa = tasaPrestamo;
        } else {
            rechazoRepository.registrar(JOB, ARCHIVO, id,
                    "Tipo de cuenta no soportado: '" + tipo + "'", interes.toString());
            return null;
        }

        // Verificacion de duplicados
        String huella = interes.getNombre() + "|" + interes.getSaldo()
                + "|" + interes.getEdad() + "|" + tipo;

        if (!huellasVistas.add(huella)) {

            rechazoRepository.registrar(JOB, ARCHIVO, id,
                    "Registro duplicado (mismo titular, saldo, edad y tipo)", interes.toString());
            return null;
        }

        // Calculo: interes = saldo * tasa, redondeado a 2 decimales
        BigDecimal interesMensual = interes.getSaldo()
                .multiply(tasa)
                .setScale(2, RoundingMode.HALF_UP);

        InteresCalculado resultado = new InteresCalculado();
        resultado.setCuentaId(interes.getCuentaId());
        resultado.setNombre(interes.getNombre().trim());
        resultado.setTipo(tipo);
        resultado.setSaldoInicial(interes.getSaldo().setScale(2, RoundingMode.HALF_UP));
        resultado.setTasaAplicada(tasa);
        resultado.setInteresMensual(interesMensual);
        resultado.setSaldoFinal(interes.getSaldo().add(interesMensual).setScale(2, RoundingMode.HALF_UP));

        return resultado;
    }
}
