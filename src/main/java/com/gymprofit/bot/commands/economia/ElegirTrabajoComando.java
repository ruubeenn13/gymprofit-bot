package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.TrabajoService;
import com.gymprofit.bot.services.TrabajoService.ResultadoElegir;
import com.gymprofit.bot.services.Trabajos;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;
import java.util.Locale;

/** {@code /elegir-trabajo}: fija tu trabajo actual (si cumples el requisito de nivel). */
public final class ElegirTrabajoComando implements Comando {

    private static final String NOMBRE = "elegir-trabajo";
    /** Prefijo de los roles cosméticos de trabajo (identifica y limpia el anterior). */
    private static final String PREFIJO_ROL = "💼 ";
    private static final java.awt.Color COLOR_ROL_TRABAJO = new java.awt.Color(0x9B59B6);

    private final TrabajoService trabajos;

    public ElegirTrabajoComando(TrabajoService trabajos) {
        this.trabajos = trabajos;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData trabajo = new OptionData(OptionType.STRING, "trabajo",
                Messages.get(Messages.ES, "comando.elegirtrabajo.opcion.trabajo"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.elegirtrabajo.opcion.trabajo"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.elegirtrabajo.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.elegirtrabajo.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.elegirtrabajo.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(trabajo);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String id = evento.getOption("trabajo").getAsString();

        evento.deferReply(false).queue();
        ResultadoElegir r = trabajos.elegir(evento.getUser().getIdLong(), id);
        if (r == ResultadoElegir.OK && evento.getMember() != null) {
            asignarRolTrabajo(evento.getGuild(), evento.getMember(), id);
        }
        String mensaje = switch (r) {
            case OK -> Messages.get(locale, "elegirtrabajo.ok", Messages.get(locale, "trabajo." + id));
            case NO_EXISTE -> Messages.get(locale, "elegirtrabajo.noexiste");
            case REQUISITO -> Messages.get(locale, "elegirtrabajo.requisito",
                    Trabajos.porId(id).map(Trabajos::requisitoNivel).orElse(0));
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }

    /**
     * Asigna el rol cosmético del trabajo (prefijo {@value #PREFIJO_ROL}), quitando el rol de
     * trabajo anterior. El rol se crea <b>solo si no existe</b> (lazy) y se reutiliza por nombre, para
     * no agotar el cupo diario de creación de roles de Discord. El nombre va en español (fijo) para
     * que sea el mismo rol para todos.
     */
    private void asignarRolTrabajo(Guild guild, Member miembro, String id) {
        String nombreRol = PREFIJO_ROL + Messages.get(Messages.ES, "trabajo." + id);
        List<Role> viejos = miembro.getRoles().stream()
                .filter(rol -> rol.getName().startsWith(PREFIJO_ROL) && !rol.getName().equals(nombreRol))
                .toList();
        for (Role viejo : viejos) {
            guild.removeRoleFromMember(miembro, viejo).reason("Cambio de trabajo").queue(null, e -> { });
        }
        Role existente = guild.getRolesByName(nombreRol, false).stream().findFirst().orElse(null);
        if (existente != null) {
            guild.addRoleToMember(miembro, existente).queue(null, e -> { });
        } else {
            guild.createRole().setName(nombreRol).setColor(COLOR_ROL_TRABAJO)
                    .reason("Rol de trabajo del RPG")
                    .queue(creado -> guild.addRoleToMember(miembro, creado).queue(null, e -> { }),
                            e -> { });
        }
    }
}
