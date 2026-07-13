package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * Repositorio JDBC de {@link Sugerencia} (tabla {@code sugerencias}). Sigue el patrón del resto.
 */
public final class SugerenciaRepositorio {

    private final DataSource dataSource;

    public SugerenciaRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Crea una sugerencia (estado PENDIENTE) y devuelve su id. */
    public long crear(long discordId, long mensajeId, String contenido) {
        String sql = "INSERT INTO sugerencias (discord_id, mensaje_id, contenido) VALUES (?, ?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, discordId);
            ps.setLong(2, mensajeId);
            ps.setString(3, contenido);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error creando sugerencia de " + discordId, e);
        }
    }

    /** Sugerencia por id. */
    public Optional<Sugerencia> buscar(long id) {
        String sql = "SELECT id, discord_id, mensaje_id, contenido, estado FROM sugerencias WHERE id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando la sugerencia " + id, e);
        }
    }

    /** Resuelve la sugerencia (ACEPTADA/RECHAZADA). Devuelve {@code true} si existía y estaba pendiente. */
    public boolean resolver(long id, String estado, long resueltoPor) {
        String sql = "UPDATE sugerencias SET estado = ?, resuelto_por = ?, resuelto_en = NOW() "
                + "WHERE id = ? AND estado = 'PENDIENTE'";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, estado);
            ps.setLong(2, resueltoPor);
            ps.setLong(3, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error resolviendo la sugerencia " + id, e);
        }
    }

    private static Sugerencia mapear(ResultSet rs) throws SQLException {
        return new Sugerencia(rs.getLong("id"), rs.getLong("discord_id"), rs.getLong("mensaje_id"),
                rs.getString("contenido"), rs.getString("estado"));
    }
}
