package com.gymprofit.bot.commands.contenido;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.commands.moderacion.ModHelper;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /redes}: publica (o actualiza) las redes sociales oficiales en {@code 📱・redes-sociales}.
 * Cada red es una opción opcional; se listan solo las que se rellenen. Solo altos cargos.
 */
public final class RedesComando implements Comando {

    private static final String NOMBRE = "redes";
    private static final String CANAL = "📱・redes-sociales";

    /** Opción → emoji, en orden de aparición en el embed. */
    private static final Map<String, String> REDES = new LinkedHashMap<>();

    static {
        REDES.put("instagram", "📸");
        REDES.put("tiktok", "🎵");
        REDES.put("youtube", "▶️");
        REDES.put("twitch", "🎮");
        REDES.put("x", "🐦");
    }

    @Override
    public SlashCommandData definicion() {
        SlashCommandData cmd = Commands.slash(NOMBRE,
                        Messages.get(Messages.ES, "comando.redes.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.redes.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.redes.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
        for (String red : REDES.keySet()) {
            cmd.addOptions(new OptionData(OptionType.STRING, red,
                    Messages.get(Messages.ES, "comando.redes.opcion." + red), false)
                    .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                            Messages.get(Messages.EN, "comando.redes.opcion." + red)));
        }
        return cmd;
    }

    @Override
    public Categoria categoria() {
        return Categoria.MODERACION;
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (!ModHelper.esAltoCargo(evento.getMember())) {
            evento.reply(Messages.get(locale, "mod.noautorizado")).setEphemeral(true).queue();
            return;
        }
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
            evento.reply(Messages.get(locale, "redes.vacio")).setEphemeral(true).queue();
            return;
        }
        TextChannel canal = evento.getGuild().getTextChannelsByName(CANAL, false)
                .stream().findFirst().orElse(null);
        if (canal == null) {
            evento.reply(Messages.get(locale, "contenido.sincanal", CANAL)).setEphemeral(true).queue();
            return;
        }

        evento.deferReply(true).queue();
        MessageEmbed embed = EmbedFactory.base(EmbedFactory.Tipo.ANUNCIO, locale,
                Messages.get(locale, "redes.titulo"), desc.toString().trim()).build();
        canal.sendMessageEmbeds(embed).queue(
                ok -> evento.getHook().sendMessage(Messages.get(locale, "redes.publicado")).queue(),
                err -> evento.getHook().sendMessage(Messages.get(locale, "comando.error.generico")).queue());
    }

    private static String capitalizar(String s) {
        return "x".equals(s) ? "X" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
