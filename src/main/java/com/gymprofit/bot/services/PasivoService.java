package com.gymprofit.bot.services;

import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.PasivoRepositorio;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lógica de los <b>efectos pasivos</b>: ranuras (equipar/quitar), desbloqueo por nivel y cálculo de
 * los bonos activos ya sumados y ya topados. El catálogo de bonos vive en {@link Pasivos}; aquí solo
 * va la lógica.
 *
 * <p><b>La fuente de verdad es siempre el inventario.</b> {@code pasivos_equipados} guarda una
 * referencia, no un derecho: en cada consulta se cruzan las ranuras con
 * {@link InventarioRepositorio#listar(long)} y se <b>descartan las ranuras cuyo ítem ya no se posee</b>.
 * Esto cierra de golpe cuatro exploits (vender, regalar, publicar en el mercado, trocar y seguir
 * cobrando el bono) y, sobre todo, significa que {@code VentaService}, {@code RegaloService},
 * {@code MercadoService}, {@code TruequeService} y {@code RoboService} <b>no se tocan</b>: no hay
 * hooks de limpieza que recordar al añadir el sexto sitio donde se puede perder un ítem. La fila
 * muerta se queda ahí, inofensiva, y {@code /pasivos ver} la marca con un aviso.
 *
 * <p>La suma y el topado son una <b>función pura y estática</b> ({@link #sumarYTopar}) para poder
 * testear el balance sin base de datos, igual que {@code TrabajoService.conBonoEstudios} o
 * {@code CombateService.dano}.
 */
public final class PasivoService {

    /**
     * Nivel al que se desbloquea cada ranura (índice 0 = ranura 1): <b>los umbrales de
     * {@link Rango}</b> (Novato 0 / Habitual 10 / Veterano 25 / Leyenda 50). No se copian los números
     * a mano: se leen del enum para que ranuras y roles no puedan desincronizarse nunca. El desbloqueo
     * cae a la vez que el rol nuevo, así el subidón de nivel se nota doble y no hay que explicarle al
     * jugador dos escalas distintas.
     */
    private static final int[] NIVEL_RANURA =
            Arrays.stream(Rango.values()).mapToInt(Rango::nivelMin).toArray();

    /** Ranuras que existen como máximo (una por rango). Más allá de esto, {@code RANURA_INVALIDA}. */
    public static final int RANURAS_MAX = NIVEL_RANURA.length;

    /** Estados de {@code equipar} y {@code quitar}. Uno por fila de la tabla de errores del diseño. */
    public enum Estado {
        OK, NO_EXISTE, SIN_PASIVO, NO_TIENE, RANURA_INVALIDA, RANURA_BLOQUEADA, YA_EQUIPADO,
        SIN_HUECO, VACIA
    }

    /**
     * Estado de una ranura para {@code /pasivos ver}.
     *
     * @param ranura    número de ranura (1..4)
     * @param itemId    ítem equipado, o {@code null} si está vacía
     * @param bloqueada la ranura aún no está desbloqueada por nivel
     * @param falta     hay un ítem equipado pero <b>ya no se posee</b>: la ranura no cuenta
     */
    public record EstadoRanura(int ranura, String itemId, boolean bloqueada, boolean falta) {
    }

    /**
     * Resultado de equipar.
     *
     * @param estado         resultado
     * @param ranura         ranura afectada (0 si no aplica)
     * @param itemId         ítem que se intentaba equipar
     * @param reemplazado    ítem que salía de esa ranura, o {@code null}
     * @param nivelRequerido nivel que pide la ranura (solo con {@code RANURA_BLOQUEADA})
     * @param totales        bonos activos <b>tras</b> la operación (vacío si falló)
     */
    public record ResultadoEquipar(Estado estado, int ranura, String itemId, String reemplazado,
                                   int nivelRequerido, Map<Pasivos.Tipo, Double> totales) {
    }

    /**
     * Resultado de quitar.
     *
     * @param estado  resultado
     * @param itemId  ítem que sale de la ranura, o {@code null} si falló
     * @param totales bonos activos <b>tras</b> la operación (vacío si falló)
     */
    public record ResultadoQuitar(Estado estado, String itemId, Map<Pasivos.Tipo, Double> totales) {
    }

    private final PasivoRepositorio pasivos;
    private final InventarioRepositorio inventario;
    private final UsuarioDiscordRepositorio usuarios;

    public PasivoService(PasivoRepositorio pasivos, InventarioRepositorio inventario,
                         UsuarioDiscordRepositorio usuarios) {
        this.pasivos = pasivos;
        this.inventario = inventario;
        this.usuarios = usuarios;
    }

    /** Ranuras desbloqueadas según el nivel: 1 / 2 / 3 / 4 a partir de los niveles 0 / 10 / 25 / 50. */
    public static int ranurasDe(int nivel) {
        int n = 1;
        for (int i = 1; i < NIVEL_RANURA.length; i++) {
            if (nivel >= NIVEL_RANURA[i]) {
                n = i + 1;
            }
        }
        return n;
    }

    /** Nivel al que se desbloquea una ranura (1..4). Para ranuras fuera de rango, {@link Integer#MAX_VALUE}. */
    public static int nivelDeRanura(int ranura) {
        return ranura >= 1 && ranura <= RANURAS_MAX ? NIVEL_RANURA[ranura - 1] : Integer.MAX_VALUE;
    }

    /**
     * Suma los bonos de una lista de pasivos y aplica los topes. <b>Pura.</b>
     *
     * <p>Se topa <b>la suma</b>, nunca cada bono por separado: dos ítems de 20 % con tope 30 % dan
     * 30 %, no 40 % ni 20 %. El mapa devuelto tiene <b>siempre los nueve tipos</b> (a 0,0 los que no
     * tienen fuente) para que ningún llamante tenga que comprobar ausencias.
     */
    public static Map<Pasivos.Tipo, Double> sumarYTopar(List<Pasivos.Pasivo> equipados) {
        Map<Pasivos.Tipo, Double> total = new EnumMap<>(Pasivos.Tipo.class);
        for (Pasivos.Tipo t : Pasivos.Tipo.values()) {
            total.put(t, 0.0);
        }
        for (Pasivos.Pasivo p : equipados) {
            for (Pasivos.Bono b : p.bonos()) {
                total.merge(b.tipo(), b.magnitud(), Double::sum);
            }
        }
        total.replaceAll((t, v) -> Math.min(Pasivos.TOPES.get(t), v));
        return total;
    }

    /** Bonos activos del jugador: sumados sobre las ranuras válidas y topados. Nunca {@code null}. */
    public Map<Pasivos.Tipo, Double> bonosDe(long discordId) {
        return sumarYTopar(activosDe(discordId));
    }

    /** Atajo tipado; {@code 0.0} si no hay bono de ese tipo. */
    public double bonoDe(long discordId, Pasivos.Tipo tipo) {
        return bonosDe(discordId).getOrDefault(tipo, 0.0);
    }

    /**
     * Vista completa de las 4 ranuras para {@code /pasivos ver}, en orden. Siempre devuelve 4
     * elementos, marcando cuáles están bloqueadas por nivel y cuáles apuntan a un ítem que ya no se
     * posee.
     */
    public List<EstadoRanura> ranuras(long discordId) {
        Map<Integer, String> eq = pasivos.equipados(discordId);
        Map<String, Integer> inv = inventario.listar(discordId);
        int disponibles = ranurasDe(nivelDe(discordId));
        List<EstadoRanura> res = new ArrayList<>(RANURAS_MAX);
        for (int r = 1; r <= RANURAS_MAX; r++) {
            String item = eq.get(r);
            boolean bloqueada = r > disponibles;
            boolean falta = item != null && inv.getOrDefault(item, 0) <= 0;
            res.add(new EstadoRanura(r, item, bloqueada, falta));
        }
        return res;
    }

    /**
     * Ítems que el jugador <b>posee</b>, <b>tienen pasivo</b> y <b>no están ya equipados</b>. Es lo
     * que alimenta el autocompletado de {@code /pasivos equipar}: filtrar ahí evita la mitad de los
     * errores antes de que ocurran.
     */
    public List<String> equipablesDe(long discordId) {
        Map<String, Integer> inv = inventario.listar(discordId);
        var yaEquipados = pasivos.equipados(discordId).values();
        List<String> res = new ArrayList<>();
        for (Pasivos.Pasivo p : Pasivos.CATALOGO) {
            if (inv.getOrDefault(p.itemId(), 0) > 0 && !yaEquipados.contains(p.itemId())) {
                res.add(p.itemId());
            }
        }
        return res;
    }

    /**
     * Equipa un ítem en una ranura. <b>No consume el ítem</b> (precedente:
     * {@code CombateService.equipar} solo referencia el id y exige poseerlo), así que se puede quitar
     * y volver a poner las veces que se quiera sin coste.
     *
     * @param ranura ranura 1..4, o {@code null} para usar la primera libre desbloqueada
     */
    public ResultadoEquipar equipar(long discordId, String itemId, Integer ranura) {
        if (Items.porId(itemId).isEmpty()) {
            return fallo(Estado.NO_EXISTE, itemId);
        }
        if (Pasivos.porId(itemId).isEmpty()) {
            return fallo(Estado.SIN_PASIVO, itemId);
        }
        // Equipar sí escribe, así que aquí la fila del usuario puede (y debe) crearse: la FK de
        // pasivos_equipados apunta a usuarios_discord y sin fila el INSERT reventaría.
        usuarios.obtenerOCrear(discordId);
        if (inventario.cantidad(discordId, itemId) <= 0) {
            return fallo(Estado.NO_TIENE, itemId);
        }

        Map<Integer, String> eq = pasivos.equipados(discordId);
        Optional<Integer> yaEn = eq.entrySet().stream()
                .filter(e -> e.getValue().equals(itemId))
                .map(Map.Entry::getKey).findFirst();
        if (yaEn.isPresent()) {
            // Un ítem en dos ranuras duplicaría el bono con una sola compra.
            return new ResultadoEquipar(Estado.YA_EQUIPADO, yaEn.get(), itemId, null, 0, Map.of());
        }

        int disponibles = ranurasDe(nivelDe(discordId));
        int destino;
        if (ranura == null) {
            destino = 0;
            for (int r = 1; r <= disponibles; r++) {
                if (!eq.containsKey(r)) {
                    destino = r;
                    break;
                }
            }
            // Sin hueco NO se elige por el jugador: pisar en silencio un cohete de 3 000 000 de
            // coins es inaceptable aunque sea reversible. El comando lista las ranuras y pregunta.
            if (destino == 0) {
                return fallo(Estado.SIN_HUECO, itemId);
            }
        } else {
            if (ranura < 1 || ranura > RANURAS_MAX) {
                return fallo(Estado.RANURA_INVALIDA, itemId);
            }
            if (ranura > disponibles) {
                return new ResultadoEquipar(Estado.RANURA_BLOQUEADA, ranura, itemId, null,
                        nivelDeRanura(ranura), Map.of());
            }
            destino = ranura;
        }

        String reemplazado = eq.get(destino);
        try {
            pasivos.equipar(discordId, destino, itemId);
        } catch (PasivoRepositorio.ItemYaEquipadoException e) {
            // Carrera entre dos comandos simultáneos: el UNIQUE del esquema ha ganado. Se traduce al
            // mismo error bonito en vez de soltar un fallo genérico.
            return new ResultadoEquipar(Estado.YA_EQUIPADO,
                    pasivos.ranuraDe(discordId, itemId).orElse(0), itemId, null, 0, Map.of());
        }
        return new ResultadoEquipar(Estado.OK, destino, itemId, reemplazado, 0, bonosDe(discordId));
    }

    /** Vacía una ranura. El ítem nunca salió del inventario: solo deja de contar. */
    public ResultadoQuitar quitar(long discordId, int ranura) {
        if (ranura < 1 || ranura > RANURAS_MAX) {
            return new ResultadoQuitar(Estado.RANURA_INVALIDA, null, Map.of());
        }
        usuarios.obtenerOCrear(discordId);
        String itemId = pasivos.equipados(discordId).get(ranura);
        if (itemId == null) {
            return new ResultadoQuitar(Estado.VACIA, null, Map.of());
        }
        pasivos.quitar(discordId, ranura);
        return new ResultadoQuitar(Estado.OK, itemId, bonosDe(discordId));
    }

    // ---------------------- internos ----------------------

    private static ResultadoEquipar fallo(Estado estado, String itemId) {
        return new ResultadoEquipar(estado, 0, itemId, null, 0, Map.of());
    }

    /**
     * Pasivos que de verdad cuentan: ranura desbloqueada por nivel <b>y</b> ítem todavía en el
     * inventario.
     */
    private List<Pasivos.Pasivo> activosDe(long discordId) {
        Map<Integer, String> eq = pasivos.equipados(discordId);
        if (eq.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> inv = inventario.listar(discordId);
        int disponibles = ranurasDe(nivelDe(discordId));
        List<Pasivos.Pasivo> res = new ArrayList<>();
        for (Map.Entry<Integer, String> e : eq.entrySet()) {
            if (e.getKey() > disponibles || inv.getOrDefault(e.getValue(), 0) <= 0) {
                continue;
            }
            Pasivos.porId(e.getValue()).ifPresent(res::add);
        }
        return res;
    }

    /**
     * Nivel del jugador <b>sin crear su fila</b>: {@code bonosDe} y {@code ranuras} son caminos de
     * lectura y consultar no puede generar datos nuevos (RGPD, ADR-009).
     */
    private int nivelDe(long discordId) {
        return usuarios.buscar(discordId).map(UsuarioDiscord::nivel).orElse(0);
    }
}
