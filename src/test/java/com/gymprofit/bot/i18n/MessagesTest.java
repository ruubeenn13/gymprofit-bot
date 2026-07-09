package com.gymprofit.bot.i18n;

import org.junit.jupiter.api.Test;

import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guarda la regla de sincronización ES/EN (SPEC §8, review-checklist): ambos bundles
 * deben existir y exponer exactamente el mismo conjunto de claves. Si alguien añade una
 * clave a un idioma y olvida el otro, este test falla.
 */
class MessagesTest {

    @Test
    void ambosBundlesCargan() {
        assertNotNull(ResourceBundle.getBundle("messages", Messages.ES));
        assertNotNull(ResourceBundle.getBundle("messages", Messages.EN));
    }

    @Test
    void esYEnTienenLasMismasClaves() {
        Set<String> es = new TreeSet<>(ResourceBundle.getBundle("messages", Messages.ES).keySet());
        Set<String> en = new TreeSet<>(ResourceBundle.getBundle("messages", Messages.EN).keySet());
        assertEquals(es, en, "Las claves de messages_es y messages_en deben coincidir");
    }

    @Test
    void desdeTagResuelveElIdioma() {
        assertEquals(Messages.EN, Messages.desdeTag("en-US"));
        assertEquals(Messages.EN, Messages.desdeTag("en-GB"));
        assertEquals(Messages.ES, Messages.desdeTag("es-ES"));
        assertEquals(Messages.ES, Messages.desdeTag("fr"), "Idioma no soportado cae a español");
        assertEquals(Messages.ES, Messages.desdeTag(null), "Sin idioma, español por defecto");
    }
}
