package com.gymprofit.bot.services;

/**
 * Números puros de la cuota de mercado de empresa (F5 reputación/competencia). La cuota de una empresa es
 * su parte del prestigio de su rama; comparada con la cuota "justa" (1/N) da una cuota <b>relativa</b>
 * ({@link #relativa}) que escala sus ingresos por venta ({@link #factorVenta}): líder de rama vende con
 * prima, rezagado con penalización, media neutra. Acotado a ±25 % (antiinflación). Sin estado.
 */
public final class Cuota {

    private Cuota() {}

    /** Pendiente del factor respecto a la cuota relativa. */
    public static final double SENSIBILIDAD = 0.25;
    /** Tope de penalización de venta (cuota marginal). */
    public static final double MIN = 0.75;
    /** Tope de prima de venta (dominio de la rama). */
    public static final double MAX = 1.25;

    /**
     * Cuota relativa: {@code (propio/total) / (1/n) = propio*n/total}. 1.0 = te toca tu parte media.
     * Defensivo: con {@code total <= 0} o {@code n <= 1} (sin competencia) devuelve 1.0.
     */
    public static double relativa(long prestigioPropio, long prestigioTotal, int n) {
        if (prestigioTotal <= 0 || n <= 1) {
            return 1.0;
        }
        return (double) prestigioPropio * n / prestigioTotal;
    }

    /** Factor de venta: {@code clamp(1 + SENSIBILIDAD*(relativa-1), MIN, MAX)}. */
    public static double factorVenta(double relativa) {
        double f = 1 + SENSIBILIDAD * (relativa - 1);
        return Math.max(MIN, Math.min(MAX, f));
    }
}
