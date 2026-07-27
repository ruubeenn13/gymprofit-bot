package com.gymprofit.bot.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeteccionAbusoTest {

    @Test @DisplayName("flood: N mensajes en la ventana → true; espaciados → false")
    void flood() {
        long t = 1_000_000L;
        assertTrue(DeteccionAbuso.esFlood(List.of(t, t + 500, t + 1000, t + 1500, t + 2000), t + 2000));
        assertFalse(DeteccionAbuso.esFlood(List.of(t, t + 5000, t + 10000, t + 15000, t + 20000), t + 20000));
        assertFalse(DeteccionAbuso.esFlood(List.of(t, t + 500, t + 1000), t + 1000)); // menos de N
    }

    @Test @DisplayName("invite de Discord detectado; texto normal no")
    void invite() {
        assertTrue(DeteccionAbuso.tieneInviteDiscord("únete a discord.gg/abc123"));
        assertTrue(DeteccionAbuso.tieneInviteDiscord("https://discord.com/invite/xyz"));
        assertTrue(DeteccionAbuso.tieneInviteDiscord("DISCORD.GG/MAYUS")); // case-insensitive
        assertFalse(DeteccionAbuso.tieneInviteDiscord("mira este vídeo youtube.com/watch?v=1"));
        assertFalse(DeteccionAbuso.tieneInviteDiscord("hablamos en el server"));
        assertFalse(DeteccionAbuso.tieneInviteDiscord(null));
    }
}
