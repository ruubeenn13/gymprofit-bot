package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Repositorio JDBC de las ranuras de efectos pasivos ({@code pasivos_equipados}: jugador → ranura →
 * ítem). Guarda solo una <b>referencia</b>: el bono se recalcula siempre contra el inventario en
 * {@code PasivoService}, así que aquí no hay ninguna noción de «derecho adquirido».
 *
 * <p>No hay método de borrado por jugador a propósito: el olvido RGPD borra {@code usuarios_discord}
 * y estas filas se van por el {@code ON DELETE CASCADE} de la FK, igual que {@code descanso} o
 * {@code durabilidad_picos}.
 */
public final class PasivoRepositorio {

    /**
     * Código de error de MySQL para clave duplicada ({@code ER_DUP_ENTRY}). Hace falta distinguirlo
     * porque la violación de la FK a {@code usuarios_discord} llega como la <i>misma</i> excepción
     * de Java ({@link SQLIntegrityConstraintViolationException}) y no es el mismo problema.
     */
    private static final int ERROR_CLAVE_DUPLICADA = 1062;

    /**
     * El ítem ya ocupa otra ranura de ese jugador. Lo lanza el {@code UNIQUE (discord_id, item_id)}
     * del esquema, que es la garantía dura frente a dos comandos simultáneos; el service comprueba
     * antes para poder dar un error bonito, pero si pierde la carrera, esta excepción es la red.
     */
    public static final class ItemYaEquipadoException extends RuntimeException {
        public ItemYaEquipadoException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    private final DataSource dataSource;

    public PasivoRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Ranuras ocupadas del jugador (ranura → itemId), ordenadas por ranura. Vacío si no hay nada. */
    public Map<Integer, String> equipados(long discordId) {
        String sql = "SELECT ranura, item_id FROM pasivos_equipados "
                + "WHERE discord_id = ? ORDER BY ranura";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<Integer, String> res = new LinkedHashMap<>();
                while (rs.next()) {
                    res.put(rs.getInt("ranura"), rs.getString("item_id"));
                }
                return res;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listando los pasivos de " + discordId, e);
        }
    }

    /**
     * Pone un ítem en una ranura, reemplazando lo que hubiera.
     *
     * <p>Se hace con <b>DELETE de la ranura + INSERT</b> dentro de una transacción, y no con el
     * {@code INSERT … ON DUPLICATE KEY UPDATE} que parecería más corto, porque ese
     * {@code ON DUPLICATE KEY} dispara con <i>cualquier</i> clave única: si el ítem ya ocupara otra
     * ranura, MySQL no daría error sino que actualizaría <i>esa otra fila</i> dejándola igual, y el
     * jugador vería un «equipado» que no cambió nada (y la ranura pedida vacía). Con INSERT limpio,
     * el {@code UNIQUE (discord_id, item_id)} sí revienta y podemos avisar de verdad.
     *
     * @throws ItemYaEquipadoException si el ítem ya ocupa otra ranura del mismo jugador
     */
    public void equipar(long discordId, int ranura, String itemId) {
        try (Connection con = dataSource.getConnection()) {
            // Transacción: vaciar la ranura y llenarla son un solo cambio para el resto del mundo;
            // si el INSERT choca con el UNIQUE, el rollback devuelve la ranura a su contenido previo.
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM pasivos_equipados WHERE discord_id = ? AND ranura = ?")) {
                    ps.setLong(1, discordId);
                    ps.setInt(2, ranura);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO pasivos_equipados (discord_id, ranura, item_id) "
                                + "VALUES (?, ?, ?)")) {
                    ps.setLong(1, discordId);
                    ps.setInt(2, ranura);
                    ps.setString(3, itemId);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            // Choque con uq_pasivos_item: el mismo ítem en dos ranuras duplicaría el bono con una
            // sola compra, que es el exploit más obvio del sistema.
            if (e.getErrorCode() == ERROR_CLAVE_DUPLICADA) {
                throw new ItemYaEquipadoException(
                        "El ítem " + itemId + " ya está equipado por " + discordId, e);
            }
            throw new DatabaseException("Error equipando " + itemId + " para " + discordId, e);
        } catch (SQLException e) {
            throw new DatabaseException("Error equipando " + itemId + " para " + discordId, e);
        }
    }

    /** Vacía una ranura. Devuelve {@code false} si ya estaba vacía. */
    public boolean quitar(long discordId, int ranura) {
        String sql = "DELETE FROM pasivos_equipados WHERE discord_id = ? AND ranura = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setInt(2, ranura);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error quitando la ranura " + ranura + " de " + discordId, e);
        }
    }

    /** Ranura que ocupa un ítem en ese jugador, si está equipado. */
    public Optional<Integer> ranuraDe(long discordId, String itemId) {
        String sql = "SELECT ranura FROM pasivos_equipados WHERE discord_id = ? AND item_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setString(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando la ranura de " + itemId, e);
        }
    }
}
