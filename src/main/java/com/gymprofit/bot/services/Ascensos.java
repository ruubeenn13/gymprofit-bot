package com.gymprofit.bot.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogo de los <b>ascensos de carrera</b> (datos, en código): agrupa los sectores de
 * {@link Trabajos} en ramas, define la stat dominante de cada rama y los requisitos de cada salto
 * de tier. Satélite de {@code Trabajos} — mismo patrón que {@code Pasivos} con {@code Items} —
 * para no ampliar el record del catálogo base ni tocar sus tests.
 *
 * <p>El porqué de las ramas: el catálogo tiene ~26 sectores para ~50 puestos, así que la mayoría
 * no da para una carrera. Las ramas los agrupan en 7 recorridos con identidad; si una rama no
 * tiene un tier (hueco) el ascenso salta al siguiente existente, y si topa por debajo de t4 su
 * carrera simplemente acaba antes (el catálogo puede crecer por rama sin tocar esto).
 */
public final class Ascensos {

    /** Ramas de carrera. El nombre visible sale de i18n ({@code rama.<minúsculas>}). */
    public enum Rama { SALUD, TECNICA, TRANSPORTE, HOSTELERIA, NEGOCIOS, ARTE, SERVICIOS }

    /**
     * Requisitos de un salto de tier (los del <b>tier destino</b>).
     *
     * @param turnos   turnos currados en el puesto actual (antigüedad)
     * @param estudios puntos de estudios mínimos
     * @param stat     valor mínimo de la stat dominante de la rama
     * @param coins    coste en coins; se <b>queman</b> (sumidero antiinflación)
     */
    public record Requisitos(int turnos, int estudios, int stat, long coins) {
    }

    /** Sector del catálogo de trabajos → rama. Integridad vigilada por {@code AscensosTest}. */
    private static final Map<String, Rama> SECTOR_A_RAMA = Map.ofEntries(
            Map.entry("Sanidad", Rama.SALUD),
            Map.entry("Ciencia", Rama.SALUD),
            Map.entry("Deporte", Rama.SALUD),
            Map.entry("Tecnología", Rama.TECNICA),
            Map.entry("Oficios", Rama.TECNICA),
            Map.entry("Automoción", Rama.TECNICA),
            Map.entry("Construcción", Rama.TECNICA),
            Map.entry("Agricultura", Rama.TECNICA),
            Map.entry("Transporte", Rama.TRANSPORTE),
            Map.entry("Logística", Rama.TRANSPORTE),
            Map.entry("Aviación", Rama.TRANSPORTE),
            Map.entry("Hostelería", Rama.HOSTELERIA),
            Map.entry("Comercio", Rama.HOSTELERIA),
            Map.entry("Pesca", Rama.HOSTELERIA),
            Map.entry("Belleza", Rama.HOSTELERIA),
            Map.entry("Negocios", Rama.NEGOCIOS),
            Map.entry("Finanzas", Rama.NEGOCIOS),
            Map.entry("Derecho", Rama.NEGOCIOS),
            Map.entry("Educación", Rama.NEGOCIOS),
            Map.entry("Atención", Rama.NEGOCIOS),
            Map.entry("Arte", Rama.ARTE),
            Map.entry("Medios", Rama.ARTE),
            Map.entry("Entretenimiento", Rama.ARTE),
            Map.entry("Servicios", Rama.SERVICIOS),
            Map.entry("Seguridad", Rama.SERVICIOS),
            Map.entry("Emergencias", Rama.SERVICIOS));

    /** Stat dominante de cada rama (nombre de columna de {@code personajes}). */
    private static final Map<Rama, String> STAT_DE_RAMA = Map.of(
            Rama.SALUD, "resistencia",
            Rama.TECNICA, "fuerza",
            Rama.TRANSPORTE, "fuerza",
            Rama.HOSTELERIA, "carisma",
            Rama.NEGOCIOS, "carisma",
            Rama.ARTE, "carisma",
            Rama.SERVICIOS, "resistencia");

    /**
     * Requisitos por tier destino. Escala pegada a la economía lenta: t4 (50k) es el precio de un
     * bien caro, para que el último ascenso sea una decisión de late-game, no un trámite.
     */
    private static final Map<Integer, Requisitos> REQUISITOS = Map.of(
            2, new Requisitos(10, 5, 10, 500L),
            3, new Requisitos(25, 15, 25, 5_000L),
            4, new Requisitos(50, 30, 40, 50_000L));

    private Ascensos() {
    }

    /** Rama de un sector del catálogo, o {@code null} si el sector no está mapeado (test lo caza). */
    public static Rama ramaDe(String sector) {
        return SECTOR_A_RAMA.get(sector);
    }

    /** Stat dominante de la rama (columna de {@code personajes}). */
    public static String statDe(Rama rama) {
        return STAT_DE_RAMA.get(rama);
    }

    /** Requisitos del salto AL tier indicado. */
    public static Requisitos requisitosPara(int tierDestino) {
        return REQUISITOS.get(tierDestino);
    }

    /** Tier más bajo con puestos en la rama: el punto de entrada, siempre elegible. */
    public static int tierEntrada(Rama rama) {
        return Trabajos.CATALOGO.stream()
                .filter(t -> ramaDe(t.sector()) == rama)
                .mapToInt(Trabajos::tier).min().orElse(0);
    }

    /**
     * Siguiente tier <b>existente</b> de la rama por encima del actual, o vacío si la rama topa
     * ahí. Saltar huecos aquí (y no en el llamante) mantiene la regla en un único sitio.
     */
    public static Optional<Integer> siguienteTier(Rama rama, int tierActual) {
        return Trabajos.CATALOGO.stream()
                .filter(t -> ramaDe(t.sector()) == rama && t.tier() > tierActual)
                .map(Trabajos::tier)
                .min(Integer::compareTo);
    }

    /** Puestos de la rama en un tier concreto (para el autocompletado de {@code ascender}). */
    public static List<Trabajos> puestosDe(Rama rama, int tier) {
        return Trabajos.CATALOGO.stream()
                .filter(t -> ramaDe(t.sector()) == rama && t.tier() == tier)
                .toList();
    }
}
