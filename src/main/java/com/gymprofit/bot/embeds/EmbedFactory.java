package com.gymprofit.bot.embeds;

import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.Color;
import java.time.Instant;
import java.util.Locale;

/**
 * Fábrica central de embeds (SPEC §7). <b>Única vía</b> para crear embeds: prohibido usar
 * {@code new EmbedBuilder()} suelto. Garantiza que todos cumplan las reglas visuales —color por
 * categoría, un solo emoji identificador en el título y footer + timestamp de marca.
 *
 * <p>Uso: {@code EmbedFactory.base(Tipo.LOGRO, locale, titulo)} devuelve un {@link EmbedBuilder}
 * ya coloreado y con footer; el llamador añade descripción y fields (localizados vía i18n) y
 * llama a {@code build()}.</p>
 */
public final class EmbedFactory {

    /**
     * Paleta por categoría (SPEC §7). Son los <b>únicos</b> colores permitidos; no se inventan
     * otros.
     */
    public enum Categoria {
        /** Marca, bienvenida, ejercicio del día, anuncios. */
        MARCA(0xFF6A00),
        /** Logros, rachas, insignias. */
        LOGROS(0xE8B84B),
        /** Economía, tienda, retos completados. */
        ECONOMIA(0x1E8E4A),
        /** Stats, perfil e info general. */
        STATS(0x378ADD),
        /** Moderación y warns. */
        MODERACION(0xC62828);

        private final Color color;

        Categoria(int rgb) {
            this.color = new Color(rgb);
        }

        public Color color() {
            return color;
        }
    }

    /**
     * Tipo de embed: asocia cada emoji identificador (§7 regla 2, máximo uno por título) con su
     * categoría de color.
     *
     * <p>La §7 fija el color de anuncios/ejercicio (naranja), logros/racha (dorado),
     * economía/retos (verde) y moderación (rojo). Para los tipos cuyo color no especifica
     * —duelos, trivia, sugerencias, tickets— se usa <b>azul</b> como color de información
     * general (decisión de diseño; el tono serio de tickets se transmite por el texto, no por un
     * rojo de sanción).</p>
     */
    public enum Tipo {
        ANUNCIO("📣", Categoria.MARCA),
        EJERCICIO("🏋️", Categoria.MARCA),
        BIENVENIDA("👋", Categoria.MARCA),
        LOGRO("🏆", Categoria.LOGROS),
        RACHA("🔥", Categoria.LOGROS),
        ECONOMIA("🪙", Categoria.ECONOMIA),
        RETO("🎯", Categoria.ECONOMIA),
        STATS("📊", Categoria.STATS),
        DUELO("⚔️", Categoria.STATS),
        TRIVIA("🧠", Categoria.STATS),
        SUGERENCIA("💡", Categoria.STATS),
        TICKET("🎫", Categoria.STATS),
        MODERACION("🛡️", Categoria.MODERACION);

        private final String emoji;
        private final Categoria categoria;

        Tipo(String emoji, Categoria categoria) {
            this.emoji = emoji;
            this.categoria = categoria;
        }

        public String emoji() {
            return emoji;
        }

        public Categoria categoria() {
            return categoria;
        }
    }

    private EmbedFactory() {
    }

    /**
     * Base de cualquier embed: aplica el color de la categoría del tipo, antepone su emoji al
     * título (un único identificador) y fija el footer de marca con timestamp.
     *
     * @param tipo   tipo de embed (define emoji y color)
     * @param locale idioma para el footer
     * @param titulo título ya localizado por el llamador (sin emoji)
     * @return el {@link EmbedBuilder} listo para añadir descripción/fields
     */
    public static EmbedBuilder base(Tipo tipo, Locale locale, String titulo) {
        return new EmbedBuilder()
                .setColor(tipo.categoria().color())
                .setTitle(tipo.emoji() + "  " + titulo)
                .setFooter(Messages.get(locale, "embed.footer"))
                .setTimestamp(Instant.now());
    }

    /**
     * Igual que {@link #base(Tipo, Locale, String)} pero con descripción corta (§7 regla 4).
     */
    public static EmbedBuilder base(Tipo tipo, Locale locale, String titulo, String descripcion) {
        return base(tipo, locale, titulo).setDescription(descripcion);
    }
}
