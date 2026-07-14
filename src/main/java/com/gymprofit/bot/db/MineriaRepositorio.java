package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Repositorio JDBC del estado de minería ({@code mineria}). {@link #obtenerOCrear} garantiza la fila
 * (requiere que exista antes {@code usuarios_discord}, por la FK). {@link #registrarMinado} sube el
 * nivel y marca el cooldown en una sola operación atómica.
 */
public final class MineriaRepositorio {

    private final DataSource dataSource;

    public MineriaRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Estado de minería, creándolo con valores por defecto si no existe. */
    public MineriaEstado obtenerOCrear(long discordId) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT IGNORE INTO mineria (discord_id) VALUES (?)")) {
            ps.setLong(1, discordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error creando el estado de minería de " + discordId, e);
        }
        String sql = "SELECT discord_id, nivel_mineria, ultimo_minado FROM mineria WHERE discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new DatabaseException("El estado de minería " + discordId
                            + " no existe tras crearlo", null);
                }
                java.sql.Timestamp um = rs.getTimestamp("ultimo_minado");
                return new MineriaEstado(rs.getLong("discord_id"), rs.getInt("nivel_mineria"),
                        um == null ? null : um.toInstant());
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error consultando la minería de " + discordId, e);
        }
    }

    /** Registra un minado: sube el nivel en 1 y fija {@code ultimo_minado = NOW()}. */
    public void registrarMinado(long discordId) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE mineria SET nivel_mineria = nivel_mineria + 1, ultimo_minado = NOW() "
                             + "WHERE discord_id = ?")) {
            ps.setLong(1, discordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error registrando minado de " + discordId, e);
        }
    }

    /**
     * Durabilidad actual de un pico del jugador. Si no hay fila, el pico está intacto: devuelve
     * {@code durabilidadMax}.
     */
    public int durabilidad(long discordId, String picoId, int durabilidadMax) {
        String sql = "SELECT durabilidad FROM durabilidad_picos WHERE discord_id = ? AND pico_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setString(2, picoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : durabilidadMax;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error consultando durabilidad de " + discordId, e);
        }
    }

    /**
     * Gasta 1 de durabilidad al pico (suelo 0). En el primer uso crea la fila en
     * {@code durabilidadMax - 1}; después descuenta 1 sin bajar de 0.
     */
    public void gastarDurabilidad(long discordId, String picoId, int durabilidadMax) {
        String sql = "INSERT INTO durabilidad_picos (discord_id, pico_id, durabilidad) "
                + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE durabilidad = GREATEST(0, durabilidad - 1)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setString(2, picoId);
            ps.setInt(3, Math.max(0, durabilidadMax - 1));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error gastando durabilidad de " + discordId, e);
        }
    }

    /** Repara un pico dejándolo a {@code durabilidadMax} (el coste lo cobra el service antes). */
    public void repararPico(long discordId, String picoId, int durabilidadMax) {
        String sql = "INSERT INTO durabilidad_picos (discord_id, pico_id, durabilidad) "
                + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE durabilidad = VALUES(durabilidad)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setString(2, picoId);
            ps.setInt(3, durabilidadMax);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error reparando pico de " + discordId, e);
        }
    }
}
