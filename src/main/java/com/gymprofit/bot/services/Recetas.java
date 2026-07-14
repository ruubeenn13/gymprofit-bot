package com.gymprofit.bot.services;

import java.util.List;
import java.util.Optional;

/**
 * Catálogo de recetas de herrería (COMBAT-6 crafting, en código). Cada receta combina minerales
 * (obtenidos con {@code /minar}) para fabricar un ítem existente del catálogo {@link Items} (arma,
 * armadura o pico), cerrando el bucle minar → forjar → combatir/minar. No consume coins: el coste es
 * el tiempo de minería. La receta se identifica por el id del ítem que produce.
 *
 * @param resultado    id del ítem fabricado (catálogo {@link Items})
 * @param ingredientes minerales necesarios (id → cantidad), en orden de visualización
 */
public record Recetas(String resultado, List<Ingrediente> ingredientes) {

    /**
     * Un ingrediente de una receta.
     *
     * @param itemId   id del mineral (catálogo {@link Items}, categoría {@code MINERAL})
     * @param cantidad unidades necesarias
     */
    public record Ingrediente(String itemId, int cantidad) {
    }

    private static Ingrediente i(String itemId, int cantidad) {
        return new Ingrediente(itemId, cantidad);
    }

    private static Recetas r(String resultado, Ingrediente... ingredientes) {
        return new Recetas(resultado, List.of(ingredientes));
    }

    /** Catálogo completo (armas, armaduras y picos fabricables). */
    public static final List<Recetas> CATALOGO = List.of(
            // --- Picos (permite subir de tier sin pasar por la tienda) ---
            r("pico_hierro", i("hierro", 5), i("carbon", 3)),
            r("pico_diamante", i("diamante", 8), i("oro", 4)),
            r("pico_mithril", i("mithril", 10), i("obsidiana", 6)),
            // --- Armas ---
            r("espada_corta", i("hierro", 3), i("carbon", 2)),
            r("maza", i("hierro", 4), i("piedra", 5)),
            r("hacha", i("hierro", 5), i("carbon", 3)),
            r("espada", i("hierro", 6), i("plata", 2)),
            r("katana", i("plata", 8), i("hierro", 6)),
            r("baston_arcano", i("esmeralda", 6), i("oro", 4)),
            r("espada_legendaria", i("mithril", 10), i("diamante", 8), i("oro", 5)),
            // --- Armaduras ---
            r("cota_malla", i("hierro", 8)),
            r("placas", i("hierro", 10), i("plata", 3)),
            r("armadura_hierro", i("hierro", 12), i("carbon", 5)),
            r("armadura_dorada", i("oro", 10), i("plata", 5)),
            r("armadura_draconica", i("obsidiana", 8), i("diamante", 5)));

    /** Busca una receta por el id del ítem que produce. */
    public static Optional<Recetas> porResultado(String resultado) {
        return CATALOGO.stream().filter(rc -> rc.resultado().equals(resultado)).findFirst();
    }
}
