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
 */
public record Items(String id, Categoria categoria, String emoji, long precio, Efecto efecto, int valor) {

    public enum Categoria { CONSUMIBLE, EQUIPO, BIEN }

    public enum Efecto { NINGUNO, ENERGIA, SALUD }

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
            new Items("cohete", Categoria.BIEN, "🚀", 3000000, Efecto.NINGUNO, 0));

    /** Busca un ítem por id. */
    public static Optional<Items> porId(String id) {
        return CATALOGO.stream().filter(i -> i.id().equals(id)).findFirst();
    }
}
