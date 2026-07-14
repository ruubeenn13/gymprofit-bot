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
    private final int ataqueJugador;
    private final int defensaJugador;
    private final double critJugador;
    private final double esquivaJugador;
    private final double roboVida;
    private final int hpMaxJugador;

    private final java.util.Map<String, Integer> cooldowns = new java.util.HashMap<>();

    // Estado de mazmorra (oleadas). Para un combate normal: mazmorraId null, sin oleadas siguientes.
    private final java.util.List<Monstruos> oleadasSiguientes = new java.util.ArrayList<>();
    private String mazmorraId;
    private int oleadaActual = 1;
    private int oleadasTotal = 1;

    private Monstruos monstruo;
    private int hpMaxMonstruo;
    private int hpJugador;
    private int hpMonstruo;
    private boolean defendiendo;
    private int turno = 1;

    public CombateSesion(long jugadorId, String mundoId, Monstruos monstruo,
                         int ataqueJugador, int defensaJugador, double critJugador,
                         double esquivaJugador, double roboVida, int hpMaxJugador) {
        this.jugadorId = jugadorId;
        this.mundoId = mundoId;
        this.monstruo = monstruo;
        this.ataqueJugador = ataqueJugador;
        this.defensaJugador = defensaJugador;
        this.critJugador = critJugador;
        this.esquivaJugador = esquivaJugador;
        this.roboVida = roboVida;
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

    public double critJugador() {
        return critJugador;
    }

    public double esquivaJugador() {
        return esquivaJugador;
    }

    /** Fracción del daño infligido que se cura el jugador (robo de vida del encantamiento). */
    public double roboVida() {
        return roboVida;
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

    /** Turnos que faltan para que una habilidad vuelva a estar disponible (0 = lista). */
    public int cooldown(String habilidadId) {
        return cooldowns.getOrDefault(habilidadId, 0);
    }

    /** Pone una habilidad en cooldown durante {@code turnos}. */
    public void ponerCooldown(String habilidadId, int turnos) {
        cooldowns.put(habilidadId, turnos);
    }

    /** Avanza el turno y descuenta un turno a todos los cooldowns activos (suelo 0). */
    public void avanzarTurno() {
        turno++;
        cooldowns.replaceAll((id, restante) -> Math.max(0, restante - 1));
    }

    // ---------------------- Mazmorra (oleadas) ----------------------

    /** Configura esta sesión como mazmorra: id + oleadas que vienen tras el monstruo actual. */
    public void configurarMazmorra(String mazmorraId, java.util.List<Monstruos> siguientes, int total) {
        this.mazmorraId = mazmorraId;
        this.oleadasSiguientes.addAll(siguientes);
        this.oleadasTotal = total;
    }

    /** ¿Es una mazmorra (varias oleadas)? */
    public boolean esMazmorra() {
        return mazmorraId != null;
    }

    public String mazmorraId() {
        return mazmorraId;
    }

    public int oleadaActual() {
        return oleadaActual;
    }

    public int oleadasTotal() {
        return oleadasTotal;
    }

    /**
     * Pasa a la siguiente oleada: nuevo monstruo a tope, el jugador conserva su HP (riesgo). Devuelve
     * {@code false} si no quedan oleadas (mazmorra completada).
     */
    public boolean avanzarOleada() {
        if (oleadasSiguientes.isEmpty()) {
            return false;
        }
        monstruo = oleadasSiguientes.remove(0);
        hpMaxMonstruo = monstruo.hp();
        hpMonstruo = hpMaxMonstruo;
        defendiendo = false;
        oleadaActual++;
        return true;
    }

    public boolean monstruoMuerto() {
        return hpMonstruo <= 0;
    }

    public boolean jugadorMuerto() {
        return hpJugador <= 0;
    }
}
