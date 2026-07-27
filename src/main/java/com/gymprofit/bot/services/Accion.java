package com.gymprofit.bot.services;

/**
 * Números puros de las participaciones de empresa (F5 acciones/dividendos). Una empresa tiene un pool
 * fijo de {@link #POOL} participaciones (= porcentaje directo); el precio de cada una deriva del prestigio
 * de la empresa ({@link #precioParticipacion}) y el dividendo de cada accionista es su fracción del pool
 * sobre el pot semanal ({@link #dividendoDe}). Sin estado ni dependencias: testeable.
 */
public final class Accion {

    private Accion() {}

    /** Participaciones totales de cualquier empresa (= porcentaje: 1 participación = 1 %). */
    public static final int POOL = 100;

    /** Fracción del bote que forma el pot de dividendos cada semana (sumidero modesto: ver antiinflación). */
    public static final double FRACCION_DIVIDENDO = 0.05;

    /** Precio de una participación: el valor (prestigio) repartido entre el pool, con suelo 1. */
    public static long precioParticipacion(long prestigio) {
        return Math.max(1, prestigio / POOL);
    }

    /** Dividendo de un accionista: su fracción del pool sobre el pot (redondeo a la baja). */
    public static long dividendoDe(long pot, int participaciones) {
        return (long) Math.floor((double) pot * participaciones / POOL);
    }
}
