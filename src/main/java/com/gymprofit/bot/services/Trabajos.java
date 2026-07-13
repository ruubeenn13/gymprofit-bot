package com.gymprofit.bot.services;

import java.util.List;
import java.util.Optional;

/**
 * Catálogo de trabajos (datos, en código). Carreras de varios sectores con tiers y requisito de
 * nivel; cada trabajo paga en un rango y gasta energía por turno. Los salarios están anclados a la
 * escala económica lenta del RPG (ver spec). El nombre/descripcion sale de i18n con clave
 * {@code trabajo.<id>}.
 *
 * @param id             identificador estable (clave i18n y columna {@code personajes.trabajo})
 * @param sector         sector (tecnología, hostelería, sanidad…)
 * @param tier           nivel de la carrera (1 = entrada)
 * @param requisitoNivel nivel de servidor (XP) mínimo para poder ejercerlo
 * @param salarioMin     pago mínimo por turno
 * @param salarioMax     pago máximo por turno
 * @param energiaCoste   energía que consume un turno
 */
public record Trabajos(String id, String sector, int tier, int requisitoNivel,
                       int salarioMin, int salarioMax, int energiaCoste) {

    /** Catálogo completo, ordenado por tier/requisito. */
    public static final List<Trabajos> CATALOGO = List.of(
            // --- Tier 1: entrada (sin requisito) ---
            new Trabajos("repartidor", "Transporte", 1, 0, 30, 50, 20),
            new Trabajos("camarero", "Hostelería", 1, 0, 30, 55, 20),
            new Trabajos("cajero", "Comercio", 1, 0, 30, 50, 15),
            new Trabajos("limpiador", "Servicios", 1, 0, 25, 45, 20),
            new Trabajos("jardinero", "Agricultura", 1, 0, 30, 55, 20),
            new Trabajos("mozo_almacen", "Logística", 1, 0, 35, 60, 20),
            new Trabajos("teleoperador", "Atención", 1, 0, 30, 50, 15),
            new Trabajos("repartidor_comida", "Transporte", 1, 2, 40, 65, 20),
            new Trabajos("pescador", "Pesca", 1, 0, 35, 55, 20),
            new Trabajos("peluquero", "Belleza", 1, 2, 40, 60, 15),
            new Trabajos("panadero", "Hostelería", 1, 2, 35, 58, 20),
            new Trabajos("socorrista", "Deporte", 1, 3, 40, 62, 20),
            new Trabajos("barrendero", "Servicios", 1, 0, 28, 48, 20),
            // --- Tier 2: cualificado (nivel 5-10) ---
            new Trabajos("programador_jr", "Tecnología", 2, 5, 80, 140, 25),
            new Trabajos("cocinero", "Hostelería", 2, 5, 70, 120, 25),
            new Trabajos("electricista", "Oficios", 2, 6, 85, 145, 30),
            new Trabajos("fontanero", "Oficios", 2, 6, 85, 140, 30),
            new Trabajos("mecanico", "Automoción", 2, 7, 90, 150, 30),
            new Trabajos("disenador", "Arte", 2, 7, 80, 150, 25),
            new Trabajos("enfermero", "Sanidad", 2, 8, 90, 150, 30),
            new Trabajos("entrenador", "Deporte", 2, 8, 85, 140, 30),
            new Trabajos("profesor", "Educación", 2, 9, 95, 160, 25),
            new Trabajos("periodista", "Medios", 2, 9, 90, 155, 25),
            new Trabajos("contable", "Finanzas", 2, 10, 100, 165, 25),
            new Trabajos("policia", "Seguridad", 2, 10, 95, 160, 35),
            new Trabajos("taxista", "Transporte", 2, 5, 75, 130, 20),
            new Trabajos("camionero", "Transporte", 2, 6, 85, 145, 25),
            new Trabajos("carpintero", "Oficios", 2, 6, 85, 140, 30),
            new Trabajos("soldador", "Oficios", 2, 7, 90, 150, 30),
            new Trabajos("bombero", "Emergencias", 2, 8, 95, 160, 35),
            new Trabajos("farmaceutico", "Sanidad", 2, 9, 100, 165, 25),
            new Trabajos("fotografo", "Arte", 2, 7, 80, 150, 25),
            new Trabajos("dj", "Entretenimiento", 2, 8, 85, 170, 25),
            // --- Tier 3: profesional (nivel 15-25) ---
            new Trabajos("ingeniero", "Tecnología", 3, 15, 180, 300, 35),
            new Trabajos("arquitecto", "Construcción", 3, 16, 190, 320, 35),
            new Trabajos("abogado", "Derecho", 3, 18, 200, 340, 30),
            new Trabajos("actor", "Entretenimiento", 3, 18, 150, 400, 30),
            new Trabajos("piloto", "Aviación", 3, 20, 230, 380, 40),
            new Trabajos("medico", "Sanidad", 3, 20, 220, 380, 40),
            new Trabajos("cientifico", "Ciencia", 3, 22, 210, 360, 35),
            new Trabajos("veterinario", "Sanidad", 3, 16, 190, 320, 35),
            new Trabajos("dentista", "Sanidad", 3, 17, 200, 340, 30),
            new Trabajos("psicologo", "Sanidad", 3, 15, 180, 300, 25),
            new Trabajos("banquero", "Finanzas", 3, 20, 220, 360, 30),
            new Trabajos("notario", "Derecho", 3, 22, 230, 370, 25),
            new Trabajos("empresario", "Negocios", 3, 25, 250, 450, 35),
            // --- Tier 4: élite (nivel 30-40) ---
            new Trabajos("cirujano", "Sanidad", 4, 30, 500, 800, 45),
            new Trabajos("juez", "Derecho", 4, 32, 520, 820, 40),
            new Trabajos("astronauta", "Ciencia", 4, 38, 600, 950, 50),
            new Trabajos("ceo", "Negocios", 4, 40, 650, 1000, 45));

    /** Busca un trabajo por id. */
    public static Optional<Trabajos> porId(String id) {
        return CATALOGO.stream().filter(t -> t.id().equals(id)).findFirst();
    }
}
