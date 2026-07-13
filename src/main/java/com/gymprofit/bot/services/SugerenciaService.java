package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Sugerencia;
import com.gymprofit.bot.db.SugerenciaRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.Optional;

/**
 * Lógica de las sugerencias: alta (garantiza la fila de usuario para la FK), consulta y resolución.
 * La creación del post del foro y su etiquetado los hace el comando; aquí va la persistencia.
 */
public final class SugerenciaService {

    /** Longitud máxima del contenido (columna VARCHAR(1000)). */
    public static final int MAX_CONTENIDO = 1000;

    private final SugerenciaRepositorio sugerencias;
    private final UsuarioDiscordRepositorio usuarios;

    public SugerenciaService(SugerenciaRepositorio sugerencias, UsuarioDiscordRepositorio usuarios) {
        this.sugerencias = sugerencias;
        this.usuarios = usuarios;
    }

    /** Registra una sugerencia pendiente y devuelve su id. */
    public long crear(long discordId, long mensajeId, String contenido) {
        usuarios.obtenerOCrear(discordId);
        return sugerencias.crear(discordId, mensajeId,
                contenido.length() > MAX_CONTENIDO ? contenido.substring(0, MAX_CONTENIDO) : contenido);
    }

    public Optional<Sugerencia> buscar(long id) {
        return sugerencias.buscar(id);
    }

    /** Marca la sugerencia como ACEPTADA/RECHAZADA. {@code true} si existía y estaba pendiente. */
    public boolean resolver(long id, String estado, long resueltoPor) {
        return sugerencias.resolver(id, estado, resueltoPor);
    }
}
