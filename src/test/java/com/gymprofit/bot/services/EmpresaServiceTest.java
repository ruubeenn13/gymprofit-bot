package com.gymprofit.bot.services;

import com.gymprofit.bot.db.DatabaseException;
import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.Pendiente;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.services.EmpresaService.ResultadoDisolver;
import com.gymprofit.bot.services.EmpresaService.ResultadoFundar;
import com.gymprofit.bot.services.EmpresaService.ResultadoIngreso;
import com.gymprofit.bot.services.EmpresaService.ResultadoMejora;
import com.gymprofit.bot.services.EmpresaService.ResultadoResolver;
import com.gymprofit.bot.services.EmpresaService.SalidaFundar;
import com.gymprofit.bot.services.EmpresaService.SalidaMejora;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica las reglas de {@link EmpresaService} con repositorios mockeados: los gates de
 * {@code fundar} (trabajo, tier 4, no-pertenencia, saldo) y su <b>cobro estrictamente lo último</b>
 * (fundar en la BD y solo entonces gastar, comprobado con {@link InOrder}), el consentimiento por
 * ambas vías ({@code invitar}/{@code solicitar}) y la resolución de pendientes por la parte correcta.
 */
class EmpresaServiceTest {

    private final EmpresaRepositorio repo = mock(EmpresaRepositorio.class);
    private final PersonajeRepositorio personajes = mock(PersonajeRepositorio.class);
    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final TrabajoService trabajos = mock(TrabajoService.class);

    private EmpresaService svc() {
        return new EmpresaService(repo, personajes, economia, trabajos);
    }

    // ------------------------------------------------------------------ fundar

