package com.gymprofit.bot.services;

import com.gymprofit.bot.db.DatabaseException;
import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaPropuestaRepositorio;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.MiembroEmpresa;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.Propuesta;
import com.gymprofit.bot.db.Voto;
import com.gymprofit.bot.services.EmpresaGestionService.ResultadoGestion;
import com.gymprofit.bot.services.EmpresaGestionService.ResultadoVoto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica {@link EmpresaGestionService} con repositorios mockeados y un {@link Clock#fixed} para
 * que la caducidad sea determinista. Cubre la <b>autoridad</b> (dueño ejecuta directo, directivo
 * propone, resto no autorizado), la <b>regla de rango</b> (no tocar a un igual/superior), las tres
 * <b>ejecuciones</b> (cambiar rango, sacar conserva el trabajo, despedir manda al paro) y el
 * <b>predicado de votación</b> (mayoría estricta, desempate del dueño, caducidad, revalidación al
 * ejecutar y voto de quien no es alto cargo). Se comprueba el comportamiento real (verify de la
 * acción en el repo y del cierre de la propuesta), no ecos de mock.
 */
class EmpresaGestionServiceTest {

    private final EmpresaRepositorio repo = mock(EmpresaRepositorio.class);
    private final EmpresaPropuestaRepositorio propuestas = mock(EmpresaPropuestaRepositorio.class);
    private final PersonajeRepositorio personajes = mock(PersonajeRepositorio.class);

    /** Instante fijo del reloj de test; toda expira/caducidad se calcula relativa a él. */
    private static final Instant AHORA = Instant.parse("2026-07-23T12:00:00Z");
    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);

    private static final long EMPRESA = 10L;
    private static final long DUENO = 1L;
    private static final long DIRECTIVO = 2L;
    private static final long EMPLEADO = 3L;
    private static final long OBJETIVO = 4L;
    private static final long DIRECTIVO_2 = 5L;

    private EmpresaGestionService svc() {
        return new EmpresaGestionService(repo, propuestas, personajes, reloj);
    }

    // ------------------------------------------------------------------ autoridad

    @Test
    @DisplayName("el dueño gestiona a un inferior: ejecuta directo (EJECUTADA)")
    void duenoEjecutaDirecto() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa()));
        when(repo.miembros(EMPRESA)).thenReturn(List.of(
                miembro(DUENO, RangoEmpresa.DUENO), miembro(OBJETIVO, RangoEmpresa.BECARIO)));

        assertEquals(ResultadoGestion.EJECUTADA,
                svc().gestionar(DUENO, TipoPropuesta.SACAR, OBJETIVO, null));
        verify(repo).quitarMiembro(EMPRESA, OBJETIVO);
        verify(propuestas, never()).crear(anyLong(), any(), anyLong(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("el directivo gestiona a un inferior: crea propuesta y vota Sí (PROPUESTA_CREADA)")
    void directivoProponeYVotaSi() {
        when(repo.deMiembro(DIRECTIVO)).thenReturn(Optional.of(empresa()));
        when(repo.miembros(EMPRESA)).thenReturn(List.of(
                miembro(DIRECTIVO, RangoEmpresa.DIRECTIVO), miembro(OBJETIVO, RangoEmpresa.BECARIO)));
        when(propuestas.crear(anyLong(), any(), anyLong(), any(), anyLong(), any())).thenReturn(99L);

        assertEquals(ResultadoGestion.PROPUESTA_CREADA,
                svc().gestionar(DIRECTIVO, TipoPropuesta.DESPEDIR, OBJETIVO, null));
        // Expira exactamente a 48 h del reloj fijo y se persiste como propuesta DESPEDIR sin rango.
        verify(propuestas).crear(EMPRESA, TipoPropuesta.DESPEDIR, OBJETIVO, null, DIRECTIVO,
                AHORA.plus(Duration.ofHours(48)));
        verify(propuestas).votar(99L, DIRECTIVO, true);
        verify(repo, never()).quitarMiembro(anyLong(), anyLong());
    }

    @Test
    @DisplayName("la UNIQUE de propuesta activa se traduce a YA_HAY_PROPUESTA")
    void directivoConPropuestaDuplicada() {
        when(repo.deMiembro(DIRECTIVO)).thenReturn(Optional.of(empresa()));
        when(repo.miembros(EMPRESA)).thenReturn(List.of(
                miembro(DIRECTIVO, RangoEmpresa.DIRECTIVO), miembro(OBJETIVO, RangoEmpresa.BECARIO)));
        when(propuestas.crear(anyLong(), any(), anyLong(), any(), anyLong(), any()))
                .thenThrow(new DatabaseException("dup", new RuntimeException()));

        assertEquals(ResultadoGestion.YA_HAY_PROPUESTA,
                svc().gestionar(DIRECTIVO, TipoPropuesta.SACAR, OBJETIVO, null));
    }

    @Test
    @DisplayName("un empleado no puede gestionar: NO_AUTORIZADO")
    void empleadoNoAutorizado() {
        when(repo.deMiembro(EMPLEADO)).thenReturn(Optional.of(empresa()));
        when(repo.miembros(EMPRESA)).thenReturn(List.of(
                miembro(EMPLEADO, RangoEmpresa.EMPLEADO), miembro(OBJETIVO, RangoEmpresa.BECARIO)));

        assertEquals(ResultadoGestion.NO_AUTORIZADO,
                svc().gestionar(EMPLEADO, TipoPropuesta.SACAR, OBJETIVO, null));
        verify(repo, never()).quitarMiembro(anyLong(), anyLong());
        verify(propuestas, never()).crear(anyLong(), any(), anyLong(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("el actor no está en ninguna empresa: NO_AUTORIZADO")
    void actorSinEmpresa() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.empty());

        assertEquals(ResultadoGestion.NO_AUTORIZADO,
                svc().gestionar(DUENO, TipoPropuesta.SACAR, OBJETIVO, null));
    }

    @Test
    @DisplayName("tocar a un igual o superior: RANGO_INVALIDO")
    void tocaAUnIgual() {
        when(repo.deMiembro(DIRECTIVO)).thenReturn(Optional.of(empresa()));
        when(repo.miembros(EMPRESA)).thenReturn(List.of(
                miembro(DIRECTIVO, RangoEmpresa.DIRECTIVO), miembro(DIRECTIVO_2, RangoEmpresa.DIRECTIVO)));

        assertEquals(ResultadoGestion.RANGO_INVALIDO,
                svc().gestionar(DIRECTIVO, TipoPropuesta.SACAR, DIRECTIVO_2, null));
        verify(repo, never()).quitarMiembro(anyLong(), anyLong());
    }

    @Test
    @DisplayName("el objetivo no es de la empresa del actor: NO_ES_MIEMBRO")
    void objetivoNoEsMiembro() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa()));
        when(repo.miembros(EMPRESA)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));

        assertEquals(ResultadoGestion.NO_ES_MIEMBRO,
                svc().gestionar(DUENO, TipoPropuesta.SACAR, OBJETIVO, null));
    }

    @Test
    @DisplayName("cambiar a un rango >= el del actor: RANGO_INVALIDO")
    void cambiarRangoNoInferior() {
        when(repo.deMiembro(DIRECTIVO)).thenReturn(Optional.of(empresa()));
        when(repo.miembros(EMPRESA)).thenReturn(List.of(
                miembro(DIRECTIVO, RangoEmpresa.DIRECTIVO), miembro(OBJETIVO, RangoEmpresa.BECARIO)));

        assertEquals(ResultadoGestion.RANGO_INVALIDO,
                svc().gestionar(DIRECTIVO, TipoPropuesta.CAMBIAR_RANGO, OBJETIVO, RangoEmpresa.DIRECTIVO));
        verify(repo, never()).actualizarRango(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("cambiar a DUENO nunca es válido, ni para el propio dueño: RANGO_INVALIDO")
    void cambiarRangoADueno() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa()));
        when(repo.miembros(EMPRESA)).thenReturn(List.of(
                miembro(DUENO, RangoEmpresa.DUENO), miembro(OBJETIVO, RangoEmpresa.BECARIO)));

        assertEquals(ResultadoGestion.RANGO_INVALIDO,
                svc().gestionar(DUENO, TipoPropuesta.CAMBIAR_RANGO, OBJETIVO, RangoEmpresa.DUENO));
        verify(repo, never()).actualizarRango(anyLong(), anyLong(), any());
    }

    // ------------------------------------------------------------------ ejecución

    @Test
    @DisplayName("CAMBIAR_RANGO ejecutado por el dueño llama a actualizarRango")
    void ejecutarCambiarRango() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa()));
        when(repo.miembros(EMPRESA)).thenReturn(List.of(
                miembro(DUENO, RangoEmpresa.DUENO), miembro(OBJETIVO, RangoEmpresa.BECARIO)));

        assertEquals(ResultadoGestion.EJECUTADA,
                svc().gestionar(DUENO, TipoPropuesta.CAMBIAR_RANGO, OBJETIVO, RangoEmpresa.ENCARGADO));
        verify(repo).actualizarRango(EMPRESA, OBJETIVO, RangoEmpresa.ENCARGADO);
        verify(personajes, never()).fijarTrabajo(anyLong(), any());
    }

    @Test
    @DisplayName("SACAR quita al miembro pero NO toca su trabajo")
    void ejecutarSacar() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa()));
        when(repo.miembros(EMPRESA)).thenReturn(List.of(
                miembro(DUENO, RangoEmpresa.DUENO), miembro(OBJETIVO, RangoEmpresa.BECARIO)));

        assertEquals(ResultadoGestion.EJECUTADA,
                svc().gestionar(DUENO, TipoPropuesta.SACAR, OBJETIVO, null));
        verify(repo).quitarMiembro(EMPRESA, OBJETIVO);
        verify(personajes, never()).fijarTrabajo(anyLong(), any());
    }

    @Test
    @DisplayName("DESPEDIR quita al miembro y lo manda al paro (fijarTrabajo null)")
    void ejecutarDespedir() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa()));
        when(repo.miembros(EMPRESA)).thenReturn(List.of(
                miembro(DUENO, RangoEmpresa.DUENO), miembro(OBJETIVO, RangoEmpresa.BECARIO)));

        assertEquals(ResultadoGestion.EJECUTADA,
                svc().gestionar(DUENO, TipoPropuesta.DESPEDIR, OBJETIVO, null));
        verify(repo).quitarMiembro(EMPRESA, OBJETIVO);
        verify(personajes).fijarTrabajo(OBJETIVO, null);
    }

    // ------------------------------------------------------------------ votación

    @Test
    @DisplayName("mayoría estricta de Sí aprueba y ejecuta (APROBADA_EJECUTADA)")
    void votacionAprobadaPorMayoria() {
        Propuesta prop = propuesta(TipoPropuesta.SACAR, null, futuro());
        when(propuestas.porId(99L)).thenReturn(Optional.of(prop));
        when(repo.altosCargos(EMPRESA)).thenReturn(List.of(
                miembro(DUENO, RangoEmpresa.DUENO), miembro(DIRECTIVO, RangoEmpresa.DIRECTIVO),
                miembro(DIRECTIVO_2, RangoEmpresa.DIRECTIVO)));
        when(propuestas.votos(99L)).thenReturn(List.of(voto(DUENO, true), voto(DIRECTIVO_2, true)));
        when(repo.miembros(EMPRESA)).thenReturn(List.of(
                miembro(DIRECTIVO, RangoEmpresa.DIRECTIVO), miembro(OBJETIVO, RangoEmpresa.BECARIO)));

        assertEquals(ResultadoVoto.APROBADA_EJECUTADA, svc().votar(99L, DIRECTIVO_2, true));
        verify(propuestas).votar(99L, DIRECTIVO_2, true);
        verify(repo).quitarMiembro(EMPRESA, OBJETIVO);
        verify(propuestas).cerrar(99L);
    }

    @Test
    @DisplayName("empate con el dueño en Sí aprueba")
    void votacionEmpateDuenoSi() {
        Propuesta prop = propuesta(TipoPropuesta.SACAR, null, futuro());
        when(propuestas.porId(99L)).thenReturn(Optional.of(prop));
        when(repo.altosCargos(EMPRESA)).thenReturn(List.of(
                miembro(DUENO, RangoEmpresa.DUENO), miembro(DIRECTIVO, RangoEmpresa.DIRECTIVO)));
        when(propuestas.votos(99L)).thenReturn(List.of(voto(DUENO, true), voto(DIRECTIVO, false)));
        when(repo.miembros(EMPRESA)).thenReturn(List.of(
                miembro(DIRECTIVO, RangoEmpresa.DIRECTIVO), miembro(OBJETIVO, RangoEmpresa.BECARIO)));

        assertEquals(ResultadoVoto.APROBADA_EJECUTADA, svc().votar(99L, DUENO, true));
        verify(repo).quitarMiembro(EMPRESA, OBJETIVO);
        verify(propuestas).cerrar(99L);
    }

    @Test
    @DisplayName("empate con el dueño en No rechaza")
    void votacionEmpateDuenoNo() {
        Propuesta prop = propuesta(TipoPropuesta.SACAR, null, futuro());
        when(propuestas.porId(99L)).thenReturn(Optional.of(prop));
        when(repo.altosCargos(EMPRESA)).thenReturn(List.of(
                miembro(DUENO, RangoEmpresa.DUENO), miembro(DIRECTIVO, RangoEmpresa.DIRECTIVO)));
        when(propuestas.votos(99L)).thenReturn(List.of(voto(DUENO, false), voto(DIRECTIVO, true)));

        assertEquals(ResultadoVoto.RECHAZADA, svc().votar(99L, DUENO, false));
        verify(repo, never()).quitarMiembro(anyLong(), anyLong());
        verify(propuestas).cerrar(99L);
    }

    @Test
    @DisplayName("mayoría de No rechaza")
    void votacionRechazadaPorMayoria() {
        Propuesta prop = propuesta(TipoPropuesta.SACAR, null, futuro());
        when(propuestas.porId(99L)).thenReturn(Optional.of(prop));
        when(repo.altosCargos(EMPRESA)).thenReturn(List.of(
                miembro(DUENO, RangoEmpresa.DUENO), miembro(DIRECTIVO, RangoEmpresa.DIRECTIVO),
                miembro(DIRECTIVO_2, RangoEmpresa.DIRECTIVO)));
        when(propuestas.votos(99L)).thenReturn(List.of(voto(DUENO, false), voto(DIRECTIVO_2, false)));

        assertEquals(ResultadoVoto.RECHAZADA, svc().votar(99L, DIRECTIVO_2, false));
        verify(repo, never()).quitarMiembro(anyLong(), anyLong());
        verify(propuestas).cerrar(99L);
    }

    @Test
    @DisplayName("un solo voto sin mayoría queda REGISTRADO")
    void votacionRegistrada() {
        Propuesta prop = propuesta(TipoPropuesta.SACAR, null, futuro());
        when(propuestas.porId(99L)).thenReturn(Optional.of(prop));
        when(repo.altosCargos(EMPRESA)).thenReturn(List.of(
                miembro(DUENO, RangoEmpresa.DUENO), miembro(DIRECTIVO, RangoEmpresa.DIRECTIVO),
                miembro(DIRECTIVO_2, RangoEmpresa.DIRECTIVO)));
        when(propuestas.votos(99L)).thenReturn(List.of(voto(DIRECTIVO, true)));

        assertEquals(ResultadoVoto.REGISTRADO, svc().votar(99L, DIRECTIVO, true));
        verify(propuestas).votar(99L, DIRECTIVO, true);
        verify(repo, never()).quitarMiembro(anyLong(), anyLong());
        verify(propuestas, never()).cerrar(anyLong());
    }

    @Test
    @DisplayName("propuesta caducada: se cierra sin registrar el voto (CADUCADA)")
    void votacionCaducada() {
        Propuesta prop = propuesta(TipoPropuesta.SACAR, null, pasado());
        when(propuestas.porId(99L)).thenReturn(Optional.of(prop));

        assertEquals(ResultadoVoto.CADUCADA, svc().votar(99L, DUENO, true));
        verify(propuestas).cerrar(99L);
        verify(propuestas, never()).votar(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("al aprobar, si el objetivo ya no es inferior no se ejecuta la acción")
    void votacionRevalidaAlEjecutar() {
        Propuesta prop = propuesta(TipoPropuesta.SACAR, null, futuro());
        when(propuestas.porId(99L)).thenReturn(Optional.of(prop));
        when(repo.altosCargos(EMPRESA)).thenReturn(List.of(
                miembro(DUENO, RangoEmpresa.DUENO), miembro(DIRECTIVO, RangoEmpresa.DIRECTIVO)));
        when(propuestas.votos(99L)).thenReturn(List.of(voto(DUENO, true), voto(DIRECTIVO, true)));
        // El objetivo ascendió a DIRECTIVO entre proponer y aprobar: ya NO es inferior al proponente.
        when(repo.miembros(EMPRESA)).thenReturn(List.of(
                miembro(DIRECTIVO, RangoEmpresa.DIRECTIVO), miembro(OBJETIVO, RangoEmpresa.DIRECTIVO)));

        assertEquals(ResultadoVoto.RECHAZADA, svc().votar(99L, DIRECTIVO, true));
        verify(repo, never()).quitarMiembro(anyLong(), anyLong());
        verify(propuestas).cerrar(99L);
    }

    @Test
    @DisplayName("vota alguien que no es alto cargo: NO_AUTORIZADO")
    void votaNoAltoCargo() {
        Propuesta prop = propuesta(TipoPropuesta.SACAR, null, futuro());
        when(propuestas.porId(99L)).thenReturn(Optional.of(prop));
        when(repo.altosCargos(EMPRESA)).thenReturn(List.of(
                miembro(DUENO, RangoEmpresa.DUENO), miembro(DIRECTIVO, RangoEmpresa.DIRECTIVO)));

        assertEquals(ResultadoVoto.NO_AUTORIZADO, svc().votar(99L, EMPLEADO, true));
        verify(propuestas, never()).votar(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("votar una propuesta inexistente: NO_EXISTE")
    void votaPropuestaInexistente() {
        when(propuestas.porId(99L)).thenReturn(Optional.empty());

        assertEquals(ResultadoVoto.NO_EXISTE, svc().votar(99L, DUENO, true));
    }

    @Test
    @DisplayName("voto fantasma: el Sí de quien ya no es alto cargo no cuenta (REGISTRADO)")
    void votoFantasmaNoCuenta() {
        Propuesta prop = propuesta(TipoPropuesta.SACAR, null, futuro());
        when(propuestas.porId(99L)).thenReturn(Optional.of(prop));
        // Censo actual: solo dueño + directivo (N=2). DIRECTIVO_2 fue degradado/sacado y ya no figura.
        when(repo.altosCargos(EMPRESA)).thenReturn(List.of(
                miembro(DUENO, RangoEmpresa.DUENO), miembro(DIRECTIVO, RangoEmpresa.DIRECTIVO)));
        // Hay un Sí del ex-alto-cargo DIRECTIVO_2 y el Sí que acaba de emitir DIRECTIVO: sin el filtro
        // serían 2 Sí (2*2>2 → aprobaría); con el filtro solo cuenta 1 (N=2, sin dueño) → no aprueba.
        when(propuestas.votos(99L)).thenReturn(List.of(voto(DIRECTIVO_2, true), voto(DIRECTIVO, true)));

        assertEquals(ResultadoVoto.REGISTRADO, svc().votar(99L, DIRECTIVO, true));
        verify(repo, never()).quitarMiembro(anyLong(), anyLong());
        verify(propuestas, never()).cerrar(anyLong());
    }

    @Test
    @DisplayName("N=1 (solo dueño): su único Sí aprueba")
    void votacionUnicoDueno() {
        Propuesta prop = propuesta(TipoPropuesta.SACAR, null, futuro());
        when(propuestas.porId(99L)).thenReturn(Optional.of(prop));
        when(repo.altosCargos(EMPRESA)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));
        when(propuestas.votos(99L)).thenReturn(List.of(voto(DUENO, true)));
        // La revalidación cruza el proponente (DIRECTIVO) contra el objetivo (BECARIO): sigue siendo inferior.
        when(repo.miembros(EMPRESA)).thenReturn(List.of(
                miembro(DIRECTIVO, RangoEmpresa.DIRECTIVO), miembro(OBJETIVO, RangoEmpresa.BECARIO)));

        assertEquals(ResultadoVoto.APROBADA_EJECUTADA, svc().votar(99L, DUENO, true));
        verify(repo).quitarMiembro(EMPRESA, OBJETIVO);
        verify(propuestas).cerrar(99L);
    }

    @Test
    @DisplayName("empate N=2 sin voto del dueño queda REGISTRADO (espera al dueño)")
    void votacionEmpateSinDueno() {
        Propuesta prop = propuesta(TipoPropuesta.SACAR, null, futuro());
        when(propuestas.porId(99L)).thenReturn(Optional.of(prop));
        when(repo.altosCargos(EMPRESA)).thenReturn(List.of(
                miembro(DUENO, RangoEmpresa.DUENO), miembro(DIRECTIVO, RangoEmpresa.DIRECTIVO)));
        // Solo el directivo vota Sí (1-0). N=2 → 1*2==2 pero el dueño no votó Sí → no aprueba.
        when(propuestas.votos(99L)).thenReturn(List.of(voto(DIRECTIVO, true)));

        assertEquals(ResultadoVoto.REGISTRADO, svc().votar(99L, DIRECTIVO, true));
        verify(repo, never()).quitarMiembro(anyLong(), anyLong());
        verify(propuestas, never()).cerrar(anyLong());
    }

    @Test
    @DisplayName("autogestión (actor == objetivo) es RANGO_INVALIDO")
    void autogestion() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa()));
        when(repo.miembros(EMPRESA)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));

        assertEquals(ResultadoGestion.RANGO_INVALIDO,
                svc().gestionar(DUENO, TipoPropuesta.SACAR, DUENO, null));
        verify(repo, never()).quitarMiembro(anyLong(), anyLong());
    }

    // ------------------------------------------------------------------ helpers

    private static Empresa empresa() {
        return new Empresa(EMPRESA, "HIERRO", DUENO, "Acme", 1, 0L, AHORA);
    }

    private static MiembroEmpresa miembro(long discordId, RangoEmpresa rango) {
        return new MiembroEmpresa(EMPRESA, discordId, rango, AHORA);
    }

    private static Propuesta propuesta(TipoPropuesta tipo, RangoEmpresa rangoNuevo, Instant expira) {
        return new Propuesta(99L, EMPRESA, tipo, OBJETIVO, rangoNuevo, DIRECTIVO, AHORA, expira);
    }

    private static Voto voto(long votanteId, boolean si) {
        return new Voto(99L, votanteId, si);
    }

    private static Instant futuro() {
        return AHORA.plus(Duration.ofHours(24));
    }

    private static Instant pasado() {
        return AHORA.minus(Duration.ofHours(1));
    }
}
