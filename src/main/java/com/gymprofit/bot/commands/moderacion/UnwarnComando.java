package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ModeracionService;
import net.dv8tion.jda.api.Permission;
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
 * {@code /unwarn}: revoca (soft-delete) un aviso por su id (solo altos cargos). El aviso se conserva
 * en el historial marcado como inactivo, pero deja de contar para el escalado.
 */
public final class UnwarnComando implements Comando {

    private static final String NOMBRE = "unwarn";

    private final ModeracionService moderacion;

    public UnwarnComando(ModeracionService moderacion) {
        this.moderacion = moderacion;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData id = new OptionData(OptionType.INTEGER, "id",
                Messages.get(Messages.ES, "comando.unwarn.opcion.id"), true)
                .setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.unwarn.opcion.id"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.unwarn.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.unwarn.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.unwarn.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOptions(id);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (!ModHelper.esAltoCargo(evento.getMember())) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, "mod.noautorizado"))).setEphemeral(true).queue();
            return;
        }
        long id = evento.getOption("id").getAsLong();
        boolean revocado = moderacion.revocarWarn(id);
        String clave = revocado ? "unwarn.hecho" : "unwarn.noexiste";
        evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, clave, id))).setEphemeral(true).queue();
    }
}
