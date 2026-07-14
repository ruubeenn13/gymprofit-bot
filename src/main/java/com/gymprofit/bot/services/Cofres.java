package com.gymprofit.bot.services;

import java.util.List;
import java.util.Optional;

/**
 * Catálogo de cofres y sus tablas de botín (en código). Cada cofre es un {@link Items} de categoría
 * {@code COFRE} (se compra y se guarda en el inventario); al abrirlo con {@code /abrir} se tira <b>un
 * premio</b> de su tabla por peso (a menos peso, más raro). Cada premio lleva su {@link Rareza} para
 * pintarlo. Balance: el <b>valor esperado</b> de la tabla es menor que el precio del cofre (sumidero
 * controlado; ver {@code CofreService.valorEsperado} y su test). El nombre del cofre sale de i18n con
 * clave {@code item.<id>}.
 *
 * @param itemId id del ítem-cofre (catálogo {@link Items}, categoría {@code COFRE})
 * @param tabla  premios posibles (con su peso relativo)
 */
public record Cofres(String itemId, List<Premio> tabla) {

    /** Un premio posible de un cofre. */
    public record Premio(Tipo tipo, String ref, int min, int max, int peso, Rareza rareza) {
    }

    /** Tipo de premio de un cofre. */
    public enum Tipo {
        /** Un ítem del catálogo (gear, consumible o mineral): {@code ref} = id, cantidad en [min,max]. */
        ITEM,
        /** Coins: cantidad en [min,max]. */
        COINS,
        /** Encantamiento aplicado al arma: {@code ref} = id de {@link Encantamiento}. */
        ENCANTO,
        /** Sube 1 nivel el arma equipada. */
        NIVEL
    }

    // --- helpers de construcción ---
    private static Premio item(String ref, int min, int max, int peso, Rareza r) {
        return new Premio(Tipo.ITEM, ref, min, max, peso, r);
    }

    private static Premio coins(int min, int max, int peso, Rareza r) {
        return new Premio(Tipo.COINS, null, min, max, peso, r);
    }

    private static Premio enc(String ref, int peso, Rareza r) {
        return new Premio(Tipo.ENCANTO, ref, 0, 0, peso, r);
    }

    private static Premio niv(int peso, Rareza r) {
        return new Premio(Tipo.NIVEL, null, 0, 0, peso, r);
    }

    /** Catálogo completo (de común a legendario). */
    public static final List<Cofres> CATALOGO = List.of(
            new Cofres("cofre_comun", List.of(
                    coins(30, 80, 45, Rareza.COMUN),
                    item("cafe", 1, 1, 20, Rareza.COMUN),
                    item("piedra", 3, 6, 15, Rareza.COMUN),
                    item("carbon", 2, 4, 10, Rareza.COMUN),
                    item("cuero", 1, 1, 6, Rareza.RARO),
                    item("hierro", 2, 4, 4, Rareza.RARO))),
            new Cofres("cofre_raro", List.of(
                    coins(100, 250, 38, Rareza.COMUN),
                    item("hierro", 3, 6, 20, Rareza.RARO),
                    item("plata", 2, 4, 12, Rareza.RARO),
                    item("espada_corta", 1, 1, 8, Rareza.RARO),
                    item("cota_malla", 1, 1, 6, Rareza.EPICO),
                    enc("afilado", 5, Rareza.EPICO),
                    item("oro", 2, 3, 6, Rareza.EPICO),
                    item("espada", 1, 1, 5, Rareza.EPICO))),
            new Cofres("cofre_epico", List.of(
                    coins(500, 1200, 34, Rareza.RARO),
                    item("oro", 3, 6, 18, Rareza.EPICO),
                    item("esmeralda", 2, 4, 12, Rareza.EPICO),
                    item("placas", 1, 1, 9, Rareza.EPICO),
                    enc("llama", 7, Rareza.EPICO),
                    item("armadura_hierro", 1, 1, 7, Rareza.EPICO),
                    niv(5, Rareza.EPICO),
                    item("diamante", 1, 3, 5, Rareza.LEGENDARIO),
                    item("katana", 1, 1, 3, Rareza.LEGENDARIO))),
            new Cofres("cofre_legendario", List.of(
                    coins(3000, 6000, 30, Rareza.EPICO),
                    item("diamante", 3, 6, 18, Rareza.LEGENDARIO),
                    item("obsidiana", 2, 4, 12, Rareza.LEGENDARIO),
                    enc("tormenta", 8, Rareza.LEGENDARIO),
                    item("armadura_dorada", 1, 1, 8, Rareza.LEGENDARIO),
                    item("mithril", 1, 3, 7, Rareza.LEGENDARIO),
                    enc("sagrado", 5, Rareza.LEGENDARIO),
                    item("armadura_draconica", 1, 1, 4, Rareza.LEGENDARIO),
                    item("espada_legendaria", 1, 1, 2, Rareza.LEGENDARIO),
                    item("armadura_divina", 1, 1, 1, Rareza.LEGENDARIO))));

    public static Optional<Cofres> porId(String itemId) {
        return CATALOGO.stream().filter(c -> c.itemId().equals(itemId)).findFirst();
    }

    /** Suma de pesos de la tabla. */
    public int pesoTotal() {
        return tabla.stream().mapToInt(Premio::peso).sum();
    }
}
