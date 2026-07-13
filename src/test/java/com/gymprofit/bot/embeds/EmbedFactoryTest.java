package com.gymprofit.bot.embeds;

import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que {@link EmbedFactory} aplica las reglas visuales de la SPEC §7: color por
 * categoría, un único emoji identificador al inicio del título y footer de marca con timestamp.
 */
class EmbedFactoryTest {

    @Test
    void aplicaColorDeLaCategoria() {
        MessageEmbed logro = EmbedFactory.base(EmbedFactory.Tipo.LOGRO, Messages.ES, "Nuevo logro").build();
        assertEquals(0xE8B84B, logro.getColorRaw() & 0xFFFFFF, "Los logros deben ir en dorado");

        MessageEmbed warn = EmbedFactory.base(EmbedFactory.Tipo.MODERACION, Messages.ES, "Aviso").build();
        assertEquals(0xC62828, warn.getColorRaw() & 0xFFFFFF, "La moderación debe ir en rojo");

        MessageEmbed ticket = EmbedFactory.base(EmbedFactory.Tipo.TICKET, Messages.ES, "Ticket").build();
        assertEquals(0x378ADD, ticket.getColorRaw() & 0xFFFFFF, "Los tickets usan azul (info general)");
    }

    @Test
    void anteponeUnUnicoEmojiAlTitulo() {
        MessageEmbed embed = EmbedFactory.base(EmbedFactory.Tipo.RACHA, Messages.ES, "Racha en llamas").build();
        assertNotNull(embed.getTitle());
        assertTrue(embed.getTitle().startsWith("🔥"), "El título debe empezar por el emoji del tipo");
        assertTrue(embed.getTitle().contains("Racha en llamas"));
    }

    @Test
    void poneFooterDeMarcaYTimestamp() {
        MessageEmbed embed = EmbedFactory.base(
                EmbedFactory.Tipo.ANUNCIO, Messages.ES, "Anuncio", "Descripción corta").build();
        assertNotNull(embed.getFooter());
        assertEquals("GymProBot • GymProFit", embed.getFooter().getText());
        assertNotNull(embed.getTimestamp(), "Todo embed lleva timestamp (§7 regla 3)");
        assertEquals("Descripción corta", embed.getDescription());
    }

    @Test
    void noPoneLineaDeAutor() {
        // La línea de autor (nombre+logo al inicio) se retiró: repetía la cabecera del mensaje y
        // el logo ya vive solo en el footer. Ningún embed debe llevar autor.
        MessageEmbed embed = EmbedFactory.base(EmbedFactory.Tipo.STATS, Messages.ES, "Stats").build();
        assertNull(embed.getAuthor(), "Los embeds ya no llevan línea de autor (solo footer)");
    }

    @Test
    void generaTimestampsDinamicosDeDiscord() {
        assertEquals("<t:1000:R>", EmbedFactory.tiempoRelativo(1000));
        assertEquals("<t:1000:F>", EmbedFactory.fechaLarga(1000));
    }
}
