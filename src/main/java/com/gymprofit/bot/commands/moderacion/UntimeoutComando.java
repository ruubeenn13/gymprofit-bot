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

/** {@code /untimeout}: retira el timeout nativo de un miembro (solo altos cargos). */
public final class UntimeoutComando implements Comando {

    private static final String NOMBRE = "untimeout";

    private final ModeracionService moderacion;
    private final ConfigServidorService config;

    public UntimeoutComando(ModeracionService moderacion, ConfigServidorService config) {
        this.moderacion = moderacion;
        this.config = config;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.untimeout.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.untimeout.opcion.usuario"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.untimeout.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.untimeout.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.untimeout.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOptions(usuario);
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

        evento.deferReply(true).queue();
        objetivo.removeTimeout().reason("Untimeout").queue();
        moderacion.registrar(evento.getGuild().getIdLong(), objetivoUser.getIdLong(),
                actor.getIdLong(), "UNTIMEOUT", null, null, null);

        MessageEmbed embed = ModHelper.embed(locale, Messages.get(locale, "untimeout.titulo"),
                Messages.get(locale, "untimeout.hecho", objetivoUser.getAsMention()));
        evento.getHook().sendMessageEmbeds(embed).queue();
        ModHelper.registrarEnLogs(evento.getGuild(), config, embed);
    }
}
