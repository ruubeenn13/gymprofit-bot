package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Sancion;
import com.gymprofit.bot.db.SancionRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.db.Warn;
import com.gymprofit.bot.db.WarnRepositorio;
import com.gymprofit.bot.util.Cifrador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Lógica de moderación: registra avisos y sanciones, cifra el texto libre (motivos, apodos) y decide
 * el <b>escalado automático</b> por acumulación de avisos activos. Las acciones sobre Discord
 * (timeout, ban, rol…) las ejecuta cada comando; este servicio solo persiste y decide.
 *
 * <p>Escalado (avisos activos): {@value #UMBRAL_TIMEOUT_1H} → timeout 1 h; {@value #UMBRAL_TIMEOUT_24H}
 * → timeout 24 h; {@value #UMBRAL_BAN} → ban.</p>
 */
public final class ModeracionService {

    private static final Logger log = LoggerFactory.getLogger(ModeracionService.class);

    /** Umbrales de avisos activos que disparan cada escalón. */
    public static final int UMBRAL_TIMEOUT_1H = 3;
    public static final int UMBRAL_TIMEOUT_24H = 5;
    public static final int UMBRAL_BAN = 7;

    /** Duraciones de los timeouts de escalado, en segundos. */
    public static final long TIMEOUT_1H_SEG = 3600L;
    public static final long TIMEOUT_24H_SEG = 86_400L;

    /** Acción de escalado que el comando debe aplicar tras un aviso. */
    public enum AccionEscalado { NINGUNA, TIMEOUT_1H, TIMEOUT_24H, BAN }

    /**
     * Resultado de {@link #avisar}: id del aviso, nº de avisos activos y acción de escalado a aplicar.
     */
    public record ResultadoAviso(long warnId, int warnsActivos, AccionEscalado accion) {
    }

    private final WarnRepositorio warns;
    private final SancionRepositorio sanciones;
    private final UsuarioDiscordRepositorio usuarios;
    private final Cifrador cifrador;

    public ModeracionService(WarnRepositorio warns, SancionRepositorio sanciones,
                             UsuarioDiscordRepositorio usuarios, Cifrador cifrador) {
        this.warns = warns;
        this.sanciones = sanciones;
        this.usuarios = usuarios;
        this.cifrador = cifrador;
    }

    /**
     * Registra un aviso (motivo cifrado), lo anota también en el historial de sanciones, cuenta los
     * avisos activos y devuelve la acción de escalado a aplicar.
     */
    public ResultadoAviso avisar(long guildId, long usuarioId, long moderadorId, String motivo) {
        usuarios.obtenerOCrear(usuarioId); // garantiza la fila (FK de warns)
        String cif = cifrarTexto(motivo);
        long warnId = warns.insertar(usuarioId, moderadorId, cif);
        sanciones.insertar(guildId, usuarioId, moderadorId, "WARN", cif, null, null);
        int activos = warns.contarActivos(usuarioId);
        return new ResultadoAviso(warnId, activos, escalado(activos));
    }

    /** Registra una sanción cualquiera en el historial (cifrando motivo/apodo). */
    public void registrar(long guildId, long usuarioId, long moderadorId, String tipo,
                          String motivo, String nickAnterior, Long duracionSeg) {
        sanciones.insertar(guildId, usuarioId, moderadorId, tipo,
                cifrarTexto(motivo), cifrarTexto(nickAnterior), duracionSeg);
    }

    /** Avisos del usuario (con el motivo ya descifrado), paginado. */
    public List<Warn> listarWarns(long usuarioId, int limite, int offset) {
        return warns.listarPorUsuario(usuarioId, limite, offset).stream()
                .map(w -> new Warn(w.id(), w.discordId(), w.moderadorId(),
                        descifrarTexto(w.motivo()), w.activo(), w.creadoEn()))
                .toList();
    }

    public int contarWarnsActivos(long usuarioId) {
        return warns.contarActivos(usuarioId);
    }

    public int contarWarnsTotales(long usuarioId) {
        return warns.contarTotales(usuarioId);
    }

    /** Revoca un aviso por id. {@code true} si existía y estaba activo. */
    public boolean revocarWarn(long id) {
        return warns.revocar(id);
    }

    /** Revoca todos los avisos activos del usuario; devuelve cuántos. */
    public int limpiarWarns(long usuarioId) {
        return warns.revocarTodos(usuarioId);
    }

    /** Historial completo de sanciones del usuario (motivos/apodos descifrados), paginado. */
    public List<Sancion> modlogs(long guildId, long usuarioId, int limite, int offset) {
        return sanciones.listarPorUsuario(guildId, usuarioId, limite, offset).stream()
                .map(s -> new Sancion(s.id(), s.guildId(), s.discordId(), s.moderadorId(), s.tipo(),
                        descifrarTexto(s.motivo()), descifrarTexto(s.nickAnterior()),
                        s.duracionSeg(), s.creadoEn()))
                .toList();
    }

    public int contarSanciones(long guildId, long usuarioId) {
        return sanciones.contarPorUsuario(guildId, usuarioId);
    }

    /** Edita el motivo de una sanción del historial. {@code true} si existía. */
    public boolean editarMotivo(long sancionId, String motivo) {
        return sanciones.actualizarMotivo(sancionId, cifrarTexto(motivo));
    }

    /** Decide el escalón según el nº de avisos activos. */
    public static AccionEscalado escalado(int avisosActivos) {
        if (avisosActivos >= UMBRAL_BAN) {
            return AccionEscalado.BAN;
        }
        if (avisosActivos >= UMBRAL_TIMEOUT_24H) {
            return AccionEscalado.TIMEOUT_24H;
        }
        if (avisosActivos >= UMBRAL_TIMEOUT_1H) {
            return AccionEscalado.TIMEOUT_1H;
        }
        return AccionEscalado.NINGUNA;
    }

    /** Cifra el texto si hay clave; si no, no persiste el texto (degradado seguro). */
    private String cifrarTexto(String texto) {
        if (texto == null) {
            return null;
        }
        if (!cifrador.habilitado()) {
            log.warn("BOT_CRYPTO_KEY no configurada: no se persiste el texto libre de la sanción");
            return null;
        }
        return cifrador.cifrar(texto);
    }

    /** Descifra el texto; devuelve {@code null} si no hay clave o el dato está corrupto. */
    private String descifrarTexto(String cifrado) {
        if (cifrado == null || !cifrador.habilitado()) {
            return null;
        }
        try {
            return cifrador.descifrar(cifrado);
        } catch (RuntimeException e) {
            log.warn("No se pudo descifrar un texto de sanción (¿clave cambiada?)");
            return null;
        }
    }
}
