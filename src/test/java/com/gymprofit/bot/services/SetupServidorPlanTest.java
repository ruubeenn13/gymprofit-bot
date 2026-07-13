package com.gymprofit.bot.services;

import com.gymprofit.bot.services.ConfigServidorService.Objetivo;
import com.gymprofit.bot.services.ConfigServidorService.TipoCanal;
import com.gymprofit.bot.services.SetupServidorPlan.CanalPlan;
import com.gymprofit.bot.services.SetupServidorPlan.CategoriaPlan;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica la coherencia del blueprint del servidor (datos puros, sin JDA): número de categorías
 * y canales, unicidad de nombres, cobertura de los canales de config y de los objetivos.
 */
class SetupServidorPlanTest {

    @Test
    void hayCategoriasYCanalesEsperados() {
        assertTrue(SetupServidorPlan.CATEGORIAS.size() >= 7, "al menos 7 categorías");
        long canales = SetupServidorPlan.CATEGORIAS.stream()
                .mapToLong(c -> c.canales().size()).sum();
        assertTrue(canales >= 20, "al menos 20 canales en total");
    }

    @Test
    void nombresDeCanalUnicos() {
        Set<String> vistos = new HashSet<>();
        for (CategoriaPlan cat : SetupServidorPlan.CATEGORIAS) {
            for (CanalPlan ch : cat.canales()) {
                assertTrue(vistos.add(ch.nombre()), "nombre de canal duplicado: " + ch.nombre());
            }
        }
    }

    @Test
    void todosLosCanalesDeConfigEstanMapeadosUnaVez() {
        Set<TipoCanal> mapeados = EnumSet.noneOf(TipoCanal.class);
        for (CategoriaPlan cat : SetupServidorPlan.CATEGORIAS) {
            for (CanalPlan ch : cat.canales()) {
                if (ch.claveConfig() != null) {
                    assertTrue(mapeados.add(ch.claveConfig()),
                            "clave de config duplicada: " + ch.claveConfig());
                }
            }
        }
        assertEquals(EnumSet.allOf(TipoCanal.class), mapeados,
                "cada canal configurable aparece exactamente una vez");
    }

    @Test
    void losCanalesDeMensajesLlevanTopicYLosDeVozNo() {
        for (CategoriaPlan cat : SetupServidorPlan.CATEGORIAS) {
            for (CanalPlan ch : cat.canales()) {
                boolean sinTopic = ch.tipo() == SetupServidorPlan.TipoCanalDiscord.VOZ
                        || ch.tipo() == SetupServidorPlan.TipoCanalDiscord.ESCENARIO;
                if (sinTopic) {
                    assertTrue(ch.topic() == null,
                            "canal de voz/escenario no debe llevar topic: " + ch.nombre());
                } else {
                    assertTrue(ch.topic() != null && !ch.topic().isBlank(),
                            "canal de mensajes sin topic: " + ch.nombre());
                    assertTrue(ch.topic().length() <= 1024,
                            "topic demasiado largo (>1024): " + ch.nombre());
                }
            }
        }
    }

    @Test
    void soloForosYMediaLlevanEtiquetasYSonValidas() {
        for (CategoriaPlan cat : SetupServidorPlan.CATEGORIAS) {
            for (CanalPlan ch : cat.canales()) {
                boolean postContainer = ch.tipo() == SetupServidorPlan.TipoCanalDiscord.FORO
                        || ch.tipo() == SetupServidorPlan.TipoCanalDiscord.MEDIA;
                if (postContainer) {
                    assertTrue(!ch.etiquetas().isEmpty(),
                            "foro/media sin etiquetas: " + ch.nombre());
                    ch.etiquetas().forEach(t -> assertTrue(t.length() <= 20,
                            "etiqueta >20 chars: " + t));
                } else {
                    assertTrue(ch.etiquetas().isEmpty(),
                            "solo foros y media llevan etiquetas: " + ch.nombre());
                }
            }
        }
    }

    @Test
    void losCuatroObjetivosTienenRol() {
        Set<Objetivo> objetivos = EnumSet.noneOf(Objetivo.class);
        SetupServidorPlan.ROLES.stream()
                .filter(r -> r.objetivo() != null)
                .forEach(r -> objetivos.add(r.objetivo()));
        assertEquals(EnumSet.allOf(Objetivo.class), objetivos);
    }

    @Test
    void existenLosRolesNuevosDeExperienciaIdiomaYNotificaciones() {
        Set<String> nombres = new HashSet<>();
        SetupServidorPlan.ROLES.forEach(r -> nombres.add(r.nombre()));
        for (String esperado : new String[]{"🌱 Principiante", "💪 Intermedio", "🔥 Avanzado",
                "🇪🇸 Español", "🇬🇧 English", "📅 Eventos", "🎁 Sorteos"}) {
            assertTrue(nombres.contains(esperado), "falta el rol " + esperado);
        }
    }

    @Test
    void losCanalesDeAnunciosSonSoloLectura() {
        for (String nombre : new String[]{"📣・anuncios", "📲・novedades-app",
                "📅・eventos", "🎁・sorteos"}) {
            CanalPlan canal = buscarCanal(nombre);
            assertTrue(canal.soloLectura(), nombre + " debe ser solo-lectura");
        }
    }

    @Test
    void losForosFitnessDanPermisoAlStaffTecnico() {
        // rutinas y dudas: Coach; nutrición: Nutricionista. Cada uno con al menos un override.
        assertTrue(tienePermisoDe(buscarCanal("📚・rutinas"), "🧑‍🏫 Coach"), "Coach en rutinas");
        assertTrue(tienePermisoDe(buscarCanal("❓・dudas"), "🧑‍🏫 Coach"), "Coach en dudas");
        assertTrue(tienePermisoDe(buscarCanal("🍎・nutrición"), "🍎 Nutricionista"), "Nutri en nutrición");
    }

    private static CanalPlan buscarCanal(String nombre) {
        return SetupServidorPlan.CATEGORIAS.stream()
                .flatMap(c -> c.canales().stream())
                .filter(c -> c.nombre().equals(nombre))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no existe el canal " + nombre));
    }

    private static boolean tienePermisoDe(CanalPlan canal, String rol) {
        return canal.permisos().stream().anyMatch(p -> p.rolNombre().equals(rol) && p.allow() != 0);
    }
}
