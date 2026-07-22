package com.gymprofit.bot.services;

import com.gymprofit.bot.db.DescansoEstado;
import com.gymprofit.bot.db.DescansoRepositorio;
import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del descanso: el cálculo puro de energía (sin BD ni JDA) y el comportamiento de
 * dormir/despertar/estado/fatiga contra repositorios mockeados.
 */
class DescansoServiceTest {

    private static final Camas CASA = new Camas("casa", 30, 100);
    private static final int SALUD_OK = 100;

    @Test
    @DisplayName("la energia es proporcional a los minutos dormidos")
    void proporcionalALosMinutos() {
        // 2 h en casa a 30/h = 60.
        assertEquals(60, DescansoService.energiaGanada(120, CASA, SALUD_OK, 0, 0));
        // 30 min = media hora = 15.
        assertEquals(15, DescansoService.energiaGanada(30, CASA, SALUD_OK, 0, 0));
    }

    @Test
    @DisplayName("dormir mas de 9 h no suma mas que dormir 9 h")
    void topeDeNueveHoras() {
        int nueve = DescansoService.energiaGanada(9 * 60, Camas.SUELO, SALUD_OK, 0, 0);
        int doce = DescansoService.energiaGanada(12 * 60, Camas.SUELO, SALUD_OK, 0, 0);
        assertEquals(nueve, doce);
    }

    @Test
    @DisplayName("en el suelo no se pasa del tope 60 aunque se duerman las 9 h")
    void topePorCama() {
        // 9 h x 10/h = 90 brutos, pero el suelo topa en 60 y se parte de 0.
        assertEquals(60, DescansoService.energiaGanada(9 * 60, Camas.SUELO, SALUD_OK, 0, 0));
    }

    @Test
    @DisplayName("el tope cuenta la energia que ya se tenia")
    void topeCuentaLaEnergiaPrevia() {
        // Con 50 y tope 60, dormir 9 h en el suelo solo puede dar 10.
        assertEquals(10, DescansoService.energiaGanada(9 * 60, Camas.SUELO, SALUD_OK, 50, 0));
    }

    @Test
    @DisplayName("por encima del tope de la cama no se gana nada, pero nunca se resta")
    void porEncimaDelTopeNoGanaNiResta() {
        assertEquals(0, DescansoService.energiaGanada(9 * 60, Camas.SUELO, SALUD_OK, 80, 0));
    }

    @Test
    @DisplayName("con salud baja se descansa la mitad")
    void saludBajaDescansaLaMitad() {
        // 2 h en casa = 60 brutos; con salud 20 (<30) la mitad = 30.
        assertEquals(30, DescansoService.energiaGanada(120, CASA, 20, 0, 0));
    }

    @Test
    @DisplayName("dormir 0 minutos no da energia")
    void ceroMinutosCeroEnergia() {
        assertEquals(0, DescansoService.energiaGanada(0, CASA, SALUD_OK, 0, 0));
    }

    @Test
    @DisplayName("la resistencia acelera el descanso: +1 % por punto")
    void resistenciaAceleraElDescanso() {
        // 1 h en casa = 30 brutos; con 20 de resistencia, +20 % = 36.
        assertEquals(36, DescansoService.energiaGanada(60, CASA, SALUD_OK, 0, 20));
    }

    @Test
    @DisplayName("el bono de resistencia topa en +50 %")
    void bonoDeResistenciaTopado() {
        // 1 h en casa = 30 brutos; el tope es +50 % = 45, aunque tenga 500 de resistencia.
        assertEquals(45, DescansoService.energiaGanada(60, CASA, SALUD_OK, 0, 500));
        assertEquals(DescansoService.energiaGanada(60, CASA, SALUD_OK, 0, 500), DescansoService.energiaGanada(60, CASA, SALUD_OK, 0, 50));
    }

    @Test
    @DisplayName("la resistencia acelera pero no rompe el tope de la cama")
    void resistenciaNoRompeElTopeDeCama() {
        // Con 500 de resistencia, en el suelo se sigue sin pasar de 60.
        assertEquals(60, DescansoService.energiaGanada(9 * 60, Camas.SUELO, SALUD_OK, 0, 500));
    }

    // ---- Comportamiento: dormir, despertar, estado y fatiga (con repositorios mockeados) ----

    private static final long ID = 7L;
    private static final Instant AHORA = Instant.parse("2026-07-15T10:00:00Z");

    private DescansoRepositorio descansoRepo;
    private PersonajeRepositorio personajeRepo;
    private InventarioRepositorio inventarioRepo;
    private EconomiaRepositorio economiaRepo;
    private UsuarioDiscordRepositorio usuarios;
    private DescansoService servicio;

