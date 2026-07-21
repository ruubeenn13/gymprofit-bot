package com.gymprofit.bot.db;

import java.util.Locale;

/**
 * Frase motivadora del banco bilingüe (tabla {@code frases}, sembrada en la V2). La usan
 * {@code /frase} y el post del ejercicio del día.
 *
 * @param id      identificador
 * @param textoEs texto en español
 * @param textoEn texto en inglés
 * @param autor   autor, o {@code null} si es anónima/propia
 */
public record Frase(long id, String textoEs, String textoEn, String autor) {

    /** Texto en el idioma pedido (cualquier cosa que no sea inglés cae a español). */
    public String texto(Locale locale) {
        return (locale != null && "en".equalsIgnoreCase(locale.getLanguage()))
                ? textoEn : textoEs;
    }
}
