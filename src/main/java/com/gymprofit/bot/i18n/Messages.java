package com.gymprofit.bot.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Helper de i18n sobre {@link ResourceBundle}. Toda cadena visible del bot sale de
 * {@code messages_es.properties} / {@code messages_en.properties}
 * (ver {@code GYMPROBOT_SPEC.md} §8). <b>Prohibido hardcodear texto.</b>
 *
 * <p>Idiomas soportados: {@code es} (por defecto) y {@code en}. Cualquier otro locale
 * cae a español.</p>
 */
public final class Messages {

    private static final String BUNDLE = "messages";
    public static final Locale ES = Locale.forLanguageTag("es");
    public static final Locale EN = Locale.forLanguageTag("en");

    private Messages() {
    }

    /**
     * Devuelve el texto asociado a {@code key} en el idioma indicado, aplicando
     * {@link MessageFormat} con los argumentos dados.
     *
     * @param locale idioma; {@code null} usa español
     * @param key    clave de la propiedad
     * @param args   argumentos para los placeholders {@code {0}, {1}...}
     * @return el texto formateado
     */
    public static String get(Locale locale, String key, Object... args) {
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, normalize(locale));
        String pattern = bundle.getString(key);
        return (args == null || args.length == 0) ? pattern : MessageFormat.format(pattern, args);
    }

    /**
     * Resuelve el {@link Locale} soportado a partir de una etiqueta de idioma (p. ej. la que da
     * Discord con {@code DiscordLocale.getLocale()}, como {@code "es-ES"} o {@code "en-US"}).
     * Cualquier idioma que no sea inglés cae a español (idioma por defecto).
     *
     * @param languageTag etiqueta de idioma; {@code null}/vacía usa español
     */
    public static Locale desdeTag(String languageTag) {
        return (languageTag != null && languageTag.toLowerCase(Locale.ROOT).startsWith("en"))
                ? EN : ES;
    }

    private static Locale normalize(Locale locale) {
        if (locale != null && "en".equalsIgnoreCase(locale.getLanguage())) {
            return EN;
        }
        return ES;
    }
}
