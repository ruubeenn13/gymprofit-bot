package com.gymprofit.bot.services;

import com.gymprofit.bot.api.ApiException;
import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.db.EjercicioDia;
import com.gymprofit.bot.db.EjercicioDiaRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica la elección diaria: mismo día → mismo ejercicio (sin tocar la API), no repite
 * dentro de una ronda, cambia de ronda al agotar el catálogo y resuelve la carrera si otro
 * proceso insertó primero.
 */
class EjercicioDiaServiceTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    // 2026-07-20 a las 08:00 de Madrid (verano, UTC+2).
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-07-20T06:00:00Z"), MADRID);
    private static final LocalDate HOY = LocalDate.of(2026, 7, 20);

    private EjercicioDiaRepositorio repo;
    private EjercicioService ejercicios;
    private EjercicioDiaService service;

    private static EjercicioDTO dto(int id) {
        return new EjercicioDTO(id, "E" + id, null, "PECHO", null, "INTERMEDIO",
                null, null, null, null, null, true);
    }

    @BeforeEach
    void preparar() {
        repo = mock(EjercicioDiaRepositorio.class);
        ejercicios = mock(EjercicioService.class);
        // Random fijo: con new Random(1), nextInt acotado es determinista dentro del test.
        service = new EjercicioDiaService(repo, ejercicios, new Random(1), RELOJ);
    }

    @Test
    void mismoDiaDevuelveLoYaElegidoSinTocarLaApi() {
        when(repo.buscarPorFecha(HOY)).thenReturn(Optional.of(new EjercicioDia(HOY, 7, 1)));
        assertEquals(7, service.deHoy().ejercicioId());
        verify(ejercicios, never()).listarTodos(anyString());
    }

    @Test
    void eligeSoloEntreLosNoUsadosDeLaRonda() {
        when(repo.buscarPorFecha(HOY)).thenReturn(Optional.empty());
        when(ejercicios.listarTodos("es")).thenReturn(List.of(dto(1), dto(2), dto(3)));
        when(repo.rondaActual()).thenReturn(1);
        when(repo.idsDeRonda(1)).thenReturn(Set.of(1, 3));
        when(repo.insertar(any())).thenReturn(true);

        EjercicioDia dia = service.deHoy();
        assertEquals(2, dia.ejercicioId()); // único candidato libre
        assertEquals(1, dia.ronda());
    }

    @Test
    void catalogoAgotadoEmpiezaRondaNueva() {
        when(repo.buscarPorFecha(HOY)).thenReturn(Optional.empty());
        when(ejercicios.listarTodos("es")).thenReturn(List.of(dto(1), dto(2)));
        when(repo.rondaActual()).thenReturn(1);
        when(repo.idsDeRonda(1)).thenReturn(Set.of(1, 2)); // todos usados
        when(repo.insertar(any())).thenReturn(true);

        EjercicioDia dia = service.deHoy();
        assertEquals(2, dia.ronda());
        assertTrue(dia.ejercicioId() == 1 || dia.ejercicioId() == 2); // vuelve a valer todo
    }

    @Test
    void siOtroProcesoInsertoPrimeroDevuelveLoSuyo() {
        when(repo.buscarPorFecha(HOY))
                .thenReturn(Optional.empty())                              // 1ª lectura: nada
                .thenReturn(Optional.of(new EjercicioDia(HOY, 9, 1)));     // tras perder la carrera
        when(ejercicios.listarTodos("es")).thenReturn(List.of(dto(1)));
        when(repo.rondaActual()).thenReturn(1);
        when(repo.idsDeRonda(1)).thenReturn(Set.of());
        when(repo.insertar(any())).thenReturn(false); // el INSERT IGNORE no ganó

        assertEquals(9, service.deHoy().ejercicioId());
    }

    /**
     * Catálogo vacío (API caída devolviendo lista vacía): debe salir por la vía de error de la
     * API, no por un {@code IllegalArgumentException} de {@code nextInt(0)}.
     */
    @Test
    void catalogoVacioLanzaApiException() {
        when(repo.buscarPorFecha(HOY)).thenReturn(Optional.empty());
        when(ejercicios.listarTodos("es")).thenReturn(List.of());
        when(repo.rondaActual()).thenReturn(1);
        when(repo.idsDeRonda(1)).thenReturn(Set.of());

        assertThrows(ApiException.class, () -> service.deHoy());
        verify(repo, never()).insertar(any());
    }
}
