package com.gymprofit.bot.services;

import com.gymprofit.bot.db.ConfigServidor;
import com.gymprofit.bot.db.ConfigServidorRepositorio;
import com.gymprofit.bot.services.ConfigServidorService.Objetivo;
import com.gymprofit.bot.services.ConfigServidorService.TipoCanal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica que {@link ConfigServidorService} fija el campo indicado y conserva el resto de la
 * configuración, con el repositorio mockeado.
 */
@ExtendWith(MockitoExtension.class)
class ConfigServidorServiceTest {

    @Mock
    private ConfigServidorRepositorio repositorio;

    @Test
    void fijarCanalSoloCambiaEseCanal() {
        when(repositorio.obtenerOCrear(10L)).thenReturn(ConfigServidor.porDefecto(10L));

        new ConfigServidorService(repositorio).fijarCanal(10L, TipoCanal.BIENVENIDA, 555L);

        ArgumentCaptor<ConfigServidor> cap = ArgumentCaptor.forClass(ConfigServidor.class);
        verify(repositorio).guardar(cap.capture());
        assertEquals(555L, cap.getValue().canalBienvenida());
        assertNull(cap.getValue().canalLogros(), "El resto de canales siguen sin fijar");
        assertEquals("es", cap.getValue().idioma());
    }

    @Test
    void fijarRolSoloCambiaEseObjetivo() {
        when(repositorio.obtenerOCrear(10L)).thenReturn(ConfigServidor.porDefecto(10L));

        new ConfigServidorService(repositorio).fijarRol(10L, Objetivo.FUERZA, 777L);

        ArgumentCaptor<ConfigServidor> cap = ArgumentCaptor.forClass(ConfigServidor.class);
        verify(repositorio).guardar(cap.capture());
        assertEquals(777L, cap.getValue().rolObjetivoFuerza());
        assertNull(cap.getValue().rolObjetivoCardio());
    }

    @Test
    void fijarIdiomaCambiaElIdioma() {
        when(repositorio.obtenerOCrear(10L)).thenReturn(ConfigServidor.porDefecto(10L));

        new ConfigServidorService(repositorio).fijarIdioma(10L, "en");

        ArgumentCaptor<ConfigServidor> cap = ArgumentCaptor.forClass(ConfigServidor.class);
        verify(repositorio).guardar(cap.capture());
        assertEquals("en", cap.getValue().idioma());
    }

    @Test
    void rolDeDevuelveElRolDelObjetivo() {
        ConfigServidor c = new ConfigServidor(1L, "es", null, null, null, null, null, null,
                111L, null, 333L, null);
        assertEquals(111L, ConfigServidorService.rolDe(c, Objetivo.FUERZA));
        assertEquals(333L, ConfigServidorService.rolDe(c, Objetivo.PERDIDA_PESO));
        assertNull(ConfigServidorService.rolDe(c, Objetivo.CARDIO), "Objetivo sin rol = null");
        assertNull(ConfigServidorService.rolDe(c, Objetivo.GENERAL));
    }
}
