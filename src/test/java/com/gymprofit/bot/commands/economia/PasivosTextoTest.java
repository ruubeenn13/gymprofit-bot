package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.PasivoService;
import com.gymprofit.bot.services.PasivoService.EstadoRanura;
import com.gymprofit.bot.services.Pasivos;
import com.gymprofit.bot.services.Pasivos.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica los constructores de vista de {@code /pasivos} (estáticos y puros: no tocan JDA ni BD).
 * Se prueba el <b>resultado</b> que ve el jugador, no la aritmética: los bonos se enseñan ya sumados
 * y ya topados, y el tope se marca.
 */
class PasivosTextoTest {

    /** Mapa con los nueve tipos a 0,0 y los pares indicados sobrescritos: el mismo que da el service. */
    private static Map<Tipo, Double> bonos(Object... pares) {
        Map<Tipo, Double> m = new EnumMap<>(Tipo.class);
        for (Tipo t : Tipo.values()) {
            m.put(t, 0.0);
        }
        for (int i = 0; i < pares.length; i += 2) {
            m.put((Tipo) pares[i], (Double) pares[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("los bonos se muestran sumados, no la aritmética, y con el signo correcto")
    void lineaDeBonos() {
        String linea = PasivosTexto.bonos(Messages.ES,
                bonos(Tipo.SUELDO, 0.18, Tipo.COOLDOWN_WORK, 0.11, Tipo.ENERGIA_REGEN, 2.0));
        assertTrue(linea.contains("+18 % sueldo"), linea);
        assertTrue(linea.contains("−11 % cooldown de trabajo"), linea + " (el cooldown resta)");
        assertTrue(linea.contains("+2 energía por tick"), linea);
        assertFalse(linea.contains("XP"), "los tipos a 0 no se pintan: " + linea);
    }

    @Test
    @DisplayName("un tipo saturado se marca con (tope)")
    void marcaDeTope() {
        String linea = PasivosTexto.bonos(Messages.ES, bonos(Tipo.SUELDO, 0.30));
        assertTrue(linea.contains("+30 % sueldo"), linea);
        assertTrue(linea.contains(Messages.get(Messages.ES, "pasivos.ver.tope")), linea);
    }

    @Test
    @DisplayName("sin ningún bono la línea sale vacía (no se ensucia el perfil de un novato)")
    void sinBonosCadenaVacia() {
        assertEquals("", PasivosTexto.bonos(Messages.ES, bonos()));
        assertEquals("", PasivosTexto.bonos(Messages.ES, Map.of()));
    }

    @Test
    @DisplayName("las cuatro ranuras se pintan con su estado: ocupada, vacía, bloqueada y ausente")
    void listaDeRanuras() {
        String texto = PasivosTexto.ranuras(Messages.ES, List.of(
                new EstadoRanura(1, "jet", false, false),
                new EstadoRanura(2, "yate", false, true),
                new EstadoRanura(3, null, true, false),
                new EstadoRanura(4, null, true, false)));
        assertTrue(texto.contains("1️⃣"), texto);
        assertTrue(texto.contains(Messages.get(Messages.ES, "item.jet")), texto);
        assertTrue(texto.contains("⚠️"), "el yate vendido se marca: " + texto);
        assertTrue(texto.contains("🔒"), "las bloqueadas llevan candado: " + texto);
        assertTrue(texto.contains("25") && texto.contains("50"),
                "cada bloqueada dice su nivel: " + texto);
    }

    @Test
    @DisplayName("el pie anuncia el siguiente desbloqueo, y desaparece con las 4 abiertas")
    void pieDeSiguienteDesbloqueo() {
        String pie = PasivosTexto.pie(Messages.ES, 21);
        assertTrue(pie.contains("3"), "la siguiente es la ranura 3: " + pie);
        assertTrue(pie.contains("25"), pie);
        assertTrue(pie.contains("4"), "le faltan 4 niveles: " + pie);
        assertEquals(Messages.get(Messages.ES, "pasivos.ver.pie.completo"),
                PasivosTexto.pie(Messages.ES, 50));
    }

    @Test
    @DisplayName("todos los ítems del catálogo tienen descripción en ES y EN")
    void descripcionesCompletas() {
        for (Pasivos.Pasivo p : Pasivos.CATALOGO) {
            String es = PasivosTexto.descripcion(Messages.ES, p.itemId());
            String en = PasivosTexto.descripcion(Messages.EN, p.itemId());
            assertFalse(es.isBlank(), "falta pasivo." + p.itemId() + ".desc (ES)");
            assertFalse(en.isBlank(), "falta pasivo." + p.itemId() + ".desc (EN)");
        }
    }

    @Test
    @DisplayName("todos los tipos tienen nombre traducido en ES y EN")
    void nombresDeTipoCompletos() {
        for (Tipo t : Tipo.values()) {
            assertFalse(PasivosTexto.nombreTipo(Messages.ES, t).isBlank(), "ES: " + t);
            assertFalse(PasivosTexto.nombreTipo(Messages.EN, t).isBlank(), "EN: " + t);
        }
        assertEquals(4, PasivoService.RANURAS_MAX, "el formateo asume 4 ranuras");
    }
}
