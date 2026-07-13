package com.gymprofit.bot.services;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica el formateo de la transcripción del ticket (ignora vacíos, orden y formato). */
class TicketServiceTest {

    @Test
    void formateaAutorYContenidoPorLinea() {
        String t = TicketService.formatearTranscript(List.of(
                new String[]{"ruben", "hola, tengo un problema"},
                new String[]{"staff", "cuéntame"}));
        assertEquals("ruben: hola, tengo un problema\nstaff: cuéntame", t);
    }

    @Test
    void ignoraMensajesSinTexto() {
        String t = TicketService.formatearTranscript(List.of(
                new String[]{"bot", null},
                new String[]{"ruben", "   "},
                new String[]{"staff", "resuelto"}));
        assertEquals("staff: resuelto", t);
    }

    @Test
    void listaVaciaDaCadenaVacia() {
        assertTrue(TicketService.formatearTranscript(List.of()).isEmpty());
    }
}
