package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.TrabajoService;
import com.gymprofit.bot.services.TrabajoService.ResultadoElegir;
import com.gymprofit.bot.services.TrabajoService.ResultadoWork;
import com.gymprofit.bot.services.Trabajos;
import com.gymprofit.bot.util.Duraciones;
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
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * {@code /trabajo} con subcomandos (lista, elegir, currar). Consolida el empleo en un solo comando
 * para no gastar cupo de slash commands (límite de 100 de Discord).
 *
 * <p>{@code lista} muestra el catálogo por tier con sueldo y requisito; {@code elegir} fija tu empleo
 * si cumples el nivel y te da su rol cosmético; {@code currar} trabaja un turno (gana coins, gasta
 * energía, con cooldown). Los tres son <b>públicos</b>: la economía se juega a la vista de todos.
 */
public final class TrabajoComando implements Comando {

    private static final String NOMBRE = "trabajo";
    /** Prefijo de los roles cosméticos de trabajo (identifica y limpia el anterior). */
    private static final String PREFIJO_ROL = "💼 ";
    private static final Color COLOR_ROL_TRABAJO = new Color(0x9B59B6);

    private final TrabajoService trabajos;

    public TrabajoComando(TrabajoService trabajos) {
        this.trabajos = trabajos;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData trabajo = new OptionData(OptionType.STRING, "trabajo",
                Messages.get(Messages.ES, "comando.elegirtrabajo.opcion.trabajo"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.elegirtrabajo.opcion.trabajo"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.trabajo.familia"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.trabajo.familia"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.trabajo.familia"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("lista", "comando.trabajos.descripcion"),
                        sub("elegir", "comando.elegirtrabajo.descripcion").addOptions(trabajo),
                        sub("currar", "comando.work.descripcion"));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String sub = evento.getSubcommandName() == null ? "lista" : evento.getSubcommandName();
        switch (sub) {
            case "lista" -> lista(evento, locale);
            case "elegir" -> elegir(evento, locale);
            case "currar" -> currar(evento, locale);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
        }
    }

    private void lista(SlashCommandInteractionEvent evento, Locale locale) {
        StringBuilder sb = new StringBuilder();
        int tierActual = 0;
        for (Trabajos t : Trabajos.CATALOGO) {
            if (t.tier() != tierActual) {
                tierActual = t.tier();
                sb.append("\n**").append(Messages.get(locale, "trabajos.tier", tierActual))
                        .append("**\n");
            }
            sb.append(Messages.get(locale, "trabajos.linea",
                    t.id(), Messages.get(locale, "trabajo." + t.id()), t.sector(),
                    t.salarioMin(), t.salarioMax(), t.requisitoNivel())).append('\n');
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "trabajos.titulo"),
                Messages.get(locale, "trabajos.intro") + "\n" + sb).build();
        evento.replyEmbeds(embed).queue();
    }

    private void elegir(SlashCommandInteractionEvent evento, Locale locale) {
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

    private void currar(SlashCommandInteractionEvent evento, Locale locale) {
        evento.deferReply(false).queue();
        ResultadoWork r = trabajos.trabajar(evento.getUser().getIdLong(), Instant.now());
        // Dormido: en vez de un aviso seco, el embed con botones para seguir durmiendo o despertar.
        if (r.estado() == TrabajoService.EstadoWork.DORMIDO) {
            evento.getHook().sendMessageEmbeds(DescansoComando.embedBloqueado(locale))
                    .setComponents(DescansoComando.botonesBloqueado(locale,
                            evento.getUser().getIdLong()))
                    .queue();
            return;
        }
        String desc = switch (r.estado()) {
            case OK -> Messages.get(locale, "work.ok", r.pago(), r.energiaRestante());
            case SIN_TRABAJO -> Messages.get(locale, "work.sintrabajo");
            case EN_COOLDOWN -> Messages.get(locale, "work.cooldown",
                    Duraciones.formatear(r.segundosRestantes()));
            case SIN_ENERGIA -> Messages.get(locale, "work.sinenergia");
            // Inalcanzable: DORMIDO sale por el return de arriba (necesita botones, no solo texto).
            case DORMIDO -> throw new IllegalStateException("DORMIDO ya tratado");
        };
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "work.titulo"), desc).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
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
