package com.gymprofit.bot.events;

import com.gymprofit.bot.commands.economia.DescansoComando;
import com.gymprofit.bot.commands.economia.PelearComando;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.BatallaService;
import com.gymprofit.bot.services.BatallaService.ResultadoInicio;
import com.gymprofit.bot.services.BatallaService.Turno;
import com.gymprofit.bot.services.CombateSesion;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conduce la batalla por turnos (COMBAT-3). Escucha el menú de rivales de {@code /pelear}, los
 * botones Atacar/Defender/Objeto/Huir y el submenú de objetos, todos con customId
 * {@code pelear:<accion>:<ownerId>[...]}. Guarda una {@link CombateSesion} por jugador en memoria y
 * la descarta al terminar la pelea. Solo el dueño del combate puede pulsar sus botones.
 */
public final class CombateListener extends ListenerAdapter {

    private static final String PREFIJO = "pelear:";

    private final BatallaService batalla;
    private final InventarioRepositorio inventario;
    private final com.gymprofit.bot.services.MisionService misiones;
    private final Map<Long, CombateSesion> sesiones = new ConcurrentHashMap<>();

    public CombateListener(BatallaService batalla, InventarioRepositorio inventario,
                           com.gymprofit.bot.services.MisionService misiones) {
        this.batalla = batalla;
        this.inventario = inventario;
        this.misiones = misiones;
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evento) {
        String id = evento.getComponentId();
        if (!id.startsWith(PREFIJO) || evento.getGuild() == null) {
            return;
        }
        String[] partes = id.split(":");
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (!esDueno(evento, partes[2], locale)) {
            return;
        }
        long ownerId = Long.parseUnsignedLong(partes[2]);
        if (partes[1].equals("rival")) {
            seleccionarRival(evento, ownerId, partes[3], evento.getValues().get(0), locale);
        } else if (partes[1].equals("usar")) {
            usarObjeto(evento, ownerId, evento.getValues().get(0), locale);
        } else if (partes[1].equals("hab")) {
            usarHabilidad(evento, ownerId, evento.getValues().get(0), locale);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        String id = evento.getComponentId();
        if (!id.startsWith(PREFIJO) || evento.getGuild() == null) {
            return;
        }
        String[] partes = id.split(":");
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (!esDueno(evento, partes[2], locale)) {
            return;
        }
        long ownerId = Long.parseUnsignedLong(partes[2]);
        if (partes[1].equals("mazent")) {
            entrarMazmorra(evento, ownerId, partes[3], locale);
            return;
        }
        CombateSesion s = sesiones.get(ownerId);
        switch (partes[1]) {
            case "atacar" -> {
                if (s == null) { sinSesion(evento, locale); return; }
                resolver(evento, ownerId, s, batalla.atacar(s));
            }
            case "defender" -> {
                if (s == null) { sinSesion(evento, locale); return; }
                resolver(evento, ownerId, s, batalla.defender(s));
            }
            case "objeto" -> {
                if (s == null) { sinSesion(evento, locale); return; }
                abrirObjetos(evento, ownerId, s, locale);
            }
            case "habilidad" -> {
                if (s == null) { sinSesion(evento, locale); return; }
                evento.editMessageEmbeds(PelearComando.embedBatalla(locale, s, null))
                        .setComponents(PelearComando.filasHabilidades(ownerId, locale, s)).queue();
            }
            case "volver" -> {
                if (s == null) { sinSesion(evento, locale); return; }
                evento.editMessageEmbeds(PelearComando.embedBatalla(locale, s, null))
                        .setComponents(PelearComando.botonesBatalla(ownerId, locale)).queue();
            }
            case "huir" -> {
                sesiones.remove(ownerId);
                MessageEmbed fin = s != null ? PelearComando.embedHuida(locale, s)
                        : EmbedFactory.aviso(EmbedFactory.Tipo.DUELO, locale,
                                Messages.get(locale, "batalla.nosesion"));
                evento.editMessageEmbeds(fin).setComponents().queue();
            }
            default -> { /* customId ajeno */ }
        }
    }

    // ---------------------- handlers ----------------------

    private void seleccionarRival(StringSelectInteractionEvent evento, long ownerId,
                                  String mundoId, String monstruoId, Locale locale) {
        if (sesiones.containsKey(ownerId)) {
            evento.reply(Messages.get(locale, "batalla.yaencombate")).setEphemeral(true).queue();
            return;
        }
        ResultadoInicio r = batalla.iniciar(ownerId, monstruoId);
        if (r.estado() == BatallaService.InicioEstado.DORMIDO) {
            bloqueadoPorSueno(evento, ownerId, locale);
            return;
        }
        if (r.estado() != BatallaService.InicioEstado.OK) {
            evento.editMessageEmbeds(motivoInicio(locale, r)).setComponents().queue();
            return;
        }
        sesiones.put(ownerId, r.sesion());
        evento.editMessageEmbeds(PelearComando.embedBatalla(locale, r.sesion(), null))
                .setComponents(PelearComando.botonesBatalla(ownerId, locale)).queue();
    }

    private void entrarMazmorra(ButtonInteractionEvent evento, long ownerId, String mazmorraId,
                                Locale locale) {
        if (sesiones.containsKey(ownerId)) {
            evento.reply(Messages.get(locale, "batalla.yaencombate")).setEphemeral(true).queue();
            return;
        }
        ResultadoInicio r = batalla.iniciarMazmorra(ownerId, mazmorraId);
        if (r.estado() == BatallaService.InicioEstado.DORMIDO) {
            bloqueadoPorSueno(evento, ownerId, locale);
            return;
        }
        if (r.estado() != BatallaService.InicioEstado.OK) {
            evento.editMessageEmbeds(motivoInicio(locale, r)).setComponents().queue();
            return;
        }
        sesiones.put(ownerId, r.sesion());
        evento.editMessageEmbeds(PelearComando.embedBatalla(locale, r.sesion(), null))
                .setComponents(PelearComando.botonesBatalla(ownerId, locale)).queue();
    }

    private void abrirObjetos(ButtonInteractionEvent evento, long ownerId, CombateSesion s,
                              Locale locale) {
        Map<String, Integer> inv = inventario.listar(ownerId);
        List<ActionRow> filas = PelearComando.filasObjetos(ownerId, locale, inv);
        if (filas == null) {
            evento.reply(Messages.get(locale, "batalla.objeto.vacio")).setEphemeral(true).queue();
            return;
        }
        evento.editMessageEmbeds(PelearComando.embedBatalla(locale, s, null))
                .setComponents(filas).queue();
    }

    private void usarObjeto(StringSelectInteractionEvent evento, long ownerId, String itemId,
                            Locale locale) {
        CombateSesion s = sesiones.get(ownerId);
        if (s == null) {
            sinSesion(evento, locale);
            return;
        }
        Turno t = batalla.usarObjeto(s, itemId);
        if (t == null) {
            evento.reply(Messages.get(locale, "batalla.objeto.invalido")).setEphemeral(true).queue();
            return;
        }
        resolver(evento, ownerId, s, t);
    }

    private void usarHabilidad(StringSelectInteractionEvent evento, long ownerId, String habId,
                              Locale locale) {
        CombateSesion s = sesiones.get(ownerId);
        if (s == null) {
            sinSesion(evento, locale);
            return;
        }
        Turno t = batalla.usarHabilidad(s, habId);
        if (t == null) {
            evento.reply(Messages.get(locale, "batalla.hab.encooldown")).setEphemeral(true).queue();
            return;
        }
        resolver(evento, ownerId, s, t);
    }

    /** Aplica el desenlace de un turno al mensaje (continúa, victoria o derrota). */
    private void resolver(IReplyCallback evento, long ownerId, CombateSesion s, Turno t) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        switch (t.desenlace()) {
            case VICTORIA -> {
                BatallaService.Botin botin = batalla.recompensar(s);
                var misComp = misiones.registrarVictoria(ownerId, s.monstruo());
                if (s.esMazmorra() && s.avanzarOleada()) {
                    // Siguiente oleada: el jugador conserva su HP (riesgo).
                    editar(evento, PelearComando.embedBatalla(locale, s,
                            Messages.get(locale, "batalla.oleada", s.oleadaActual(), s.oleadasTotal())),
                            PelearComando.botonesBatalla(ownerId, locale));
                } else if (s.esMazmorra()) {
                    BatallaService.BonusMazmorra bonus = batalla.completarMazmorra(ownerId, s.mazmorraId());
                    sesiones.remove(ownerId);
                    editar(evento, PelearComando.embedMazmorraCompletada(locale, s, bonus), null);
                } else {
                    sesiones.remove(ownerId);
                    editar(evento, PelearComando.embedVictoria(locale, s, botin, misComp), null);
                }
            }
            case DERROTA -> {
                batalla.penalizar(ownerId);
                sesiones.remove(ownerId);
                editar(evento, PelearComando.embedDerrota(locale, s), null);
            }
            case CONTINUA -> editar(evento, PelearComando.embedBatalla(locale, s, logTurno(locale, t)),
                    PelearComando.botonesBatalla(ownerId, locale));
        }
    }

