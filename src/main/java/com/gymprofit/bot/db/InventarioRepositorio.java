package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Repositorio JDBC del inventario ({@code inventario}: dueño → ítem → cantidad). Añadir suma
 * cantidades; quitar es atómico y no deja cantidades negativas.
 */
public final class InventarioRepositorio {

    private final DataSource dataSource;

    public InventarioRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Suma {@code cantidad} unidades del ítem al inventario del usuario. */
    public void anadir(long discordId, String itemId, int cantidad) {
        String sql = "INSERT INTO inventario (discord_id, item_id, cantidad) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE cantidad = cantidad + VALUES(cantidad)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setString(2, itemId);
            ps.setInt(3, cantidad);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error añadiendo ítem a " + discordId, e);
        }
    }

    /** Cantidad que posee el usuario de un ítem. */
    public int cantidad(long discordId, String itemId) {
        String sql = "SELECT cantidad FROM inventario WHERE discord_id = ? AND item_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setString(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error consultando inventario de " + discordId, e);
        }
    }

    /** Quita {@code cantidad} unidades si las tiene (atómico). Devuelve {@code true} si se aplicó. */
    public boolean quitar(long discordId, String itemId, int cantidad) {
        String sql = "UPDATE inventario SET cantidad = cantidad - ? "
                + "WHERE discord_id = ? AND item_id = ? AND cantidad >= ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, cantidad);
            ps.setLong(2, discordId);
            ps.setString(3, itemId);
            ps.setInt(4, cantidad);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error quitando ítem a " + discordId, e);
        }
    }

    /** Inventario del usuario (ítem → cantidad), solo con cantidad &gt; 0, orden estable. */
    public Map<String, Integer> listar(long discordId) {
        String sql = "SELECT item_id, cantidad FROM inventario "
                + "WHERE discord_id = ? AND cantidad > 0 ORDER BY item_id";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Integer> inv = new LinkedHashMap<>();
                while (rs.next()) {
                    inv.put(rs.getString("item_id"), rs.getInt("cantidad"));
                }
                return inv;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listando inventario de " + discordId, e);
        }
    }
}
