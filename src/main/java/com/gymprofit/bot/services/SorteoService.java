package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Sorteo;
import com.gymprofit.bot.db.SorteoRepositorio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Lógica de los sorteos: alta, consulta de vencidos, cierre y elección de ganadores al azar. Las
 * acciones sobre Discord (publicar, leer reacciones, anunciar) las hace el job/comando; aquí vive la
 * lógica de datos y el sorteo de ganadores (puro, testeable).
 */
public final class SorteoService {

    /** Longitud máxima del premio (encaja en la columna). */
    public static final int MAX_PREMIO = 200;

    private final SorteoRepositorio repositorio;

    public SorteoService(SorteoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    /** Da de alta un sorteo y devuelve su id. */
    public long crear(long guildId, long canalId, long mensajeId, String premio,
                      int numGanadores, long creadorId, long finEpoch) {
        return repositorio.insertar(guildId, canalId, mensajeId,
                premio.length() > MAX_PREMIO ? premio.substring(0, MAX_PREMIO) : premio,
                numGanadores, creadorId, finEpoch);
    }

    /** Sorteos activos ya vencidos (para el job). */
    public List<Sorteo> vencidos(long ahoraEpoch) {
        return repositorio.vencidos(ahoraEpoch);
    }

    /** Marca un sorteo como resuelto. */
    public void cerrar(long id) {
        repositorio.cerrar(id);
    }

    /**
     * Elige hasta {@code cuantos} ganadores al azar y sin repetición entre los participantes.
     * Devuelve lista vacía si no hay participantes.
     */
    public static List<Long> elegirGanadores(List<Long> participantes, int cuantos) {
        return elegirGanadores(participantes, cuantos, new Random());
    }

    /** Variante con {@link Random} inyectable (para tests deterministas). */
    static List<Long> elegirGanadores(List<Long> participantes, int cuantos, Random aleatorio) {
        if (participantes.isEmpty() || cuantos <= 0) {
            return List.of();
        }
        List<Long> copia = new ArrayList<>(participantes);
        Collections.shuffle(copia, aleatorio);
        return List.copyOf(copia.subList(0, Math.min(cuantos, copia.size())));
    }
}
