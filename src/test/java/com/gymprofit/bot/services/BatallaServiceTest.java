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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
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
    private final DescansoService descanso = mock(DescansoService.class);

    /** Estos tests pelean despiertos: el bloqueo por sueño se prueba aparte. */
    @BeforeEach
    void despierto() {
        when(descanso.estaDormido(anyLong())).thenReturn(false);
    }

    /** Mock de pasivos sin ningún bono: es lo que usan todos los tests que no van de esto. */
    private PasivoService sinPasivos() {
        PasivoService p = mock(PasivoService.class);
        when(p.bonosDe(anyLong())).thenReturn(PasivoService.sumarYTopar(List.of()));
        return p;
    }

    /** Servicio con azar fijo (factor de daño = 1.0) y sin ningún pasivo equipado. */
    private BatallaService svc(double azar) {
        return new BatallaService(personajes, inventario, usuarios, economia, xp, mundos, descanso,
                sinPasivos(), () -> azar);
    }

    /** Servicio con azar fijo y unos pasivos concretos. */
    private BatallaService svcConPasivos(double azar, PasivoService pasivos) {
        return new BatallaService(personajes, inventario, usuarios, economia, xp, mundos, descanso,
                pasivos, () -> azar);
    }

    private static Personaje personaje(int fuerza, int resistencia) {
        return new Personaje(1L, fuerza, resistencia, 0, 100, 100, null, null, null, null,
                null, 0, null, 0, 0);
    }

    private static CombateSesion sesion(String monstruoId, int ataque, int defensa, int hpMax) {
        return sesion(monstruoId, ataque, defensa, 0.0, 0.0, hpMax);
    }

    private static CombateSesion sesion(String monstruoId, int ataque, int defensa,
                                        double crit, double esquiva, int hpMax) {
        return sesion(monstruoId, ataque, defensa, crit, esquiva, 0.0, hpMax);
    }

    private static CombateSesion sesion(String monstruoId, int ataque, int defensa,
                                        double crit, double esquiva, double roboVida, int hpMax) {
        Monstruos m = Monstruos.porId(monstruoId).orElseThrow();
        return new CombateSesion(1L, m.mundo(), m, ataque, defensa, crit, esquiva, roboVida, hpMax);
    }

    /** Azar secuenciado: devuelve los valores en orden y repite el último. */
    private static BatallaService.Aleatorio cola(double... vals) {
        int[] i = {0};
        return () -> vals[Math.min(i[0]++, vals.length - 1)];
    }

    private BatallaService svc(BatallaService.Aleatorio azar) {
        return new BatallaService(personajes, inventario, usuarios, economia, xp, mundos, descanso,
                sinPasivos(), azar);
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

    // ---------------------- Críticos y esquivas ----------------------

    @Test
    void unGolpeCriticoDoblaElDano() {
        // lobo poder 8, hp 30, def=1 ; ataque 20 -> 19, crítico -> 38 (mata)
        CombateSesion s = sesion("lobo", 20, 5, 1.0, 0.0, 100);
        // secuencia del golpe del jugador: [esquiva-monstruo, factor, crítico]
        Turno t = svc(cola(0.9, 0.5, 0.0)).atacar(s);
        assertTrue(t.critJugador());
        assertEquals(38, t.danoAlMonstruo());
        assertEquals(Desenlace.VICTORIA, t.desenlace());
    }

    @Test
    void elMonstruoPuedeEsquivarTuGolpe() {
        CombateSesion s = sesion("lobo", 20, 5, 100);
        // primer valor 0.0 < 0.05 -> el monstruo esquiva; luego contraataque normal
        Turno t = svc(cola(0.0, 0.9, 0.5, 0.9)).atacar(s);
        assertTrue(t.esquivaMonstruo());
        assertEquals(0, t.danoAlMonstruo());
        assertEquals(3, t.danoAlJugador()); // poder 8 - def 5
    }

    @Test
    void puedesEsquivarElContraataque() {
        CombateSesion s = sesion("lobo", 20, 5, 0.0, 1.0, 100); // esquiva jugador 100%
        Turno t = svc(cola(0.9, 0.5, 0.9, 0.5)).atacar(s);
        assertTrue(t.esquivaJugador());
        assertEquals(0, t.danoAlJugador());
        assertEquals(19, t.danoAlMonstruo());
        assertEquals(100, s.hpJugador());
    }

    @Test
    void elMonstruoPuedeAsestarUnCritico() {
        CombateSesion s = sesion("lobo", 20, 5, 100);
        // jugador: [0.9,0.5,0.9] (golpe normal) ; contra: [0.9 no-esq, 0.5 factor, 0.0 crítico]
        Turno t = svc(cola(0.9, 0.5, 0.9, 0.9, 0.5, 0.0)).atacar(s);
        assertTrue(t.critMonstruo());
        assertEquals(6, t.danoAlJugador()); // (8-5)=3, crítico x2
    }

    // ---------------------- Habilidades ----------------------

    @Test
    void golpePotenteDoblaElAtaque() {
        CombateSesion s = sesion("lobo", 20, 5, 100); // ataque*2=40, def=1 -> 39, mata (hp30)
        Turno t = svc(0.5).usarHabilidad(s, "golpe_potente");
        assertEquals(39, t.danoAlMonstruo());
        assertEquals(Desenlace.VICTORIA, t.desenlace());
    }

    @Test
    void curarRecuperaHpYGastaElTurno() {
        CombateSesion s = sesion("lobo", 20, 5, 100);
        s.danarJugador(60); // hp 40
        Turno t = svc(0.5).usarHabilidad(s, "curar"); // cura 30% de 100 = 30
        assertEquals(30, t.curado());
        assertEquals(67, s.hpJugador()); // 40 + 30 - 3 (contra)
    }

    @Test
    void aturdirGolpeaYEvitaElContraataque() {
        CombateSesion s = sesion("oso", 20, 5, 200); // oso hp120: sobrevive al golpe
        Turno t = svc(0.5).usarHabilidad(s, "aturdir");
        assertTrue(t.sinContraataque());
        assertTrue(t.danoAlMonstruo() > 0);
        assertEquals(200, s.hpJugador()); // no recibe contraataque
    }

    @Test
    void unaHabilidadEnCooldownNoSePuedeRepetir() {
        CombateSesion s = sesion("oso", 20, 5, 300); // sobrevive para poder reintentar
        assertNotNull(svc(0.5).usarHabilidad(s, "golpe_potente"));
        assertTrue(s.cooldown("golpe_potente") > 0);
        assertNull(svc(0.5).usarHabilidad(s, "golpe_potente"));
    }

    @Test
    void habilidadInexistenteDevuelveNull() {
        CombateSesion s = sesion("lobo", 20, 5, 100);
        assertNull(svc(0.5).usarHabilidad(s, "teletransporte"));
    }

    // ---------------------- Encantamientos (efecto en combate) ----------------------

    @Test
    void elRoboDeVidaCuraAlGolpear() {
        // roboVida 50%; ataque 41 vs lobo (def 1) -> 40 daño, mata; cura round(40*0.5)=20
        CombateSesion s = sesion("lobo", 41, 5, 0.0, 0.0, 0.5, 200);
        s.danarJugador(100); // hp 100
        Turno t = svc(0.5).atacar(s);
        assertEquals(Desenlace.VICTORIA, t.desenlace());
        assertEquals(120, s.hpJugador());
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
    void dormidoNoPuedePelear() {
        when(descanso.estaDormido(1L)).thenReturn(true);
        assertEquals(InicioEstado.DORMIDO, svc(0.5).iniciar(1L, "lobo").estado());
        // El intento sale gratis: dormido no se gasta energía.
        verify(personajes, never()).gastarEnergia(anyLong(), anyInt());
    }

    @Test
    void dormidoNoPuedeEntrarEnMazmorra() {
        when(descanso.estaDormido(1L)).thenReturn(true);
        assertEquals(InicioEstado.DORMIDO, svc(0.5).iniciarMazmorra(1L, "guarida_lobos").estado());
        verify(personajes, never()).gastarEnergia(anyLong(), anyInt());
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

    // ---------------------- Mazmorras ----------------------

    @Test
    void iniciarMazmorraCargaLaPrimeraOleada() {
        when(personajes.obtenerOCrear(1L)).thenReturn(personaje(5, 3));
        when(mundos.completados(1L)).thenReturn(Set.of());
        when(usuarios.buscar(1L)).thenReturn(Optional.of(
                new UsuarioDiscord(1L, 0, 10, 0, 0, null, null, false)));
        when(personajes.gastarEnergia(1L, BatallaService.ENERGIA_POR_MAZMORRA)).thenReturn(true);
        var r = svc(0.5).iniciarMazmorra(1L, "guarida_lobos");
        assertEquals(InicioEstado.OK, r.estado());
        assertTrue(r.sesion().esMazmorra());
        assertEquals(5, r.sesion().oleadasTotal());
        assertEquals("lobo", r.sesion().monstruo().id());
    }

    @Test
    void iniciarMazmorraSinEnergiaFalla() {
        when(personajes.obtenerOCrear(1L)).thenReturn(personaje(5, 3));
        when(mundos.completados(1L)).thenReturn(Set.of());
        when(usuarios.buscar(1L)).thenReturn(Optional.of(
                new UsuarioDiscord(1L, 0, 10, 0, 0, null, null, false)));
        when(personajes.gastarEnergia(1L, BatallaService.ENERGIA_POR_MAZMORRA)).thenReturn(false);
        assertEquals(InicioEstado.SIN_ENERGIA,
                svc(0.5).iniciarMazmorra(1L, "guarida_lobos").estado());
    }

    @Test
    void avanzarOleadaRecorreLasOleadasYLuegoTermina() {
        CombateSesion s = sesion("lobo", 20, 5, 100);
        s.configurarMazmorra("guarida_lobos", List.of(
                Monstruos.porId("jabali").orElseThrow(), Monstruos.porId("oso").orElseThrow()), 3);
        s.danarJugador(30); // el HP se conserva entre oleadas
        assertTrue(s.avanzarOleada());
        assertEquals("jabali", s.monstruo().id());
        assertEquals(s.hpMaxMonstruo(), s.hpMonstruo()); // el monstruo empieza a tope
        assertEquals(70, s.hpJugador());                 // el jugador no se cura
        assertTrue(s.avanzarOleada());
        assertEquals("oso", s.monstruo().id());
        assertFalse(s.avanzarOleada());                  // no quedan más oleadas
    }

    @Test
    void completarMazmorraDaElBonus() {
        when(xp.ganarXp(anyLong(), anyInt())).thenReturn(new XpResultado(null, false, 1, 1));
        var b = svc(0.5).completarMazmorra(1L, "guarida_lobos");
        assertEquals(300, b.coins());
        assertEquals(150, b.xp());
        verify(economia).ingresar(eq(1L), eq(300L), anyString());
        verify(xp).ganarXp(1L, 150);
    }

    // ---------------------- Efectos pasivos en combate ----------------------

    /** Monstruo del mundo inicial que ya usan los tests de {@code iniciar}. */
    private static final String MONSTRUO_DE_PRUEBA = "lobo";

    /** Mismo montaje de mocks que usan los tests de iniciar(): todo en orden para pelear. */
    private void montarInicioOk() {
        when(personajes.obtenerOCrear(1L)).thenReturn(personaje(5, 3));
        when(mundos.completados(1L)).thenReturn(Set.of());
        when(usuarios.buscar(1L)).thenReturn(Optional.of(
                new UsuarioDiscord(1L, 0, 10, 0, 0, null, null, false)));
        when(personajes.gastarEnergia(1L, BatallaService.ENERGIA_POR_PELEA)).thenReturn(true);
    }

    @Test
    @DisplayName("conPasivos suma ataque y defensa planos y el crítico aditivo, con techo 0,9")
    void conPasivosEsPuro() {
        var bonos = PasivoService.sumarYTopar(List.of(
                Pasivos.porId("cohete").orElseThrow(),       // +5 at, +4 def, +3 % crit
                Pasivos.porId("mancuernas").orElseThrow()));  // +3 at
        var r = BatallaService.conPasivos(100, 50, 0.20, bonos);
        assertEquals(108, r.ataque());
        assertEquals(54, r.defensa());
        assertEquals(0.23, r.critico(), 1e-9);

        // Mismo techo duro que el encantamiento CRITICO: un jugador ya saturado no gana nada.
        assertEquals(0.9, BatallaService.conPasivos(1, 1, 0.89, bonos).critico(), 1e-9);
        // Sin bonos no cambia nada.
        var cero = PasivoService.sumarYTopar(List.of());
        assertEquals(100, BatallaService.conPasivos(100, 50, 0.20, cero).ataque());
    }

    @Test
    @DisplayName("la sesión nace con el ataque, la defensa y el crítico ya sumados")
    void laSesionNaceConLosPasivos() {
        PasivoService pasivos = mock(PasivoService.class);
        when(pasivos.bonosDe(anyLong())).thenReturn(PasivoService.sumarYTopar(List.of(
                Pasivos.porId("cohete").orElseThrow())));

        montarInicioOk();
        var conPasivos = svcConPasivos(0.5, pasivos).iniciar(1L, MONSTRUO_DE_PRUEBA);
        var sinPasivosRes = svc(0.5).iniciar(1L, MONSTRUO_DE_PRUEBA);

        assertEquals(InicioEstado.OK, conPasivos.estado());
        assertEquals(sinPasivosRes.sesion().ataqueJugador() + 5,
                conPasivos.sesion().ataqueJugador(), "el cohete da +5 de ataque");
        assertEquals(sinPasivosRes.sesion().defensaJugador() + 4,
                conPasivos.sesion().defensaJugador(), "y +4 de defensa");
        assertEquals(sinPasivosRes.sesion().critJugador() + 0.03,
                conPasivos.sesion().critJugador(), 1e-9, "y +3 % de crítico");
    }

    @Test
    @DisplayName("la sesión es un snapshot: cambiar los pasivos a mitad de mazmorra no la altera")
    void elSnapshotNoCambiaAMitadDePelea() {
        PasivoService pasivos = mock(PasivoService.class);
        when(pasivos.bonosDe(anyLong())).thenReturn(PasivoService.sumarYTopar(List.of()));

        montarInicioOk();
        var sesion = svcConPasivos(0.5, pasivos).iniciar(1L, MONSTRUO_DE_PRUEBA).sesion();
        int ataqueAntes = sesion.ataqueJugador();

        // El jugador equipa el cohete justo antes del golpe final del jefe: no le vale de nada.
        when(pasivos.bonosDe(anyLong())).thenReturn(PasivoService.sumarYTopar(List.of(
                Pasivos.porId("cohete").orElseThrow())));

        assertEquals(ataqueAntes, sesion.ataqueJugador(),
                "los bonos se congelan al empezar: nada de equipar el cohete a mitad de pelea");
    }

    @Test
    void catalogoDeMazmorrasEsCoherente() {
        for (Mazmorras mz : Mazmorras.CATALOGO) {
            assertTrue(Mundos.porId(mz.mundo()).isPresent(), mz.id());
            assertTrue(mz.oleadas().size() > 0, mz.id());
            for (String id : mz.oleadas()) {
                assertTrue(Monstruos.porId(id).isPresent(), "monstruo inexistente: " + id);
            }
        }
    }
}
