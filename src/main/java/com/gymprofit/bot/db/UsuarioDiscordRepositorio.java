package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Repositorio JDBC de {@link UsuarioDiscord} (tabla {@code usuarios_discord}), la tabla
 * troncal de la Fase 1 (XP, economía, racha, idioma). Primer repositorio de la capa
 * {@code db/}; marca el patrón que seguirán el resto: recibe el {@link DataSource} del
 * {@link Database}, una conexión por operación (la devuelve el pool) y consultas parametrizadas.
 */
public final class UsuarioDiscordRepositorio {

    private final DataSource dataSource;

    public UsuarioDiscordRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Busca un usuario por su ID de Discord.
     *
     * @return el usuario, o {@link Optional#empty()} si aún no existe
     */
    public Optional<UsuarioDiscord> buscar(long discordId) {
        String sql = "SELECT discord_id, xp, nivel, coins, racha, ultima_racha_fecha, "
                + "idioma, opt_out_logros FROM usuarios_discord WHERE discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando el usuario " + discordId, e);
        }
    }

    /**
     * Devuelve el usuario, creándolo con valores por defecto si aún no existe. Idempotente:
     * la fila garantiza la integridad referencial del resto de tablas (warns, tickets…).
     */
    public UsuarioDiscord obtenerOCrear(long discordId) {
        String sql = "INSERT INTO usuarios_discord (discord_id) VALUES (?) "
                + "ON DUPLICATE KEY UPDATE discord_id = discord_id";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error creando el usuario " + discordId, e);
        }
        // La fila existe seguro tras el upsert.
        return buscar(discordId).orElseThrow(() ->
                new DatabaseException("El usuario " + discordId + " no existe tras crearlo", null));
    }

    /**
     * Inserta o actualiza (upsert) todos los campos mutables del usuario.
     */
    public void guardar(UsuarioDiscord u) {
        String sql = "INSERT INTO usuarios_discord "
                + "(discord_id, xp, nivel, coins, racha, ultima_racha_fecha, idioma, opt_out_logros) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE xp = VALUES(xp), nivel = VALUES(nivel), "
                + "coins = VALUES(coins), racha = VALUES(racha), "
                + "ultima_racha_fecha = VALUES(ultima_racha_fecha), idioma = VALUES(idioma), "
                + "opt_out_logros = VALUES(opt_out_logros)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, u.discordId());
            ps.setInt(2, u.xp());
            ps.setInt(3, u.nivel());
            ps.setInt(4, u.coins());
            ps.setInt(5, u.racha());
            ps.setObject(6, u.ultimaRachaFecha());
            ps.setString(7, u.idioma());
            ps.setBoolean(8, u.optOutLogros());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error guardando el usuario " + u.discordId(), e);
        }
    }

    /** Mapea la fila actual del {@link ResultSet} a un {@link UsuarioDiscord}. */
    private static UsuarioDiscord mapear(ResultSet rs) throws SQLException {
        return new UsuarioDiscord(
                rs.getLong("discord_id"),
                rs.getInt("xp"),
                rs.getInt("nivel"),
                rs.getInt("coins"),
                rs.getInt("racha"),
                rs.getObject("ultima_racha_fecha", LocalDate.class),
                rs.getString("idioma"),
                rs.getBoolean("opt_out_logros")
        );
    }
}
