package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio JDBC de {@link Sancion} (tabla {@code sanciones}, historial de auditoría). {@code
 * motivo} y {@code nickAnterior} se guardan tal cual llegan (el servicio ya los entrega cifrados).
 */
public final class SancionRepositorio {

    private final DataSource dataSource;

    public SancionRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Registra una sanción (motivo/nick ya cifrados) y devuelve su id generado. */
    public long insertar(long guildId, long discordId, long moderadorId, String tipo,
                         String motivoCifrado, String nickAnteriorCifrado, Long duracionSeg) {
        String sql = "INSERT INTO sanciones "
                + "(guild_id, discord_id, moderador_id, tipo, motivo, nick_anterior, duracion_seg) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql,
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, guildId);
            ps.setLong(2, discordId);
            ps.setLong(3, moderadorId);
            ps.setString(4, tipo);
            ps.setString(5, motivoCifrado);
            ps.setString(6, nickAnteriorCifrado);
            if (duracionSeg == null) {
                ps.setNull(7, Types.BIGINT);
            } else {
                ps.setLong(7, duracionSeg);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error registrando sanción de " + discordId, e);
        }
    }

    /** Historial del usuario en el servidor (más reciente primero), paginado. */
    public List<Sancion> listarPorUsuario(long guildId, long discordId, int limite, int offset) {
        String sql = "SELECT id, guild_id, discord_id, moderador_id, tipo, motivo, nick_anterior, "
                + "duracion_seg, creado_en FROM sanciones WHERE guild_id = ? AND discord_id = ? "
                + "ORDER BY id DESC LIMIT ? OFFSET ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            ps.setLong(2, discordId);
            ps.setInt(3, limite);
            ps.setInt(4, offset);
            try (ResultSet rs = ps.executeQuery()) {
                List<Sancion> sanciones = new ArrayList<>();
                while (rs.next()) {
                    sanciones.add(mapear(rs));
                }
                return sanciones;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listando sanciones de " + discordId, e);
        }
    }

    /** Todas las sanciones del usuario en cualquier servidor (para el export de datos, acotado). */
    public List<Sancion> listarTodasDelUsuario(long discordId, int limite) {
        String sql = "SELECT id, guild_id, discord_id, moderador_id, tipo, motivo, nick_anterior, "
                + "duracion_seg, creado_en FROM sanciones WHERE discord_id = ? ORDER BY id DESC LIMIT ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setInt(2, limite);
            try (ResultSet rs = ps.executeQuery()) {
                List<Sancion> sanciones = new ArrayList<>();
                while (rs.next()) {
                    sanciones.add(mapear(rs));
                }
                return sanciones;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error exportando sanciones de " + discordId, e);
        }
    }

    /** Nº total de sanciones del usuario en el servidor, para paginar. */
    public int contarPorUsuario(long guildId, long discordId) {
        String sql = "SELECT COUNT(*) FROM sanciones WHERE guild_id = ? AND discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            ps.setLong(2, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error contando sanciones de " + discordId, e);
        }
    }

    /** Edita el motivo (ya cifrado) de una sanción por id. Devuelve {@code true} si existía. */
    public boolean actualizarMotivo(long id, String motivoCifrado) {
        String sql = "UPDATE sanciones SET motivo = ? WHERE id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, motivoCifrado);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error actualizando el motivo de la sanción " + id, e);
        }
    }

    /** Borra TODAS las sanciones del usuario en todos los servidores (derecho al olvido). */
    public int borrarTodasDelUsuario(long discordId) {
        String sql = "DELETE FROM sanciones WHERE discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error borrando todas las sanciones de " + discordId, e);
        }
    }

    /** Purga las sanciones anteriores a {@code limite} (retención). Devuelve cuántas se borraron. */
    public int purgarAnterioresA(java.time.Instant limite) {
        String sql = "DELETE FROM sanciones WHERE creado_en < ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, java.sql.Timestamp.from(limite));
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error purgando sanciones antiguas", e);
        }
    }

    /** Borra todas las sanciones del usuario en el servidor (derecho al olvido). */
    public int borrarPorUsuario(long guildId, long discordId) {
        String sql = "DELETE FROM sanciones WHERE guild_id = ? AND discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            ps.setLong(2, discordId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error borrando sanciones de " + discordId, e);
        }
    }

    private static Sancion mapear(ResultSet rs) throws SQLException {
        Long duracion = (Long) rs.getObject("duracion_seg");
        return new Sancion(
                rs.getLong("id"),
                rs.getLong("guild_id"),
                rs.getLong("discord_id"),
                rs.getLong("moderador_id"),
                rs.getString("tipo"),
                rs.getString("motivo"),
                rs.getString("nick_anterior"),
                duracion,
                rs.getTimestamp("creado_en").toInstant());
    }
}
