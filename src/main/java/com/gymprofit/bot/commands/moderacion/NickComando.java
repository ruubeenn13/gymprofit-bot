package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ConfigServidorService;
import com.gymprofit.bot.services.ModeracionService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/**
 * {@code /nick}: fuerza el apodo de un miembro (p. ej. nombres troll). Guarda el apodo anterior
 * <b>cifrado</b> en el historial. Solo altos cargos.
 */
public final class NickComando implements Comando {

    private static final String NOMBRE = "nick";
    private static final int MAX_NICK = 32;

    private final ModeracionService moderacion;
    private final ConfigServidorService config;

    public NickComando(ModeracionService moderacion, ConfigServidorService config) {
        this.moderacion = moderacion;
        this.config = config;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.nick.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.nick.opcion.usuario"));
        OptionData apodo = new OptionData(OptionType.STRING, "apodo",
                Messages.get(Messages.ES, "comando.nick.opcion.apodo"), true).setMaxLength(MAX_NICK)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.nick.opcion.apodo"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.nick.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.nick.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.nick.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.NICKNAME_MANAGE))
                .addOptions(usuario, apodo);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        Member actor = evento.getMember();
        if (!ModHelper.esAltoCargo(actor)) {
            evento.reply(Messages.get(locale, "mod.noautorizado")).setEphemeral(true).queue();
            return;
        }
        Member objetivo = evento.getOption("usuario").getAsMember();
        User objetivoUser = evento.getOption("usuario").getAsUser();
        if (objetivo == null) {
            evento.reply(Messages.get(locale, "mod.noenservidor")).setEphemeral(true).queue();
            return;
        }
        if (!ModHelper.puedeModerar(actor, objetivo)) {
            evento.reply(Messages.get(locale, "mod.nopuedes")).setEphemeral(true).queue();
            return;
        }
        String apodo = evento.getOption("apodo").getAsString();
        String anterior = objetivo.getNickname(); // puede ser null (usaba su nombre)

        evento.deferReply(true).queue();
        objetivo.modifyNickname(apodo).reason("Cambio de apodo por moderación").queue();
        moderacion.registrar(evento.getGuild().getIdLong(), objetivoUser.getIdLong(),
                actor.getIdLong(), "NICK", null, anterior, null);

        MessageEmbed embed = ModHelper.embed(locale, Messages.get(locale, "nick.titulo"),
                Messages.get(locale, "nick.hecho", objetivoUser.getAsMention(), apodo));
        evento.getHook().sendMessageEmbeds(embed).queue();
        ModHelper.registrarEnLogs(evento.getGuild(), config, embed);
    }
}
