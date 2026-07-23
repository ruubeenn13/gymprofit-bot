package com.gymprofit.bot.services;

import com.gymprofit.bot.db.CarreraRepositorio;
import com.gymprofit.bot.db.DescansoEstado;
import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.TrabajoService.EstadoAscenso;
import com.gymprofit.bot.services.TrabajoService.EstadoWork;
import com.gymprofit.bot.services.TrabajoService.ResultadoDimitir;
import com.gymprofit.bot.services.TrabajoService.ResultadoElegir;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica el pago por turno, el bloqueo por sueño, la coherencia del catálogo de trabajos y el
 * sistema de ascensos (gate de elegir por tier y validación en orden de {@code ascender}).
 */
class TrabajoServiceTest {

    private final PersonajeRepositorio personajes = mock(PersonajeRepositorio.class);
    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final DescansoService descanso = mock(DescansoService.class);
    private final CarreraRepositorio carreras = mock(CarreraRepositorio.class);

    @Test
    void dormidoNoSePuedeCurrar() {
        when(descanso.estaDormido(1L)).thenReturn(true);
        TrabajoService svc = new TrabajoService(personajes, economia, usuarios, descanso, carreras);

        assertEquals(EstadoWork.DORMIDO, svc.trabajar(1L, Instant.now()).estado());
        // El intento sale gratis: no cobra ni gasta energía.
        verify(personajes, never()).trabajar(anyLong(), anyInt());
        verify(economia, never()).ingresar(anyLong(), anyLong(), anyString());
    }

    @Test
    void elPagoCaeDentroDelRango() {
        Random rng = new Random(7);
        for (int i = 0; i < 100; i++) {
            int pago = TrabajoService.calcularPago(80, 140, rng);
            assertTrue(pago >= 80 && pago <= 140, "pago fuera de rango: " + pago);
        }
    }

    @Test
    void losEstudiosDanBonoAlSueldoConTope() {
        assertEquals(100, TrabajoService.conBonoEstudios(100, 0));    // sin estudios
        assertEquals(110, TrabajoService.conBonoEstudios(100, 10));   // +10%
        assertEquals(125, TrabajoService.conBonoEstudios(100, 25));   // +25% (tope)
        assertEquals(125, TrabajoService.conBonoEstudios(100, 100));  // no pasa del tope
    }

    @Test
    @DisplayName("con fatiga el sueldo baja un 20 %")
    void fatigaBajaElSueldo() {
        assertEquals(80, TrabajoService.conPenalizacionFatiga(100, true));
        assertEquals(100, TrabajoService.conPenalizacionFatiga(100, false));
        // Suelo de 1 coin: currar fatigado paga menos, pero nunca sale gratis para el jefe.
        assertEquals(1, TrabajoService.conPenalizacionFatiga(1, true));
    }

    @Test
    @DisplayName("conBonoPasivos aplica el bono de sueldo y satura en el tope del +30 %")
    void bonoDeSueldoPorPasivos() {
        assertEquals(100, TrabajoService.conBonoPasivos(100, 0.0), "sin pasivos no cambia nada");
        assertEquals(111, TrabajoService.conBonoPasivos(100, 0.11), "el jet solo: +11 %");
        assertEquals(130, TrabajoService.conBonoPasivos(100, 0.30), "justo en el tope");
        assertEquals(130, TrabajoService.conBonoPasivos(100, 0.35),
                "35 % bruto satura a 30 %: el tope no es un error, es el diseño");
        assertEquals(100, TrabajoService.conBonoPasivos(100, -0.5),
                "un bono negativo nunca recorta el sueldo");
    }

    @Test
    @DisplayName("el orden de la tubería es estudios → pasivos → fatiga, y no otro")
    void ordenDeLaTuberiaDeSueldo() {
        // 100 base · 25 de estudios (+25 %) · +30 % de pasivos · fatiga (×0,8).
        int conEstudios = TrabajoService.conBonoEstudios(100, 25);
        assertEquals(125, conEstudios);
        int conPasivos = TrabajoService.conBonoPasivos(conEstudios, 0.30);
        assertEquals(163, conPasivos, "round(125 × 1,30) = 163");
        assertEquals(130, TrabajoService.conPenalizacionFatiga(conPasivos, true),
                "round(163 × 0,8) = 130; la fatiga recorta el sueldo FINAL, no el base");
        // Si alguien invirtiera el orden (fatiga antes que pasivos) saldría 130 también por
        // casualidad en este caso, así que se comprueba un caso donde el orden sí se nota.
        assertEquals(11, TrabajoService.conPenalizacionFatiga(
                        TrabajoService.conBonoPasivos(TrabajoService.conBonoEstudios(9, 25), 0.30), true),
                "9 → 11 (estudios) → 14 (pasivos) → 11 (fatiga)");
    }