    /** Edita el mensaje de la interacción (botón o menú) con embed y componentes dados. */
    private static void editar(IReplyCallback evento, MessageEmbed embed, ActionRow fila) {
        if (evento instanceof ButtonInteractionEvent b) {
            if (fila == null) {
                b.editMessageEmbeds(embed).setComponents().queue();
            } else {
                b.editMessageEmbeds(embed).setComponents(fila).queue();
            }
        } else if (evento instanceof StringSelectInteractionEvent m) {
            if (fila == null) {
                m.editMessageEmbeds(embed).setComponents().queue();
            } else {
                m.editMessageEmbeds(embed).setComponents(fila).queue();
            }
        }
    }

    /** Resumen localizado del turno para el embed de batalla (con marcas de crítico/esquiva). */
    private static String logTurno(Locale locale, Turno t) {
        if (t.sinContraataque()) {
            // Aturdir: hay daño + el monstruo no contraataca. Objeto de energía: sin daño.
            if (t.danoAlMonstruo() > 0) {
                return Messages.get(locale, "batalla.frag.golpe", t.danoAlMonstruo())
                        + (t.critJugador() ? Messages.get(locale, "batalla.frag.critico") : "")
                        + Messages.get(locale, "batalla.frag.aturde");
            }
            return Messages.get(locale, "batalla.log.energia");
        }
        // Parte del jugador: curar, defender, esquiva del enemigo o golpe (con crítico).
        String jugador;
        if (t.curado() > 0) {
            jugador = Messages.get(locale, "batalla.frag.curas", t.curado());
        } else if (t.esquivaMonstruo()) {
            jugador = Messages.get(locale, "batalla.frag.enemigoEsquiva");
        } else if (t.danoAlMonstruo() == 0) {
            jugador = Messages.get(locale, "batalla.frag.defiendes");
        } else {
            jugador = Messages.get(locale, "batalla.frag.golpe", t.danoAlMonstruo())
                    + (t.critJugador() ? Messages.get(locale, "batalla.frag.critico") : "");
        }
        // Parte del monstruo: esquiva del jugador o contraataque (con crítico).
        String monstruo = t.esquivaJugador()
                ? Messages.get(locale, "batalla.frag.esquivas")
                : Messages.get(locale, t.critMonstruo()
                        ? "batalla.frag.contraCrit" : "batalla.frag.contra", t.danoAlJugador());
        return jugador + monstruo;
    }

