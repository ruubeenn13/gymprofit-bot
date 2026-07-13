package com.gymprofit.bot.services;

import java.util.List;
import java.util.Optional;

/**
 * Catálogo del <b>árbol de mejoras</b> (datos, en código). Tres ramas (fuerza, resistencia,
 * carisma), cada una con niveles encadenados: para comprar un nodo hay que tener su
 * <b>prerrequisito</b>. Cada nodo sube su atributo y cuesta cada vez más (sumidero de dinero,
 * progresión lenta). El nombre de la rama sale de i18n con clave {@code atributo.<atributo>}.
 *
 * @param id        id estable del nodo (columna {@code mejoras.nodo})
 * @param atributo  atributo que sube (fuerza/resistencia/carisma; también columna de personajes)
 * @param nivel     nivel dentro de la rama (1 = primero)
 * @param prereq    id del nodo previo requerido, o {@code null} si es la raíz de la rama
 * @param valor     cuánto sube el atributo
 * @param precio    coste en coins
 */
public record Mejoras(String id, String atributo, int nivel, String prereq, int valor, long precio) {

    /** Atributos que tienen rama, en orden de visualización. */
    public static final List<String> RAMAS = List.of("fuerza", "resistencia", "carisma");

    /** Árbol completo: 3 ramas × 4 niveles. */
    public static final List<Mejoras> CATALOGO = List.of(
            new Mejoras("fuerza1", "fuerza", 1, null, 2, 500),
            new Mejoras("fuerza2", "fuerza", 2, "fuerza1", 3, 2000),
            new Mejoras("fuerza3", "fuerza", 3, "fuerza2", 5, 6000),
            new Mejoras("fuerza4", "fuerza", 4, "fuerza3", 8, 15000),
            new Mejoras("resistencia1", "resistencia", 1, null, 2, 500),
            new Mejoras("resistencia2", "resistencia", 2, "resistencia1", 3, 2000),
            new Mejoras("resistencia3", "resistencia", 3, "resistencia2", 5, 6000),
            new Mejoras("resistencia4", "resistencia", 4, "resistencia3", 8, 15000),
            new Mejoras("carisma1", "carisma", 1, null, 2, 500),
            new Mejoras("carisma2", "carisma", 2, "carisma1", 3, 2000),
            new Mejoras("carisma3", "carisma", 3, "carisma2", 5, 6000),
            new Mejoras("carisma4", "carisma", 4, "carisma3", 8, 15000));

    /** Busca un nodo por id. */
    public static Optional<Mejoras> porId(String id) {
        return CATALOGO.stream().filter(m -> m.id().equals(id)).findFirst();
    }

    /** Nodos de una rama (atributo), en orden de nivel. */
    public static List<Mejoras> deRama(String atributo) {
        return CATALOGO.stream().filter(m -> m.atributo().equals(atributo)).toList();
    }
}
