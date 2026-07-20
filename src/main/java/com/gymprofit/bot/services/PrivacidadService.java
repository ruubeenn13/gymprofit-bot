package com.gymprofit.bot.services;

import com.gymprofit.bot.db.DescansoEstado;
import com.gymprofit.bot.db.DescansoRepositorio;
import com.gymprofit.bot.db.Sancion;
import com.gymprofit.bot.db.SancionRepositorio;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.db.Warn;
import com.gymprofit.bot.db.WarnRepositorio;
import com.gymprofit.bot.util.Cifrador;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.util.List;
import java.util.Optional;

/**
 * Herramientas de privacidad (RGPD): exportar todo lo que el bot guarda de un usuario
 * (acceso/portabilidad) y borrarlo por completo (derecho al olvido). Los motivos cifrados se
 * descifran solo para el propio usuario en el export.
 *
 * <p>El borrado no enumera todas las tablas del jugador: basta con {@code usuarios_discord}, porque
 * el resto (incluido {@code descanso}) cuelga de ella con {@code ON DELETE CASCADE}.
 */
public final class PrivacidadService {

    /** Tope de filas por colección en el export (evita respuestas gigantes). */
    private static final int MAX_EXPORT = 500;

    /** Resultado de un borrado: cuántas filas se eliminaron por tipo. */
    public record ResultadoBorrado(boolean existiaUsuario, int sanciones) {
    }

    private final UsuarioDiscordRepositorio usuarios;
    private final WarnRepositorio warns;
    private final SancionRepositorio sanciones;
    private final DescansoRepositorio descanso;
    private final Cifrador cifrador;

    public PrivacidadService(UsuarioDiscordRepositorio usuarios, WarnRepositorio warns,
                             SancionRepositorio sanciones, DescansoRepositorio descanso,
                             Cifrador cifrador) {
        this.usuarios = usuarios;
        this.warns = warns;
        this.sanciones = sanciones;
        this.descanso = descanso;
        this.cifrador = cifrador;
    }

    /**
     * Borra todos los datos del usuario en {@code gymprofit_bot}: su fila de gamificación (que por
     * {@code CASCADE} elimina sus avisos) y todas sus sanciones. Idempotente.
     */
    public ResultadoBorrado borrar(long discordId) {
        int sancionesBorradas = sanciones.borrarTodasDelUsuario(discordId);
        boolean existia = usuarios.borrar(discordId); // cascada: warns
        return new ResultadoBorrado(existia, sancionesBorradas);
    }

    /** Construye un JSON con todo lo que el bot guarda del usuario (motivos descifrados). */
    public DataObject exportar(long discordId) {
        DataObject raiz = DataObject.empty().put("discord_id", Long.toUnsignedString(discordId));

        Optional<UsuarioDiscord> u = usuarios.buscar(discordId);
        if (u.isPresent()) {
            UsuarioDiscord d = u.get();
            raiz.put("gamificacion", DataObject.empty()
                    .put("xp", d.xp()).put("nivel", d.nivel()).put("coins", d.coins())
                    .put("racha", d.racha()).put("idioma", d.idioma())
                    .put("opt_out_logros", d.optOutLogros()));
        }

        DataArray avisos = DataArray.empty();
        for (Warn w : warns.listarPorUsuario(discordId, MAX_EXPORT, 0)) {
            avisos.add(DataObject.empty()
                    .put("id", w.id())
                    .put("motivo", descifrar(w.motivo()))
                    .put("activo", w.activo())
                    .put("fecha", w.creadoEn().toString()));
        }
        raiz.put("avisos", avisos);

        DataArray sancionesJson = DataArray.empty();
        for (Sancion s : sanciones.listarTodasDelUsuario(discordId, MAX_EXPORT)) {
            sancionesJson.add(DataObject.empty()
                    .put("id", s.id())
                    .put("guild_id", Long.toUnsignedString(s.guildId()))
                    .put("tipo", s.tipo())
                    .put("motivo", descifrar(s.motivo()))
                    .put("duracion_seg", s.duracionSeg())
                    .put("fecha", s.creadoEn().toString()));
        }
        raiz.put("sanciones", sancionesJson);

        // El descanso también es dato del usuario: derecho de acceso y portabilidad (ADR-009). Se
        // consulta con buscar() y no con obtenerOCrear(): pedir tus datos no puede crearte una fila.
        descanso.buscar(discordId).ifPresent(d -> raiz.put("descanso", DataObject.empty()
                .put("dormido_desde", d.dormidoDesde() == null ? null : d.dormidoDesde().toString())
                .put("ultimo_despertar",
                        d.ultimoDespertar() == null ? null : d.ultimoDespertar().toString())
                .put("consumidos_hoy", d.consumidosHoy())
                .put("dia_consumos", d.diaConsumos() == null ? null : d.diaConsumos().toString())));
        return raiz;
    }

    private String descifrar(String cifrado) {
        if (cifrado == null || !cifrador.habilitado()) {
            return null;
        }
        try {
            return cifrador.descifrar(cifrado);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
