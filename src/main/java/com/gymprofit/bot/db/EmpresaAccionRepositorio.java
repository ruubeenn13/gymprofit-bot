package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio JDBC de las participaciones de empresa (F5). Tabla {@code empresa_acciones}: cuántas
 * participaciones tiene cada jugador de cada empresa. {@code fijar} hace upsert (borra la fila al llegar a
 * 0). {@code vendidasDe} suma el pool colocado de una empresa; {@code accionistas} y {@code carteraDe} son
 * las dos vistas que necesitan el reparto de dividendos y el comando de cartera.
 */
public final class EmpresaAccionRepositorio {

    /** Un accionista de una empresa (para el reparto de dividendos). */
    public record Accionista(long discordId, int cantidad) {}

    /** Una posición del jugador: participaciones que tiene en una empresa (para la cartera). */
    public record PosicionAccion(long empresaId, int cantidad) {}

    private final DataSource dataSource;

    public EmpresaAccionRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Participaciones que tiene un jugador en una empresa (0 si ninguna). */
    public int participacionesDe(long empresaId, long discordId) {
        String sql = "SELECT cantidad FROM empresa_acciones WHERE empresa_id = ? AND discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, empresaId);
            ps.setLong(2, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("cantidad") : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo participaciones de " + discordId, e);
        }
    }

    /** Participaciones vendidas (colocadas) de una empresa: SUM del pool. */
    public int vendidasDe(long empresaId) {
        String sql = "SELECT COALESCE(SUM(cantidad), 0) AS total FROM empresa_acciones WHERE empresa_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, empresaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("total") : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo participaciones vendidas de " + empresaId, e);
        }
    }

    /** Accionistas de una empresa (para repartir dividendos). */
    public List<Accionista> accionistas(long empresaId) {
        String sql = "SELECT discord_id, cantidad FROM empresa_acciones WHERE empresa_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, empresaId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Accionista> res = new ArrayList<>();
                while (rs.next()) {
                    res.add(new Accionista(rs.getLong("discord_id"), rs.getInt("cantidad")));
                }
                return res;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo accionistas de " + empresaId, e);
        }
    }

    /** Cartera de participaciones del jugador (empresas donde tiene algo). */
    public List<PosicionAccion> carteraDe(long discordId) {
        String sql = "SELECT empresa_id, cantidad FROM empresa_acciones WHERE discord_id = ? ORDER BY empresa_id";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PosicionAccion> res = new ArrayList<>();
                while (rs.next()) {
                    res.add(new PosicionAccion(rs.getLong("empresa_id"), rs.getInt("cantidad")));
                }
                return res;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo la cartera de acciones de " + discordId, e);
        }
    }

    /** Fija las participaciones (upsert); borra la fila si {@code cantidad <= 0}. */
    public void fijar(long empresaId, long discordId, int cantidad) {
        String sql = cantidad <= 0
                ? "DELETE FROM empresa_acciones WHERE empresa_id = ? AND discord_id = ?"
                : "INSERT INTO empresa_acciones (empresa_id, discord_id, cantidad) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE cantidad = VALUES(cantidad)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, empresaId);
            ps.setLong(2, discordId);
            if (cantidad > 0) {
                ps.setInt(3, cantidad);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error fijando participaciones de " + discordId, e);
        }
    }
}
