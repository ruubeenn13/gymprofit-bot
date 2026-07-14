package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.BatallaService.Botin;
import com.gymprofit.bot.services.BatallaService.Turno;
import com.gymprofit.bot.services.CombateSesion;
import com.gymprofit.bot.services.Habilidad;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.MundoService;
import com.gymprofit.bot.services.MundoService.MundoVista;
import com.gymprofit.bot.services.Mundos;
import com.gymprofit.bot.services.Monstruos;
import com.gymprofit.bot.services.Rareza;
import com.gymprofit.bot.util.Barras;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /pelear <mundo>}: abre la batalla por turnos (COMBAT-3). Comprueba que el mundo está
 * desbloqueado y que tienes nivel, y muestra un menú para elegir rival; a partir de ahí toda la
 * pelea la conduce {@code CombateListener} (botones Atacar / Defender / Objeto / Huir). Esta clase
 * también reúne los <b>constructores de vista</b> (embeds y componentes) que reutiliza el listener.
 */
public final class PelearComando implements Comando {

    private static final String NOMBRE = "pelear";
    private static final int SEGMENTOS_BARRA = 12;

    private final MundoService mundos;

    public PelearComando(MundoService mundos) {
        this.mundos = mundos;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData mundo = new OptionData(OptionType.STRING, "mundo",
                Messages.get(Messages.ES, "comando.pelear.opcion.mundo"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.pelear.opcion.mundo"));
        for (Mundos m : Mundos.CATALOGO) {
            mundo.addChoice(m.emoji() + " " + Messages.get(Messages.ES, "mundo." + m.id()), m.id());
        }
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.pelear.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.pelear.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.pelear.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(mundo);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String mundoId = evento.getOption("mundo").getAsString();
        long userId = evento.getUser().getIdLong();

        MundoService.Progreso progreso = mundos.progreso(userId);
        MundoVista vista = progreso.mundos().stream()
                .filter(v -> v.mundo().id().equals(mundoId)).findFirst().orElse(null);
        if (vista == null) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.DUELO, locale,
                    Messages.get(locale, "monstruos.vacio"))).setEphemeral(true).queue();
            return;
        }
        if (!vista.desbloqueado()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.DUELO, locale,
                    Messages.get(locale, "pelear.bloqueado"))).setEphemeral(true).queue();
            return;
        }
        if (progreso.nivelJugador() < vista.mundo().nivelRequerido()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.DUELO, locale,
                    Messages.get(locale, "pelear.nivel", vista.mundo().nivelRequerido()))
            ).setEphemeral(true).queue();
            return;
        }
        // Menú de rivales del mundo (público: la batalla se ve en el canal de combate).
        evento.replyEmbeds(embedElegirRival(locale, vista.mundo()))
                .setComponents(ActionRow.of(menuRivales(userId, mundoId, locale)))
                .queue();
    }

    // ---------------------- Constructores de vista (reutilizados por el listener) ----------------

    /** Embed de la pantalla de selección de rival. */
    public static MessageEmbed embedElegirRival(Locale locale, Mundos mundo) {
        return EmbedFactory.base(EmbedFactory.Tipo.DUELO, locale,
                Messages.get(locale, "pelear.elige.titulo",
                        mundo.emoji() + " " + Messages.get(locale, "mundo." + mundo.id())),
                Messages.get(locale, "pelear.elige.desc")).build();
    }

    /** Menú desplegable con los monstruos del mundo (id de rival como valor). */
    public static StringSelectMenu menuRivales(long userId, String mundoId, Locale locale) {
        StringSelectMenu.Builder menu = StringSelectMenu.create("pelear:rival:" + userId + ":" + mundoId)
                .setPlaceholder(Messages.get(locale, "pelear.elige.placeholder"));
        for (Monstruos mo : Monstruos.deMundo(mundoId)) {
            String desc = Messages.get(locale, "pelear.rival.desc", mo.poder(), mo.hp())
                    + (mo.esJefe() ? " " + Messages.get(locale, "pelear.rival.jefe") : "");
            menu.addOptions(SelectOption.of(Messages.get(locale, "monstruo." + mo.id()), mo.id())
                    .withDescription(desc)
                    .withEmoji(Emoji.fromFormatted(mo.emoji())));
        }
        return menu.build();
    }

    /** Embed del estado de la batalla (barras de HP + resumen del último turno). */
    public static MessageEmbed embedBatalla(Locale locale, CombateSesion s, String logTurno) {
        Monstruos m = s.monstruo();
        String nombreMon = m.emoji() + " " + Messages.get(locale, "monstruo." + m.id());
        StringBuilder sb = new StringBuilder();
        sb.append(Messages.get(locale, "batalla.jugador",
                barra(s.hpJugador(), s.hpMaxJugador()), s.hpJugador(), s.hpMaxJugador())).append('\n');
        sb.append(Messages.get(locale, "batalla.monstruo", nombreMon,
                barra(s.hpMonstruo(), s.hpMaxMonstruo()), s.hpMonstruo(), s.hpMaxMonstruo()));
        if (logTurno != null && !logTurno.isEmpty()) {
            sb.append("\n\n").append(logTurno);
        }
        return EmbedFactory.base(EmbedFactory.Tipo.DUELO, locale,
                Messages.get(locale, "batalla.titulo", nombreMon), sb.toString()).build();
    }

    /** Fila de botones de acción de la batalla (5: atacar, defender, habilidad, objeto, huir). */
    public static ActionRow botonesBatalla(long userId, Locale locale) {
        return ActionRow.of(
                Button.danger("pelear:atacar:" + userId, Messages.get(locale, "batalla.btn.atacar"))
                        .withEmoji(Emoji.fromUnicode("⚔️")),
                Button.primary("pelear:defender:" + userId, Messages.get(locale, "batalla.btn.defender"))
                        .withEmoji(Emoji.fromUnicode("🛡️")),
                Button.success("pelear:habilidad:" + userId, Messages.get(locale, "batalla.btn.habilidad"))
                        .withEmoji(Emoji.fromUnicode("✨")),
                Button.secondary("pelear:objeto:" + userId, Messages.get(locale, "batalla.btn.objeto"))
                        .withEmoji(Emoji.fromUnicode("🎒")),
                Button.secondary("pelear:huir:" + userId, Messages.get(locale, "batalla.btn.huir"))
                        .withEmoji(Emoji.fromUnicode("🏃")));
    }

    /** Menú de habilidades (con su cooldown) + botón para volver. */
    public static List<ActionRow> filasHabilidades(long userId, Locale locale, CombateSesion s) {
        StringSelectMenu.Builder menu = StringSelectMenu.create("pelear:hab:" + userId)
                .setPlaceholder(Messages.get(locale, "batalla.hab.placeholder"));
        for (Habilidad h : Habilidad.values()) {
            int cd = s.cooldown(h.id());
            String desc = cd > 0 ? Messages.get(locale, "batalla.hab.cooldown", cd)
                    : Messages.get(locale, "batalla.hab.listo");
            menu.addOptions(SelectOption.of(Messages.get(locale, "habilidad." + h.id()), h.id())
                    .withDescription(desc)
                    .withEmoji(Emoji.fromUnicode(h.emoji())));
        }
        return List.of(ActionRow.of(menu.build()),
                ActionRow.of(Button.secondary("pelear:volver:" + userId,
                        Messages.get(locale, "batalla.btn.volver"))));
    }

    /**
     * Menú de consumibles usables en combate (SALUD/ENERGIA que el jugador posee), más un botón para
     * volver. Devuelve {@code null} si no tiene ninguno.
     */
    public static List<ActionRow> filasObjetos(long userId, Locale locale, Map<String, Integer> inv) {
        StringSelectMenu.Builder menu = StringSelectMenu.create("pelear:usar:" + userId)
                .setPlaceholder(Messages.get(locale, "batalla.objeto.placeholder"));
        boolean alguno = false;
        for (Map.Entry<String, Integer> e : inv.entrySet()) {
            Items item = Items.porId(e.getKey()).orElse(null);
            if (item == null || item.categoria() != Items.Categoria.CONSUMIBLE
                    || item.efecto() == Items.Efecto.NINGUNO) {
                continue;
            }
            String efecto = item.efecto() == Items.Efecto.SALUD
                    ? Messages.get(locale, "batalla.objeto.cura", item.valor())
                    : Messages.get(locale, "batalla.objeto.energia");
            menu.addOptions(SelectOption.of(
                            Messages.get(locale, "item." + item.id()) + " ×" + e.getValue(),
                            item.id())
                    .withDescription(efecto)
                    .withEmoji(Emoji.fromFormatted(item.emoji())));
            alguno = true;
        }
        if (!alguno) {
            return null;
        }
        return List.of(ActionRow.of(menu.build()),
                ActionRow.of(Button.secondary("pelear:volver:" + userId,
                        Messages.get(locale, "batalla.btn.volver"))));
    }

    /** Embed de victoria con el botín. */
    public static MessageEmbed embedVictoria(Locale locale, CombateSesion s, Botin botin) {
        Monstruos m = s.monstruo();
        String nombreMon = m.emoji() + " " + Messages.get(locale, "monstruo." + m.id());
        StringBuilder sb = new StringBuilder(Messages.get(locale, "batalla.victoria.desc", nombreMon));
        sb.append("\n\n").append(Messages.get(locale, "batalla.recompensa", botin.coins(), botin.xp()));
        if (!botin.items().isEmpty()) {
            StringBuilder loot = new StringBuilder();
            for (String id : botin.items()) {
                Items item = Items.porId(id).orElse(null);
                String rareza = item != null ? Rareza.de(item).emoji() + " " : "";
                String emoji = item != null ? item.emoji() : "🎁";
                loot.append(rareza).append(emoji).append(' ')
                        .append(Messages.get(locale, "item." + id)).append(", ");
            }
            loot.setLength(loot.length() - 2);
            sb.append('\n').append(Messages.get(locale, "batalla.loot", loot.toString()));
        }
        if (botin.subioNivel()) {
            sb.append('\n').append(Messages.get(locale, "batalla.subenivel"));
        }
        if (botin.jefeDerrotado()) {
            sb.append("\n\n").append(botin.siguienteMundoId() != null
                    ? Messages.get(locale, "batalla.desbloqueo",
                        Messages.get(locale, "mundo." + botin.siguienteMundoId()))
                    : Messages.get(locale, "batalla.jefe.final"));
        }
        return EmbedFactory.base(EmbedFactory.Tipo.LOGRO, locale,
                Messages.get(locale, "batalla.victoria.titulo"), sb.toString()).build();
    }

    /** Embed de derrota. */
    public static MessageEmbed embedDerrota(Locale locale, CombateSesion s) {
        Monstruos m = s.monstruo();
        String nombreMon = m.emoji() + " " + Messages.get(locale, "monstruo." + m.id());
        return EmbedFactory.base(EmbedFactory.Tipo.MODERACION, locale,
                Messages.get(locale, "batalla.derrota.titulo"),
                Messages.get(locale, "batalla.derrota.desc", nombreMon)).build();
    }

    /** Embed de huida. */
    public static MessageEmbed embedHuida(Locale locale, CombateSesion s) {
        Monstruos m = s.monstruo();
        String nombreMon = m.emoji() + " " + Messages.get(locale, "monstruo." + m.id());
        return EmbedFactory.aviso(EmbedFactory.Tipo.DUELO, locale,
                Messages.get(locale, "batalla.huida", nombreMon));
    }

    private static String barra(int actual, int max) {
        return Barras.progreso(actual, max, SEGMENTOS_BARRA);
    }
}
