package com.gymprofit.bot.services;

import com.gymprofit.bot.db.DescansoEstado;
import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.TrabajoService.EstadoWork;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica el pago por turno, el bloqueo por sueño y la coherencia del catálogo de trabajos. */
class TrabajoServiceTest {

    private final PersonajeRepositorio personajes = mock(PersonajeRepositorio.class);
    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final DescansoService descanso = mock(DescansoService.class);

    @Test
    void dormidoNoSePuedeCurrar() {
        when(descanso.estaDormido(1L)).thenReturn(true);
        TrabajoService svc = new TrabajoService(personajes, economia, usuarios, descanso);

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
        TrabajoService svc = new TrabajoService(personajes, economia, usuarios, descanso);

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
        TrabajoService svc = new TrabajoService(personajes, economia, usuarios, descanso, pasivos);

        assertEquals(EstadoWork.OK, svc.trabajar(1L, ahora).estado());
        // Un solo viaje a los pasivos por turno aunque se usen dos tipos de bono distintos.
        verify(pasivos).bonosDe(1L);
    }

    /** Personaje de pruebas con trabajo asignado, energía de sobra y el último turno en {@code ultimoWork}. */
    private static Personaje personaje(Instant ultimoWork) {
        String trabajo = Trabajos.CATALOGO.get(0).id();
        return new Personaje(1L, 5, 5, 5, 100, 100, trabajo, ultimoWork, null, null,
                null, 0, null, 0);
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
