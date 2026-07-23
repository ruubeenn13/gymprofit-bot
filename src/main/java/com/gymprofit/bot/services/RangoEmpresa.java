package com.gymprofit.bot.services;

/**
 * Rango interno de un miembro dentro de su empresa. En F1 solo se usan {@link #DUENO} (fundador) y
 * {@link #BECARIO} (nuevos miembros); el resto existe para la gestión de rangos de F2. El orden del
 * enum va de menor a mayor autoridad. Cada valor tiene su clave i18n {@code rango.<minuscula>}.
 */
public enum RangoEmpresa {
    BECARIO, EMPLEADO, ENCARGADO, DIRECTIVO, DUENO;

    /** Clave i18n del nombre visible (p. ej. {@code rango.dueno}). */
    public String claveI18n() {
        return "rango." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
