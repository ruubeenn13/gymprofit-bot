package com.gymprofit.bot.services;

/**
 * Números del préstamo empresarial (F5d): funciones puras que definen cuánto puede pedir una empresa,
 * cuánto acaba debiendo con el interés y en qué cuota semanal se devuelve.
 *
 * <p>Una empresa puede pedir prestado hasta un límite que escala con su nivel ({@link #limite}); sobre
 * el principal se aplica un interés fijo ({@link #deudaConInteres}), de modo que a medio plazo el
 * préstamo es un <b>sumidero neto</b> (la empresa devuelve más de lo recibido, quemando la diferencia
 * como el resto de la economía antiinflación). La devolución se reparte en un número fijo de cuotas
 * semanales ({@link #cuota}) que cobra el job de la cuota de F5b junto al impuesto.
 *
 * <p>Los tres pesos (límite por nivel, interés y plazo) son tunables si el balance de juego lo pide; se
 * centralizan aquí para que el módulo de préstamos sea la única fuente de verdad.
 */
public final class Prestamo {

    // Crédito máximo por nivel: el límite de una empresa es su nivel * este factor.
    private static final long LIMITE_POR_NIVEL = 20_000L;
    // Interés fijo sobre el principal: lo que hace del préstamo un sumidero neto a medio plazo.
    private static final double INTERES = 0.20;
    // Semanas en las que se reparte la devolución de la deuda.
    private static final int PLAZO_SEMANAS = 4;

    // Clase de utilidad: solo expone funciones puras estáticas, no se instancia.
    private Prestamo() {
    }

    /**
     * Crédito máximo que la empresa puede pedir según su nivel. Escala linealmente para que crecer
     * amplíe la capacidad de endeudamiento.
     *
     * @param nivel nivel de la empresa
     * @return límite de préstamo ({@code nivel * LIMITE_POR_NIVEL})
     */
    public static long limite(int nivel) {
        return nivel * LIMITE_POR_NIVEL;
    }

    /**
     * Deuda total a devolver por un principal dado, aplicando el interés fijo. Es lo que se anota como
     * {@code deuda} al conceder el préstamo (mayor que lo recibido: la diferencia es el sumidero).
     *
     * @param principal cantidad prestada a la empresa
     * @return deuda con interés ({@code round(principal * (1 + INTERES))})
     */
    public static long deudaConInteres(long principal) {
        return Math.round(principal * (1 + INTERES));
    }

    /**
     * Cuota semanal para amortizar una deuda en el plazo fijo. Divide hacia arriba ({@code ceil}) para
     * que la suma de cuotas nunca deje deuda residual: la última cuota puede ser algo menor.
     *
     * @param deuda deuda pendiente de devolver
     * @return cuota semanal ({@code ceil(deuda / PLAZO_SEMANAS)})
     */
    public static long cuota(long deuda) {
        return (deuda + PLAZO_SEMANAS - 1) / PLAZO_SEMANAS;
    }
}
