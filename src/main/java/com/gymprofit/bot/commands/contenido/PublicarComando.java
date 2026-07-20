package com.gymprofit.bot.commands.contenido;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.commands.config.PanelRolesFactory;
import com.gymprofit.bot.commands.moderacion.ModHelper;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.events.TicketListener;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.SorteoService;
import com.gymprofit.bot.util.Duraciones;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

/**
 * {@code /publicar} con subcomandos (anuncio, redes, panel, sorteo). Consolida la publicación de
 * contenido de staff en un solo comando para no gastar cupo de slash commands (límite de 100 de
 * Discord). Solo altos cargos.
 *
 * <p>{@code anuncio} publica un embed con marca en {@code 📣・anuncios} (con ping opcional al rol
 * {@code 📣 Avisos}); {@code redes} publica las redes oficiales en {@code 📱・redes-sociales};
 * {@code panel} (re)publica y fija el panel de auto-roles o el de tickets; {@code sorteo} crea un
 * sorteo en {@code 🎁・sorteos} con reacción 🎉 y lo guarda para que el {@code SorteoJob} elija
 * ganadores al vencer.
 */
public final class PublicarComando implements Comando {

    /** Emoji de participación del sorteo (compartido con el job). */
    public static final String EMOJI_SORTEO = "🎉";

    private static final String NOMBRE = "publicar";
    private static final String CANAL_ANUNCIOS = "📣・anuncios";
    private static final String CANAL_REDES = "📱・redes-sociales";
    private static final String CANAL_ROLES = "🎭・roles";
    private static final String CANAL_SOPORTE = "🎫・soporte";
    private static final String CANAL_SORTEOS = "🎁・sorteos";
    private static final String ROL_AVISOS = "📣 Avisos";
    private static final String ROL_SORTEOS = "🎁 Sorteos";
    private static final int MAX_GANADORES = 20;

    /** Opción → emoji de cada red social, en orden de aparición en el embed. */
    private static final Map<String, String> REDES = new LinkedHashMap<>();

    static {
        REDES.put("instagram", "📸");
        REDES.put("tiktok", "🎵");
        REDES.put("youtube", "▶️");
        REDES.put("twitch", "🎮");
        REDES.put("x", "🐦");
    }

    private final SorteoService sorteos;

    public PublicarComando(SorteoService sorteos) {
        this.sorteos = sorteos;
    }

    @Override
    public Categoria categoria() {
        return Categoria.MODERACION;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.publicar.familia"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.publicar.familia"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.publicar.familia"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(subAnuncio(), subRedes(), subPanel(), subSorteo());
    }

