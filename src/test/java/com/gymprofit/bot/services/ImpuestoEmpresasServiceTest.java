package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.services.ImpuestoEmpresasService.Resolucion;
import com.gymprofit.bot.services.ImpuestoEmpresasService.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica {@link ImpuestoEmpresasService} con el repositorio mockeado. Es dinero + quiebra: se
 * comprueba que la cuota se quema por el <b>gate atomico</b> {@code gastarDelBote} y que solo se
 * resetea el contador de impagos si el gasto tuvo exito; que un impago fallido (carrera) degrada a
 * MOROSA o QUIEBRA segun el contador, sin quemar de mas; y que la tercera vez disuelve la empresa.
 * Se verifica comportamiento real (verify sobre gastarDelBote/fijarImpagos/disolver), no ecos de mock.
 */
class ImpuestoEmpresasServiceTest {

    private final EmpresaRepositorio repo = mock(EmpresaRepositorio.class);

    private static final Instant AHORA = Instant.parse("2026-07-24T12:00:00Z");
    private static final long EMPRESA_ID = 42L;

    private ImpuestoEmpresasService svc() {
        return new ImpuestoEmpresasService(repo);
    }

    // Empresa de test: todos los args del record, con canalId null y mercancia 0.
    private static Empresa empresa(int nivel, long bote, int impagos) {
        return new Empresa(EMPRESA_ID, "rama", 1L, "Acme", nivel, bote, AHORA, null, 0L, impagos, false);
    }

    @Test
    @DisplayName("bote cubre la cuota: paga, quema la cuota y resetea impagos a 0")
    void boteCubreLaCuota_paga() {
        Empresa e = empresa(2, 8_000L, 1); // cuota nivel 2 = 5.000
        when(repo.gastarDelBote(EMPRESA_ID, 5_000L)).thenReturn(true);

        Resolucion r = svc().evaluar(e);
        assertEquals(Tipo.PAGA, r.tipo());
        assertEquals(5_000L, r.cuota());

        Resolucion aplicada = svc().aplicar(e);
        assertEquals(Tipo.PAGA, aplicada.tipo());
        verify(repo).gastarDelBote(EMPRESA_ID, 5_000L);
        verify(repo).fijarImpagos(EMPRESA_ID, 0);
        verify(repo, never()).disolver(anyLong());
    }

    @Test
    @DisplayName("bote exactamente igual a la cuota: paga (pin del limite >=)")
    void boteIgualALaCuota_paga() {
        Empresa e = empresa(2, 5_000L, 0); // bote == cuota nivel 2
        when(repo.gastarDelBote(EMPRESA_ID, 5_000L)).thenReturn(true);

        Resolucion r = svc().evaluar(e);
        assertEquals(Tipo.PAGA, r.tipo());

        svc().aplicar(e);
        verify(repo).gastarDelBote(EMPRESA_ID, 5_000L);
        verify(repo).fijarImpagos(EMPRESA_ID, 0);
    }

    @Test
    @DisplayName("bote no cubre y es el primer impago: morosa, sin quemar nada")
    void boteNoCubre_primerImpago_morosa() {
        Empresa e = empresa(2, 1_000L, 0); // cuota 5.000, falta 4.000

        Resolucion r = svc().evaluar(e);
        assertEquals(Tipo.MOROSA, r.tipo());
        assertEquals(1, r.impagos());
        assertEquals(4_000L, r.falta());

        svc().aplicar(e);
        verify(repo).fijarImpagos(EMPRESA_ID, 1);
        verify(repo, never()).gastarDelBote(anyLong(), anyLong());
        verify(repo, never()).disolver(anyLong());
    }

    @Test
    @DisplayName("bote no cubre por tercera vez: quiebra, disuelve la empresa")
    void boteNoCubre_terceraVez_quiebra() {
        Empresa e = empresa(2, 0L, 2); // impagos 2 -> 3 = MOROSIDAD_MAX

        Resolucion r = svc().evaluar(e);
        assertEquals(Tipo.QUIEBRA, r.tipo());

        svc().aplicar(e);
        verify(repo).disolver(EMPRESA_ID);
        verify(repo, never()).gastarDelBote(anyLong(), anyLong());
        verify(repo, never()).fijarImpagos(anyLong(), anyInt());
    }

    @Test
    @DisplayName("gastarDelBote false (carrera): cuenta como impago, no disuelve, devuelve MOROSA")
    void gastarDelBoteFalse_cuentaComoImpago() {
        Empresa e = empresa(2, 8_000L, 0); // parece cubrir, pero el gate falla
        when(repo.gastarDelBote(EMPRESA_ID, 5_000L)).thenReturn(false);

        Resolucion aplicada = svc().aplicar(e);
        assertEquals(Tipo.MOROSA, aplicada.tipo());
        verify(repo).fijarImpagos(EMPRESA_ID, 1);
        verify(repo, never()).disolver(anyLong());
    }

    @Test
    @DisplayName("gastarDelBote false con dos impagos previos: quiebra")
    void gastarDelBoteFalse_conDosImpagos_quiebra() {
        Empresa e = empresa(2, 8_000L, 2); // gate falla y ya lleva 2 -> el tercero quiebra
        when(repo.gastarDelBote(EMPRESA_ID, 5_000L)).thenReturn(false);

        Resolucion aplicada = svc().aplicar(e);
        assertEquals(Tipo.QUIEBRA, aplicada.tipo());
        verify(repo).disolver(EMPRESA_ID);
    }
}
