package com.gymprofit.bot.services;

/**
 * Numeros de la produccion de empresa (F5a): funciones puras que definen cuanta mercancia genera el
 * trabajo de un miembro, cuanto cabe en el almacen segun el nivel de la empresa y que impuesto se
 * queda el Estado al vender.
 *
 * <p>La mercancia es un <b>subproducto del curro</b>: cada vez que un miembro usa {@code /trabajo
 * currar} su empresa acumula unidades (ademas de su paga personal), y esas unidades se venden luego
 * con {@code /empresa vender} para llenar el bote. El almacen tiene un <b>tope por nivel</b>
 * ({@link #capacidad}) para que subir de nivel siga mereciendo la pena y no baste con acumular
 * indefinidamente sin reinvertir. El impuesto de la venta es un <b>sumidero</b>: el dinero cobrado
 * se quema (no va a nadie), lo que contiene la inflacion del principio antiinflacion del RPG.
 *
 * <p>Todos los pesos son tunables si el balance de juego lo pide; se centralizan aqui para que el
 * job del curro y el comando de venta compartan una unica fuente de verdad.
 */
public final class Produccion {

    // Unidades base que produce un curro, antes de sumar el nivel de la empresa.
    private static final int BASE_POR_CURRO = 5;

    // Multiplicador del tope del almacen: cada nivel aporta 100 unidades de capacidad.
    // OJO: debe coincidir con el `nivel * 100` del clamp en EmpresaRepositorio.sumarMercancia.
    private static final int FACTOR_CAPACIDAD = 100;

    /** Dinero bruto que paga cada unidad de mercancia al venderse. */
    public static final long PRECIO_UNIDAD = 50L;

    /** Fraccion del bruto de la venta que se queda el Estado (sumidero antiinflacion). */
    public static final double IMPUESTO_VENTA = 0.15;

    // Clase de utilidad: solo expone funciones puras estaticas, no se instancia.
    private Produccion() {
    }

    /**
     * Unidades de mercancia que produce un curro de un miembro, segun el nivel de su empresa. Es un
     * subproducto del trabajo: una empresa de mas nivel rinde mas por cada curro de sus miembros.
     *
     * @param nivel nivel de la empresa
     * @return unidades producidas ({@code BASE_POR_CURRO + nivel})
     */
    public static int unidadesPorCurro(int nivel) {
        return BASE_POR_CURRO + nivel;
    }

    /**
     * Capacidad maxima del almacen de la empresa segun su nivel. Es el tope al que se recorta la
     * mercancia acumulada, para que subir de nivel siga aportando y no baste con producir sin fin.
     *
     * @param nivel nivel de la empresa
     * @return unidades que caben en el almacen ({@code nivel * FACTOR_CAPACIDAD})
     */
    public static long capacidad(int nivel) {
        return (long) nivel * FACTOR_CAPACIDAD;
    }

    /**
     * Impuesto que se queda el Estado al vender un lote de mercancia. El dinero se quema (sumidero),
     * no se ingresa a nadie: contiene la inflacion del RPG. Se redondea hacia abajo para no cobrar de
     * mas al vendedor.
     *
     * @param bruto dinero bruto de la venta (unidades vendidas * {@link #PRECIO_UNIDAD})
     * @return impuesto a descontar del bruto
     */
    public static long impuesto(long bruto) {
        return (long) Math.floor(bruto * IMPUESTO_VENTA);
    }
}
