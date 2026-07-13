package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * Repositorio JDBC de {@link Ticket} (tabla {@code tickets}). Sigue el patrón del resto de repos.
 */
public final class TicketRepositorio {

    private final DataSource dataSource;

    public TicketRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Abre un ticket (estado ABIERTO) y devuelve su id. */
    public long abrir(long discordId, long canalId, String asunto) {
        String sql = "INSERT INTO tickets (discord_id, canal_id, asunto) VALUES (?, ?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, discordId);
            ps.setLong(2, canalId);
            ps.setString(3, asunto);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error abriendo ticket de " + discordId, e);
        }
    }

    /** {@code true} si el usuario ya tiene un ticket abierto (para no duplicar). */
    public boolean tieneAbierto(long discordId) {
        String sql = "SELECT COUNT(*) FROM tickets WHERE discord_id = ? AND estado = 'ABIERTO'";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error comprobando tickets de " + discordId, e);
        }
    }

    /** Ticket asociado a un canal, si existe. */
    public Optional<Ticket> porCanal(long canalId) {
        String sql = "SELECT id, discord_id, canal_id, estado FROM tickets WHERE canal_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, canalId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Ticket(rs.getLong("id"), rs.getLong("discord_id"),
                        rs.getLong("canal_id"), rs.getString("estado"))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando ticket del canal " + canalId, e);
        }
    }

    /** Cierra el ticket guardando su transcripción. */
    public void cerrar(long id, String transcript) {
        String sql = "UPDATE tickets SET estado = 'CERRADO', transcript = ?, cerrado_en = NOW() "
                + "WHERE id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, transcript);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error cerrando el ticket " + id, e);
        }
    }
}
