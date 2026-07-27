package com.gymprofit.bot.services;

/**
 * Catálogo (en código) de los eventos del clima económico global (F5). Cada entrada declara qué
 * palancas de la economía mueve mientras está activo: multiplicadores de venta, producción, impuesto
 * semanal y faucet del curro (neutro = {@code 1.0}) y el sesgo que impone a la bolsa. El estado activo
 * (cuál y hasta cuándo) vive en BD ({@code evento_economico}); aquí solo van los datos fijos. El nombre
 * y el efecto legibles salen de i18n con clave {@code evento.<name en minúsculas>.*}. Función pura.
 *
 * <p>El catálogo está balanceado a propósito (4 buenos / 4 malos, {@link Tono}): por cada evento que
 * crea coins hay uno que los reduce, para no romper el principio antiinflación del proyecto.
 */
public enum EventoEconomico {
    BOOM_CONSUMO("📈", Tono.BUENO, 1.30, 1.0, 1.0, 1.0, BolsaSesgo.NINGUNO),
    RECESION("📉", Tono.MALO, 0.70, 1.0, 1.0, 1.0, BolsaSesgo.NINGUNO),
    AUGE_INDUSTRIAL("🏭", Tono.BUENO, 1.0, 1.50, 1.0, 1.0, BolsaSesgo.NINGUNO),
    CRISIS_SUMINISTRO("🦠", Tono.MALO, 1.0, 0.50, 1.0, 1.0, BolsaSesgo.NINGUNO),
    SUBIDA_IMPUESTOS("🏛️", Tono.MALO, 1.0, 1.0, 1.50, 1.0, BolsaSesgo.NINGUNO),
    AYUDAS_PUBLICAS("🎁", Tono.BUENO, 1.0, 1.0, 0.50, 1.0, BolsaSesgo.NINGUNO),
    MERCADO_ALCISTA("🐂", Tono.BUENO, 1.0, 1.0, 1.0, 1.0, BolsaSesgo.ALCISTA),
    MERCADO_BAJISTA("🐻", Tono.MALO, 1.0, 1.0, 1.0, 1.0, BolsaSesgo.BAJISTA);

    /** Signo del evento (para el color/emoji del anuncio y para balancear el catálogo). */
    public enum Tono { BUENO, MALO }

    /** Cómo sesga la bolsa: NINGUNO no la toca; ALCISTA/BAJISTA inclinan el 50/50 de crash/boom. */
    public enum BolsaSesgo { NINGUNO, ALCISTA, BAJISTA }

    private final String emoji;
    private final Tono tono;
    private final double ventaMult;
    private final double produccionMult;
    private final double impuestoMult;
    private final double curroMult;
    private final BolsaSesgo bolsaSesgo;

    EventoEconomico(String emoji, Tono tono, double ventaMult, double produccionMult,
                    double impuestoMult, double curroMult, BolsaSesgo bolsaSesgo) {
        this.emoji = emoji;
        this.tono = tono;
        this.ventaMult = ventaMult;
        this.produccionMult = produccionMult;
        this.impuestoMult = impuestoMult;
        this.curroMult = curroMult;
        this.bolsaSesgo = bolsaSesgo;
    }

    public String emoji() { return emoji; }
    public Tono tono() { return tono; }
    public double ventaMult() { return ventaMult; }
    public double produccionMult() { return produccionMult; }
    public double impuestoMult() { return impuestoMult; }
    public double curroMult() { return curroMult; }
    public BolsaSesgo bolsaSesgo() { return bolsaSesgo; }

    /** Clave i18n base del evento ({@code evento.boom_consumo}); el service/comando añade {@code .nombre}/{@code .efecto}. */
    public String claveI18n() { return "evento." + name().toLowerCase(java.util.Locale.ROOT); }

    /** Un evento al azar del catálogo. {@code azar} en {@code [0,1)} (inyectado, testeable). */
    public static EventoEconomico aleatorio(double azar) {
        EventoEconomico[] v = values();
        return v[Math.min(v.length - 1, (int) (azar * v.length))];
    }
}
