package com.gymprofit.bot.events;

import com.gymprofit.bot.commands.moderacion.ModHelper;
import com.gymprofit.bot.db.Ticket;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.TicketService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Sistema de tickets por botón: {@code ticket:abrir} crea un canal privado (autor + staff) en la
 * categoría TICKETS; {@code ticket:cerrar} guarda la transcripción en {@code 🗄️・logs-tickets} y en la
 * BD, y borra el canal. El panel se publica con {@code /panel tipo:ticket}.
 */
public final class TicketListener extends ListenerAdapter {

    public static final String BOTON_ABRIR = "ticket:abrir";
    public static final String BOTON_CERRAR = "ticket:cerrar";
    private static final String CATEGORIA_TICKETS = "▬▬ 🎫 TICKETS ▬▬";
    private static final String CANAL_LOGS = "🗄️・logs-tickets";
    private static final int MAX_MENSAJES_TRANSCRIPT = 200;

    private final TicketService tickets;

    public TicketListener(TicketService tickets) {
        this.tickets = tickets;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        if (evento.getGuild() == null) {
            return;
        }
        if (BOTON_ABRIR.equals(evento.getComponentId())) {
            abrir(evento);
        } else if (BOTON_CERRAR.equals(evento.getComponentId())) {
            cerrar(evento);
        }
    }

    private void abrir(ButtonInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        Guild guild = evento.getGuild();
        Member autor = evento.getMember();
        if (tickets.tieneAbierto(autor.getIdLong())) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, "ticket.yaabierto"))).setEphemeral(true).queue();
            return;
        }
        Category categoria = guild.getCategoriesByName(CATEGORIA_TICKETS, false)
                .stream().findFirst().orElse(null);
        if (categoria == null) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, "ticket.sincategoria"))).setEphemeral(true).queue();
            return;
        }
        evento.deferReply(true).queue();

        var accion = categoria.createTextChannel("ticket-" + autor.getUser().getName())
                .addPermissionOverride(guild.getPublicRole(), 0L, Permission.VIEW_CHANNEL.getRawValue())
                .addMemberPermissionOverride(autor.getIdLong(),
                        Permission.VIEW_CHANNEL.getRawValue() | Permission.MESSAGE_SEND.getRawValue()
                                | Permission.MESSAGE_HISTORY.getRawValue(), 0L)
                .addMemberPermissionOverride(guild.getSelfMember().getIdLong(),
                        Permission.VIEW_CHANNEL.getRawValue() | Permission.MESSAGE_SEND.getRawValue(), 0L);
        for (Role staff : rolesStaff(guild)) {
            accion = accion.addPermissionOverride(staff,
                    Permission.VIEW_CHANNEL.getRawValue() | Permission.MESSAGE_SEND.getRawValue(), 0L);
        }
        accion.queue(canal -> {
            long id = tickets.registrar(autor.getIdLong(), canal.getIdLong(), "Soporte");
            MessageEmbed embed = EmbedFactory.base(EmbedFactory.Tipo.TICKET, locale,
                    Messages.get(locale, "ticket.abierto.titulo"),
                    Messages.get(locale, "ticket.abierto.desc", autor.getAsMention())).build();
            canal.sendMessageEmbeds(embed)
                    .addActionRow(Button.danger(BOTON_CERRAR, Messages.get(locale, "ticket.boton.cerrar")))
                    .queue();
            evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, "ticket.creado", canal.getAsMention())))
                    .queue();
        }, err -> evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, "comando.error.generico"))).queue());
    }

    private void cerrar(ButtonInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        TextChannel canal = evento.getChannel().asTextChannel();
        Optional<Ticket> ticket = tickets.porCanal(canal.getIdLong());
        if (ticket.isEmpty()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, "ticket.nocanal"))).setEphemeral(true).queue();
            return;
        }
        // Solo el autor o el staff pueden cerrar.
        boolean autorizado = ModHelper.esAltoCargo(evento.getMember())
                || evento.getUser().getIdLong() == ticket.get().discordId();
        if (!autorizado) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, "mod.noautorizado"))).setEphemeral(true).queue();
            return;
        }
        evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.MODERACION, locale, Messages.get(locale, "ticket.cerrando"))).queue();

        canal.getIterableHistory().takeAsync(MAX_MENSAJES_TRANSCRIPT).thenAccept(mensajes -> {
            List<String[]> lineas = new ArrayList<>();
            List<Message> orden = new ArrayList<>(mensajes);
            Collections.reverse(orden); // más antiguos primero
            for (Message m : orden) {
                lineas.add(new String[]{m.getAuthor().getName(), m.getContentDisplay()});
            }
            String transcript = TicketService.formatearTranscript(lineas);
            tickets.cerrar(ticket.get().id(), transcript);
            publicarEnLogs(evento.getGuild(), locale, canal.getName(), transcript);
            canal.delete().queueAfter(3, TimeUnit.SECONDS);
        });
    }

    private void publicarEnLogs(Guild guild, Locale locale, String nombreCanal, String transcript) {
        TextChannel logs = guild.getTextChannelsByName(CANAL_LOGS, false)
                .stream().findFirst().orElse(null);
        if (logs == null) {
            return;
        }
        MessageEmbed embed = EmbedFactory.base(EmbedFactory.Tipo.TICKET, locale,
                Messages.get(locale, "ticket.log.titulo"),
                Messages.get(locale, "ticket.log.desc", nombreCanal)).build();
        byte[] datos = (transcript.isEmpty() ? "(vacío)" : transcript).getBytes(StandardCharsets.UTF_8);
        logs.sendMessageEmbeds(embed)
                .addFiles(FileUpload.fromData(datos, nombreCanal + ".txt"))
                .queue();
    }

    private static List<Role> rolesStaff(Guild guild) {
        List<Role> roles = new ArrayList<>();
        for (String nombre : ModHelper.ROLES_ALTOS) {
            guild.getRolesByName(nombre, false).stream().findFirst().ifPresent(roles::add);
        }
        return roles;
    }
}
