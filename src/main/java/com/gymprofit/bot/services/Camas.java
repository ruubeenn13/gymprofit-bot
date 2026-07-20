package com.gymprofit.bot.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogo de sitios donde dormir. Cada cama define cuánta energía se gana por hora y su
 * <b>tope</b>: en el suelo no se llega a 100 por mucho que se duerma. Es lo que da progresión
 * (suelo → saco → colchón → vivienda) y lo que por fin da un uso a los bienes de vivienda, que
 * hasta ahora solo eran un sumidero de coins.
 *
 * <p>La cama no se equipa: sale del inventario, igual que el pico en {@code /minar}. Las viviendas
 * por encima de {@code casa} no dan más (ya está el tope en 100): siguen siendo estatus.
 *
 * @param itemId       id en {@link Items}, o {@code null} para las entradas virtuales
 * @param energiaHora  energía ganada por hora dormida
 * @param tope         energía máxima alcanzable durmiendo aquí
 */
public record Camas(String itemId, int energiaHora, int tope) {

    /** Sin nada: se duerme en el suelo. No tiene ítem. */
    public static final Camas SUELO = new Camas(null, 10, 60);

    /** Hotel: se paga por noche, no se posee. No tiene ítem. */
    public static final Camas HOTEL = new Camas(null, 25, 100);

    /** Precio por noche del hotel (sumidero de coins). */
    public static final long PRECIO_HOTEL = 200;

    /**
     * Camas que se poseen, ordenadas <b>de peor a mejor</b>.
     *
     * <p><b>Invariante:</b> el catálogo debe ser una <b>cadena de camas comparables</b>: cada
     * entrada tiene que ser mejor o igual que la anterior <b>en los dos ejes</b> (energía/hora y
     * tope). Hoy se cumple, así que el guard de {@link #mejorDe} siempre pasa y el resultado no
     * depende del orden.
     *
     * <p>Ese guard existe por si alguien añade al final una cama estrictamente <b>peor</b>: la
     * descarta en vez de degradar al jugador. Lo que <b>no</b> puede resolver es una cama
     * <b>incomparable</b> (más energía/hora pero menos tope, o al revés): rompería el invariante y
     * con el mismo inventario el resultado pasaría a depender de la posición en esta lista, en
     * silencio y sin que ningún test lo detecte. Si algún día hace falta una cama así, hay que
     * decidir antes un criterio explícito de «mejor» (p. ej. ordenar por tope y desempatar por
     * energía/hora) en vez de confiar en el orden.
     */
    public static final List<Camas> CATALOGO = List.of(
            new Camas("saco_dormir", 15, 75),
            new Camas("colchon", 20, 85),
            new Camas("piso", 25, 95),
            new Camas("apartamento", 25, 95),
            // De casa en adelante ya se llega a 100: las viviendas caras son estatus, no ventaja.
            new Camas("casa", 30, 100),
            new Camas("chalet", 30, 100),
            new Camas("mansion", 30, 100),
            new Camas("isla", 30, 100),
            new Camas("castillo", 30, 100),
            new Camas("rascacielos", 30, 100));

    /**
     * La siguiente cama que <b>sube el tope</b> respecto a {@code actual}, o vacío si ya no hay
     * mejora posible (tope 100).
     *
     * <p>Sirve para explicarle al jugador por qué se ha quedado corto y qué comprar: sin esto, un
     * «tope alcanzado» a secas parece que el bot le esté estafando. Se filtra por <b>tope</b> y no
     * por energía/hora porque el tope es lo que de verdad le está limitando; por eso salta las camas
     * de tope idéntico (apartamento tras piso, chalet tras casa): no arreglarían nada.
     */
    public static Optional<Camas> siguienteMejor(Camas actual) {
        return CATALOGO.stream().filter(c -> c.tope() > actual.tope()).findFirst();
    }

    /** La mejor cama que hay en el inventario, o {@link #SUELO} si no hay ninguna. */
    public static Camas mejorDe(Map<String, Integer> inventario) {
        Camas mejor = SUELO;
        for (Camas c : CATALOGO) {
            Integer n = inventario.get(c.itemId());
            if (n != null && n > 0 && c.tope() >= mejor.tope() && c.energiaHora() >= mejor.energiaHora()) {
                mejor = c;
            }
        }
        return mejor;
    }
}