    @BeforeEach
    void setUp() {
        descansoRepo = mock(DescansoRepositorio.class);
        personajeRepo = mock(PersonajeRepositorio.class);
        inventarioRepo = mock(InventarioRepositorio.class);
        economiaRepo = mock(EconomiaRepositorio.class);
        usuarios = mock(UsuarioDiscordRepositorio.class);
        servicio = new DescansoService(descansoRepo, personajeRepo, inventarioRepo,
                economiaRepo, usuarios);
    }

    /** Estado de descanso despierto y sin consumos. */
    private static DescansoEstado despierto() {
        return new DescansoEstado(ID, null, null, 0, null, null);
    }

    /**
     * Personaje con la energía y salud dadas. <b>Resistencia 0 a propósito</b>: así el bono por
     * resistencia no ensucia las cuentas de estos tests (se prueba aparte, en los puros).
     * Orden del record: discordId, fuerza, resistencia, carisma, energia, salud, …
     */
    private static Personaje personaje(int energia, int salud) {
        return new Personaje(ID, 10, 0, 10, energia, salud, null, null, null, null, null, 0, null, 0, 0);
    }

    @Test
    @DisplayName("dormir acuesta al jugador")
    void dormirAcuesta() {
        when(descansoRepo.obtenerOCrear(ID)).thenReturn(despierto());
        when(inventarioRepo.listar(ID)).thenReturn(Map.of());
        when(personajeRepo.obtenerOCrear(ID)).thenReturn(personaje(0, SALUD_OK));

        DescansoService.ResultadoDormir r = servicio.dormir(ID, false, AHORA);

        assertEquals(DescansoService.EstadoDormir.OK, r.estado());
        assertEquals(Camas.SUELO, r.cama());
        verify(descansoRepo).acostar(ID, AHORA, null);
    }

    @Test
    @DisplayName("no se puede dormir dos veces seguidas")
    void noSePuedeDormirDosVeces() {
        when(descansoRepo.obtenerOCrear(ID))
                .thenReturn(new DescansoEstado(ID, AHORA, null, 0, null, null));

        DescansoService.ResultadoDormir r = servicio.dormir(ID, false, AHORA);

        assertEquals(DescansoService.EstadoDormir.YA_DORMIDO, r.estado());
        verify(descansoRepo, never()).acostar(anyLong(), any(), any());
    }

    @Test
    @DisplayName("el hotel cobra y usa la cama de hotel")
    void hotelCobra() {
        when(descansoRepo.obtenerOCrear(ID)).thenReturn(despierto());
        when(economiaRepo.gastar(eq(ID), eq(Camas.PRECIO_HOTEL), anyString())).thenReturn(true);
        when(personajeRepo.obtenerOCrear(ID)).thenReturn(personaje(0, SALUD_OK));

        DescansoService.ResultadoDormir r = servicio.dormir(ID, true, AHORA);

        assertEquals(DescansoService.EstadoDormir.OK, r.estado());
        assertEquals(Camas.HOTEL, r.cama());
        verify(economiaRepo).gastar(eq(ID), eq(Camas.PRECIO_HOTEL), anyString());
    }

    @Test
    @DisplayName("sin saldo no se duerme en el hotel")
    void hotelSinSaldo() {
        when(descansoRepo.obtenerOCrear(ID)).thenReturn(despierto());
        when(economiaRepo.gastar(eq(ID), eq(Camas.PRECIO_HOTEL), anyString())).thenReturn(false);
        when(personajeRepo.obtenerOCrear(ID)).thenReturn(personaje(0, SALUD_OK));

        DescansoService.ResultadoDormir r = servicio.dormir(ID, true, AHORA);

        assertEquals(DescansoService.EstadoDormir.SIN_SALDO, r.estado());
        verify(descansoRepo, never()).acostar(anyLong(), any(), any());
    }

    @Test
    @DisplayName("despertar tras 2 h en casa da 60 de energia")
    void despertarDaEnergia() {
        // Resistencia 0 en el helper: el bono por resistencia se prueba aparte, en los tests puros.
        Instant hace2h = AHORA.minus(Duration.ofHours(2));
        when(descansoRepo.obtenerOCrear(ID))
                .thenReturn(new DescansoEstado(ID, hace2h, null, 0, null, null));
        when(personajeRepo.obtenerOCrear(ID)).thenReturn(personaje(0, 100));
        when(inventarioRepo.listar(ID)).thenReturn(Map.of("casa", 1));

        DescansoService.ResultadoDespertar r = servicio.despertar(ID, AHORA);

        assertEquals(DescansoService.EstadoDespertar.OK, r.estado());
        assertEquals(60, r.energiaGanada());
        assertEquals(120, r.minutosDormidos());
        verify(personajeRepo).sumarEnergiaConTope(ID, 60, 100);
        verify(descansoRepo).levantar(ID, AHORA);
    }

