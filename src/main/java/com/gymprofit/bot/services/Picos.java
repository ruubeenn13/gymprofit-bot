package com.gymprofit.bot.services;

import java.util.List;
import java.util.Optional;

/**
 * Catálogo de picos de minería (COMBAT-5, en código). Cada pico es un {@link Items} de categoría
 * {@code PICO} (para reusar tienda/compra/inventario); aquí solo va el dato específico de minería: su
 * <b>tier</b>, que desbloquea qué minerales puede extraer (ver {@link Minerales}). Un pico de tier N
 * saca todos los minerales de tier ≤ N. La durabilidad llega en COMBAT-5b.
 *
 * @param itemId         id del ítem-pico (catálogo {@link Items}, categoría {@code PICO})
 * @param tier           nivel del pico (1 = madera … 4 = mithril)
 * @param durabilidadMax durabilidad máxima (usos antes de romperse; se repara con /reparar)
 */
public record Picos(String itemId, int tier, int durabilidadMax) {

    /** Catálogo, ordenado por tier. */
    public static final List<Picos> CATALOGO = List.of(
            new Picos("pico_madera", 1, 30),
            new Picos("pico_hierro", 2, 60),
            new Picos("pico_diamante", 3, 100),
            new Picos("pico_mithril", 4, 150));

    public static Optional<Picos> porId(String itemId) {
        return CATALOGO.stream().filter(p -> p.itemId().equals(itemId)).findFirst();
    }

    /** ¿Es este id un pico? */
    public static boolean esPico(String itemId) {
        return porId(itemId).isPresent();
    }
}
