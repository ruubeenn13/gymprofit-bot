package com.gymprofit.bot.services;

/**
 * Calculo del prestigio de una empresa (F4 estatus): funcion pura que combina nivel, numero de
 * miembros y bote en un unico entero comparable para ordenar el ranking.
 *
 * <p>Los pesos estan elegidos a proposito para que el ranking premie el esfuerzo sostenido y no el
 * mero acaparamiento de dinero, y son tunables si el balance de juego lo pide:
 * <ul>
 *   <li><b>nivel x 10.000</b>: domina. El nivel (0-10) no se compra barato, se gana quemando bote,
 *       asi que un solo nivel de diferencia pesa mas que millones de bote.</li>
 *   <li><b>miembros x 1.000</b>: desempata entre empresas del mismo nivel; una empresa mas grande y
 *       activa figura por delante.</li>
 *   <li><b>bote / 1.000</b>: pesa muy poco a proposito, para no premiar sentarse sobre el dinero sin
 *       reinvertirlo en subir de nivel.</li>
 * </ul>
 */
public final class Prestigio {

    // Clase de utilidad: solo expone la funcion pura estatica, no se instancia.
    private Prestigio() {
    }

    /**
     * Puntuacion de prestigio de una empresa a partir de sus tres componentes.
     *
     * @param nivel       nivel de la empresa (domina, x10.000)
     * @param numMiembros numero de miembros (desempata, x1.000)
     * @param bote        bote acumulado (pesa poco, /1.000)
     * @return el prestigio como entero comparable
     */
    public static long calcular(int nivel, int numMiembros, long bote) {
        return nivel * 10_000L + numMiembros * 1_000L + bote / 1_000L;
    }
}