    @Test
    @DisplayName("despertar sin estar dormido no hace nada")
    void despertarSinDormir() {
        when(descansoRepo.obtenerOCrear(ID)).thenReturn(despierto());

        DescansoService.ResultadoDespertar r = servicio.despertar(ID, AHORA);

        assertEquals(DescansoService.EstadoDespertar.NO_DORMIDO, r.estado());
        verify(personajeRepo, never()).sumarEnergiaConTope(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("estaDormido refleja el estado")
    void estaDormido() {
        when(descansoRepo.obtenerOCrear(ID))
                .thenReturn(new DescansoEstado(ID, AHORA, null, 0, null, null));
        assertTrue(servicio.estaDormido(ID));
    }

    @Test
    @DisplayName("mas de 24 h sin despertar es fatiga")
    void fatigaTras24h() {
        DescansoEstado hace25h = new DescansoEstado(ID, null, AHORA.minus(Duration.ofHours(25)), 0, null, null);
        assertTrue(DescansoService.tieneFatiga(hace25h, AHORA));

        DescansoEstado hace2h = new DescansoEstado(ID, null, AHORA.minus(Duration.ofHours(2)), 0, null, null);
        assertFalse(DescansoService.tieneFatiga(hace2h, AHORA));
    }

    @Test
    @DisplayName("quien nunca ha dormido no arrastra fatiga")
    void sinHistorialNoHayFatiga() {
        assertFalse(DescansoService.tieneFatiga(despierto(), AHORA));
    }

    @Test
    @DisplayName("mientras duermes no tienes fatiga")
    void dormidoNoTieneFatiga() {
        DescansoEstado durmiendo = new DescansoEstado(ID, AHORA.minus(Duration.ofHours(1)),
                AHORA.minus(Duration.ofHours(30)), 0, null, null);
        assertFalse(DescansoService.tieneFatiga(durmiendo, AHORA));
    }

    @Test
    void elDesgloseAvisaDeQueSoloCuentanNueveHoras() {
        // 4 días dormido en el suelo: solo cuentan 9 h.
        var d = DescansoService.desglosar(4 * 24 * 60, Camas.SUELO, 100, 13, 0);

        assertTrue(d.recortadoPorHoras());
        assertEquals(DescansoService.MAX_HORAS * 60, d.minutosContados());
    }

    @Test
    void elDesgloseAvisaDeQueSeHaTocadoElTopeDeLaCama() {
        // 9 h en el suelo dan 90 de bruta, pero el suelo topa la energía TOTAL en 60.
        var d = DescansoService.desglosar(9 * 60, Camas.SUELO, 100, 13, 0);

        assertTrue(d.topeAlcanzado());
        assertEquals(47, d.ganada());
        assertEquals(13, d.energiaAntes());
        assertEquals(60, d.energiaDespues());
    }

    @Test
    void sinTocarTopeNiRecorteNoHayAvisos() {
        // 2 h en el suelo: 20 de energía, lejos del tope.
        var d = DescansoService.desglosar(2 * 60, Camas.SUELO, 100, 10, 0);

        assertEquals(20, d.ganada());
        assertFalse(d.topeAlcanzado());
        assertFalse(d.recortadoPorHoras());
        assertFalse(d.penalizadoPorSalud());
        assertEquals(0, d.bonoResistenciaPct());
    }

    @Test
    void elDesgloseReportaLaPenalizacionPorSaludYElBonoDeResistencia() {
        var d = DescansoService.desglosar(2 * 60, Camas.SUELO, 10, 0, 30);

        assertTrue(d.penalizadoPorSalud());
        assertEquals(30, d.bonoResistenciaPct());
        assertEquals(13, d.ganada()); // 20 × 1,30 × 0,5 = 13
    }

    @Test
    void yaDescansadoNoGanaNadaPeroAvisaDelTope() {
        var d = DescansoService.desglosar(9 * 60, Camas.SUELO, 100, 80, 0);

        assertEquals(0, d.ganada());
        assertTrue(d.topeAlcanzado());
        assertEquals(80, d.energiaDespues()); // dormir nunca resta
    }

    @Test
    @DisplayName("dormir informa de la energia que se tenia al acostarse")
    void dormirDevuelveLaEnergiaActual() {
        when(descansoRepo.obtenerOCrear(ID)).thenReturn(despierto());
        when(inventarioRepo.listar(ID)).thenReturn(Map.of());
        when(personajeRepo.obtenerOCrear(ID)).thenReturn(personaje(80, SALUD_OK));

        DescansoService.ResultadoDormir r = servicio.dormir(ID, false, AHORA);

        // 80 de energía con tope 60 (suelo): el comando avisa de que ahí no sube nada.
        assertEquals(80, r.energiaActual());
        assertEquals(Camas.SUELO, r.cama());
    }
}
