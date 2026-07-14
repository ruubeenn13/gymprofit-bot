package com.gymprofit.bot.services;

import java.util.List;
import java.util.Optional;

/**
 * Catálogo de mazmorras (COMBAT-6b, en código). Una mazmorra es una <b>secuencia de combates</b> en
 * un mundo: varias oleadas de monstruos seguidas que terminan en el jefe del mundo, <b>sin curación
 * entre oleadas</b> (ese es el riesgo). Superarla da el botín normal de cada monstruo más un
 * <b>bonus</b> de finalización. Requiere tener el mundo desbloqueado (mismo gate que {@code /pelear}).
 * El nombre sale de i18n con clave {@code mazmorra.<id>}.
 *
 * @param id         identificador estable (clave i18n)
 * @param emoji      emoji para embeds
 * @param mundo      id del mundo (gate de desbloqueo/nivel; ver {@link Mundos})
 * @param oleadas    ids de monstruos en orden (el último suele ser el jefe del mundo)
 * @param bonusCoins coins extra al completar la mazmorra
 * @param bonusXp    XP extra al completar la mazmorra
 */
public record Mazmorras(String id, String emoji, String mundo, List<String> oleadas,
                        long bonusCoins, int bonusXp) {

    /** Catálogo completo. */
    public static final List<Mazmorras> CATALOGO = List.of(
            new Mazmorras("guarida_lobos", "🐺", "bosque",
                    List.of("lobo", "lobo", "jabali", "oso", "ent_ancestral"), 300, 150),
            new Mazmorras("nido_goblin", "👺", "cueva",
                    List.of("goblin", "goblin", "kobold", "troll_cueva", "rey_goblin"), 500, 250),
            new Mazmorras("foso_pantano", "🐸", "pantano",
                    List.of("rana_toxica", "hombre_lagarto", "no_muerto", "hidra_menor",
                            "reina_cienaga"), 800, 400),
            new Mazmorras("tumba_desierto", "🏜️", "desierto",
                    List.of("momia", "nomada", "escorpion_gigante", "genio_corrupto",
                            "faraon_maldito"), 1200, 600));

    public static Optional<Mazmorras> porId(String id) {
        return CATALOGO.stream().filter(m -> m.id().equals(id)).findFirst();
    }

    /** Número de oleadas. */
    public int totalOleadas() {
        return oleadas.size();
    }
}
