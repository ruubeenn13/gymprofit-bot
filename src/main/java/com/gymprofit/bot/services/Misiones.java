package com.gymprofit.bot.services;

import java.util.List;
import java.util.Optional;

/**
 * Catálogo de misiones de caza (COMBAT-6a, en código). Cada misión es un objetivo repetible del tipo
 * «mata N de X» que se cumple peleando: al ganar un combate se incrementan las misiones que casan con
 * el monstruo vencido y, al llegar a la meta, se completan solas (pagan coins + XP y reinician). El
 * nombre sale de i18n con clave {@code mision.<id>}.
 *
 * @param id       identificador estable (clave i18n y columna {@code mision_progreso.mision_id})
 * @param tipo     qué cuenta para el progreso
 * @param objetivo id de monstruo ({@code MONSTRUO}) o de mundo ({@code MUNDO}); ignorado en {@code JEFE}
 * @param meta     muertes necesarias para completarla
 * @param coins    recompensa en coins
 * @param xp       recompensa en XP
 */
public record Misiones(String id, Tipo tipo, String objetivo, int meta, long coins, int xp) {

    /** Qué muertes cuentan para una misión. */
    public enum Tipo {
        /** Un monstruo concreto (por id). */
        MONSTRUO,
        /** Cualquier monstruo de un mundo (por id de mundo). */
        MUNDO,
        /** Cualquier jefe. */
        JEFE
    }

    /** Catálogo completo. */
    public static final List<Misiones> CATALOGO = List.of(
            new Misiones("cazador_lobos", Tipo.MONSTRUO, "lobo", 10, 150, 60),
            new Misiones("plaga_goblins", Tipo.MONSTRUO, "goblin", 10, 200, 80),
            new Misiones("cazarrecompensas", Tipo.MONSTRUO, "bandido", 8, 300, 120),
            new Misiones("limpieza_bosque", Tipo.MUNDO, "bosque", 15, 250, 100),
            new Misiones("azote_cueva", Tipo.MUNDO, "cueva", 15, 350, 140),
            new Misiones("purga_pantano", Tipo.MUNDO, "pantano", 15, 500, 200),
            new Misiones("terror_desierto", Tipo.MUNDO, "desierto", 15, 700, 280),
            new Misiones("matajefes", Tipo.JEFE, "", 3, 800, 300));

    public static Optional<Misiones> porId(String id) {
        return CATALOGO.stream().filter(m -> m.id().equals(id)).findFirst();
    }

    /** ¿Cuenta la muerte de este monstruo para esta misión? */
    public boolean casa(Monstruos monstruo) {
        return switch (tipo) {
            case MONSTRUO -> monstruo.id().equals(objetivo);
            case MUNDO -> monstruo.mundo().equals(objetivo);
            case JEFE -> monstruo.esJefe();
        };
    }
}
