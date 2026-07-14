package com.gymprofit.bot.services;

/**
 * Estado en memoria de una batalla por turnos (COMBAT-3). Vive mientras dura la pelea de un jugador
 * (una por jugador, la gestiona el listener) y se descarta al terminar. Guarda el HP de ambos y una
 * instantánea de la ofensiva/defensa del jugador tomada al empezar (así el arma/armadura del inicio
 * mandan durante toda la pelea). No se persiste en BD.
 */
public final class CombateSesion {

    private final long jugadorId;
    private final String mundoId;
    private final Monstruos monstruo;
    private final int ataqueJugador;
    private final int defensaJugador;
    private final int hpMaxJugador;
    private final int hpMaxMonstruo;

    private int hpJugador;
    private int hpMonstruo;
    private boolean defendiendo;
    private int turno = 1;

    public CombateSesion(long jugadorId, String mundoId, Monstruos monstruo,
                         int ataqueJugador, int defensaJugador, int hpMaxJugador) {
        this.jugadorId = jugadorId;
        this.mundoId = mundoId;
        this.monstruo = monstruo;
        this.ataqueJugador = ataqueJugador;
        this.defensaJugador = defensaJugador;
        this.hpMaxJugador = hpMaxJugador;
        this.hpJugador = hpMaxJugador;
        this.hpMaxMonstruo = monstruo.hp();
        this.hpMonstruo = monstruo.hp();
    }

    public long jugadorId() {
        return jugadorId;
    }

    public String mundoId() {
        return mundoId;
    }

    public Monstruos monstruo() {
        return monstruo;
    }

    public int ataqueJugador() {
        return ataqueJugador;
    }

    public int defensaJugador() {
        return defensaJugador;
    }

    public int hpMaxJugador() {
        return hpMaxJugador;
    }

    public int hpMaxMonstruo() {
        return hpMaxMonstruo;
    }

    public int hpJugador() {
        return hpJugador;
    }

    public int hpMonstruo() {
        return hpMonstruo;
    }

    public boolean defendiendo() {
        return defendiendo;
    }

    public int turno() {
        return turno;
    }

    /** Aplica daño al monstruo (suelo 0). */
    public void danarMonstruo(int dano) {
        hpMonstruo = Math.max(0, hpMonstruo - dano);
    }

    /** Aplica daño al jugador (suelo 0). */
    public void danarJugador(int dano) {
        hpJugador = Math.max(0, hpJugador - dano);
    }

    /** Cura HP al jugador (tope al máximo). */
    public void curarJugador(int cantidad) {
        hpJugador = Math.min(hpMaxJugador, hpJugador + cantidad);
    }

    public void ponerDefendiendo(boolean valor) {
        defendiendo = valor;
    }

    public void avanzarTurno() {
        turno++;
    }

    public boolean monstruoMuerto() {
        return hpMonstruo <= 0;
    }

    public boolean jugadorMuerto() {
        return hpJugador <= 0;
    }
}
