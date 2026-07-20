package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.GremioService;
import com.gymprofit.bot.services.GremioService.Info;
import com.gymprofit.bot.services.GremioService.ResultadoCrear;
import com.gymprofit.bot.services.GremioService.ResultadoMiembro;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code /gremio} con subcomandos (crear, ver, add, kick, salir, disolver). Consolida en un solo
 * comando toda la gestión de gremios para no gastar cupo de slash commands. El canal privado se
 * gestiona con {@link GremioCanal}.
 */
public final class GremioComando implements Comando {

    private static final String NOMBRE = "gremio";

    private final GremioService gremios;

    public GremioComando(GremioService gremios) {
        this.gremios = gremios;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData nombre = opcion(OptionType.STRING, "nombre", "comando.creargremio.opcion.nombre");
        OptionData usuarioAdd = opcion(OptionType.USER, "usuario", "comando.gremioadd.opcion.usuario");
        OptionData usuarioKick = opcion(OptionType.USER, "usuario", "comando.gremiokick.opcion.usuario");
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.gremio.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.gremio.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.gremio.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("crear", "comando.creargremio.descripcion").addOptions(nombre),
                        sub("ver", "comando.gremio.descripcion"),
                        sub("add", "comando.gremioadd.descripcion").addOptions(usuarioAdd),
                        sub("kick", "comando.gremiokick.descripcion").addOptions(usuarioKick),
                        sub("salir", "comando.salirgremio.descripcion"),
                        sub("disolver", "comando.disolvergremio.descripcion"));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    private static OptionData opcion(OptionType tipo, String nombre, String claveDesc) {
        return new OptionData(tipo, nombre, Messages.get(Messages.ES, claveDesc), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        switch (evento.getSubcommandName() == null ? "ver" : evento.getSubcommandName()) {
            case "crear" -> crear(evento);
            case "add" -> add(evento);
            case "kick" -> kick(evento);
            case "salir" -> salir(evento);
            case "disolver" -> disolver(evento);
            default -> ver(evento);
        }
    }

    private void crear(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String nombre = evento.getOption("nombre").getAsString().trim();
        long dueno = evento.getUser().getIdLong();
        evento.deferReply(false).queue();
        ResultadoCrear r = gremios.crear(dueno, nombre);
        if (r.estado() == GremioService.EstadoCrear.OK && evento.getGuild() != null) {
            GremioCanal.crear(evento.getGuild(), nombre, dueno,
                    canalId -> gremios.fijarCanal(r.gremioId(), canalId));
        }
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "gremio.creado", nombre, GremioService.COSTE_CREAR);
            case YA_EN_GREMIO -> Messages.get(locale, "gremio.yaengremio");
            case NOMBRE_INVALIDO -> Messages.get(locale, "gremio.nombreinvalido",
                    GremioService.NOMBRE_MIN, GremioService.NOMBRE_MAX);
            case NOMBRE_USADO -> Messages.get(locale, "gremio.nombreusado");
            case SIN_SALDO -> Messages.get(locale, "gremio.sinsaldo", GremioService.COSTE_CREAR);
        };
        responder(evento, locale, mensaje);
    }

    private void ver(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferReply(false).queue();
        Optional<Info> info = gremios.info(evento.getUser().getIdLong());
        if (info.isEmpty()) {
            responder(evento, locale, Messages.get(locale, "gremio.notienes"));
            return;
        }
        Info i = info.get();
        String miembros = i.miembros().stream().map(id -> "<@" + id + ">")
                .collect(Collectors.joining(", "));
        String desc = Messages.get(locale, "gremio.cuerpo", "<@" + i.gremio().dueno() + ">",
                i.miembros().size(), GremioService.MAX_MIEMBROS, miembros);
        evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "gremio.titulo", i.gremio().nombre()), desc).build()).queue();
    }

    private void add(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        User objetivo = evento.getOption("usuario").getAsUser();
        if (objetivo.isBot()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "regalar.bot"))).setEphemeral(true).queue();
            return;
        }
        evento.deferReply(false).queue();
        ResultadoMiembro r = gremios.anadir(evento.getUser().getIdLong(), objetivo.getIdLong());
        if (r.estado() == GremioService.EstadoMiembro.OK && evento.getGuild() != null) {
            GremioCanal.anadir(evento.getGuild(), r.gremio().canalId(), objetivo.getIdLong());
        }
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "gremio.anadido", objetivo.getAsMention());
            case NO_ERES_DUENO -> Messages.get(locale, "gremio.noeresdueno");
            case OBJETIVO_EN_GREMIO -> Messages.get(locale, "gremio.objetivoengremio");
            case LLENO -> Messages.get(locale, "gremio.lleno", GremioService.MAX_MIEMBROS);
            default -> Messages.get(locale, "comando.error.generico");
        };
        responder(evento, locale, mensaje);
    }

    private void kick(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        User objetivo = evento.getOption("usuario").getAsUser();
        evento.deferReply(false).queue();
        ResultadoMiembro r = gremios.expulsar(evento.getUser().getIdLong(), objetivo.getIdLong());
        if (r.estado() == GremioService.EstadoMiembro.OK && evento.getGuild() != null) {
            GremioCanal.quitar(evento.getGuild(), r.gremio().canalId(), objetivo.getIdLong());
        }
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "gremio.expulsado", objetivo.getAsMention());
            case NO_ERES_DUENO -> Messages.get(locale, "gremio.noeresdueno");
            case NO_MIEMBRO -> Messages.get(locale, "gremio.nomiembro");
            case NO_PUEDES -> Messages.get(locale, "gremio.nopuedesati");
            default -> Messages.get(locale, "comando.error.generico");
        };
        responder(evento, locale, mensaje);
    }

    private void salir(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        long usuario = evento.getUser().getIdLong();
        evento.deferReply(false).queue();
        ResultadoMiembro r = gremios.salir(usuario);
        if (r.estado() == GremioService.EstadoMiembro.OK && evento.getGuild() != null) {
            GremioCanal.quitar(evento.getGuild(), r.gremio().canalId(), usuario);
        }
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "gremio.saliste");
            case NO_TIENES -> Messages.get(locale, "gremio.notienes");
            case ERES_DUENO -> Messages.get(locale, "gremio.eresdueno");
            default -> Messages.get(locale, "comando.error.generico");
        };
        responder(evento, locale, mensaje);
    }

    private void disolver(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferReply(false).queue();
        ResultadoMiembro r = gremios.disolver(evento.getUser().getIdLong());
        if (r.estado() == GremioService.EstadoMiembro.OK && evento.getGuild() != null) {
            GremioCanal.eliminar(evento.getGuild(), r.gremio().canalId());
        }
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "gremio.disuelto");
            case NO_TIENES -> Messages.get(locale, "gremio.notienes");
            case NO_ERES_DUENO -> Messages.get(locale, "gremio.noeresdueno");
            default -> Messages.get(locale, "comando.error.generico");
        };
        responder(evento, locale, mensaje);
    }

    private static void responder(SlashCommandInteractionEvent evento, Locale locale, String mensaje) {
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
