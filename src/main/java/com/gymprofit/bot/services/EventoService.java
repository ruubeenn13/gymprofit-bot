package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EventoServidor;
import com.gymprofit.bot.db.EventoServidorRepositorio;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Lógica del reto de la semana y del próximo evento del servidor (contadores «🎯 Reto» y
 * «⏳ Evento»). El staff los fija con {@code /reto} y {@code /evento}; el job de estadísticas lee de
 * aquí para renombrar los canales.
 */
public final class EventoService {

    /** Formato de fecha que acepta {@code /evento}: {@code 2026-07-20 18:30}. */
    public static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** Zona en la que se interpreta la fecha que introduce el staff (comunidad hispana). */
    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
    /** Longitudes máximas (encajan en el nombre del canal, límite 100). */
    private static final int MAX_RETO = 80;
    private static final int MAX_NOMBRE_EVENTO = 60;

    private final EventoServidorRepositorio repositorio;

    public EventoService(EventoServidorRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    /** Reto/evento actuales del servidor. */
    public EventoServidor obtener(long guildId) {
        return repositorio.obtener(guildId);
    }

    /** Fija el reto de la semana (recortado a {@value #MAX_RETO} caracteres). */
    public void fijarReto(long guildId, String texto) {
        repositorio.guardarReto(guildId, recortar(texto, MAX_RETO));
    }

    /**
     * Fija el próximo evento; la fecha se interpreta en la zona horaria peninsular. Devuelve el
     * instante en epoch (segundos), útil para mostrar un timestamp dinámico en la confirmación.
     */
    public long fijarEvento(long guildId, String nombre, LocalDateTime fecha) {
        long finEpoch = fecha.atZone(ZONA).toEpochSecond();
        repositorio.guardarEvento(guildId, recortar(nombre, MAX_NOMBRE_EVENTO), finEpoch);
        return finEpoch;
    }

    /**
     * Parsea la fecha que introduce el staff ({@link #FORMATO}). Devuelve {@link Optional#empty()}
     * si el formato no es válido, para que el comando avise sin lanzar excepción.
     */
    public static Optional<LocalDateTime> parsearFecha(String texto) {
        try {
            return Optional.of(LocalDateTime.parse(texto.trim(), FORMATO));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static String recortar(String s, int max) {
        String limpio = s.strip();
        return limpio.length() > max ? limpio.substring(0, max) : limpio;
    }
}
