package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JDBC de la bolsa ({@code acciones}, {@code cartera}). Los precios se leen/actualizan
 * (el job de mercado los mueve) y las posiciones de cada jugador se compran/venden con upserts.
 */
public final class BolsaRepositorio {

    private final DataSource dataSource;

    public BolsaRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Todos los precios. */
    public List<PrecioAccion> precios() {
        String sql = "SELECT id, precio, precio_previo FROM acciones";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<PrecioAccion> res = new ArrayList<>();
            while (rs.next()) {
                res.add(new PrecioAccion(rs.getString("id"), rs.getLong("precio"),
                        rs.getLong("precio_previo")));
            }
            return res;
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo precios de la bolsa", e);
        }
    }

    /** Precio de una acción. */
    public Optional<PrecioAccion> precio(String id) {
        String sql = "SELECT id, precio, precio_previo FROM acciones WHERE id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new PrecioAccion(rs.getString("id"),
                        rs.getLong("precio"), rs.getLong("precio_previo"))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo el precio de " + id, e);
        }
    }

    /** Fija un precio nuevo, guardando el actual como anterior (para la tendencia). */
    public void actualizarPrecio(String id, long nuevo) {
        String sql = "UPDATE acciones SET precio_previo = precio, precio = ? WHERE id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, nuevo);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error actualizando el precio de " + id, e);
        }
    }

    /** Posiciones del jugador (con cantidad &gt; 0). */
    public List<Posicion> cartera(long discordId) {
        String sql = "SELECT accion_id, cantidad, coste FROM cartera "
                + "WHERE discord_id = ? AND cantidad > 0 ORDER BY accion_id";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Posicion> res = new ArrayList<>();
                while (rs.next()) {
                    res.add(new Posicion(rs.getString("accion_id"), rs.getLong("cantidad"),
                            rs.getLong("coste")));
                }
                return res;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo la cartera de " + discordId, e);
        }
    }

    /** Posición del jugador en una acción concreta. */
    public Optional<Posicion> posicion(long discordId, String accionId) {
        String sql = "SELECT accion_id, cantidad, coste FROM cartera "
                + "WHERE discord_id = ? AND accion_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setString(2, accionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Posicion(rs.getString("accion_id"),
                        rs.getLong("cantidad"), rs.getLong("coste"))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo la posición de " + discordId, e);
        }
    }

    /** Añade acciones a la posición (upsert: suma cantidad y coste). */
    public void comprar(long discordId, String accionId, long cantidad, long coste) {
        String sql = "INSERT INTO cartera (discord_id, accion_id, cantidad, coste) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE cantidad = cantidad + VALUES(cantidad), "
                + "coste = coste + VALUES(coste)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setString(2, accionId);
            ps.setLong(3, cantidad);
            ps.setLong(4, coste);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error comprando acciones de " + discordId, e);
        }
    }

    /** Fija cantidad y coste de una posición (para vender parcial/total); borra si llega a 0. */
    public void fijarPosicion(long discordId, String accionId, long cantidad, long coste) {
        String sql = cantidad <= 0
                ? "DELETE FROM cartera WHERE discord_id = ? AND accion_id = ?"
                : "UPDATE cartera SET cantidad = ?, coste = ? WHERE discord_id = ? AND accion_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (cantidad <= 0) {
                ps.setLong(1, discordId);
                ps.setString(2, accionId);
            } else {
                ps.setLong(1, cantidad);
                ps.setLong(2, coste);
                ps.setLong(3, discordId);
                ps.setString(4, accionId);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error fijando la posición de " + discordId, e);
        }
    }
}
