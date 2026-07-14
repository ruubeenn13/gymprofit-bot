package com.gymprofit.bot.services;

import java.util.List;
import java.util.Optional;

/**
 * Catálogo de ítems de la tienda (datos, en código). Amplio y por categorías. Los <b>consumibles</b>
 * tienen efecto inmediato (energía/salud) al usarlos; el <b>equipo</b> y los <b>bienes</b> se poseen
 * (sus efectos pasivos llegan en fases posteriores). El nombre sale de i18n con clave
 * {@code item.<id>}. Precios anclados a la escala económica lenta del RPG.
 *
 * @param id        identificador estable (clave i18n y columna {@code inventario.item_id})
 * @param categoria categoría del ítem
 * @param emoji     emoji para los embeds
 * @param precio    precio de compra en coins
 * @param efecto    efecto al usarlo (solo consumibles)
 * @param valor     magnitud del efecto (p. ej. +energía)
 * @param ataque    puntos de ataque (solo {@link Categoria#ARMA}; suma al poder de combate)
 * @param defensa   puntos de defensa (solo {@link Categoria#ARMADURA}; suma al poder de combate)
 */
public record Items(String id, Categoria categoria, String emoji, long precio, Efecto efecto,
                    int valor, int ataque, int defensa) {

    /**
     * Categorías del catálogo. {@code ARMA}/{@code ARMADURA} son equipables (COMBAT-1) y aportan
     * ataque/defensa al poder de combate; el resto no se equipa.
     */
    public enum Categoria { CONSUMIBLE, EQUIPO, BIEN, ARMA, ARMADURA, PICO, MINERAL }

    public enum Efecto { NINGUNO, ENERGIA, SALUD }

    /** Constructor de compatibilidad para los ítems no combativos (sin ataque/defensa). */
    public Items(String id, Categoria categoria, String emoji, long precio, Efecto efecto, int valor) {
        this(id, categoria, emoji, precio, efecto, valor, 0, 0);
    }

    /** ¿Es un ítem equipable (arma o armadura)? */
    public boolean esEquipable() {
        return categoria == Categoria.ARMA || categoria == Categoria.ARMADURA;
    }

    /** Catálogo completo, agrupado por categoría. */
    public static final List<Items> CATALOGO = List.of(
            // --- Consumibles (efecto inmediato al usar) ---
            new Items("fruta", Categoria.CONSUMIBLE, "🍎", 20, Efecto.SALUD, 15),
            new Items("cafe", Categoria.CONSUMIBLE, "☕", 25, Efecto.ENERGIA, 15),
            new Items("agua", Categoria.CONSUMIBLE, "💧", 15, Efecto.ENERGIA, 10),
            new Items("batido", Categoria.CONSUMIBLE, "🥤", 45, Efecto.ENERGIA, 25),
            new Items("pizza", Categoria.CONSUMIBLE, "🍕", 35, Efecto.SALUD, 20),
            new Items("bocadillo", Categoria.CONSUMIBLE, "🥪", 30, Efecto.SALUD, 18),
            new Items("bebida_energetica", Categoria.CONSUMIBLE, "⚡", 90, Efecto.ENERGIA, 40),
            new Items("menu", Categoria.CONSUMIBLE, "🍱", 120, Efecto.ENERGIA, 55),
            new Items("botiquin", Categoria.CONSUMIBLE, "🩹", 130, Efecto.SALUD, 50),
            new Items("cena_gourmet", Categoria.CONSUMIBLE, "🍽️", 200, Efecto.SALUD, 70),
            new Items("te", Categoria.CONSUMIBLE, "🍵", 22, Efecto.ENERGIA, 12),
            new Items("zumo", Categoria.CONSUMIBLE, "🧃", 24, Efecto.SALUD, 14),
            new Items("chocolate", Categoria.CONSUMIBLE, "🍫", 30, Efecto.ENERGIA, 18),
            new Items("helado", Categoria.CONSUMIBLE, "🍦", 26, Efecto.SALUD, 12),
            new Items("hamburguesa", Categoria.CONSUMIBLE, "🍔", 45, Efecto.SALUD, 25),
            new Items("ensalada", Categoria.CONSUMIBLE, "🥗", 40, Efecto.SALUD, 22),
            new Items("sushi", Categoria.CONSUMIBLE, "🍣", 75, Efecto.SALUD, 30),
            new Items("sopa", Categoria.CONSUMIBLE, "🍲", 55, Efecto.SALUD, 28),
            new Items("barrita", Categoria.CONSUMIBLE, "🍬", 35, Efecto.ENERGIA, 20),
            new Items("vitaminas", Categoria.CONSUMIBLE, "💊", 80, Efecto.SALUD, 35),
            new Items("proteina", Categoria.CONSUMIBLE, "🥛", 110, Efecto.ENERGIA, 50),
            new Items("gel_energetico", Categoria.CONSUMIBLE, "🧪", 100, Efecto.ENERGIA, 48),
            // --- Equipo (se posee; efectos pasivos en fases posteriores) ---
            new Items("movil", Categoria.EQUIPO, "📱", 600, Efecto.NINGUNO, 0),
            new Items("portatil", Categoria.EQUIPO, "💻", 1500, Efecto.NINGUNO, 0),
            new Items("herramientas", Categoria.EQUIPO, "🧰", 800, Efecto.NINGUNO, 0),
            new Items("mancuernas", Categoria.EQUIPO, "🏋️", 700, Efecto.NINGUNO, 0),
            new Items("uniforme", Categoria.EQUIPO, "🦺", 500, Efecto.NINGUNO, 0),
            new Items("bici", Categoria.EQUIPO, "🚲", 1200, Efecto.NINGUNO, 0),
            new Items("moto", Categoria.EQUIPO, "🏍️", 6000, Efecto.NINGUNO, 0),
            new Items("traje", Categoria.EQUIPO, "🤵", 2500, Efecto.NINGUNO, 0),
            new Items("mochila", Categoria.EQUIPO, "🎒", 400, Efecto.NINGUNO, 0),
            new Items("gafas", Categoria.EQUIPO, "🕶️", 300, Efecto.NINGUNO, 0),
            new Items("gorra", Categoria.EQUIPO, "🧢", 200, Efecto.NINGUNO, 0),
            new Items("zapatillas", Categoria.EQUIPO, "👟", 600, Efecto.NINGUNO, 0),
            new Items("auriculares", Categoria.EQUIPO, "🎧", 500, Efecto.NINGUNO, 0),
            new Items("reloj", Categoria.EQUIPO, "⌚", 2000, Efecto.NINGUNO, 0),
            new Items("consola", Categoria.EQUIPO, "🎮", 800, Efecto.NINGUNO, 0),
            new Items("patinete", Categoria.EQUIPO, "🛴", 800, Efecto.NINGUNO, 0),
            new Items("guitarra", Categoria.EQUIPO, "🎸", 1500, Efecto.NINGUNO, 0),
            new Items("camara", Categoria.EQUIPO, "📷", 1800, Efecto.NINGUNO, 0),
            new Items("telescopio", Categoria.EQUIPO, "🔭", 2200, Efecto.NINGUNO, 0),
            new Items("dron", Categoria.EQUIPO, "🚁", 4000, Efecto.NINGUNO, 0),
            // --- Bienes (estatus; efectos pasivos en fases posteriores) ---
            new Items("piso", Categoria.BIEN, "🏢", 25000, Efecto.NINGUNO, 0),
            new Items("coche", Categoria.BIEN, "🚗", 35000, Efecto.NINGUNO, 0),
            new Items("casa", Categoria.BIEN, "🏠", 80000, Efecto.NINGUNO, 0),
            new Items("coche_lujo", Categoria.BIEN, "🏎️", 200000, Efecto.NINGUNO, 0),
            new Items("mansion", Categoria.BIEN, "🏰", 500000, Efecto.NINGUNO, 0),
            new Items("yate", Categoria.BIEN, "🛥️", 900000, Efecto.NINGUNO, 0),
            new Items("apartamento", Categoria.BIEN, "🏬", 40000, Efecto.NINGUNO, 0),
            new Items("chalet", Categoria.BIEN, "🏡", 150000, Efecto.NINGUNO, 0),
            new Items("moto_agua", Categoria.BIEN, "🚤", 60000, Efecto.NINGUNO, 0),
            new Items("furgoneta", Categoria.BIEN, "🚐", 45000, Efecto.NINGUNO, 0),
            new Items("camion", Categoria.BIEN, "🚚", 90000, Efecto.NINGUNO, 0),
            new Items("helicoptero", Categoria.BIEN, "🚁", 350000, Efecto.NINGUNO, 0),
            new Items("avioneta", Categoria.BIEN, "🛩️", 400000, Efecto.NINGUNO, 0),
            new Items("jet", Categoria.BIEN, "🛫", 700000, Efecto.NINGUNO, 0),
            new Items("isla", Categoria.BIEN, "🏝️", 800000, Efecto.NINGUNO, 0),
            new Items("rascacielos", Categoria.BIEN, "🏙️", 1500000, Efecto.NINGUNO, 0),
            new Items("castillo", Categoria.BIEN, "🏯", 1200000, Efecto.NINGUNO, 0),
            new Items("cohete", Categoria.BIEN, "🚀", 3000000, Efecto.NINGUNO, 0),
            // --- Armas (equipables; +ataque al poder de combate). Temática aventura, no-gym. ---
            new Items("puno", Categoria.ARMA, "🥊", 50, Efecto.NINGUNO, 0, 2, 0),
            new Items("palo", Categoria.ARMA, "🪵", 120, Efecto.NINGUNO, 0, 4, 0),
            new Items("daga", Categoria.ARMA, "🗡️", 300, Efecto.NINGUNO, 0, 7, 0),
            new Items("espada_corta", Categoria.ARMA, "🔪", 600, Efecto.NINGUNO, 0, 10, 0),
            new Items("maza", Categoria.ARMA, "🔨", 1000, Efecto.NINGUNO, 0, 13, 0),
            new Items("hacha", Categoria.ARMA, "🪓", 1600, Efecto.NINGUNO, 0, 16, 0),
            new Items("lanza", Categoria.ARMA, "🔱", 2400, Efecto.NINGUNO, 0, 19, 0),
            new Items("espada", Categoria.ARMA, "⚔️", 3500, Efecto.NINGUNO, 0, 23, 0),
            new Items("arco", Categoria.ARMA, "🏹", 5000, Efecto.NINGUNO, 0, 27, 0),
            new Items("ballesta", Categoria.ARMA, "🎯", 7000, Efecto.NINGUNO, 0, 31, 0),
            new Items("katana", Categoria.ARMA, "🎏", 10000, Efecto.NINGUNO, 0, 36, 0),
            new Items("martillo_guerra", Categoria.ARMA, "🛠️", 15000, Efecto.NINGUNO, 0, 42, 0),
            new Items("alabarda", Categoria.ARMA, "⚜️", 22000, Efecto.NINGUNO, 0, 48, 0),
            new Items("guadana", Categoria.ARMA, "🌾", 32000, Efecto.NINGUNO, 0, 54, 0),
            new Items("baston_arcano", Categoria.ARMA, "🪄", 45000, Efecto.NINGUNO, 0, 60, 0),
            new Items("espada_legendaria", Categoria.ARMA, "🌟", 80000, Efecto.NINGUNO, 0, 75, 0),
            // --- Armaduras (equipables; +defensa al poder de combate). ---
            new Items("ropa", Categoria.ARMADURA, "👕", 40, Efecto.NINGUNO, 0, 0, 1),
            new Items("cuero", Categoria.ARMADURA, "🧥", 250, Efecto.NINGUNO, 0, 0, 3),
            new Items("acolchada", Categoria.ARMADURA, "🥋", 500, Efecto.NINGUNO, 0, 0, 5),
            new Items("escudo_madera", Categoria.ARMADURA, "🪵", 900, Efecto.NINGUNO, 0, 0, 8),
            new Items("cota_malla", Categoria.ARMADURA, "🔗", 1200, Efecto.NINGUNO, 0, 0, 10),
            new Items("placas", Categoria.ARMADURA, "🛡️", 3000, Efecto.NINGUNO, 0, 0, 14),
            new Items("armadura_hierro", Categoria.ARMADURA, "⚙️", 5000, Efecto.NINGUNO, 0, 0, 18),
            new Items("escudo_acero", Categoria.ARMADURA, "🔰", 6500, Efecto.NINGUNO, 0, 0, 20),
            new Items("armadura_elfica", Categoria.ARMADURA, "🍃", 12000, Efecto.NINGUNO, 0, 0, 25),
            new Items("armadura_dorada", Categoria.ARMADURA, "🥇", 25000, Efecto.NINGUNO, 0, 0, 30),
            new Items("armadura_draconica", Categoria.ARMADURA, "🐉", 60000, Efecto.NINGUNO, 0, 0, 40),
            new Items("armadura_divina", Categoria.ARMADURA, "✨", 120000, Efecto.NINGUNO, 0, 0, 55),
            // --- Picos (minería; su tier/durabilidad viven en services/Picos). Comprables. ---
            new Items("pico_madera", Categoria.PICO, "🪓", 300, Efecto.NINGUNO, 0),
            new Items("pico_hierro", Categoria.PICO, "⛏️", 2000, Efecto.NINGUNO, 0),
            new Items("pico_diamante", Categoria.PICO, "💠", 15000, Efecto.NINGUNO, 0),
            new Items("pico_mithril", Categoria.PICO, "🔱", 80000, Efecto.NINGUNO, 0),
            // --- Minerales (solo se minan; precio = valor de venta). No se compran en la tienda. ---
            new Items("piedra", Categoria.MINERAL, "🪨", 3, Efecto.NINGUNO, 0),
            new Items("carbon", Categoria.MINERAL, "⚫", 6, Efecto.NINGUNO, 0),
            new Items("cobre", Categoria.MINERAL, "🟤", 12, Efecto.NINGUNO, 0),
            new Items("hierro", Categoria.MINERAL, "⚙️", 20, Efecto.NINGUNO, 0),
            new Items("plata", Categoria.MINERAL, "🥈", 40, Efecto.NINGUNO, 0),
            new Items("oro", Categoria.MINERAL, "🥇", 70, Efecto.NINGUNO, 0),
            new Items("esmeralda", Categoria.MINERAL, "🟩", 120, Efecto.NINGUNO, 0),
            new Items("diamante", Categoria.MINERAL, "💎", 200, Efecto.NINGUNO, 0),
            new Items("rubi", Categoria.MINERAL, "🔴", 350, Efecto.NINGUNO, 0),
            new Items("obsidiana", Categoria.MINERAL, "⬛", 500, Efecto.NINGUNO, 0),
            new Items("mithril", Categoria.MINERAL, "🔷", 900, Efecto.NINGUNO, 0));

    /** Busca un ítem por id. */
    public static Optional<Items> porId(String id) {
        return CATALOGO.stream().filter(i -> i.id().equals(id)).findFirst();
    }
}
