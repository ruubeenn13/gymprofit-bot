package com.gymprofit.bot.commands.consultas;

import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.api.dtos.PaginaDTO;
import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica la lista y la ficha de {@code /ejercicios} sin JDA real: codec del estado en el
 * customId (ida y vuelta, truncado a 30, búsquedas con ':'), botones deshabilitados en los
 * extremos y contenido de los embeds.
 */
class EjerciciosComandoTest {

    private static EjercicioDTO dto(int id) {
        return new EjercicioDTO(id, "Ejercicio " + id, "Descripción " + id, "PECHO",
                "Pectoral mayor", "INTERMEDIO", "http://img/" + id + ".png", null,
                "Instrucciones " + id, 8, "Barra", true);
    }

    private static PaginaDTO<EjercicioDTO> pagina(int numero, int totalPaginas, boolean ultima) {
        return new PaginaDTO<>(IntStream.rangeClosed(1, 8).mapToObj(EjerciciosComandoTest::dto).toList(),
                numero, 8, 873, totalPaginas, ultima);
    }

    @Test
    void elCodecHaceIdaYVueltaConBusquedaConDosPuntos() {
        var filtros = new EjerciciosComando.Filtros(42L, 3, "PECHO", "INTERMEDIO", "press: banca");
        String id = filtros.codificar(EjerciciosComando.PREFIJO_NAV);
        assertTrue(id.startsWith("ejercicios:"));
        var leidos = EjerciciosComando.Filtros.parsear(id, EjerciciosComando.PREFIJO_NAV);
        assertEquals(filtros, leidos); // los ':' de la búsqueda sobreviven (va en último campo)
    }

    @Test
    void laBusquedaSeTruncaA30AlCodificar() {
        String larga = "a".repeat(80);
        var filtros = new EjerciciosComando.Filtros(42L, 0, "", "", larga);
        var leidos = EjerciciosComando.Filtros.parsear(
                filtros.codificar(EjerciciosComando.PREFIJO_VOLVER), EjerciciosComando.PREFIJO_VOLVER);
        assertEquals(30, leidos.busqueda().length());
        // Peor caso real bajo el límite de 100 de Discord.
        var peor = new EjerciciosComando.Filtros(Long.MAX_VALUE, 999, "FULLBODY",
                "PRINCIPIANTE", larga);
        assertTrue(peor.codificar(EjerciciosComando.PREFIJO_VOLVER).length() <= 100);
    }

    @Test
    void primeraYUltimaPaginaDeshabilitanSusFlechas() {
        var filtros = new EjerciciosComando.Filtros(1L, 0, "", "", "");
        List<ActionRow> filas = EjerciciosComando.construirComponentes(Messages.ES, pagina(0, 110, false), filtros);
        List<Button> botones = filas.get(0).getButtons();
        assertTrue(botones.get(0).isDisabled());  // ◀ en la primera
        assertFalse(botones.get(1).isDisabled());

        var alFinal = new EjerciciosComando.Filtros(1L, 109, "", "", "");
        List<Button> ultimos = EjerciciosComando
                .construirComponentes(Messages.ES, pagina(109, 110, true), alFinal)
                .get(0).getButtons();
        assertTrue(ultimos.get(1).isDisabled());  // ▶ en la última
    }

    @Test
    void laListaLlevaMenuConLosOchoDeLaPagina() {
        var filtros = new EjerciciosComando.Filtros(1L, 0, "", "", "");
        List<ActionRow> filas = EjerciciosComando.construirComponentes(Messages.ES, pagina(0, 110, false), filtros);
        assertEquals(2, filas.size()); // botones + menú
        StringSelectMenu menu = (StringSelectMenu) filas.get(1).getComponents().get(0);
        assertEquals(8, menu.getOptions().size());
    }

    @Test
    void elEmbedDeListaResumeYNumera() {
        MessageEmbed embed = EjerciciosComando.construirLista(Messages.ES, pagina(0, 110, false));
        assertTrue(embed.getDescription().contains("Ejercicio 1"));
        assertTrue(embed.getDescription().contains("873"));
    }

    @Test
    void laFichaMuestraTodosLosCampos() {
        MessageEmbed ficha = EjerciciosComando.construirFicha(Messages.ES, dto(7));
        assertTrue(ficha.getTitle().contains("Ejercicio 7"));
        assertEquals("http://img/7.png", ficha.getImage().getUrl());
        assertTrue(ficha.getFields().stream()
                .anyMatch(f -> "Pectoral mayor".equals(f.getValue())));
    }

    /**
     * Un nombre nulo o vacío en el catálogo no puede tumbar la respuesta: sin la guarda, la lista
     * hacía NPE y el menú lanzaba IllegalArgumentException (Discord exige label no vacío).
     */
    @Test
    void unNombreNuloOVacioNoRompeLaListaNiElMenu() {
        EjercicioDTO sinNombre = new EjercicioDTO(1, null, "d", "PECHO", null, "INTERMEDIO",
                null, null, null, 8, null, true);
        EjercicioDTO vacio = new EjercicioDTO(2, "  ", "d", "PECHO", null, "INTERMEDIO",
                null, null, null, 8, null, true);
        PaginaDTO<EjercicioDTO> pagina = new PaginaDTO<>(List.of(sinNombre, vacio), 0, 8, 2, 1, true);
        var filtros = new EjerciciosComando.Filtros(1L, 0, "", "", "");

        assertTrue(EjerciciosComando.construirLista(Messages.ES, pagina).getDescription()
                .contains("—"));
        StringSelectMenu menu = (StringSelectMenu) EjerciciosComando
                .construirComponentes(Messages.ES, pagina, filtros).get(1).getComponents().get(0);
        assertEquals(2, menu.getOptions().size());
        assertEquals("—", menu.getOptions().get(0).getLabel());
        assertTrue(EjerciciosComando.construirFicha(Messages.ES, sinNombre).getTitle()
                .contains("—"));
    }

    @Test
    void listaVaciaAvisaSinComponentes() {
        PaginaDTO<EjercicioDTO> vacia = new PaginaDTO<>(List.of(), 0, 8, 0, 0, true);
        MessageEmbed embed = EjerciciosComando.construirLista(Messages.ES, vacia);
        assertTrue(embed.getDescription().contains(Messages.get(Messages.ES, "ejercicios.vacio")));
        assertTrue(EjerciciosComando.construirComponentes(Messages.ES, vacia,
                new EjerciciosComando.Filtros(1L, 0, "", "", "zzz")).isEmpty());
    }
}
