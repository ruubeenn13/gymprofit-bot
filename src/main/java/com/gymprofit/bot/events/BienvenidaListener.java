package com.gymprofit.bot.events;

import com.gymprofit.bot.db.ConfigServidor;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ConfigServidorService;
import com.gymprofit.bot.services.ConfigServidorService.Objetivo;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Bienvenida y auto-roles (SPEC §5 F1). Cuando entra un miembro, si hay canal de bienvenida
 * configurado ({@code /config canal bienvenida}), publica un embed de bienvenida con un menú de
 * selección de objetivo (Fuerza/Cardio/Pérdida de peso/General). Al elegir, asigna el rol que el
 * staff haya configurado para ese objetivo.
 */
public final class BienvenidaListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(BienvenidaListener.class);

    /** ID del componente del menú de objetivo (para enrutar la interacción). */
    private static final String MENU_OBJETIVO = "bienvenida:objetivo";

    private final ConfigServidorService configService;

    public BienvenidaListener(ConfigServidorService configService) {
        this.configService = configService;
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent evento) {
        ConfigServidor cfg = configService.obtener(evento.getGuild().getIdLong());
        if (cfg.canalBienvenida() == null) {
            return; // Sin canal configurado no se publica bienvenida.
        }
        TextChannel canal = evento.getGuild().getTextChannelById(cfg.canalBienvenida());
        if (canal == null || !canal.canTalk()) {
            log.warn("Canal de bienvenida {} no disponible en el servidor {}",
                    cfg.canalBienvenida(), evento.getGuild().getId());
            return;
        }

        Locale locale = Messages.desdeTag(cfg.idioma());
        MessageEmbed embed = EmbedFactory.base(EmbedFactory.Tipo.BIENVENIDA, locale,
                        Messages.get(locale, "bienvenida.titulo", evento.getGuild().getName()),
                        Messages.get(locale, "bienvenida.desc", evento.getMember().getAsMention()))
                .setThumbnail(evento.getUser().getEffectiveAvatarUrl())
                .build();

        canal.sendMessageEmbeds(embed).setActionRow(menuObjetivo(locale)).queue();
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evento) {
        if (!MENU_OBJETIVO.equals(evento.getComponentId()) || evento.getGuild() == null) {
            return;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        Objetivo objetivo = Objetivo.valueOf(evento.getValues().get(0));

        ConfigServidor cfg = configService.obtener(evento.getGuild().getIdLong());
        Long rolId = ConfigServidorService.rolDe(cfg, objetivo);
        Role rol = (rolId == null) ? null : evento.getGuild().getRoleById(rolId);
        if (rol == null) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale, Messages.get(locale, "bienvenida.rol.noconfig"))).setEphemeral(true).queue();
            return;
        }

        // La asignación es asíncrona; se difiere la respuesta para no agotar los 3 s del ack.
        evento.deferReply(true).queue();
        evento.getGuild().addRoleToMember(evento.getMember(), rol).queue(
                ok -> evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale, Messages.get(locale, "bienvenida.rol.asignado", rol.getName())))
                        .queue(),
                error -> {
                    log.error("No se pudo asignar el rol {} en {}", rolId, evento.getGuild().getId(), error);
                    evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale, Messages.get(locale, "bienvenida.rol.error"))).queue();
                });
    }

    /** Menú de selección de objetivo con las cuatro opciones. */
    private static StringSelectMenu menuObjetivo(Locale locale) {
        return StringSelectMenu.create(MENU_OBJETIVO)
                .setPlaceholder(Messages.get(locale, "bienvenida.menu.placeholder"))
                .addOption(Messages.get(locale, "config.campo.rol.fuerza"),
                        Objetivo.FUERZA.name(), Emoji.fromUnicode("💪"))
                .addOption(Messages.get(locale, "config.campo.rol.cardio"),
                        Objetivo.CARDIO.name(), Emoji.fromUnicode("🏃"))
                .addOption(Messages.get(locale, "config.campo.rol.perdidapeso"),
                        Objetivo.PERDIDA_PESO.name(), Emoji.fromUnicode("⚖️"))
                .addOption(Messages.get(locale, "config.campo.rol.general"),
                        Objetivo.GENERAL.name(), Emoji.fromUnicode("🌟"))
                .build();
    }
}
