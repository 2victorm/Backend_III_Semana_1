package com.duoc.bancoxyz.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ResumenDiario {

    private LocalDate fecha;
    private Integer cantidadTransacciones;
    private BigDecimal totalDebitos;
    private BigDecimal totalCreditos;
    private BigDecimal montoMayor;

    public ResumenDiario() {
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public Integer getCantidadTransacciones() {
        return cantidadTransacciones;
    }

    public void setCantidadTransacciones(Integer cantidadTransacciones) {
        this.cantidadTransacciones = cantidadTransacciones;
    }

    public BigDecimal getTotalDebitos() {
        return totalDebitos;
    }

    public void setTotalDebitos(BigDecimal totalDebitos) {
        this.totalDebitos = totalDebitos;
    }

    public BigDecimal getTotalCreditos() {
        return totalCreditos;
    }

    public void setTotalCreditos(BigDecimal totalCreditos) {
        this.totalCreditos = totalCreditos;
    }

    public BigDecimal getMontoMayor() {
        return montoMayor;
    }

    public void setMontoMayor(BigDecimal montoMayor) {
        this.montoMayor = montoMayor;
    }
}