    @Test
    @DisplayName("cooldownEfectivo: 60 min de base, 45 min con el tope del −25 %")
    void cooldownConPasivos() {
        assertEquals(Duration.ofMinutes(60), TrabajoService.cooldownEfectivo(0.0));
        assertEquals(Duration.ofMinutes(45), TrabajoService.cooldownEfectivo(0.25));
        assertEquals(Duration.ofMinutes(45), TrabajoService.cooldownEfectivo(0.90),
                "el tope satura: el suelo del cooldown es 45 min pase lo que pase");
        assertEquals(Duration.ofMinutes(54), TrabajoService.cooldownEfectivo(0.10),
                "el helicóptero solo: 60 × 0,9 = 54 min");
        assertEquals(TrabajoService.COOLDOWN_WORK, TrabajoService.cooldownEfectivo(-1.0),
                "un bono negativo no puede alargar el cooldown");
    }

    @Test
    @DisplayName("sin PasivoService inyectado el cooldown sigue siendo el base de 60 min")
    void sinPasivosElCooldownEsElBase() {
        when(descanso.estaDormido(1L)).thenReturn(false);
        Instant ahora = Instant.now();
        // Currado hace 30 min: con el cooldown base de 60 min quedan ~30 min por delante.
        Personaje p = personaje(ahora.minus(Duration.ofMinutes(30)));
        when(personajes.obtenerOCrear(1L)).thenReturn(p);
        TrabajoService svc = new TrabajoService(personajes, economia, usuarios, descanso, carreras);

        var r = svc.trabajar(1L, ahora);
        assertEquals(EstadoWork.EN_COOLDOWN, r.estado());
        assertTrue(r.segundosRestantes() > 29 * 60 && r.segundosRestantes() <= 30 * 60,
                "quedan ~30 min: " + r.segundosRestantes());
    }

    @Test
    @DisplayName("con el tope de COOLDOWN_WORK equipado, 46 min ya no bastan pero 46 con bono sí")
    void elBonoDePasivosRecortaElCooldown() {
        when(descanso.estaDormido(1L)).thenReturn(false);
        Instant ahora = Instant.now();
        // Currado hace 50 min: con 60 min de base seguiría en cooldown; con el tope (45 min) no.
        Personaje p = personaje(ahora.minus(Duration.ofMinutes(50)));
        when(personajes.obtenerOCrear(1L)).thenReturn(p);
        when(descanso.estadoDe(1L)).thenReturn(sinFatiga());
        PasivoService pasivos = mock(PasivoService.class);
        when(pasivos.bonosDe(1L)).thenReturn(Map.of(
                Pasivos.Tipo.COOLDOWN_WORK, 0.25, Pasivos.Tipo.SUELDO, 0.30));
        when(personajes.trabajar(anyLong(), anyInt())).thenReturn(true);
        TrabajoService svc =
                new TrabajoService(personajes, economia, usuarios, descanso, carreras, pasivos);

        assertEquals(EstadoWork.OK, svc.trabajar(1L, ahora).estado());
        // Un solo viaje a los pasivos por turno aunque se usen dos tipos de bono distintos.
        verify(pasivos).bonosDe(1L);
    }

    // ------------------------------------------------------------------ ascensos de carrera

