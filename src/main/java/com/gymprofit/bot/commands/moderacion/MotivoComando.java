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
 * {@code /motivo}: edita (o añade) el motivo de una sanción del historial por su id de caso. El nuevo
 * texto se guarda cifrado. Solo altos cargos.
 */
public final class MotivoComando implements Comando {

    private static final String NOMBRE = "motivo";

    private final ModeracionService moderacion;

    public MotivoComando(ModeracionService moderacion) {
        this.moderacion = moderacion;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData caso = new OptionData(OptionType.INTEGER, "caso_id",
                Messages.get(Messages.ES, "comando.motivo.opcion.caso"), true).setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.motivo.opcion.caso"));
        OptionData texto = new OptionData(OptionType.STRING, "texto",
                Messages.get(Messages.ES, "comando.motivo.opcion.texto"), true).setMaxLength(500)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.motivo.opcion.texto"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.motivo.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.motivo.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.motivo.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOptions(caso, texto);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (!ModHelper.esAltoCargo(evento.getMember())) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, "mod.noautorizado"))).setEphemeral(true).queue();
            return;
        }
        long casoId = evento.getOption("caso_id").getAsLong();
        String texto = evento.getOption("texto").getAsString();

        evento.deferReply(true).queue();
        boolean editado = moderacion.editarMotivo(casoId, texto);
        String clave = editado ? "motivo.hecho" : "motivo.noexiste";
        evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, clave, casoId))).queue();
    }
}