    /**
     * Sustituye el mensaje por el embed de «estás dormido» con sus botones. Se hace aquí y no en
     * {@link #motivoInicio} porque este caso lleva componentes: el jugador decide en el sitio si
     * sigue durmiendo o se levanta, y {@code DescansoListener} recoge el botón.
     */
    private static void bloqueadoPorSueno(IReplyCallback evento, long ownerId, Locale locale) {
        editar(evento, DescansoComando.embedBloqueado(locale),
                DescansoComando.botonesBloqueado(locale, ownerId));
    }

    private MessageEmbed motivoInicio(Locale locale, ResultadoInicio r) {
        String msg = switch (r.estado()) {
            case MONSTRUO_NO_EXISTE -> Messages.get(locale, "pelear.rival.noexiste");
            case MUNDO_BLOQUEADO -> Messages.get(locale, "pelear.bloqueado");
            case NIVEL_INSUFICIENTE -> Messages.get(locale, "pelear.nivel", r.detalle());
            case SIN_ENERGIA -> Messages.get(locale, "pelear.sinenergia", r.detalle());
            case EN_COOLDOWN -> Messages.get(locale, "pelear.cooldown", r.detalle());
            // OK y DORMIDO no llegan aquí: se resuelven antes (DORMIDO, con su embed de botones).
            case OK, DORMIDO -> "";
        };
        return EmbedFactory.aviso(EmbedFactory.Tipo.DUELO, locale, msg);
    }

    /** ¿Es quien pulsa el dueño del combate? Si no, aviso efímero y corta. */
    private static boolean esDueno(IReplyCallback evento, String ownerIdStr, Locale locale) {
        long ownerId = Long.parseUnsignedLong(ownerIdStr);
        if (evento.getUser().getIdLong() == ownerId) {
            return true;
        }
        evento.reply(Messages.get(locale, "batalla.noestuyo")).setEphemeral(true).queue();
        return false;
    }

    private static void sinSesion(IReplyCallback evento, Locale locale) {
        editar(evento, EmbedFactory.aviso(EmbedFactory.Tipo.DUELO, locale,
                Messages.get(locale, "batalla.nosesion")), null);
    }
}