    @Test
    @DisplayName("elegir: el tier de entrada de la rama es libre; un tier superior exige carrera")
    void elegirRespetaLaCarrera() {
        nivelServidor(50); // nivel de servidor sobrado: aquí se prueba el gate de tier, no el de nivel
        when(carreras.tierAlcanzado(anyLong(), anyString())).thenReturn(0);
        assertEquals(ResultadoElegir.OK, svc().elegir(1L, "camarero"), "t1: entrada libre");
        assertEquals(ResultadoElegir.TIER, svc().elegir(1L, "cocinero"),
                "t2 sin carrera: hay que ascender, no elegir");

        when(carreras.tierAlcanzado(1L, "HOSTELERIA")).thenReturn(2);
        assertEquals(ResultadoElegir.OK, svc().elegir(1L, "cocinero"),
                "con t2 alcanzado en la rama, el t2 se elige libremente");
    }

    @Test
    @DisplayName("elegir: la rama de arte entra por t2 (no tiene t1)")
    void entradaDeRamaSinT1() {
        nivelServidor(50);
        when(carreras.tierAlcanzado(anyLong(), anyString())).thenReturn(0);
        assertEquals(ResultadoElegir.OK, svc().elegir(1L, "disenador"),
                "t2 es la entrada de ARTE: libre aunque no haya carrera");
    }

    @Test
    @DisplayName("ascender: valida los 4 requisitos en orden y devuelve qué falta")
    void ascenderValidaRequisitos() {
        nivelServidor(50);
        when(carreras.tierAlcanzado(1L, "HOSTELERIA")).thenReturn(0);

        // Cada requisito que falla, en orden: turnos, estudios, stat, coins.
        assertEquals(EstadoAscenso.TURNOS, svcConPersonaje(camarero(9, 5, 10)).ascender(1L, "cocinero").estado());
        assertEquals(EstadoAscenso.ESTUDIOS, svcConPersonaje(camarero(10, 4, 10)).ascender(1L, "cocinero").estado());
        assertEquals(EstadoAscenso.STAT, svcConPersonaje(camarero(10, 5, 9)).ascender(1L, "cocinero").estado());
        when(economia.gastar(1L, 500L, "ascenso")).thenReturn(false);
        assertEquals(EstadoAscenso.COINS, svcConPersonaje(camarero(10, 5, 10)).ascender(1L, "cocinero").estado());
    }

    @Test
    @DisplayName("ascender OK: quema los coins, fija el tier de la rama y cambia el puesto")
    void ascenderOkQuemaYFija() {
        nivelServidor(50);
        when(carreras.tierAlcanzado(1L, "HOSTELERIA")).thenReturn(0);
        when(economia.gastar(1L, 500L, "ascenso")).thenReturn(true);

        var r = svcConPersonaje(camarero(10, 5, 10)).ascender(1L, "cocinero");
        assertEquals(EstadoAscenso.OK, r.estado());
        verify(economia).gastar(1L, 500L, "ascenso");
        verify(carreras).fijarTier(1L, "HOSTELERIA", 2);
        verify(personajes).fijarTrabajo(1L, "cocinero"); // fijarTrabajo ya resetea la antigüedad
    }

    @Test
    @DisplayName("ascender rechaza destino de otra rama, de tier equivocado o sin trabajo actual")
    void ascenderRechazaDestinosInvalidos() {
        nivelServidor(50);
        when(carreras.tierAlcanzado(anyLong(), anyString())).thenReturn(0);
        assertEquals(EstadoAscenso.SIN_TRABAJO, svcConPersonaje(sinTrabajo()).ascender(1L, "cocinero").estado());
        assertEquals(EstadoAscenso.DESTINO, svcConPersonaje(camarero(99, 99, 99)).ascender(1L, "policia").estado(),
                "policia es de SERVICIOS, no de la rama del camarero");
        assertEquals(EstadoAscenso.DESTINO, svcConPersonaje(camarero(99, 99, 99)).ascender(1L, "panadero").estado(),
                "panadero es t1: no es el siguiente tier");
    }

    @Test
    @DisplayName("ascender en el tope de la rama devuelve TOPE")
    void ascenderEnElTope() {
        nivelServidor(50);
        when(carreras.tierAlcanzado(1L, "ARTE")).thenReturn(3);
        assertEquals(EstadoAscenso.TOPE, svcConPersonaje(conTrabajo("actor", 99, 99, 99)).ascender(1L, "actor").estado());
    }

