package com.gymprofit.bot.services;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogo de <b>efectos pasivos</b> de los 30 ítems que hasta ahora se compraban y no hacían nada:
 * los 20 de {@link Items.Categoria#EQUIPO} y los 10 vehículos de {@link Items.Categoria#BIEN} (los
 * otros 10 BIEN son camas y ya tienen efecto vía {@link Camas}; darles además un pasivo sería doble
 * recompensa por la misma compra y obligaría a reabrir el balance del descanso).
 *
 * <p><b>Catálogo paralelo, igual que {@link Picos}, {@link Camas} y {@link Cofres}:</b> se empareja
 * por {@code itemId} y {@link Items} <b>no se toca</b>. Meter los bonos en {@code Items} obligaría a
 * ampliar un record de 8 componentes con campos nulos en 79 de 109 filas, y los precios de
 * {@code Items} son carga estructural probada por {@code RarezaTest} y por {@code Camas}.
 *
 * <p><b>Convenio de unidades</b> (crítico al implementar):
 * <ul>
 *   <li>Tipos <b>porcentuales</b> ({@link Tipo#SUELDO}, {@link Tipo#COOLDOWN_WORK}, {@link Tipo#XP},
 *       {@link Tipo#MINERIA_DURABILIDAD}, {@link Tipo#CRITICO}): la magnitud es una <b>fracción</b>
 *       ({@code 0.11} = 11 %), mismo convenio que {@code TrabajoService.BONO_ESTUDIOS} y
 *       {@code Encantamiento.magnitud()}.</li>
 *   <li>Tipos <b>planos</b> ({@link Tipo#ENERGIA_REGEN}, {@link Tipo#MINERIA_CANTIDAD},
 *       {@link Tipo#COMBATE_ATAQUE}, {@link Tipo#COMBATE_DEFENSA}): enteros guardados en un
 *       {@code double}; se redondean con {@code Math.round} al aplicarlos.</li>
 *   <li>{@link Tipo#COOLDOWN_WORK} es una <b>reducción</b>: la magnitud es positiva y se resta.</li>
 * </ul>
 *
 * <p><b>Reglas de diseño que el catálogo cumple</b> (y que {@code PasivosTest} vigila): las
 * magnitudes escalan con el precio; ningún ítem alcanza por sí solo el tope de su tipo (siempre hacen
 * falta ≥ 2 ranuras bien elegidas); los bonos son temáticos, no aleatorios; y cuantos más bonos, más
 * caro (3 solo en los vehículos de lujo).
 */
public final class Pasivos {

    private Pasivos() {
    }

    /** Los nueve tipos de bono. Cada uno tiene un tope global en {@link #TOPES}. */
    public enum Tipo {
        /** % extra sobre el pago de {@code /trabajo currar}. */
        SUELDO,
        /** Recorte del cooldown de 60 min de {@code /trabajo currar}. */
        COOLDOWN_WORK,
        /** % extra de XP de <b>cualquier</b> fuente. */
        XP,
        /** Energía extra en cada tick de {@code EnergiaJob}. */
        ENERGIA_REGEN,
        /** Minerales extra por {@code /minar} (sube también el tope duro del minado). */
        MINERIA_CANTIDAD,
        /** Probabilidad de <b>no</b> gastar durabilidad del pico. */
        MINERIA_DURABILIDAD,
        /** Ataque plano en batalla. */
        COMBATE_ATAQUE,
        /** Defensa plana en batalla. */
        COMBATE_DEFENSA,
        /** Probabilidad de crítico (aditiva, antes del tope propio de {@code CombateService}). */
        CRITICO
    }

    /**
     * Un bono concreto: qué mejora y cuánto.
     *
     * @param tipo     qué mejora
     * @param magnitud fracción (tipos porcentuales) o entero (tipos planos); ver el convenio arriba
     */
    public record Bono(Tipo tipo, double magnitud) {
    }

    /**
     * Los efectos pasivos de un ítem. Un ítem puede llevar varios bonos (1–3): un {@code jet} con un
     * único +11 % de sueldo es aburrido; uno que además acorta el cooldown y da XP se <i>siente</i>
     * como un jet.
     *
     * @param itemId id en {@link Items}
     * @param bonos  bonos que aporta (nunca vacío)
     */
    public record Pasivo(String itemId, List<Bono> bonos) {
    }

    /** Azúcar para que la tabla del catálogo se lea como la tabla del diseño. */
    private static Pasivo p(String itemId, Bono... bonos) {
        return new Pasivo(itemId, List.of(bonos));
    }

    private static Bono b(Tipo tipo, double magnitud) {
        return new Bono(tipo, magnitud);
    }

    /**
     * Los 30 ítems con efecto pasivo, ordenados por precio dentro de cada bloque. El comentario de
     * cada línea justifica el bono; esa justificación va también a i18n ({@code pasivo.<id>.desc})
     * para que {@code /pasivos ver} y el autocompletado se expliquen solos.
     */
    public static final List<Pasivo> CATALOGO = List.of(
            // --- Equipo (20) ---
            // Te la pones y ya vas al gym: bono de entrada, casi simbólico.
            p("gorra", b(Tipo.XP, 0.02)),
            // Ves venir el golpe antes que el otro.
            p("gafas", b(Tipo.CRITICO, 0.01)),
            // Cargas más piedra por viaje.
            p("mochila", b(Tipo.MINERIA_CANTIDAD, 1)),
            // Vas de uniforme: te pagan mejor y el chaleco algo protege.
            p("uniforme", b(Tipo.SUELDO, 0.04), b(Tipo.COMBATE_DEFENSA, 1)),
            // Con música entrenas más y mejor.
            p("auriculares", b(Tipo.XP, 0.03)),
            // Te organizas: turnos y rutinas siempre a mano.
            p("movil", b(Tipo.SUELDO, 0.03), b(Tipo.XP, 0.02)),
            // Buen calzado, menos castigo en las piernas.
            p("zapatillas", b(Tipo.ENERGIA_REGEN, 1), b(Tipo.COMBATE_DEFENSA, 2)),
            // Hierro en casa: pegas más fuerte y aguantas más.
            p("mancuernas", b(Tipo.COMBATE_ATAQUE, 3), b(Tipo.ENERGIA_REGEN, 1)),
            // Con tu propio juego de herramientas rindes más y rompes menos.
            p("herramientas", b(Tipo.SUELDO, 0.05), b(Tipo.MINERIA_DURABILIDAD, 0.05)),
            // Reflejos, competitividad y horas de práctica.
            p("consola", b(Tipo.XP, 0.04)),
            // Llegas antes al curro.
            p("patinete", b(Tipo.COOLDOWN_WORK, 0.04)),
            // Vas antes y encima haces cardio.
            p("bici", b(Tipo.COOLDOWN_WORK, 0.06), b(Tipo.ENERGIA_REGEN, 1)),
            // Aprender un instrumento entrena la cabeza.
            p("guitarra", b(Tipo.XP, 0.05)),
            // La herramienta que sirve para todo.
            p("portatil", b(Tipo.SUELDO, 0.06), b(Tipo.XP, 0.04)),
            // Documentas la veta y aprendes del terreno.
            p("camara", b(Tipo.MINERIA_CANTIDAD, 1), b(Tipo.XP, 0.03)),
            // Puntual y midiendo cada sesión.
            p("reloj", b(Tipo.SUELDO, 0.04), b(Tipo.XP, 0.05)),
            // Localizas el filón antes de picar a ciegas.
            p("telescopio", b(Tipo.MINERIA_CANTIDAD, 1), b(Tipo.MINERIA_DURABILIDAD, 0.08)),
            // Traje bueno: mejor sueldo y más presencia.
            p("traje", b(Tipo.SUELDO, 0.08), b(Tipo.CRITICO, 0.01)),
            // Explora la mina por ti y te dice dónde picar.
            p("dron", b(Tipo.MINERIA_CANTIDAD, 2), b(Tipo.MINERIA_DURABILIDAD, 0.10)),
            // Te mueves rápido y llegas a más sitios.
            p("moto", b(Tipo.COOLDOWN_WORK, 0.08), b(Tipo.SUELDO, 0.03)),
            // --- Bienes: vehículos (10). Las 10 camas quedan fuera a propósito (ya usan Camas). ---
            // El primer vehículo de verdad: cambia tu día entero.
            p("coche", b(Tipo.COOLDOWN_WORK, 0.09), b(Tipo.SUELDO, 0.04)),
            // Cabe todo: herramienta, material y lo que saques.
            p("furgoneta", b(Tipo.COOLDOWN_WORK, 0.07), b(Tipo.MINERIA_CANTIDAD, 1)),
            // Ocio de verdad: desconectas y vuelves entero.
            p("moto_agua", b(Tipo.ENERGIA_REGEN, 2), b(Tipo.XP, 0.03)),
            // Trabajo pesado: mueves tonelaje y cobras por ello.
            p("camion", b(Tipo.MINERIA_CANTIDAD, 2), b(Tipo.SUELDO, 0.06),
                    b(Tipo.MINERIA_DURABILIDAD, 0.08)),
            // Deportivo blindado: impone y protege.
            p("coche_lujo", b(Tipo.SUELDO, 0.07), b(Tipo.CRITICO, 0.02), b(Tipo.COMBATE_DEFENSA, 2)),
            // Apoyo aéreo: llegas a la veta y a la pelea desde arriba.
            p("helicoptero", b(Tipo.COOLDOWN_WORK, 0.10), b(Tipo.MINERIA_DURABILIDAD, 0.12),
                    b(Tipo.COMBATE_ATAQUE, 3)),
            // Vuelas a donde haga falta y aprendes por el camino.
            p("avioneta", b(Tipo.SUELDO, 0.09), b(Tipo.XP, 0.06), b(Tipo.ENERGIA_REGEN, 1)),
            // Jet privado: el mejor paquete de trabajo del juego.
            p("jet", b(Tipo.SUELDO, 0.11), b(Tipo.COOLDOWN_WORK, 0.11), b(Tipo.XP, 0.06)),
            // Descanso de lujo: vuelves nuevo y con contactos.
            p("yate", b(Tipo.ENERGIA_REGEN, 3), b(Tipo.XP, 0.07), b(Tipo.SUELDO, 0.07)),
            // El tope absoluto del catálogo y la única pieza sci-fi: rompe el techo en combate.
            p("cohete", b(Tipo.COMBATE_ATAQUE, 5), b(Tipo.COMBATE_DEFENSA, 4), b(Tipo.CRITICO, 0.03)));

    /**
     * Tope <b>global y saturante</b> de cada tipo: se topa <b>la suma</b> de las ranuras, nunca cada
     * bono por separado. Mismo mecanismo que {@code TrabajoService.BONO_ESTUDIOS_MAX} y que los topes
     * de {@code CombateService.probCritico}. Sin topes, cuatro ranuras de sueldo romperían la
     * economía lenta (ADR-010).
     *
     * <p>Cuatro topes (durabilidad, ataque, defensa y crítico) <b>no se alcanzan</b> ni con el mejor
     * build posible: es margen deliberado para añadir ítems en el futuro sin retocar los topes ni
     * bajarle el bono a nadie (que es lo que peor sienta).
     */
    // Map.of admite como mucho 10 pares y aquí son 9; con un décimo tipo habría que pasar a
    // Map.ofEntries. El EnumMap intermedio fija el orden natural del enum antes de congelar el mapa.
    public static final Map<Tipo, Double> TOPES = Map.copyOf(new EnumMap<>(Map.of(
            Tipo.SUELDO, 0.30,
            Tipo.COOLDOWN_WORK, 0.25,
            Tipo.XP, 0.20,
            Tipo.ENERGIA_REGEN, 5.0,
            Tipo.MINERIA_CANTIDAD, 3.0,
            Tipo.MINERIA_DURABILIDAD, 0.40,
            Tipo.COMBATE_ATAQUE, 12.0,
            Tipo.COMBATE_DEFENSA, 10.0,
            Tipo.CRITICO, 0.08)));

    /** ¿La magnitud de este tipo es una fracción (%) o un entero plano? */
    public static boolean esPorcentual(Tipo tipo) {
        return switch (tipo) {
            case SUELDO, COOLDOWN_WORK, XP, MINERIA_DURABILIDAD, CRITICO -> true;
            case ENERGIA_REGEN, MINERIA_CANTIDAD, COMBATE_ATAQUE, COMBATE_DEFENSA -> false;
        };
    }

    /** Busca el pasivo de un ítem. Vacío si el ítem no existe o no tiene efecto pasivo. */
    public static Optional<Pasivo> porId(String itemId) {
        return CATALOGO.stream().filter(p -> p.itemId().equals(itemId)).findFirst();
    }

    /**
     * Ítems que dan un tipo de bono, con su magnitud, en el orden del catálogo.
     *
     * <p>La usa {@code EnergiaJob} para <b>generar</b> el SQL del segundo pase: los ids y las
     * magnitudes salen siempre de aquí, nunca escritos a mano en la consulta. El orden es estable
     * ({@link LinkedHashMap}) para que el enlazado de parámetros del {@code PreparedStatement} sea
     * determinista.
     */
    public static Map<String, Double> fuentesDe(Tipo tipo) {
        Map<String, Double> res = new LinkedHashMap<>();
        for (Pasivo p : CATALOGO) {
            for (Bono b : p.bonos()) {
                if (b.tipo() == tipo) {
                    res.put(p.itemId(), b.magnitud());
                }
            }
        }
        return res;
    }
}
