package com.gymprofit.bot.services;

import java.util.List;
import java.util.Optional;

/**
 * Bestiario del RPG de combate (datos, en código). Catálogo AMPLIO: cada mundo (ver {@link Mundos})
 * tiene varios monstruos normales por dificultad más un <b>jefe</b> (matar al jefe desbloquea el
 * mundo siguiente, COMBAT-3). Los stats escalan por mundo y dificultad; el {@code loot} es una lista
 * de drops con probabilidad, cuyos ids apuntan al catálogo de {@link Items}. El nombre sale de i18n
 * con clave {@code monstruo.<id>}.
 *
 * <p>En COMBAT-2 esto es solo <i>datos + navegación</i> (bestiario): la pelea por turnos que consume
 * estos valores (poder/HP/coins/XP/loot) llega en COMBAT-3.</p>
 *
 * @param id         identificador estable (clave i18n {@code monstruo.<id>})
 * @param emoji      emoji para los embeds del bestiario
 * @param mundo      id del mundo al que pertenece (ver {@link Mundos})
 * @param dificultad dificultad (define agrupación y color narrativo); {@code JEFE} = jefe del mundo
 * @param poder      poder de combate del monstruo (se enfrenta al del jugador)
 * @param hp         puntos de vida de combate
 * @param coins      coins que suelta al morir
 * @param xp         XP que otorga al morir
 * @param loot       tabla de botín (item + probabilidad de drop en [0,1])
 */
