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
    void todosLosCanalesDeTextoTienenTopicValido() {
        for (CategoriaPlan cat : SetupServidorPlan.CATEGORIAS) {
            for (CanalPlan ch : cat.canales()) {
                if (ch.tipo() == SetupServidorPlan.TipoCanalDiscord.TEXTO) {
                    assertTrue(ch.topic() != null && !ch.topic().isBlank(),
                            "canal de texto sin topic: " + ch.nombre());
                    assertTrue(ch.topic().length() <= 1024,
                            "topic demasiado largo (>1024): " + ch.nombre());
                } else {
                    assertTrue(ch.topic() == null,
                            "canal de voz no debe llevar topic: " + ch.nombre());
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
}
