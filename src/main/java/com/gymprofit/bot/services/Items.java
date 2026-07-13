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
            new Items("botiquin", Categoria.CONSUMIBLE, "🧰", 130, Efecto.SALUD, 50),
            new Items("cena_gourmet", Categoria.CONSUMIBLE, "🍽️", 200, Efecto.SALUD, 70),
            // --- Equipo (se posee; efectos pasivos en fases posteriores) ---
            new Items("movil", Categoria.EQUIPO, "📱", 600, Efecto.NINGUNO, 0),
            new Items("portatil", Categoria.EQUIPO, "💻", 1500, Efecto.NINGUNO, 0),
            new Items("herramientas", Categoria.EQUIPO, "🧰", 800, Efecto.NINGUNO, 0),
            new Items("mancuernas", Categoria.EQUIPO, "🏋️", 700, Efecto.NINGUNO, 0),
            new Items("uniforme", Categoria.EQUIPO, "🦺", 500, Efecto.NINGUNO, 0),
            new Items("bici", Categoria.EQUIPO, "🚲", 1200, Efecto.NINGUNO, 0),
            new Items("moto", Categoria.EQUIPO, "🏍️", 6000, Efecto.NINGUNO, 0),
            new Items("traje", Categoria.EQUIPO, "🤵", 2500, Efecto.NINGUNO, 0),
            // --- Bienes (estatus; efectos pasivos en fases posteriores) ---
            new Items("piso", Categoria.BIEN, "🏢", 25000, Efecto.NINGUNO, 0),
            new Items("coche", Categoria.BIEN, "🚗", 35000, Efecto.NINGUNO, 0),
            new Items("casa", Categoria.BIEN, "🏠", 80000, Efecto.NINGUNO, 0),
            new Items("coche_lujo", Categoria.BIEN, "🏎️", 200000, Efecto.NINGUNO, 0),
            new Items("mansion", Categoria.BIEN, "🏰", 500000, Efecto.NINGUNO, 0),
            new Items("yate", Categoria.BIEN, "🛥️", 900000, Efecto.NINGUNO, 0));

    /** Busca un ítem por id. */
    public static Optional<Items> porId(String id) {
        return CATALOGO.stream().filter(i -> i.id().equals(id)).findFirst();
    }
}
