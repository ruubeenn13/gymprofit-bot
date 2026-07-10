package com.gymprofit.bot.commands.comunidad;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EventoService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /evento}: fija el próximo evento (nombre + fecha) que muestra en cuenta atrás el contador
 * «⏳ Evento» de SERVER STATS. Solo staff con «Gestionar servidor». La fecha se introduce como
 * {@code 2026-07-20 18:30} (hora peninsular).
 */
public final class EventoComando implements Comando {

    private static final String NOMBRE = "evento";

    private final EventoService eventos;

    public EventoComando(EventoService eventos) {
        this.eventos = eventos;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData nombre = new OptionData(OptionType.STRING, "nombre",
                Messages.get(Messages.ES, "comando.evento.opcion.nombre"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.evento.opcion.nombre"));
        OptionData fecha = new OptionData(OptionType.STRING, "fecha",
                Messages.get(Messages.ES, "comando.evento.opcion.fecha"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.evento.opcion.fecha"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.evento.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.evento.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.evento.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addOptions(nombre, fecha);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String nombre = evento.getOption("nombre").getAsString();
        String fechaTexto = evento.getOption("fecha").getAsString();

        Optional<LocalDateTime> fecha = EventoService.parsearFecha(fechaTexto);
        if (fecha.isEmpty()) {
            evento.reply(Messages.get(locale, "evento.fecha_invalida")).setEphemeral(true).queue();
            return;
        }

        evento.deferReply(true).queue();
        long finEpoch = eventos.fijarEvento(evento.getGuild().getIdLong(), nombre, fecha.get());

        // Timestamps dinámicos de Discord: fecha absoluta y cuenta atrás relativa que se actualiza.
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ANUNCIO, locale,
                Messages.get(locale, "evento.fijado.titulo"),
                Messages.get(locale, "evento.fijado.desc", nombre,
                        EmbedFactory.fechaLarga(finEpoch), EmbedFactory.tiempoRelativo(finEpoch)))
                .build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }
}
