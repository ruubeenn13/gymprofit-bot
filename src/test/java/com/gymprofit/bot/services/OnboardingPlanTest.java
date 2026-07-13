package com.gymprofit.bot.services;

import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica el cuerpo JSON del onboarding sin tocar Discord: nº de preguntas y canales, flags de
 * cada prompt, resolución de roles/canales a IDs y omisión de referencias inexistentes.
 */
class OnboardingPlanTest {

    /** Construye mapas nombre→id que cubren todos los roles y canales referenciados por el plan. */
    private static void mapasCompletos(Map<String, Long> roles, Map<String, Long> canales) {
        long id = 1000L;
        for (OnboardingPlan.Prompt prompt : OnboardingPlan.PROMPTS) {
            for (OnboardingPlan.Opcion opcion : prompt.opciones()) {
                for (String rol : opcion.roles()) {
                    roles.putIfAbsent(rol, id++);
                }
                for (String canal : opcion.canales()) {
                    canales.putIfAbsent(canal, id++);
                }
            }
        }
        for (String canal : OnboardingPlan.CANALES_DEFECTO) {
            canales.putIfAbsent(canal, id++);
        }
    }

    @Test
    void bodyTieneCincoPreguntasYCanalesPorDefecto() {
        Map<String, Long> roles = new HashMap<>();
        Map<String, Long> canales = new HashMap<>();
        mapasCompletos(roles, canales);

        DataObject body = OnboardingPlan.construirBody(roles, canales);

        assertTrue(body.getBoolean("enabled"), "onboarding habilitado");
        assertEquals(5, body.getArray("prompts").length(), "5 preguntas");
        assertEquals(OnboardingPlan.CANALES_DEFECTO.size(),
                body.getArray("default_channel_ids").length(), "todos los canales por defecto resueltos");
        assertTrue(body.getArray("default_channel_ids").length() >= 7, "Discord exige >=7");
    }

    @Test
    void primeraPreguntaEsIdiomaObligatoriaYUnica() {
        Map<String, Long> roles = new HashMap<>();
        Map<String, Long> canales = new HashMap<>();
        mapasCompletos(roles, canales);

        DataObject idioma = OnboardingPlan.construirBody(roles, canales).getArray("prompts").getObject(0);

        assertTrue(idioma.getString("title").contains("Idioma"), "es la pregunta de idioma");
        assertTrue(idioma.getBoolean("single_select"), "single-select");
        assertTrue(idioma.getBoolean("required"), "obligatoria");
        assertEquals(2, idioma.getArray("options").length(), "2 idiomas");
        assertEquals(1, idioma.getArray("options").getObject(0).getArray("role_ids").length(),
                "cada idioma asigna 1 rol");
    }

    @Test
    void interesesAsignaCanalesSinRoles() {
        Map<String, Long> roles = new HashMap<>();
        Map<String, Long> canales = new HashMap<>();
        mapasCompletos(roles, canales);

        DataArray prompts = OnboardingPlan.construirBody(roles, canales).getArray("prompts");
        DataObject intereses = prompts.getObject(prompts.length() - 1);
        DataObject opcion = intereses.getArray("options").getObject(0);

        assertTrue(intereses.getString("title").contains("interesa"), "es la pregunta de intereses");
        assertEquals(0, opcion.getArray("role_ids").length(), "intereses no da rol");
        assertEquals(1, opcion.getArray("channel_ids").length(), "intereses mete en 1 canal");
    }

    @Test
    void referenciasInexistentesSeOmiten() {
        // Mapas vacíos: ningún rol ni canal se resuelve.
        DataObject body = OnboardingPlan.construirBody(new HashMap<>(), new HashMap<>());

        assertEquals(0, body.getArray("default_channel_ids").length(), "sin canales resueltos");
        DataObject opcion = body.getArray("prompts").getObject(0).getArray("options").getObject(0);
        assertEquals(0, opcion.getArray("role_ids").length(), "sin roles resueltos");
        assertEquals(0, opcion.getArray("channel_ids").length(), "sin canales resueltos");
    }
}
