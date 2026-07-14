package com.gymprofit.bot.services;

import java.util.List;

/**
 * Catálogo de minerales (COMBAT-5, en código). Cada mineral es un {@link Items} de categoría
 * {@code MINERAL} (nombre/emoji/valor de venta = su precio en {@code Items}); aquí solo va su
 * <b>tier</b>, que indica el pico mínimo necesario para extraerlo (ver {@link Picos}). A mayor tier,
 * más raro y valioso: {@code /minar} lo suelta con menos probabilidad (peso inverso al tier).
 *
 * @param itemId id del ítem-mineral (catálogo {@link Items}, categoría {@code MINERAL})
 * @param tier   pico mínimo para extraerlo (1 = madera … 4 = mithril)
 */
public record Minerales(String itemId, int tier) {

    /** Catálogo, por tier ascendente. */
    public static final List<Minerales> CATALOGO = List.of(
            new Minerales("piedra", 1),
            new Minerales("carbon", 1),
            new Minerales("cobre", 2),
            new Minerales("hierro", 2),
            new Minerales("plata", 3),
            new Minerales("oro", 3),
            new Minerales("esmeralda", 3),
            new Minerales("diamante", 3),
            new Minerales("rubi", 4),
            new Minerales("obsidiana", 4),
            new Minerales("mithril", 4));

    /** Minerales que puede extraer un pico de tier {@code tierPico} (todos los de tier ≤). */
    public static List<Minerales> extraiblesCon(int tierPico) {
        return CATALOGO.stream().filter(m -> m.tier() <= tierPico).toList();
    }
}
