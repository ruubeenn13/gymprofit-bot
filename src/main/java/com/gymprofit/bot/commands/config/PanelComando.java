package com.gymprofit.bot.commands.config;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.commands.moderacion.ModHelper;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.events.TicketListener;
import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/**
 * {@code /panel}: (re)publica el panel de auto-roles (menús de objetivo y notificaciones) en un
 * canal (por defecto {@code 🎭・roles}) y lo fija. Útil si se borró o se quiere en otro canal. Solo
 * altos cargos. (El panel de tickets llegará con el bloque de Tickets.)
 */
public final class PanelComando implements Comando {

    private static final String NOMBRE = "panel";
    private static final String CANAL_ROLES = "🎭・roles";
    private static final String CANAL_SOPORTE = "🎫・soporte";

    @Override
    public SlashCommandData definicion() {
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

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.panel.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.panel.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.panel.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addOptions(tipo, canal);
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
            evento.reply(Messages.get(locale, "contenido.sincanal", canalDefecto))
                    .setEphemeral(true).queue();
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
        evento.getHook().sendMessage(Messages.get(locale, "panel.publicado")).queue();
    }
}