    @Test
    @DisplayName("ascender con nivel de servidor insuficiente devuelve NIVEL sin cobrar")
    void ascenderSinNivelDeServidor() {
        nivelServidor(0); // cocinero pide nivel 5
        when(carreras.tierAlcanzado(1L, "HOSTELERIA")).thenReturn(0);

        var r = svcConPersonaje(camarero(99, 99, 99)).ascender(1L, "cocinero");
        assertEquals(EstadoAscenso.NIVEL, r.estado());
        verify(economia, never()).gastar(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("opcionesAscenso: sin trabajo no hay nada que ascender")
    void opcionesAscensoSinTrabajo() {
        when(carreras.tierAlcanzado(anyLong(), anyString())).thenReturn(0);
        assertTrue(svcConPersonaje(sinTrabajo()).opcionesAscenso(1L).isEmpty());
    }

    @Test
    @DisplayName("opcionesAscenso: desde camarero (t1) ofrece solo los puestos t2 de su rama")
    void opcionesAscensoDesdeEntrada() {
        when(carreras.tierAlcanzado(1L, "HOSTELERIA")).thenReturn(0);
        var opciones = svcConPersonaje(camarero(0, 0, 0)).opcionesAscenso(1L);
        assertTrue(opciones.stream().anyMatch(t -> "cocinero".equals(t.id())),
                "cocinero es el t2 canónico de HOSTELERIA");
        assertTrue(opciones.stream().allMatch(t -> t.tier() == 2
                        && Ascensos.ramaDe(t.sector()) == Ascensos.Rama.HOSTELERIA),
                "solo puestos del siguiente tier de TU rama");
    }

    @Test
    @DisplayName("opcionesAscenso: en el tope de la rama la lista queda vacía")
    void opcionesAscensoEnTope() {
        when(carreras.tierAlcanzado(1L, "ARTE")).thenReturn(3);
        assertTrue(svcConPersonaje(conTrabajo("actor", 0, 0, 0)).opcionesAscenso(1L).isEmpty(),
                "ARTE topa en t3: no queda tier al que ascender");
    }

    @Test
    @DisplayName("tierAlcanzadoEn: gana el máximo entre la entrada de la rama y la BD")
    void tierAlcanzadoEsElMaximo() {
        when(carreras.tierAlcanzado(1L, "HOSTELERIA")).thenReturn(0);
        assertEquals(1, svc().tierAlcanzadoEn(1L, Ascensos.Rama.HOSTELERIA),
                "sin carrera guardada manda la entrada (t1)");
        when(carreras.tierAlcanzado(1L, "ARTE")).thenReturn(0);
        assertEquals(2, svc().tierAlcanzadoEn(1L, Ascensos.Rama.ARTE),
                "ARTE entra por t2 aunque la BD diga 0");
        when(carreras.tierAlcanzado(1L, "HOSTELERIA")).thenReturn(3);
        assertEquals(3, svc().tierAlcanzadoEn(1L, Ascensos.Rama.HOSTELERIA),
                "la carrera guardada manda cuando supera la entrada");
    }

    @Test
    @DisplayName("infoCarrera: con trabajo trae rama, tier y siguiente salto; sin trabajo, todo neutro")
    void infoCarreraConYSinTrabajo() {
        when(carreras.tierAlcanzado(1L, "HOSTELERIA")).thenReturn(0);
        var info = svcConPersonaje(camarero(7, 3, 8)).infoCarrera(1L);
        assertEquals(Ascensos.Rama.HOSTELERIA, info.rama());
        assertEquals(1, info.tierAlcanzado());
        assertEquals("camarero", info.puestoActual());
        assertEquals(7, info.turnosPuesto());
        assertEquals(3, info.estudios());
        assertEquals(8, info.stat(), "la stat que trae es la dominante de la rama (carisma)");
        assertEquals(Optional.of(2), info.siguiente());
        assertEquals(Ascensos.requisitosPara(2), info.requisitos());

        var sin = svcConPersonaje(sinTrabajo()).infoCarrera(1L);
        assertNull(sin.rama(), "sin trabajo no hay rama");
        assertNull(sin.puestoActual());
        assertNull(sin.requisitos());
        assertTrue(sin.siguiente().isEmpty());
        assertEquals(0, sin.tierAlcanzado());
    }

    @Test
    @DisplayName("dimitir: con trabajo vuelve al paro (fijarTrabajo null resetea la antigüedad)")
    void dimitirDejaEnParo() {
        var r = svcConPersonaje(camarero(20, 5, 10)).dimitir(1L);
        assertEquals(ResultadoDimitir.OK, r);
        // fijarTrabajo(null) pone trabajo=NULL y turnos_puesto=0 en la misma sentencia.
        verify(personajes).fijarTrabajo(1L, null);
    }

    @Test
    @DisplayName("dimitir: en paro devuelve SIN_TRABAJO y no toca nada")
    void dimitirEnParoNoTocaNada() {
        var r = svcConPersonaje(sinTrabajo()).dimitir(1L);
        assertEquals(ResultadoDimitir.SIN_TRABAJO, r);
        verify(personajes, never()).fijarTrabajo(anyLong(), anyString());
        verify(personajes, never()).fijarTrabajo(anyLong(), isNull());
    }

    /** Service con el montaje completo de mocks (sin pasivos: aquí no pintan nada). */
    private TrabajoService svc() {
        return new TrabajoService(personajes, economia, usuarios, descanso, carreras);
    }

    /** Service cuyo {@code obtenerOCrear} devuelve el personaje dado. */
    private TrabajoService svcConPersonaje(Personaje p) {
        when(personajes.obtenerOCrear(1L)).thenReturn(p);
        return svc();
    }

    /** Stubea el usuario 1L con el nivel de servidor indicado (el resto da igual aquí). */
    private void nivelServidor(int nivel) {
        when(usuarios.obtenerOCrear(1L)).thenReturn(
                new UsuarioDiscord(1L, 0, nivel, 0, 0, null, null, false));
    }

    /** Personaje con el puesto dado y los tres valores que miran los ascensos. */
    private static Personaje conTrabajo(String trabajo, int turnosPuesto, int estudios, int carisma) {
        return new Personaje(1L, 5, 5, carisma, 100, 100, trabajo, null, null, null,
                null, 0, null, estudios, turnosPuesto);
    }

    /** Camarero (t1 Hostelería): el punto de partida canónico de los tests de ascenso. */
    private static Personaje camarero(int turnosPuesto, int estudios, int carisma) {
        return conTrabajo("camarero", turnosPuesto, estudios, carisma);
    }

    /** Personaje en paro: sin puesto no hay carrera que ascender. */
    private static Personaje sinTrabajo() {
        return conTrabajo(null, 0, 0, 5);
    }

    /** Personaje de pruebas con trabajo asignado, energía de sobra y el último turno en {@code ultimoWork}. */
    private static Personaje personaje(Instant ultimoWork) {
        String trabajo = Trabajos.CATALOGO.get(0).id();
        return new Personaje(1L, 5, 5, 5, 100, 100, trabajo, ultimoWork, null, null,
                null, 0, null, 0, 0);
    }

    /** Estado de descanso despierto y recién levantado: sin fatiga, para no ensuciar el pago. */
    private static DescansoEstado sinFatiga() {
        return new DescansoEstado(1L, null, Instant.now(), 0, null, null);
    }

    @Test
    void catalogoAmplioYConsistente() {
        assertTrue(Trabajos.CATALOGO.size() >= 25, "catálogo amplio");
        for (Trabajos t : Trabajos.CATALOGO) {
            assertTrue(t.salarioMin() > 0 && t.salarioMax() >= t.salarioMin(),
                    "rango de salario válido en " + t.id());
            assertTrue(t.energiaCoste() > 0 && t.energiaCoste() <= 100,
                    "coste de energía válido en " + t.id());
            assertTrue(Trabajos.porId(t.id()).isPresent(), "porId encuentra " + t.id());
        }
    }

    @Test
    void trabajosDeTierAltoPaganMasQueLosDeEntrada() {
        int maxTier1 = Trabajos.CATALOGO.stream().filter(t -> t.tier() == 1)
                .mapToInt(Trabajos::salarioMax).max().orElse(0);
        int minTier4 = Trabajos.CATALOGO.stream().filter(t -> t.tier() == 4)
                .mapToInt(Trabajos::salarioMin).min().orElse(0);
        assertTrue(minTier4 > maxTier1, "los trabajos de élite pagan más que los de entrada");
    }
}
