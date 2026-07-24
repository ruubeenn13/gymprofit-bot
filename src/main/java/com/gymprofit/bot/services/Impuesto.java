package com.gymprofit.bot.services;

/**
 * Numeros del impuesto de empresa (F5b): funciones puras que definen la cuota semanal que toda
 * empresa debe pagar por existir y el umbral de morosidad que la lleva a la quiebra.
 *
 * <p>La cuota es el <b>coste de existir</b>: cada semana se quema del bote una cantidad que escala
 * con el nivel ({@link #cuota}), de modo que subir de nivel no sale gratis y una empresa parada
 * (sin ingresos por curro) sangra hasta quedarse sin bote. Como en {@link Produccion}, el dinero
 * cobrado se quema (sumidero antiinflacion): no va a nadie. Si el bote no cubre la cuota se anota un
 * impago; a {@link #MOROSIDAD_MAX} impagos <b>consecutivos</b> la empresa quiebra y se disuelve. El
 * contador se resetea en cuanto una cuota se paga.
 *
 * <p>Ambos pesos son tunables si el balance de juego lo pide; se centralizan aqui para que el job
 * del impuesto sea la unica fuente de verdad.
 */
public final class Impuesto {

    // Coste por nivel de la cuota semanal: la cuota es nivel * este factor.
    private static final long FACTOR_CUOTA = 2_500L;

    /** Impagos consecutivos que provocan la quiebra de la empresa. */
    public static final int MOROSIDAD_MAX = 3;

    // Clase de utilidad: solo expone funciones puras estaticas, no se instancia.
    private Impuesto() {
    }

    /**
     * Cuota semanal que la empresa debe pagar por existir, segun su nivel. Escala linealmente con el
     * nivel para que crecer conlleve un coste de mantenimiento mayor.
     *
     * @param nivel nivel de la empresa
     * @return cuota a quemar del bote ({@code nivel * FACTOR_CUOTA})
     */
    public static long cuota(int nivel) {
        return nivel * FACTOR_CUOTA;
    }
}