public record Monstruos(String id, String emoji, String mundo, Dificultad dificultad,
                        int poder, int hp, long coins, int xp, List<Drop> loot) {

    /** Dificultad del monstruo. {@code JEFE} es el jefe único del mundo. */
    public enum Dificultad { FACIL, NORMAL, DIFICIL, JEFE }

    /**
     * Un drop de la tabla de botín.
     *
     * @param itemId id del ítem soltado (catálogo {@link Items})
     * @param prob   probabilidad de soltarlo, en {@code [0,1]}
     */
    public record Drop(String itemId, double prob) {
    }

    /** ¿Es el jefe del mundo? (derivado de la dificultad). */
    public boolean esJefe() {
        return dificultad == Dificultad.JEFE;
    }

    // --- Helpers de construcción para mantener el catálogo legible ---

    private static Drop d(String itemId, double prob) {
        return new Drop(itemId, prob);
    }

    private static Monstruos m(String id, String emoji, String mundo, Dificultad dif,
                              int poder, int hp, long coins, int xp, Drop... loot) {
        return new Monstruos(id, emoji, mundo, dif, poder, hp, coins, xp, List.of(loot));
    }

    /** Bestiario completo, agrupado por mundo (mismo orden que {@link Mundos#CATALOGO}). */
    public static final List<Monstruos> CATALOGO = List.of(
            // ===================== 🌲 Bosque =====================
            m("lobo",           "🐺", "bosque", Dificultad.FACIL,   8,  30,  8,  6, d("fruta", 0.50)),
            m("jabali",         "🐗", "bosque", Dificultad.FACIL,  10,  36,  9,  7, d("fruta", 0.40), d("agua", 0.30)),
            m("rata_gigante",   "🐀", "bosque", Dificultad.FACIL,   7,  26,  6,  5, d("agua", 0.50)),
            m("arana_bosque",   "🕷️", "bosque", Dificultad.NORMAL, 16,  60, 14, 12, d("cafe", 0.40), d("cuero", 0.05)),
            m("serpiente",      "🐍", "bosque", Dificultad.NORMAL, 18,  64, 15, 13, d("bocadillo", 0.40)),
            m("oso",            "🐻", "bosque", Dificultad.DIFICIL,30, 120, 26, 22, d("ropa", 0.20), d("cuero", 0.10)),
            m("bandido",        "🗡️", "bosque", Dificultad.DIFICIL,28, 110, 30, 20, d("palo", 0.15), d("cuero", 0.10)),
            m("ent_ancestral",  "🌳", "bosque", Dificultad.JEFE,   80, 480,120, 90, d("daga", 0.30), d("cuero", 0.40), d("batido", 0.60)),
            // ===================== 🕸️ Cueva =====================
            m("murcielago",     "🦇", "cueva", Dificultad.FACIL,  16,  55, 12, 10, d("agua", 0.50)),
            m("goblin",         "👺", "cueva", Dificultad.FACIL,  20,  70, 15, 12, d("fruta", 0.40), d("palo", 0.10)),
            m("slime",          "🟢", "cueva", Dificultad.FACIL,  15,  60, 11, 10, d("agua", 0.40)),
            m("escorpion",      "🦂", "cueva", Dificultad.NORMAL, 30, 110, 22, 18, d("cafe", 0.40)),
            m("kobold",         "🦎", "cueva", Dificultad.NORMAL, 34, 120, 24, 20, d("acolchada", 0.06), d("cuero", 0.10)),
            m("troll_cueva",    "🧌", "cueva", Dificultad.DIFICIL,50, 200, 40, 32, d("escudo_madera", 0.10), d("botiquin", 0.30)),
            m("golem_piedra",   "🗿", "cueva", Dificultad.DIFICIL,55, 230, 44, 35, d("acolchada", 0.10)),
            m("rey_goblin",     "👑", "cueva", Dificultad.JEFE,  130, 780,200,150, d("espada_corta", 0.30), d("cota_malla", 0.20), d("menu", 0.60)),
            // ===================== 🐸 Pantano =====================
            m("rana_toxica",    "🐸", "pantano", Dificultad.FACIL,  26,  90, 18, 14, d("agua", 0.50)),
            m("sanguijuela",    "🪱", "pantano", Dificultad.FACIL,  24,  85, 16, 13, d("fruta", 0.40)),
            m("mosquito_gigante","🦟","pantano", Dificultad.FACIL,  28,  95, 19, 15, d("cafe", 0.30)),
            m("hombre_lagarto", "🦎", "pantano", Dificultad.NORMAL, 44, 160, 30, 26, d("maza", 0.05), d("cuero", 0.15)),
            m("bruja_pantano",  "🧙", "pantano", Dificultad.NORMAL, 48, 170, 34, 28, d("vitaminas", 0.30)),
            m("hidra_menor",    "🐲", "pantano", Dificultad.DIFICIL,70, 280, 55, 45, d("escudo_madera", 0.15), d("botiquin", 0.30)),
            m("no_muerto",      "🧟", "pantano", Dificultad.DIFICIL,66, 265, 50, 42, d("acolchada", 0.15)),
            m("reina_cienaga",  "👸", "pantano", Dificultad.JEFE,  190,1140,300,220, d("hacha", 0.20), d("armadura_hierro", 0.15), d("cena_gourmet", 0.60)),
            // ===================== 🏜️ Desierto =====================
            m("escarabajo",     "🪲", "desierto", Dificultad.FACIL,  40, 140, 26, 22, d("agua", 0.50)),
            m("buitre",         "🦅", "desierto", Dificultad.FACIL,  44, 150, 28, 24, d("fruta", 0.40)),
            m("serpiente_arena","🐍", "desierto", Dificultad.FACIL,  42, 145, 27, 23, d("bebida_energetica", 0.30)),
            m("momia",          "🧟", "desierto", Dificultad.NORMAL, 66, 240, 44, 38, d("cota_malla", 0.06)),
            m("nomada",         "🏹", "desierto", Dificultad.NORMAL, 70, 250, 48, 40, d("arco", 0.05), d("cuero", 0.15)),
            m("escorpion_gigante","🦂","desierto",Dificultad.DIFICIL,95, 380, 72, 60, d("placas", 0.06), d("botiquin", 0.40)),
            m("genio_corrupto", "🧞", "desierto", Dificultad.DIFICIL,100,400, 80, 65, d("baston_arcano", 0.03)),
            m("faraon_maldito", "🔺", "desierto", Dificultad.JEFE,  260,1560,420,300, d("espada", 0.20), d("armadura_hierro", 0.20), d("cena_gourmet", 0.70)),
            // ===================== 🏔️ Montaña helada =====================
            m("lobo_hielo",     "❄️", "montana_helada", Dificultad.FACIL,  55, 190, 34, 30, d("batido", 0.40)),
            m("yeti_joven",     "🦍", "montana_helada", Dificultad.FACIL,  60, 210, 38, 32, d("fruta", 0.40)),
            m("aguila_nieve",   "🦅", "montana_helada", Dificultad.FACIL,  57, 200, 36, 31, d("bebida_energetica", 0.30)),
            m("elemental_hielo","🧊", "montana_helada", Dificultad.NORMAL, 88, 320, 58, 50, d("cota_malla", 0.08)),
            m("barbaro",        "🪓", "montana_helada", Dificultad.NORMAL, 92, 330, 62, 52, d("hacha", 0.05), d("placas", 0.05)),
            m("mamut",          "🦣", "montana_helada", Dificultad.DIFICIL,125,500, 95, 78, d("armadura_hierro", 0.10), d("menu", 0.50)),
            m("gigante_escarcha","🧊","montana_helada",Dificultad.DIFICIL,130,520,100, 82, d("escudo_acero", 0.06)),
            m("yeti_alfa",      "🏔️", "montana_helada", Dificultad.JEFE,  340,2040,560,400, d("martillo_guerra", 0.15), d("armadura_elfica", 0.15), d("cena_gourmet", 0.70)),
            // ===================== 🌋 Volcán =====================
            m("salamandra",     "🦎", "volcan", Dificultad.FACIL,  72, 250, 44, 40, d("gel_energetico", 0.40)),
            m("imp",            "😈", "volcan", Dificultad.FACIL,  78, 270, 48, 42, d("cafe", 0.40)),
            m("murcielago_lava","🦇", "volcan", Dificultad.FACIL,  75, 260, 46, 41, d("bebida_energetica", 0.30)),
            m("golem_lava",     "🌋", "volcan", Dificultad.NORMAL, 110,400, 74, 64, d("armadura_hierro", 0.08)),
            m("cultista",       "🕯️", "volcan", Dificultad.NORMAL, 115,415, 78, 66, d("baston_arcano", 0.04)),
            m("demonio_menor",  "👿", "volcan", Dificultad.DIFICIL,155,620,118, 98, d("escudo_acero", 0.08), d("botiquin", 0.40)),
            m("fenix",          "🔥", "volcan", Dificultad.DIFICIL,160,640,125,102, d("armadura_elfica", 0.06)),
            m("senor_llamas",   "🔥", "volcan", Dificultad.JEFE,  430,2580,720,520, d("alabarda", 0.12), d("armadura_dorada", 0.12), d("cena_gourmet", 0.80)),
            // ===================== 🏚️ Ciudad en ruinas =====================
            m("espectro",       "👻", "ciudad_ruinas", Dificultad.FACIL,  92, 320, 56, 50, d("gel_energetico", 0.40)),
            m("gargola",        "🗿", "ciudad_ruinas", Dificultad.FACIL,  98, 340, 60, 52, d("fruta", 0.40)),
            m("rata_mutante",   "🐀", "ciudad_ruinas", Dificultad.FACIL,  95, 330, 58, 51, d("bebida_energetica", 0.30)),
            m("caballero_caido","⚔️", "ciudad_ruinas", Dificultad.NORMAL, 138,500, 92, 80, d("placas", 0.08)),
            m("nigromante",     "💀", "ciudad_ruinas", Dificultad.NORMAL, 145,520, 98, 84, d("baston_arcano", 0.06)),
            m("coloso_ruinas",  "🏛️", "ciudad_ruinas", Dificultad.DIFICIL,195,780,148,122, d("armadura_dorada", 0.06), d("botiquin", 0.50)),
            m("quimera",        "🦁", "ciudad_ruinas", Dificultad.DIFICIL,205,820,158,128, d("armadura_elfica", 0.08)),
            m("guardian_eterno","🛡️", "ciudad_ruinas", Dificultad.JEFE,  540,3240,900,660, d("guadana", 0.10), d("armadura_dorada", 0.15), d("cena_gourmet", 0.80)),
            // ===================== 🌑 Reino sombrío =====================
            m("sombra",         "🌑", "reino_sombrio", Dificultad.FACIL, 115, 400, 70, 62, d("gel_energetico", 0.40)),
            m("caballero_sombrio","🖤","reino_sombrio",Dificultad.FACIL, 122, 420, 74, 64, d("proteina", 0.30)),
            m("arpia",          "🦅", "reino_sombrio", Dificultad.FACIL, 118, 410, 72, 63, d("bebida_energetica", 0.30)),
            m("verdugo",        "🪓", "reino_sombrio", Dificultad.NORMAL,170, 620,112, 98, d("armadura_dorada", 0.06)),
            m("lich",           "💀", "reino_sombrio", Dificultad.NORMAL,180, 650,120,104, d("baston_arcano", 0.08)),
            m("dragon_negro",   "🐉", "reino_sombrio", Dificultad.DIFICIL,240,960,182,152, d("armadura_draconica", 0.05), d("cena_gourmet", 0.50)),
            m("angel_caido",    "😇", "reino_sombrio", Dificultad.DIFICIL,255,1020,195,160, d("espada_legendaria", 0.02)),
            m("rey_sombrio",    "👑", "reino_sombrio", Dificultad.JEFE, 700,4200,1200,900, d("espada_legendaria", 0.10), d("armadura_divina", 0.10), d("cena_gourmet", 0.90)));

    /** Busca un monstruo por id. */
    public static Optional<Monstruos> porId(String id) {
        return CATALOGO.stream().filter(mo -> mo.id().equals(id)).findFirst();
    }

    /** Monstruos de un mundo, en el orden del catálogo (normales por dificultad y jefe al final). */
    public static List<Monstruos> deMundo(String mundoId) {
        return CATALOGO.stream().filter(mo -> mo.mundo().equals(mundoId)).toList();
    }
}
