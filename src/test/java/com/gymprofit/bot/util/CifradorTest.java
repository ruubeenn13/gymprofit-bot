package com.gymprofit.bot.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica el cifrado AES-GCM: roundtrip, no determinismo, nulos y estado deshabilitado. */
class CifradorTest {

    private final Cifrador cifrador = new Cifrador(Cifrador.generarClaveBase64());

    @Test
    void cifrarYDescifrarDevuelveElOriginal() {
        String texto = "Motivo: spam en 💬・general y flood de menciones";
        String cifrado = cifrador.cifrar(texto);
        assertNotEquals(texto, cifrado, "el cifrado no debe parecerse al original");
        assertEquals(texto, cifrador.descifrar(cifrado));
    }

    @Test
    void mismoTextoCifraDistintoCadaVez() {
        // GCM usa IV aleatorio: dos cifrados del mismo texto no deben coincidir.
        assertNotEquals(cifrador.cifrar("repetido"), cifrador.cifrar("repetido"));
    }

    @Test
    void nullSeMantieneNull() {
        assertNull(cifrador.cifrar(null));
        assertNull(cifrador.descifrar(null));
    }

    @Test
    void sinClaveNoEstaHabilitadoYLanza() {
        Cifrador sinClave = new Cifrador("");
        assertFalse(sinClave.habilitado());
        assertThrows(IllegalStateException.class, () -> sinClave.cifrar("x"));
        assertThrows(IllegalStateException.class, () -> sinClave.descifrar("x"));
    }

    @Test
    void conClaveEstaHabilitado() {
        assertTrue(cifrador.habilitado());
    }

    @Test
    void claveDeTamanoInvalidoRechazada() {
        assertThrows(IllegalArgumentException.class,
                () -> new Cifrador(java.util.Base64.getEncoder().encodeToString(new byte[16])));
    }

    @Test
    void textoManipuladoNoDescifra() {
        String cifrado = cifrador.cifrar("intacto");
        String manipulado = cifrado.substring(0, cifrado.length() - 2) + "AA";
        assertThrows(IllegalStateException.class, () -> cifrador.descifrar(manipulado));
    }
}
