package com.gymprofit.bot.services;

import com.gymprofit.bot.db.DescansoEstado;
import com.gymprofit.bot.db.DescansoRepositorio;
import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Lógica de {@code /descansar}. Dormir es un <b>estado</b>: al acostarse se guarda el instante y al
 * despertar se gana energía proporcional al tiempo dormido de verdad, según la cama ({@link Camas}).
 * La siesta no necesita comando propio: es dormir poco y ganar poco.
 *
 * <p>El instante se pasa como parámetro ({@code Instant ahora}), igual que en
 * {@code TrabajoService#trabajar}, para poder fijar el reloj en los tests.
 *
 * <p>El corazón del sistema ({@link #energiaGanada}, {@link #bonoResistencia} y
 * {@link #tieneFatiga}) es <b>puro y estático</b>: se testea sin BD ni JDA. El resto son los métodos
 * de comportamiento, que orquestan los repositorios.
 */
public final class DescansoService {

    /** Dormir más de esto no suma: dormir de más no descansa. */
    public static final int MAX_HORAS = 9;
    /** Por debajo de esta salud se descansa peor: estás malo. */
    public static final int SALUD_BAJA = 30;
    /** Multiplicador de energía cuando la salud está por debajo de {@link #SALUD_BAJA}. */
    public static final double PENAL_SALUD = 0.5;
    /** Sin dormir más de esto, aparece la fatiga. */
    public static final Duration FATIGA = Duration.ofHours(24);
    /** Bono de descanso por punto de resistencia (+1 %). */
    public static final double BONO_RESISTENCIA = 0.01;
    /** Techo del bono de resistencia (+50 %), para que no se dispare al subir stats sin límite. */
    public static final double BONO_RESISTENCIA_MAX = 0.5;

    /** Valor de {@code descanso.cama_pagada} cuando se paga hotel. */
    public static final String HOTEL_PAGADO = "hotel";

    /**
     * Máximo de consumibles al día (<b>saciedad</b>). Sin este tope, quien tiene coins se salta el
     * freno de energía a base de batidos y el descanso deja de importar.
     */
    public static final int MAX_CONSUMOS_DIA = 3;
    /** El día natural del bot, el mismo que usan {@code /daily} y el interés del banco. */
    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    /** Estado del intento de dormir. */
    public enum EstadoDormir { OK, YA_DORMIDO, SIN_SALDO }

    /** Estado del intento de despertar. */
    public enum EstadoDespertar { OK, NO_DORMIDO }

    /**
     * Resultado de acostarse.
     *
     * @param estado resultado
     * @param cama   dónde se ha acostado (para el mensaje), o {@code null} si no {@code OK}
     */
    public record ResultadoDormir(EstadoDormir estado, Camas cama) {
    }

    /**
     * Resultado de despertar.
     *
     * @param estado          resultado
     * @param energiaGanada   energía sumada
     * @param minutosDormidos minutos que ha dormido
     * @param cama            dónde durmió
     */
    public record ResultadoDespertar(EstadoDespertar estado, int energiaGanada,
                                     long minutosDormidos, Camas cama) {
    }

    /**
     * Vista de {@code /descansar estado}.
     *
     * @param dormido         si está durmiendo ahora
     * @param minutosDormidos minutos que lleva dormido (0 si está despierto)
     * @param cama            su mejor cama
     * @param fatiga          si arrastra fatiga (&gt;24 h sin dormir)
     */
    public record Vista(boolean dormido, long minutosDormidos, Camas cama, boolean fatiga) {
    }

    private final DescansoRepositorio descanso;
    private final PersonajeRepositorio personajes;
    private final InventarioRepositorio inventario;
    private final EconomiaRepositorio economia;
    private final UsuarioDiscordRepositorio usuarios;

    public DescansoService(DescansoRepositorio descanso, PersonajeRepositorio personajes,
                           InventarioRepositorio inventario, EconomiaRepositorio economia,
                           UsuarioDiscordRepositorio usuarios) {
        this.descanso = descanso;
        this.personajes = personajes;
        this.inventario = inventario;
        this.economia = economia;
        this.usuarios = usuarios;
    }

    /** Acuesta al jugador. En hotel cobra {@link Camas#PRECIO_HOTEL} por adelantado. */
    public ResultadoDormir dormir(long discordId, boolean hotel, Instant ahora) {
        usuarios.obtenerOCrear(discordId);
        DescansoEstado estado = descanso.obtenerOCrear(discordId);
        if (estado.dormido()) {
            return new ResultadoDormir(EstadoDormir.YA_DORMIDO, null);
        }
        Camas cama;
        if (hotel) {
            // Se cobra al acostarse, no al despertar: si no, dormiría gratis quien no despierte.
            if (!economia.gastar(discordId, Camas.PRECIO_HOTEL, "Noche de hotel")) {
                return new ResultadoDormir(EstadoDormir.SIN_SALDO, null);
            }
            cama = Camas.HOTEL;
        } else {
            cama = Camas.mejorDe(inventario.listar(discordId));
        }
        descanso.acostar(discordId, ahora, hotel ? HOTEL_PAGADO : null);
        return new ResultadoDormir(EstadoDormir.OK, cama);
    }

    /**
     * Levanta al jugador y le suma la energía del descanso.
     *
     * <p>La cama propia se resuelve <b>al despertar</b> (sale del inventario). Efecto secundario
     * aceptado: si compras una casa mientras duermes, despiertas mejor. Es indulgente y evita
     * guardar la cama en BD. El hotel sí se guarda al acostarse ({@code cama_pagada}): no se posee,
     * así que no podría deducirse del inventario.
     */
    public ResultadoDespertar despertar(long discordId, Instant ahora) {
        usuarios.obtenerOCrear(discordId);
        DescansoEstado estado = descanso.obtenerOCrear(discordId);
        if (!estado.dormido()) {
            return new ResultadoDespertar(EstadoDespertar.NO_DORMIDO, 0, 0, null);
        }
        Personaje p = personajes.obtenerOCrear(discordId);
        Camas cama = HOTEL_PAGADO.equals(estado.camaPagada())
                ? Camas.HOTEL : Camas.mejorDe(inventario.listar(discordId));
        long minutos = Duration.between(estado.dormidoDesde(), ahora).toMinutes();
        int ganada = energiaGanada(minutos, cama, p.salud(), p.energia(), p.resistencia());
        if (ganada > 0) {
            personajes.sumarEnergiaConTope(discordId, ganada, cama.tope());
        }
        descanso.levantar(discordId, ahora);
        return new ResultadoDespertar(EstadoDespertar.OK, ganada, minutos, cama);
    }

    /** Si el jugador está durmiendo ahora mismo (lo consultan trabajo, combate y minería). */
    public boolean estaDormido(long discordId) {
        return descanso.obtenerOCrear(discordId).dormido();
    }

    /**
     * Si al jugador le caben más consumibles hoy (saciedad). Lo consulta {@code ItemService} antes
     * de aplicar el efecto.
     *
     * <p>El contador vive en la fila de descanso junto al día al que pertenece: si ese día no es
     * hoy, el contador es de ayer y no cuenta (el reinicio es perezoso, sin job de medianoche).
     */
    public boolean puedeConsumir(long discordId) {
        DescansoEstado e = descanso.obtenerOCrear(discordId);
        LocalDate hoy = LocalDate.now(ZONA);
        return !hoy.equals(e.diaConsumos()) || e.consumidosHoy() < MAX_CONSUMOS_DIA;
    }

    /** Apunta un consumible del día. Se llama <b>solo tras un uso con éxito</b>. */
    public void registrarConsumo(long discordId) {
        descanso.registrarConsumo(discordId, LocalDate.now(ZONA));
    }

    /**
     * Estado crudo de descanso, para quien necesite calcular la fatiga por su cuenta (p. ej.
     * {@code TrabajoService}, que recorta el sueldo).
     *
     * <p>Hace falta porque {@link #tieneFatiga} es estático y puro: recibe el estado ya leído en vez
     * de ir a la BD, y alguien tiene que dárselo.
     */
    public DescansoEstado estadoDe(long discordId) {
        return descanso.obtenerOCrear(discordId);
    }

    /** Vista de {@code /descansar estado}. */
    public Vista estado(long discordId, Instant ahora) {
        usuarios.obtenerOCrear(discordId);
        DescansoEstado e = descanso.obtenerOCrear(discordId);
        long minutos = e.dormido() ? Duration.between(e.dormidoDesde(), ahora).toMinutes() : 0;
        return new Vista(e.dormido(), minutos, Camas.mejorDe(inventario.listar(discordId)),
                tieneFatiga(e, ahora));
    }

    /**
     * Si el jugador arrastra fatiga: más de {@link #FATIGA} sin dormir. <b>Puro</b>.
     *
     * <p>Quien nunca ha dormido no tiene fatiga (no se castiga al que acaba de empezar), y quien
     * está durmiendo tampoco: ya se está curando.
     */
    public static boolean tieneFatiga(DescansoEstado estado, Instant ahora) {
        if (estado.dormido() || estado.ultimoDespertar() == null) {
            return false;
        }
        return Duration.between(estado.ultimoDespertar(), ahora).compareTo(FATIGA) > 0;
    }

    /**
     * Energía que se gana al despertar. <b>Puro</b>: el corazón testeable del sistema.
     *
     * <p>La <b>resistencia</b> acelera el descanso (+1 % por punto, techo +50 %): las stats crecen sin
     * límite y sin esto el descanso se quedaría plano al progresar. Aun así <b>no rompe el tope de la
     * cama</b>: por muy en forma que estés, en el suelo no pasas de 60.
     *
     * @param minutos        minutos dormidos (se recortan a {@link #MAX_HORAS})
     * @param cama           dónde se ha dormido (energía/hora y tope)
     * @param salud          salud actual (por debajo de {@link #SALUD_BAJA} se descansa a la mitad)
     * @param energiaActual  energía antes de dormir (el tope de la cama la incluye)
     * @param resistencia    resistencia del personaje (bono al descanso)
     * @return energía a sumar, nunca negativa
     */
    public static int energiaGanada(long minutos, Camas cama, int salud, int energiaActual,
                                    int resistencia) {
        // Ojo: "tope" aquí serían minutos, pero cama.tope() es energía. Nombres distintos a propósito.
        long minutosContados = Math.min(minutos, (long) MAX_HORAS * 60);
        double bruta = minutosContados / 60.0 * cama.energiaHora();
        bruta *= 1 + bonoResistencia(resistencia);
        if (salud < SALUD_BAJA) {
            bruta *= PENAL_SALUD;
        }
        int ganada = (int) Math.round(bruta);
        // El tope es de energía TOTAL, no de ganancia: con 50 y tope 60 solo caben 10 más.
        int cabe = cama.tope() - energiaActual;
        return Math.max(0, Math.min(ganada, cabe));
    }

    /** Bono de descanso por resistencia: +1 % por punto, con techo en +50 %. <b>Puro</b>. */
    public static double bonoResistencia(int resistencia) {
        return Math.min(BONO_RESISTENCIA_MAX, Math.max(0, resistencia) * BONO_RESISTENCIA);
    }
}