    @Test
    @DisplayName("fundar sin trabajo devuelve SIN_TRABAJO y no toca la BD ni cobra")
    void fundarSinTrabajo() {
        when(personajes.obtenerOCrear(1L)).thenReturn(personaje(null));

        assertEquals(ResultadoFundar.SIN_TRABAJO, svc().fundar(1L, "Gimnasio").estado());
        verify(repo, never()).fundar(anyString(), anyLong(), anyString());
        verify(economia, never()).gastar(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("fundar sin ser tier 4 de la rama devuelve NO_ES_TIER4")
    void fundarNoTier4() {
        when(personajes.obtenerOCrear(1L)).thenReturn(camarero());
        when(trabajos.tierAlcanzadoEn(1L, Ascensos.Rama.HOSTELERIA)).thenReturn(3);

        assertEquals(ResultadoFundar.NO_ES_TIER4, svc().fundar(1L, "Gimnasio").estado());
        verify(economia, never()).gastar(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("fundar estando ya en una empresa devuelve YA_EN_EMPRESA")
    void fundarYaEnEmpresa() {
        when(personajes.obtenerOCrear(1L)).thenReturn(camarero());
        when(trabajos.tierAlcanzadoEn(1L, Ascensos.Rama.HOSTELERIA)).thenReturn(4);
        when(repo.deMiembro(1L)).thenReturn(Optional.of(empresa(10L, 9L, "HOSTELERIA", "Otra")));

        assertEquals(ResultadoFundar.YA_EN_EMPRESA, svc().fundar(1L, "Gimnasio").estado());
        verify(repo, never()).fundar(anyString(), anyLong(), anyString());
        verify(economia, never()).gastar(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("fundar sin saldo (gastar=false) devuelve SIN_SALDO y NO funda")
    void fundarSinSaldo() {
        when(personajes.obtenerOCrear(1L)).thenReturn(camarero());
        when(trabajos.tierAlcanzadoEn(1L, Ascensos.Rama.HOSTELERIA)).thenReturn(4);
        when(repo.deMiembro(1L)).thenReturn(Optional.empty());
        // El cobro es el gate atómico: si gastar devuelve false (sin saldo), no se funda nada.
        when(economia.gastar(1L, EmpresaService.COSTE_FUNDAR, "fundar_empresa")).thenReturn(false);

        assertEquals(ResultadoFundar.SIN_SALDO, svc().fundar(1L, "Gimnasio").estado());
        verify(repo, never()).fundar(anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("fundar OK: cobra ATÓMICAMENTE primero y SOLO DESPUÉS funda en la BD")
    void fundarOkCobraPrimero() {
        when(personajes.obtenerOCrear(1L)).thenReturn(camarero());
        when(trabajos.tierAlcanzadoEn(1L, Ascensos.Rama.HOSTELERIA)).thenReturn(4);
        when(repo.deMiembro(1L)).thenReturn(Optional.empty());
        when(economia.gastar(1L, EmpresaService.COSTE_FUNDAR, "fundar_empresa")).thenReturn(true);
        when(repo.fundar("HOSTELERIA", 1L, "Gimnasio")).thenReturn(77L);

        SalidaFundar salida = svc().fundar(1L, "Gimnasio");

        assertEquals(ResultadoFundar.OK, salida.estado());
        assertEquals(77L, salida.empresaId());
        assertEquals(EmpresaService.COSTE_FUNDAR, salida.costeQuemado());
        // El cobro es el gate atómico final: gastar (que valida saldo y descuenta) va ANTES del insert.
        InOrder orden = inOrder(economia, repo);
        orden.verify(economia).gastar(1L, EmpresaService.COSTE_FUNDAR, "fundar_empresa");
        orden.verify(repo).fundar("HOSTELERIA", 1L, "Gimnasio");
    }

    @Test
    @DisplayName("fundar con el nombre pillado (UNIQUE) devuelve NOMBRE_EN_USO y REEMBOLSA el coste")
    void fundarNombreEnUso() {
        when(personajes.obtenerOCrear(1L)).thenReturn(camarero());
        when(trabajos.tierAlcanzadoEn(1L, Ascensos.Rama.HOSTELERIA)).thenReturn(4);
        when(repo.deMiembro(1L)).thenReturn(Optional.empty());
        when(economia.gastar(1L, EmpresaService.COSTE_FUNDAR, "fundar_empresa")).thenReturn(true);
        when(repo.fundar("HOSTELERIA", 1L, "Gimnasio"))
                .thenThrow(new DatabaseException("uq_empresa_nombre_rama", null));

        assertEquals(ResultadoFundar.NOMBRE_EN_USO, svc().fundar(1L, "Gimnasio").estado());
        // Se cobró antes del insert fallido: hay que devolver el coste.
        verify(economia).ingresar(1L, EmpresaService.COSTE_FUNDAR, "reembolso_fundar");
    }

    // ------------------------------------------------------------------ invitar

    @Test
    @DisplayName("invitar a alguien sin trabajo en la rama de la empresa devuelve OTRA_RAMA")
    void invitarOtraRama() {
        when(repo.deMiembro(1L)).thenReturn(Optional.of(empresa(10L, 1L, "HOSTELERIA", "Gimnasio")));
        when(personajes.obtenerOCrear(2L)).thenReturn(conTrabajo("policia")); // SERVICIOS
        when(repo.deMiembro(2L)).thenReturn(Optional.empty());

        assertEquals(ResultadoIngreso.OTRA_RAMA, svc().invitar(1L, 2L));
        verify(repo, never()).crearPendiente(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("invitar a alguien ya en una empresa devuelve YA_EN_EMPRESA")
    void invitarYaEnEmpresa() {
        when(repo.deMiembro(1L)).thenReturn(Optional.of(empresa(10L, 1L, "HOSTELERIA", "Gimnasio")));
        when(personajes.obtenerOCrear(2L)).thenReturn(camarero());
        when(repo.deMiembro(2L)).thenReturn(Optional.of(empresa(20L, 5L, "HOSTELERIA", "Rival")));

        assertEquals(ResultadoIngreso.YA_EN_EMPRESA, svc().invitar(1L, 2L));
        verify(repo, never()).crearPendiente(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("invitarse a sí mismo devuelve ES_MISMO")
    void invitarASiMismo() {
        when(repo.deMiembro(1L)).thenReturn(Optional.of(empresa(10L, 1L, "HOSTELERIA", "Gimnasio")));

        assertEquals(ResultadoIngreso.ES_MISMO, svc().invitar(1L, 1L));
        verify(repo, never()).crearPendiente(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("invitar OK crea una pendiente de INVITACION sin motivo")
    void invitarOk() {
        when(repo.deMiembro(1L)).thenReturn(Optional.of(empresa(10L, 1L, "HOSTELERIA", "Gimnasio")));
        when(personajes.obtenerOCrear(2L)).thenReturn(camarero());
        when(repo.deMiembro(2L)).thenReturn(Optional.empty());

        assertEquals(ResultadoIngreso.OK, svc().invitar(1L, 2L));
        verify(repo).crearPendiente(10L, 2L, TipoPendiente.INVITACION, null);
    }

    // ------------------------------------------------------------------ solicitar

    @Test
    @DisplayName("solicitar con motivo crea una pendiente de SOLICITUD con el motivo")
    void solicitarConMotivo() {
        when(personajes.obtenerOCrear(1L)).thenReturn(camarero());
        when(repo.deMiembro(1L)).thenReturn(Optional.empty());
        when(repo.porNombreYRama("Gimnasio", "HOSTELERIA"))
                .thenReturn(Optional.of(empresa(10L, 9L, "HOSTELERIA", "Gimnasio")));

        assertEquals(ResultadoIngreso.OK, svc().solicitar(1L, "Gimnasio", "Curro duro y madrugo"));
        verify(repo).crearPendiente(10L, 1L, TipoPendiente.SOLICITUD, "Curro duro y madrugo");
    }

    @Test
    @DisplayName("solicitar a una empresa que no existe en tu rama devuelve EMPRESA_NO_EXISTE")
    void solicitarEmpresaNoExiste() {
        when(personajes.obtenerOCrear(1L)).thenReturn(camarero());
        when(repo.deMiembro(1L)).thenReturn(Optional.empty());
        when(repo.porNombreYRama("Fantasma", "HOSTELERIA")).thenReturn(Optional.empty());

        assertEquals(ResultadoIngreso.EMPRESA_NO_EXISTE, svc().solicitar(1L, "Fantasma", null));
        verify(repo, never()).crearPendiente(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("solicitar estando ya en una empresa devuelve YA_EN_EMPRESA")
    void solicitarYaEnEmpresa() {
        when(personajes.obtenerOCrear(1L)).thenReturn(camarero());
        when(repo.deMiembro(1L)).thenReturn(Optional.of(empresa(20L, 5L, "HOSTELERIA", "Rival")));

        assertEquals(ResultadoIngreso.YA_EN_EMPRESA, svc().solicitar(1L, "Gimnasio", "hola"));
        verify(repo, never()).crearPendiente(anyLong(), anyLong(), any(), any());
    }

    // ------------------------------------------------------------ solicitarPorId

    @Test
    @DisplayName("solicitarPorId OK crea una pendiente de SOLICITUD con el motivo recortado")
    void solicitarPorIdOk() {
        when(personajes.obtenerOCrear(1L)).thenReturn(camarero());
        when(repo.deMiembro(1L)).thenReturn(Optional.empty());
        when(repo.porId(10L)).thenReturn(Optional.of(empresa(10L, 9L, "HOSTELERIA", "Gimnasio")));

        assertEquals(ResultadoIngreso.OK, svc().solicitarPorId(1L, 10L, "Curro duro"));
        verify(repo).crearPendiente(10L, 1L, TipoPendiente.SOLICITUD, "Curro duro");
    }

    @Test
    @DisplayName("solicitarPorId sin trabajo devuelve SIN_TRABAJO y no crea pendiente")
    void solicitarPorIdSinTrabajo() {
        when(personajes.obtenerOCrear(1L)).thenReturn(conTrabajo(null));

        assertEquals(ResultadoIngreso.SIN_TRABAJO, svc().solicitarPorId(1L, 10L, "hola"));
        verify(repo, never()).crearPendiente(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("solicitarPorId estando ya en una empresa devuelve YA_EN_EMPRESA")
    void solicitarPorIdYaEnEmpresa() {
        when(personajes.obtenerOCrear(1L)).thenReturn(camarero());
        when(repo.deMiembro(1L)).thenReturn(Optional.of(empresa(20L, 5L, "HOSTELERIA", "Rival")));

        assertEquals(ResultadoIngreso.YA_EN_EMPRESA, svc().solicitarPorId(1L, 10L, "hola"));
        verify(repo, never()).crearPendiente(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("solicitarPorId a un id que no existe devuelve EMPRESA_NO_EXISTE")
    void solicitarPorIdNoExiste() {
        when(personajes.obtenerOCrear(1L)).thenReturn(camarero());
        when(repo.deMiembro(1L)).thenReturn(Optional.empty());
        when(repo.porId(404L)).thenReturn(Optional.empty());

        assertEquals(ResultadoIngreso.EMPRESA_NO_EXISTE, svc().solicitarPorId(1L, 404L, null));
        verify(repo, never()).crearPendiente(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("solicitarPorId a una empresa de otra rama devuelve OTRA_RAMA")
    void solicitarPorIdOtraRama() {
        when(personajes.obtenerOCrear(1L)).thenReturn(camarero()); // HOSTELERIA
        when(repo.deMiembro(1L)).thenReturn(Optional.empty());
        when(repo.porId(10L)).thenReturn(Optional.of(empresa(10L, 9L, "SALUD", "Clínica")));

        assertEquals(ResultadoIngreso.OTRA_RAMA, svc().solicitarPorId(1L, 10L, "hola"));
        verify(repo, never()).crearPendiente(anyLong(), anyLong(), any(), any());
    }

    // ------------------------------------------------------------------ resolver

    @Test
    @DisplayName("resolver una INVITACION: el invitado acepta y entra como BECARIO")
    void resolverInvitacionAceptar() {
        when(repo.pendiente(5L)).thenReturn(Optional.of(
                pendiente(5L, 10L, 2L, TipoPendiente.INVITACION, null)));
        when(repo.deMiembro(2L)).thenReturn(Optional.empty());

        assertEquals(ResultadoResolver.ACEPTADO, svc().resolver(5L, true, 2L));
        verify(repo).anadirMiembro(10L, 2L, RangoEmpresa.BECARIO);
        verify(repo).borrarPendiente(5L);
    }

    @Test
    @DisplayName("resolver una SOLICITUD: el dueño la rechaza y solo se borra la pendiente")
    void resolverSolicitudRechazar() {
        when(repo.pendiente(7L)).thenReturn(Optional.of(
                pendiente(7L, 10L, 3L, TipoPendiente.SOLICITUD, "porfa")));
        when(repo.porId(10L)).thenReturn(Optional.of(empresa(10L, 1L, "HOSTELERIA", "Gimnasio")));

        assertEquals(ResultadoResolver.RECHAZADO, svc().resolver(7L, false, 1L));
        verify(repo).borrarPendiente(7L);
        verify(repo, never()).anadirMiembro(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("resolver por la parte equivocada devuelve NO_ERES_PARTE")
    void resolverParteEquivocada() {
        when(repo.pendiente(5L)).thenReturn(Optional.of(
                pendiente(5L, 10L, 2L, TipoPendiente.INVITACION, null)));

        assertEquals(ResultadoResolver.NO_ERES_PARTE, svc().resolver(5L, true, 99L));
        verify(repo, never()).anadirMiembro(anyLong(), anyLong(), any());
        verify(repo, never()).borrarPendiente(anyLong());
    }

    @Test
    @DisplayName("resolver aceptando cuando el jugador ya entró en otra empresa devuelve YA_EN_EMPRESA")
    void resolverYaEnEmpresa() {
        when(repo.pendiente(5L)).thenReturn(Optional.of(
                pendiente(5L, 10L, 2L, TipoPendiente.INVITACION, null)));
        when(repo.deMiembro(2L)).thenReturn(Optional.of(empresa(20L, 5L, "HOSTELERIA", "Rival")));

        assertEquals(ResultadoResolver.YA_EN_EMPRESA, svc().resolver(5L, true, 2L));
        verify(repo, never()).anadirMiembro(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("resolver una pendiente inexistente devuelve PENDIENTE_NO_EXISTE")
    void resolverPendienteNoExiste() {
        when(repo.pendiente(404L)).thenReturn(Optional.empty());

        assertEquals(ResultadoResolver.PENDIENTE_NO_EXISTE, svc().resolver(404L, true, 2L));
    }

    // ------------------------------------------------------------------ disolver

    @Test
    @DisplayName("disolver por el dueño borra la empresa")
    void disolverDueno() {
        when(repo.deMiembro(1L)).thenReturn(Optional.of(empresa(10L, 1L, "HOSTELERIA", "Gimnasio")));

        assertEquals(ResultadoDisolver.OK, svc().disolver(1L));
        verify(repo).disolver(10L);
    }

    @Test
    @DisplayName("disolver por alguien que no es el dueño no autoriza y no borra")
    void disolverNoDueno() {
        when(repo.deMiembro(2L)).thenReturn(Optional.of(empresa(10L, 1L, "HOSTELERIA", "Gimnasio")));

        assertEquals(ResultadoDisolver.NO_ERES_DUENO, svc().disolver(2L));
        verify(repo, never()).disolver(anyLong());
    }

    // ------------------------------------------------------------------ mejorar

    @Test
    @DisplayName("mejorar por el dueño con bote suficiente cobra ATÓMICAMENTE y SOLO DESPUÉS sube nivel")
    void mejorarOkCobraAntesDeSubir() {
        when(repo.deMiembro(1L)).thenReturn(Optional.of(empresa(10L, 1L, "HOSTELERIA", "Gimnasio", 3)));
        // Bote suficiente: gastarDelBote confirma el cobro del coste 50_000*3.
        when(repo.gastarDelBote(10L, 150_000L)).thenReturn(true);

        SalidaMejora salida = svc().mejorar(1L);

        assertEquals(ResultadoMejora.OK, salida.estado());
        assertEquals(4, salida.nivelNuevo());
        assertEquals(150_000L, salida.coste());
        // El cobro del bote es el gate atómico: gastarDelBote va ANTES de subirNivel.
        InOrder orden = inOrder(repo);
        orden.verify(repo).gastarDelBote(10L, 150_000L);
        orden.verify(repo).subirNivel(10L);
    }

    @Test
    @DisplayName("mejorar por quien no es el dueño devuelve NO_ERES_DUENO y no cobra ni sube")
    void mejorarNoDueno() {
        when(repo.deMiembro(2L)).thenReturn(Optional.of(empresa(10L, 1L, "HOSTELERIA", "Gimnasio", 3)));

        assertEquals(ResultadoMejora.NO_ERES_DUENO, svc().mejorar(2L).estado());
        verify(repo, never()).gastarDelBote(anyLong(), anyLong());
        verify(repo, never()).subirNivel(anyLong());
    }

    @Test
    @DisplayName("mejorar en el nivel tope devuelve TOPE y no cobra ni sube")
    void mejorarEnTope() {
        when(repo.deMiembro(1L)).thenReturn(Optional.of(
                empresa(10L, 1L, "HOSTELERIA", "Gimnasio", EmpresaService.NIVEL_MAX)));

        assertEquals(ResultadoMejora.TOPE, svc().mejorar(1L).estado());
        verify(repo, never()).gastarDelBote(anyLong(), anyLong());
        verify(repo, never()).subirNivel(anyLong());
    }

    @Test
    @DisplayName("mejorar con el bote sin fondos (gastarDelBote=false) devuelve SIN_FONDOS y NO sube nivel")
    void mejorarSinFondos() {
        when(repo.deMiembro(1L)).thenReturn(Optional.of(empresa(10L, 1L, "HOSTELERIA", "Gimnasio", 3)));
        when(repo.gastarDelBote(10L, 150_000L)).thenReturn(false);

        SalidaMejora salida = svc().mejorar(1L);

        assertEquals(ResultadoMejora.SIN_FONDOS, salida.estado());
        assertEquals(150_000L, salida.coste());
        verify(repo, never()).subirNivel(anyLong());
    }

    // ------------------------------------------------------------------ helpers

    /** Personaje con el trabajo dado (el resto de stats no pintan en estas reglas). */
    private static Personaje conTrabajo(String trabajo) {
        return new Personaje(1L, 5, 5, 5, 100, 100, trabajo, null, null, null, null, 0, null, 0, 0);
    }

    /** Camarero: t1 de HOSTELERIA, la rama canónica de estos tests. */
    private static Personaje camarero() {
        return conTrabajo("camarero");
    }

    /** Personaje en paro (sin trabajo). */
    private static Personaje personaje(String trabajo) {
        return conTrabajo(trabajo);
    }

    private static Empresa empresa(long id, long duenoId, String rama, String nombre) {
        return empresa(id, duenoId, rama, nombre, 1);
    }

    /** Empresa con un nivel concreto (para las reglas de {@code mejorar}). */
    private static Empresa empresa(long id, long duenoId, String rama, String nombre, int nivel) {
        return new Empresa(id, rama, duenoId, nombre, nivel, 0L, Instant.now(), null, 0L, 0, false);
    }

    private static Pendiente pendiente(long id, long empresaId, long discordId, TipoPendiente tipo,
                                       String motivo) {
        return new Pendiente(id, empresaId, discordId, tipo, motivo, Instant.now());
    }
}
