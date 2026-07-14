package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JDBC del mercado ({@code mercado}). La compra usa {@link #reservar} (decremento
 * atómico) para no vender más unidades de las que hay ante compras simultáneas; {@link #devolver}
 * revierte una reserva si el cobro falla.
 */
public final class MercadoRepositorio {

    private final DataSource dataSource;

    public MercadoRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Crea un anuncio y devuelve su id. */
    public long crear(long vendedor, String itemId, int cantidad, long precio) {
        String sql = "INSERT INTO mercado (vendedor, item_id, cantidad, precio) VALUES (?, ?, ?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, vendedor);
            ps.setString(2, itemId);
            ps.setInt(3, cantidad);
            ps.setLong(4, precio);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error creando anuncio de mercado de " + vendedor, e);
        }
    }

    /** Anuncio por id, si existe y tiene stock. */
    public Optional<ListadoMercado> buscar(long id) {
        String sql = "SELECT id, vendedor, item_id, cantidad, precio FROM mercado "
                + "WHERE id = ? AND cantidad > 0";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando anuncio " + id, e);
        }
    }

    /** Anuncios activos (con stock), más recientes primero, hasta {@code limite}. */
    public List<ListadoMercado> listar(int limite) {
        String sql = "SELECT id, vendedor, item_id, cantidad, precio FROM mercado "
                + "WHERE cantidad > 0 ORDER BY id DESC LIMIT ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                List<ListadoMercado> res = new ArrayList<>();
                while (rs.next()) {
                    res.add(mapear(rs));
                }
                return res;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listando el mercado", e);
        }
    }

    /** Reserva (descuenta) {@code cantidad} unidades si hay stock (atómico). */
    public boolean reservar(long id, int cantidad) {
        return actualizarStock("UPDATE mercado SET cantidad = cantidad - ? "
                + "WHERE id = ? AND cantidad >= ?", id, cantidad, true);
    }

    /** Devuelve {@code cantidad} unidades a un anuncio (revierte una reserva). */
    public void devolver(long id, int cantidad) {
        actualizarStock("UPDATE mercado SET cantidad = cantidad + ? WHERE id = ?", id, cantidad, false);
    }

    /** Borra un anuncio (al retirarlo o cuando se agota). */
    public void eliminar(long id) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM mercado WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error eliminando anuncio " + id, e);
        }
    }

    private boolean actualizarStock(String sql, long id, int cantidad, boolean conMinimo) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, cantidad);
            ps.setLong(2, id);
            if (conMinimo) {
                ps.setInt(3, cantidad);
            }
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error actualizando stock del anuncio " + id, e);
        }
    }

    private static ListadoMercado mapear(ResultSet rs) throws SQLException {
        return new ListadoMercado(rs.getLong("id"), rs.getLong("vendedor"),
                rs.getString("item_id"), rs.getInt("cantidad"), rs.getLong("precio"));
    }
}
