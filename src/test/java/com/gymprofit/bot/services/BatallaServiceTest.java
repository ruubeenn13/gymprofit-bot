package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.MundoRepositorio;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.BatallaService.Botin;
import com.gymprofit.bot.services.BatallaService.Desenlace;
import com.gymprofit.bot.services.BatallaService.InicioEstado;
import com.gymprofit.bot.services.BatallaService.Turno;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica el motor de batalla: turnos (atacar/defender/objeto), desenlaces, reparto de botín y
 * validaciones de inicio. El azar se fija a 0.5 (factor de daño = 1.0) salvo cuando se testea el
 * loot, para que el balance sea determinista.
 */
class BatallaServiceTest {

    private final PersonajeRepositorio personajes = mock(PersonajeRepositorio.class);
    private final InventarioRepositorio inventario = mock(InventarioRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final XpService xp = mock(XpService.class);
    private final MundoRepositorio mundos = mock(MundoRepositorio.class);

    /** Servicio con azar fijo (factor de daño = 1.0). */
    private BatallaService svc(double azar) {
        return new BatallaService(personajes, inventario, usuarios, economia, xp, mundos, () -> azar);
    }

    private static Personaje personaje(int fuerza, int resistencia) {
        return new Personaje(1L, fuerza, resistencia, 0, 100, 100, null, null, null, null, null);
    }

    private static CombateSesion sesion(String monstruoId, int ataque, int defensa, int hpMax) {
        Monstruos m = Monstruos.porId(monstruoId).orElseThrow();
        return new CombateSesion(1L, m.mundo(), m, ataque, defensa, hpMax);
    }

    // ---------------------- Turnos ----------------------

    @Test
    void atacarDanaAlMonstruoYRecibeContraataque() {
        CombateSesion s = sesion("lobo", 20, 5, 100); // lobo poder 8, hp 30, def=8/8=1
        Turno t = svc(0.5).atacar(s);
        assertEquals(Desenlace.CONTINUA, t.desenlace());
        assertEquals(19, t.danoAlMonstruo());          // 20 - 1
        assertEquals(11, s.hpMonstruo());              // 30 - 19
        assertEquals(3, t.danoAlJugador());            // poder 8 - def 5
        assertEquals(97, s.hpJugador());
    }

    @Test
    void atacarQueMataDaVictoriaSinContraataque() {
        CombateSesion s = sesion("rata_gigante", 40, 0, 100); // hp 26
        Turno t = svc(0.5).atacar(s);
        assertEquals(Desenlace.VICTORIA, t.desenlace());
        assertEquals(0, t.danoAlJugador());
        assertTrue(s.monstruoMuerto());
    }

    @Test
    void siElContraataqueTeMataEsDerrota() {
        CombateSesion s = sesion("oso", 1, 0, 5); // oso poder 30
        Turno t = svc(0.5).atacar(s);
        assertEquals(Desenlace.DERROTA, t.desenlace());
        assertTrue(s.jugadorMuerto());
    }

    @Test
    void defenderReduceElContraataqueALaMitad() {
        CombateSesion s = sesion("lobo", 20, 0, 100); // contra sin defensa = 8
        Turno t = svc(0.5).defender(s);
        assertEquals(0, t.danoAlMonstruo());
        assertEquals(4, t.danoAlJugador());  // round(8 * 0.5)
        assertEquals(96, s.hpJugador());
    }

    @Test
    void objetoDeSaludCuraYGastaElTurno() {
        when(inventario.quitar(1L, "botiquin", 1)).thenReturn(true);
        CombateSesion s = sesion("lobo", 20, 5, 100);
        s.danarJugador(60); // hp 40
        Turno t = svc(0.5).usarObjeto(s, "botiquin"); // botiquin SALUD +50
        assertEquals(50, t.curado());
        assertFalse(t.sinContraataque());
        assertEquals(87, s.hpJugador()); // 40 + 50 - 3 (contra)
        verify(inventario).quitar(1L, "botiquin", 1);
    }

    @Test
    void objetoDeEnergiaDaTurnoExtraSinContraataque() {
        when(inventario.quitar(1L, "cafe", 1)).thenReturn(true);
        CombateSesion s = sesion("lobo", 20, 5, 100);
        Turno t = svc(0.5).usarObjeto(s, "cafe"); // cafe ENERGIA
        assertTrue(t.sinContraataque());
        assertEquals(Desenlace.CONTINUA, t.desenlace());
        assertEquals(100, s.hpJugador()); // el monstruo no contraataca
    }

    @Test
    void noSePuedeUsarUnItemQueNoEsConsumible() {
        CombateSesion s = sesion("lobo", 20, 5, 100);
        assertNull(svc(0.5).usarObjeto(s, "espada"));
        verify(inventario, never()).quitar(anyLong(), anyString(), anyInt());
    }

    @Test
    void noSePuedeUsarUnConsumibleQueNoTienes() {
        when(inventario.quitar(1L, "botiquin", 1)).thenReturn(false);
        CombateSesion s = sesion("lobo", 20, 5, 100);
        assertNull(svc(0.5).usarObjeto(s, "botiquin"));
    }

    // ---------------------- Botín ----------------------

    @Test
    void recompensarDaCoinsXpYLootYNoDesbloqueaSiNoEsJefe() {
        when(xp.ganarXp(anyLong(), anyInt())).thenReturn(
                new XpResultado(null, false, 1, 1));
        CombateSesion s = sesion("lobo", 20, 5, 100); // lobo coins 8, xp 6, loot fruta 0.5
        Botin b = svc(0.1).recompensar(s); // azar 0.1 < 0.5 -> cae fruta
        assertEquals(8, b.coins());
        assertEquals(6, b.xp());
        assertTrue(b.items().contains("fruta"));
        assertFalse(b.jefeDerrotado());
        verify(economia).ingresar(eq(1L), eq(8L), anyString());
        verify(inventario).anadir(1L, "fruta", 1);
        verify(mundos, never()).marcarJefeDerrotado(anyLong(), anyString());
    }

    @Test
    void matarAlJefeDesbloqueaElMundoSiguiente() {
        when(xp.ganarXp(anyLong(), anyInt())).thenReturn(new XpResultado(null, false, 1, 1));
        CombateSesion s = sesion("ent_ancestral", 20, 5, 100); // jefe del bosque
        Botin b = svc(0.9).recompensar(s); // azar alto -> sin loot, da igual
        assertTrue(b.jefeDerrotado());
        assertEquals("cueva", b.siguienteMundoId());
        verify(mundos).marcarJefeDerrotado(1L, "bosque");
    }

    // ---------------------- Inicio ----------------------

    @Test
    void iniciarContraMonstruoInexistenteFalla() {
        assertEquals(InicioEstado.MONSTRUO_NO_EXISTE, svc(0.5).iniciar(1L, "nope").estado());
    }

    @Test
    void iniciarEnMundoBloqueadoFalla() {
        when(personajes.obtenerOCrear(1L)).thenReturn(personaje(5, 3));
        when(mundos.completados(1L)).thenReturn(Set.of()); // cueva necesita bosque
        assertEquals(InicioEstado.MUNDO_BLOQUEADO, svc(0.5).iniciar(1L, "goblin").estado());
    }

    @Test
    void iniciarSinNivelFalla() {
        when(personajes.obtenerOCrear(1L)).thenReturn(personaje(5, 3));
        when(mundos.completados(1L)).thenReturn(Set.of("bosque")); // cueva desbloqueada
        when(usuarios.buscar(1L)).thenReturn(Optional.of(
                new UsuarioDiscord(1L, 0, 1, 0, 0, null, null, false))); // nivel 1 < 3
        var r = svc(0.5).iniciar(1L, "goblin");
        assertEquals(InicioEstado.NIVEL_INSUFICIENTE, r.estado());
        assertEquals(3, r.detalle()); // cueva nivel requerido
    }

    @Test
    void iniciarSinEnergiaFalla() {
        when(personajes.obtenerOCrear(1L)).thenReturn(personaje(5, 3));
        when(mundos.completados(1L)).thenReturn(Set.of());
        when(usuarios.buscar(1L)).thenReturn(Optional.of(
                new UsuarioDiscord(1L, 0, 10, 0, 0, null, null, false)));
        when(personajes.gastarEnergia(1L, BatallaService.ENERGIA_POR_PELEA)).thenReturn(false);
        assertEquals(InicioEstado.SIN_ENERGIA, svc(0.5).iniciar(1L, "lobo").estado()); // bosque, nivel 0 ok
    }

    @Test
    void iniciarOkCreaLaSesion() {
        when(personajes.obtenerOCrear(1L)).thenReturn(personaje(5, 3));
        when(mundos.completados(1L)).thenReturn(Set.of());
        when(usuarios.buscar(1L)).thenReturn(Optional.of(
                new UsuarioDiscord(1L, 0, 10, 0, 0, null, null, false)));
        when(personajes.gastarEnergia(1L, BatallaService.ENERGIA_POR_PELEA)).thenReturn(true);
        var r = svc(0.5).iniciar(1L, "lobo");
        assertEquals(InicioEstado.OK, r.estado());
        assertNotNull(r.sesion());
        assertEquals("lobo", r.sesion().monstruo().id());
        assertEquals(110, r.sesion().hpMaxJugador()); // 80 + 3*10
    }
}
