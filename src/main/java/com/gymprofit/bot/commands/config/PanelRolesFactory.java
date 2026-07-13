package com.gymprofit.bot.commands.config;

import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.Locale;

/**
 * Construye el mensaje del <b>panel de auto-roles</b> (embed + menús de objetivo y notificaciones).
 * Compartido por {@code /setup} (al montar el servidor) y {@code /panel} (para republicarlo). Los
 * customId {@code roles:objetivo} y {@code roles:notificaciones} los maneja {@code PanelRolesListener}.
 */
public final class PanelRolesFactory {

    private PanelRolesFactory() {
    }

    /** Mensaje completo del panel de roles en el idioma dado. */
    public static MessageCreateData mensaje(Locale locale) {
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ANUNCIO, locale,
                Messages.get(locale, "panel.roles.titulo"),
                Messages.get(locale, "panel.roles.desc"))
                .setThumbnail(EmbedFactory.iconoUrl())
                .build();

        StringSelectMenu objetivo = StringSelectMenu.create("roles:objetivo")
                .setPlaceholder(Messages.get(locale, "panel.roles.objetivo.placeholder"))
                .addOption(Messages.get(locale, "config.campo.rol.fuerza"), "FUERZA", Emoji.fromUnicode("💪"))
                .addOption(Messages.get(locale, "config.campo.rol.cardio"), "CARDIO", Emoji.fromUnicode("🏃"))
                .addOption(Messages.get(locale, "config.campo.rol.perdidapeso"), "PERDIDA_PESO", Emoji.fromUnicode("⚖️"))
                .addOption(Messages.get(locale, "config.campo.rol.general"), "GENERAL", Emoji.fromUnicode("🌟"))
                .build();

        StringSelectMenu notif = StringSelectMenu.create("roles:notificaciones")
                .setPlaceholder(Messages.get(locale, "panel.roles.notif.placeholder"))
                .setMinValues(0)
                .setMaxValues(2)
                .addOption(Messages.get(locale, "panel.roles.notif.avisos"), "AVISOS", Emoji.fromUnicode("📣"))
                .addOption(Messages.get(locale, "panel.roles.notif.retos"), "RETOS", Emoji.fromUnicode("🎯"))
                .build();

        return new MessageCreateBuilder()
                .setEmbeds(embed)
                .setComponents(ActionRow.of(objetivo), ActionRow.of(notif))
                .build();
    }
}
