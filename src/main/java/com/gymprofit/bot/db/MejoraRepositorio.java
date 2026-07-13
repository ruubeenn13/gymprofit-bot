package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * Repositorio JDBC de los nodos de mejora comprados ({@code mejoras}). Sigue el patrón del resto.
 */
public final class MejoraRepositorio {

    private final DataSource dataSource;

    public MejoraRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** {@code true} si el usuario ya compró ese nodo. */
    public boolean tiene(long discordId, String nodo) {
        String sql = "SELECT 1 FROM mejoras WHERE discord_id = ? AND nodo = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setString(2, nodo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error consultando mejora de " + discordId, e);
        }
    }

    /** Conjunto de nodos que ha comprado el usuario. */
    public Set<String> comprados(long discordId) {
        String sql = "SELECT nodo FROM mejoras WHERE discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                Set<String> nodos = new HashSet<>();
                while (rs.next()) {
                    nodos.add(rs.getString("nodo"));
                }
                return nodos;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listando mejoras de " + discordId, e);
        }
    }

    /** Marca un nodo como comprado. Devuelve {@code true} si era nuevo. */
    public boolean comprar(long discordId, String nodo) {
        String sql = "INSERT IGNORE INTO mejoras (discord_id, nodo) VALUES (?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setString(2, nodo);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error comprando mejora de " + discordId, e);
        }
    }
}
