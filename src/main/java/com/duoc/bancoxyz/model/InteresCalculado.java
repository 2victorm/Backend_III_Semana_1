package com.duoc.bancoxyz.model;

import java.math.BigDecimal;

/**
 * Resultado del calculo de intereses (dato de SALIDA).
 * El InteresProcessor transforma un Interes en un InteresCalculado:
 * por eso el Step del Job 2 es ItemProcessor<Interes, InteresCalculado>.
 */
public class InteresCalculado {

    private Long cuentaId;
    private String nombre;
    private String tipo;
    private BigDecimal saldoInicial;
    private BigDecimal tasaAplicada;
    private BigDecimal interesMensual;
    private BigDecimal saldoFinal;

    public InteresCalculado() {
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getSaldoInicial() {
        return saldoInicial;
    }

    public void setSaldoInicial(BigDecimal saldoInicial) {
        this.saldoInicial = saldoInicial;
    }

    public BigDecimal getTasaAplicada() {
        return tasaAplicada;
    }

    public void setTasaAplicada(BigDecimal tasaAplicada) {
        this.tasaAplicada = tasaAplicada;
    }

    public BigDecimal getInteresMensual() {
        return interesMensual;
    }

    public void setInteresMensual(BigDecimal interesMensual) {
        this.interesMensual = interesMensual;
    }

    public BigDecimal getSaldoFinal() {
        return saldoFinal;
    }

    public void setSaldoFinal(BigDecimal saldoFinal) {
        this.saldoFinal = saldoFinal;
    }
}
