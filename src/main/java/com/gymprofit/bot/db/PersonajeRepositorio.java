package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Repositorio JDBC de {@link Personaje} (tabla {@code personajes}). {@link #obtenerOCrear} garantiza
 * la fila del personaje (requiere que exista antes la fila de {@code usuarios_discord}, por la FK).
 */
public final class PersonajeRepositorio {

    private final DataSource dataSource;

    public PersonajeRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Devuelve el personaje, creándolo con valores por defecto si no existe. */
    public Personaje obtenerOCrear(long discordId) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT IGNORE INTO personajes (discord_id) VALUES (?)")) {
            ps.setLong(1, discordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error creando el personaje " + discordId, e);
        }
        return buscar(discordId).orElseThrow(() ->
                new DatabaseException("El personaje " + discordId + " no existe tras crearlo", null));
    }

    /** Personaje por id, si existe. */
    public Optional<Personaje> buscar(long discordId) {
        String sql = "SELECT discord_id, fuerza, resistencia, carisma, energia, salud, trabajo, "
                + "ultimo_work, arma, armadura, ultimo_combate, arma_nivel, arma_encanto, estudios "
                + "FROM personajes WHERE discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando el personaje " + discordId, e);
        }
    }

    /** Fija el trabajo actual del personaje ({@code null} = en paro). */
    public void fijarTrabajo(long discordId, String trabajo) {
        ejecutar("UPDATE personajes SET trabajo = ? WHERE discord_id = ?", ps -> {
            ps.setString(1, trabajo);
            ps.setLong(2, discordId);
        });
    }

    /**
     * Aplica un turno de trabajo: descuenta {@code energia} y marca {@code ultimo_work = NOW()}, solo
     * si hay energía suficiente. Devuelve {@code true} si se aplicó.
     */
    public boolean trabajar(long discordId, int energiaCoste) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE personajes SET energia = energia - ?, ultimo_work = NOW() "
                             + "WHERE discord_id = ? AND energia >= ?")) {
            ps.setInt(1, energiaCoste);
            ps.setLong(2, discordId);
            ps.setInt(3, energiaCoste);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error aplicando trabajo a " + discordId, e);
        }
    }

    /**
     * Entrena un atributo: gasta {@code energiaCoste} y sube el atributo en 1, solo si hay energía.
     * {@code atributo} debe ser una columna válida (fuerza/resistencia/carisma). Devuelve si se aplicó.
     */
    public boolean entrenar(long discordId, String atributo, int energiaCoste) {
        if (!atributo.equals("fuerza") && !atributo.equals("resistencia")
                && !atributo.equals("carisma") && !atributo.equals("estudios")) {
            throw new IllegalArgumentException("Atributo no válido: " + atributo);
        }
        String sql = "UPDATE personajes SET " + atributo + " = " + atributo + " + 1, "
                + "energia = energia - ? WHERE discord_id = ? AND energia >= ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, energiaCoste);
            ps.setLong(2, discordId);
            ps.setInt(3, energiaCoste);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error entrenando a " + discordId, e);
        }
    }

    /** Suma {@code cantidad} a un atributo (fuerza/resistencia/carisma). Para el árbol de mejoras. */
    public void sumarAtributo(long discordId, String atributo, int cantidad) {
        if (!atributo.equals("fuerza") && !atributo.equals("resistencia")
                && !atributo.equals("carisma") && !atributo.equals("estudios")) {
            throw new IllegalArgumentException("Atributo no válido: " + atributo);
        }
        ejecutar("UPDATE personajes SET " + atributo + " = " + atributo + " + ? WHERE discord_id = ?",
                ps -> {
                    ps.setInt(1, cantidad);
                    ps.setLong(2, discordId);
                });
    }

    /** Suma energía a un personaje (tope 100). Para consumibles. */
    public void sumarEnergia(long discordId, int cantidad) {
        ejecutar("UPDATE personajes SET energia = LEAST(100, energia + ?) WHERE discord_id = ?",
                ps -> {
                    ps.setInt(1, cantidad);
                    ps.setLong(2, discordId);
                });
    }

    /**
     * Suma energía respetando un <b>tope</b> propio de la cama en la que se ha dormido (ver
     * {@code Camas}: en el suelo no se pasa de 60 aunque se duerman las 9 h).
     *
     * <p>Ojo con el {@code GREATEST}, que no es decorativo: sin él, dormir en el suelo (tope 60)
     * teniendo 80 de energía la <b>bajaría</b> a 60. El resultado es
     * {@code max(energia, min(tope, energia + cantidad))}, así que dormir nunca resta: como mucho,
     * no suma.
     *
     * @param discordId jugador
     * @param cantidad  energía a sumar
     * @param tope      energía máxima alcanzable con esa cama
     */
    public void sumarEnergiaConTope(long discordId, int cantidad, int tope) {
        ejecutar("UPDATE personajes SET energia = GREATEST(energia, LEAST(?, energia + ?)) "
                        + "WHERE discord_id = ?",
                ps -> {
                    ps.setInt(1, tope);
                    ps.setInt(2, cantidad);
                    ps.setLong(3, discordId);
                });
    }

    /** Suma salud a un personaje (tope 100). Para consumibles. */
    public void sumarSalud(long discordId, int cantidad) {
        ejecutar("UPDATE personajes SET salud = LEAST(100, salud + ?) WHERE discord_id = ?",
                ps -> {
                    ps.setInt(1, cantidad);
                    ps.setLong(2, discordId);
                });
    }

    /**
     * Regenera energía a todos los personajes (job periódico): {@code +cantidad}, tope 100.
     *
     * <p>Salta a los que están dormidos: esos ya cobran su energía de golpe al despertar
     * ({@code DescansoService}), así que sumarles también el goteo del job sería doble ración.
     * El {@code NOT EXISTS} cubre además a quien no tiene fila en {@code descanso} (nunca ha
     * dormido): sin fila no está dormido, y debe regenerar con normalidad.
     */
    public int regenerarEnergia(int cantidad) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE personajes SET energia = LEAST(100, energia + ?) WHERE energia < 100 "
                             + "AND NOT EXISTS (SELECT 1 FROM descanso d "
                             + "WHERE d.discord_id = personajes.discord_id "
                             + "AND d.dormido_desde IS NOT NULL)")) {
            ps.setInt(1, cantidad);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error regenerando energía", e);
        }
    }

    private interface Param {
        void set(PreparedStatement ps) throws SQLException;
    }

    private void ejecutar(String sql, Param param) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            param.set(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error en la operación de personaje", e);
        }
    }

    /**
     * Equipa (o desequipa) un ítem en una ranura de combate. {@code ranura} debe ser
     * {@code arma} o {@code armadura}; {@code itemId} = {@code null} desequipa. No valida posesión
     * (eso lo hace el service antes de llamar).
     */
    public void fijarEquipo(long discordId, String ranura, String itemId) {
        if (!ranura.equals("arma") && !ranura.equals("armadura")) {
            throw new IllegalArgumentException("Ranura no válida: " + ranura);
        }
        ejecutar("UPDATE personajes SET " + ranura + " = ? WHERE discord_id = ?", ps -> {
            ps.setString(1, itemId);
            ps.setLong(2, discordId);
        });
    }

    /**
     * Gasta {@code coste} de energía si hay suficiente (atómico). A diferencia de {@link #trabajar},
     * no toca {@code ultimo_work}: es el coste de entrar en una pelea. Devuelve si se aplicó.
     */
    public boolean gastarEnergia(long discordId, int coste) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE personajes SET energia = energia - ? "
                             + "WHERE discord_id = ? AND energia >= ?")) {
            ps.setInt(1, coste);
            ps.setLong(2, discordId);
            ps.setInt(3, coste);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error gastando energía de " + discordId, e);
        }
    }

    /**
     * Registra una derrota en combate: resta {@code saludPerdida} (con suelo en 0) y fija
     * {@code ultimo_combate = NOW()} para arrancar el cooldown de pelea.
     */
    public void registrarDerrota(long discordId, int saludPerdida) {
        ejecutar("UPDATE personajes SET salud = GREATEST(0, salud - ?), ultimo_combate = NOW() "
                + "WHERE discord_id = ?", ps -> {
            ps.setInt(1, saludPerdida);
            ps.setLong(2, discordId);
        });
    }

    /** Sube en 1 el nivel de mejora del arma equipada (el coste lo cobra el service antes). */
    public void subirNivelArma(long discordId) {
        ejecutar("UPDATE personajes SET arma_nivel = arma_nivel + 1 WHERE discord_id = ?",
                ps -> ps.setLong(1, discordId));
    }

    /** Fija el encantamiento del arma ({@code null} lo quita). */
    public void fijarEncanto(long discordId, String encantoId) {
        ejecutar("UPDATE personajes SET arma_encanto = ? WHERE discord_id = ?", ps -> {
            ps.setString(1, encantoId);
            ps.setLong(2, discordId);
        });
    }

    private static Personaje mapear(ResultSet rs) throws SQLException {
        java.sql.Timestamp uw = rs.getTimestamp("ultimo_work");
        java.sql.Timestamp uc = rs.getTimestamp("ultimo_combate");
        return new Personaje(rs.getLong("discord_id"), rs.getInt("fuerza"), rs.getInt("resistencia"),
                rs.getInt("carisma"), rs.getInt("energia"), rs.getInt("salud"),
                rs.getString("trabajo"), uw == null ? null : uw.toInstant(),
                rs.getString("arma"), rs.getString("armadura"),
                uc == null ? null : uc.toInstant(),
                rs.getInt("arma_nivel"), rs.getString("arma_encanto"), rs.getInt("estudios"));
    }
}
