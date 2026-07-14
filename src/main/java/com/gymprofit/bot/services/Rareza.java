package com.gymprofit.bot.services;

/**
 * Rareza de un ítem para el botín (COMBAT-4a). No se guarda en el catálogo: se <b>deriva</b> del
 * propio ítem (tier de arma/armadura por su stat, o precio en el resto), así que no hay que anotar
 * los 60+ ítems a mano. Se usa para pintar el loot con su color (⬜ común, 🟦 raro, 🟪 épico,
 * 🟨 legendario).
 */
public enum Rareza {
    COMUN("⬜"),
    RARO("🟦"),
    EPICO("🟪"),
    LEGENDARIO("🟨");

    private final String emoji;

    Rareza(String emoji) {
        this.emoji = emoji;
    }

    public String emoji() {
        return emoji;
    }

    /** Rareza derivada de un ítem: por stat de combate (armas/armaduras) o por precio (el resto). */
    public static Rareza de(Items item) {
        return switch (item.categoria()) {
            case ARMA -> porUmbral(item.ataque(), 10, 25, 50);
            case ARMADURA -> porUmbral(item.defensa(), 8, 20, 40);
            default -> porUmbral(item.precio(), 100, 1000, 50000);
        };
    }

    /** Clasifica un valor en las cuatro rarezas según tres umbrales crecientes. */
    private static Rareza porUmbral(long valor, long raro, long epico, long legendario) {
        if (valor >= legendario) {
            return LEGENDARIO;
        }
        if (valor >= epico) {
            return EPICO;
        }
        if (valor >= raro) {
            return RARO;
        }
        return COMUN;
    }
}