    private static SubcommandData subAnuncio() {
        return sub("anuncio", "comando.anuncio.descripcion").addOptions(
                texto("titulo", "comando.anuncio.opcion.titulo", true, 256),
                texto("mensaje", "comando.anuncio.opcion.mensaje", true, 4000),
                texto("imagen", "comando.anuncio.opcion.imagen", false, 0),
                new OptionData(OptionType.BOOLEAN, "ping",
                        Messages.get(Messages.ES, "comando.anuncio.opcion.ping"), false)
                        .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                                Messages.get(Messages.EN, "comando.anuncio.opcion.ping")));
    }

    private static SubcommandData subRedes() {
        SubcommandData s = sub("redes", "comando.redes.descripcion");
        for (String red : REDES.keySet()) {
            s.addOptions(texto(red, "comando.redes.opcion." + red, false, 0));
        }
        return s;
    }

    private static SubcommandData subPanel() {
        OptionData tipo = new OptionData(OptionType.STRING, "tipo",
                Messages.get(Messages.ES, "comando.panel.opcion.tipo"), false)
                .addChoice("roles", "roles")
                .addChoice("ticket", "ticket")
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.panel.opcion.tipo"));
        OptionData canal = new OptionData(OptionType.CHANNEL, "canal",
                Messages.get(Messages.ES, "comando.panel.opcion.canal"), false)
                .setChannelTypes(ChannelType.TEXT)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.panel.opcion.canal"));
        return sub("panel", "comando.panel.descripcion").addOptions(tipo, canal);
    }

    private static SubcommandData subSorteo() {
        OptionData ganadores = new OptionData(OptionType.INTEGER, "ganadores",
                Messages.get(Messages.ES, "comando.sorteo.opcion.ganadores"), false)
                .setRequiredRange(1, MAX_GANADORES)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.sorteo.opcion.ganadores"));
        return sub("sorteo", "comando.sorteo.descripcion").addOptions(
                texto("premio", "comando.sorteo.opcion.premio", true, SorteoService.MAX_PREMIO),
                texto("duracion", "comando.sorteo.opcion.duracion", true, 0),
                ganadores);
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    /** Opción de texto; {@code maxLength} 0 = sin límite explícito. */
    private static OptionData texto(String nombre, String claveDesc, boolean obligatoria, int maxLength) {
        OptionData o = new OptionData(OptionType.STRING, nombre,
                Messages.get(Messages.ES, claveDesc), obligatoria)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
        return maxLength > 0 ? o.setMaxLength(maxLength) : o;
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (!ModHelper.esAltoCargo(evento.getMember())) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale,
                    Messages.get(locale, "mod.noautorizado"))).setEphemeral(true).queue();
            return;
        }
        String sub = evento.getSubcommandName() == null ? "anuncio" : evento.getSubcommandName();
        switch (sub) {
            case "anuncio" -> anuncio(evento, locale);
            case "redes" -> redes(evento, locale);
            case "panel" -> panel(evento, locale);
            case "sorteo" -> sorteo(evento, locale);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale,
                    Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
        }
    }

    private void anuncio(SlashCommandInteractionEvent evento, Locale locale) {
        TextChannel canal = canalPorNombre(evento, CANAL_ANUNCIOS);
        if (canal == null) {
            sinCanal(evento, locale, CANAL_ANUNCIOS);
            return;
        }
        String titulo = evento.getOption("titulo").getAsString();
        String mensaje = evento.getOption("mensaje").getAsString();
        boolean ping = evento.getOption("ping") != null && evento.getOption("ping").getAsBoolean();

        var builder = EmbedFactory.base(EmbedFactory.Tipo.ANUNCIO, locale, titulo, mensaje);
        if (evento.getOption("imagen") != null) {
            builder.setImage(evento.getOption("imagen").getAsString());
        }
        MessageEmbed embed = builder.build();

        evento.deferReply(true).queue();
        var accion = canal.sendMessageEmbeds(embed);
        Role avisos = rolPorNombre(evento, ROL_AVISOS);
        if (ping && avisos != null) {
            accion = accion.setContent(avisos.getAsMention())
                    .setAllowedMentions(EnumSet.of(MentionType.ROLE));
        }
        accion.queue(ok -> confirmar(evento, locale, "anuncio.publicado"),
                err -> confirmar(evento, locale, "comando.error.generico"));
    }

    private void redes(SlashCommandInteractionEvent evento, Locale locale) {
        StringBuilder desc = new StringBuilder();
        for (Map.Entry<String, String> red : REDES.entrySet()) {
            var opcion = evento.getOption(red.getKey());
            if (opcion != null) {
                desc.append(red.getValue()).append(" **")
                        .append(capitalizar(red.getKey())).append("**: ")
                        .append(opcion.getAsString()).append('\n');
            }
        }
        if (desc.isEmpty()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale,
                    Messages.get(locale, "redes.vacio"))).setEphemeral(true).queue();
            return;
        }
        TextChannel canal = canalPorNombre(evento, CANAL_REDES);
        if (canal == null) {
            sinCanal(evento, locale, CANAL_REDES);
            return;
        }

        evento.deferReply(true).queue();
        MessageEmbed embed = EmbedFactory.base(EmbedFactory.Tipo.ANUNCIO, locale,
                Messages.get(locale, "redes.titulo"), desc.toString().trim()).build();
        canal.sendMessageEmbeds(embed).queue(
                ok -> confirmar(evento, locale, "redes.publicado"),
                err -> confirmar(evento, locale, "comando.error.generico"));
    }

    private void panel(SlashCommandInteractionEvent evento, Locale locale) {
        boolean ticket = evento.getOption("tipo") != null
                && "ticket".equals(evento.getOption("tipo").getAsString());
        String canalDefecto = ticket ? CANAL_SOPORTE : CANAL_ROLES;

        TextChannel canal;
        if (evento.getOption("canal") != null) {
            GuildChannel elegido = evento.getOption("canal").getAsChannel();
            canal = elegido instanceof TextChannel tc ? tc : null;
        } else {
            canal = evento.getGuild().getTextChannelsByName(canalDefecto, false)
                    .stream().findFirst().orElse(evento.getChannel().asTextChannel());
        }
        if (canal == null) {
            sinCanal(evento, locale, canalDefecto);
            return;
        }

        evento.deferReply(true).queue();
        if (ticket) {
            var embed = EmbedFactory.base(EmbedFactory.Tipo.TICKET, locale,
                    Messages.get(locale, "panel.ticket.titulo"),
                    Messages.get(locale, "panel.ticket.desc")).build();
            canal.sendMessageEmbeds(embed)
                    .addActionRow(Button.primary(TicketListener.BOTON_ABRIR,
                            Messages.get(locale, "panel.ticket.boton")))
                    .queue(m -> m.pin().queue());
        } else {
            canal.sendMessage(PanelRolesFactory.mensaje(locale)).queue(m -> m.pin().queue());
        }
        confirmar(evento, locale, "panel.publicado");
    }

    private void sorteo(SlashCommandInteractionEvent evento, Locale locale) {
        OptionalLong segundos = Duraciones.parsear(evento.getOption("duracion").getAsString());
        if (segundos.isEmpty()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale,
                    Messages.get(locale, "timeout.duracioninvalida"))).setEphemeral(true).queue();
            return;
        }
        TextChannel canal = canalPorNombre(evento, CANAL_SORTEOS);
        if (canal == null) {
            sinCanal(evento, locale, CANAL_SORTEOS);
            return;
        }
        String premio = evento.getOption("premio").getAsString();
        int ganadores = evento.getOption("ganadores") != null
                ? evento.getOption("ganadores").getAsInt() : 1;
        long finEpoch = Instant.now().getEpochSecond() + segundos.getAsLong();

        evento.deferReply(true).queue();
        MessageEmbed embed = EmbedFactory.base(EmbedFactory.Tipo.RETO, locale,
                Messages.get(locale, "sorteo.titulo"),
                Messages.get(locale, "sorteo.cuerpo", premio, ganadores,
                        "<t:" + finEpoch + ":R>", EMOJI_SORTEO)).build();

        Role rol = rolPorNombre(evento, ROL_SORTEOS);
        var accion = canal.sendMessageEmbeds(embed);
        if (rol != null) {
            accion = accion.setContent(rol.getAsMention()).setAllowedMentions(EnumSet.of(MentionType.ROLE));
        }
        accion.queue(msg -> {
            msg.addReaction(Emoji.fromUnicode(EMOJI_SORTEO)).queue();
            sorteos.crear(evento.getGuild().getIdLong(), canal.getIdLong(), msg.getIdLong(),
                    premio, ganadores, evento.getUser().getIdLong(), finEpoch);
            confirmar(evento, locale, "sorteo.creado");
        }, err -> confirmar(evento, locale, "comando.error.generico"));
    }

    private static TextChannel canalPorNombre(SlashCommandInteractionEvent evento, String nombre) {
        return evento.getGuild().getTextChannelsByName(nombre, false).stream().findFirst().orElse(null);
    }

    private static Role rolPorNombre(SlashCommandInteractionEvent evento, String nombre) {
        return evento.getGuild().getRolesByName(nombre, false).stream().findFirst().orElse(null);
    }

    private static void sinCanal(SlashCommandInteractionEvent evento, Locale locale, String canal) {
        evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale,
                Messages.get(locale, "contenido.sincanal", canal))).setEphemeral(true).queue();
    }

    /** Responde al staff (efímero) tras una publicación ya diferida. */
    private static void confirmar(SlashCommandInteractionEvent evento, Locale locale, String clave) {
        evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale,
                Messages.get(locale, clave))).queue();
    }

    private static String capitalizar(String s) {
        return "x".equals(s) ? "X" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
