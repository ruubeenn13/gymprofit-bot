package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio JDBC de {@link Warn} (tabla {@code warns}). El {@code motivo} se guarda tal cual llega
 * (el servicio ya lo entrega cifrado). Sigue el patrón del resto de repos: conexión por operación y
 * consultas parametrizadas.
 */
public final class WarnRepositorio {

    private final DataSource dataSource;

    public WarnRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Inserta un aviso (motivo ya cifrado) y devuelve su id generado. */
    public long insertar(long discordId, long moderadorId, String motivoCifrado) {
        String sql = "INSERT INTO warns (discord_id, moderador_id, motivo) VALUES (?, ?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, discordId);
            ps.setLong(2, moderadorId);
            ps.setString(3, motivoCifrado);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error insertando warn de " + discordId, e);
        }
    }

    /** Nº de avisos activos del usuario (base del escalado). */
    public int contarActivos(long discordId) {
        String sql = "SELECT COUNT(*) FROM warns WHERE discord_id = ? AND activo = TRUE";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error contando warns de " + discordId, e);
        }
    }

    /** Avisos del usuario (más recientes primero), paginado con {@code LIMIT/OFFSET}. */
    public List<Warn> listarPorUsuario(long discordId, int limite, int offset) {
        String sql = "SELECT id, discord_id, moderador_id, motivo, activo, creado_en "
                + "FROM warns WHERE discord_id = ? ORDER BY id DESC LIMIT ? OFFSET ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setInt(2, limite);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                List<Warn> avisos = new ArrayList<>();
                while (rs.next()) {
                    avisos.add(mapear(rs));
                }
                return avisos;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listando warns de " + discordId, e);
        }
    }

    /** Nº total de avisos del usuario (activos e inactivos), para paginar. */
    public int contarTotales(long discordId) {
        String sql = "SELECT COUNT(*) FROM warns WHERE discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error contando warns totales de " + discordId, e);
        }
    }

    /** Revoca (soft-delete) un aviso por id. Devuelve {@code true} si existía y estaba activo. */
    public boolean revocar(long id) {
        String sql = "UPDATE warns SET activo = FALSE WHERE id = ? AND activo = TRUE";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error revocando el warn " + id, e);
        }
    }

    /** Revoca todos los avisos activos del usuario. Devuelve cuántos se revocaron. */
    public int revocarTodos(long discordId) {
        String sql = "UPDATE warns SET activo = FALSE WHERE discord_id = ? AND activo = TRUE";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error revocando los warns de " + discordId, e);
        }
    }

    /** Purga los avisos ya revocados anteriores a {@code limite} (retención). Devuelve cuántos. */
    public int purgarRevocadosAnterioresA(java.time.Instant limite) {
        String sql = "DELETE FROM warns WHERE activo = FALSE AND creado_en < ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, java.sql.Timestamp.from(limite));
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error purgando avisos revocados antiguos", e);
        }
    }

    private static Warn mapear(ResultSet rs) throws SQLException {
        return new Warn(
                rs.getLong("id"),
                rs.getLong("discord_id"),
                rs.getLong("moderador_id"),
                rs.getString("motivo"),
                rs.getBoolean("activo"),
                rs.getTimestamp("creado_en").toInstant());
    }
}
