package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.events.ReintentoRegistro;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Camas;
import com.gymprofit.bot.services.DescansoService;
import com.gymprofit.bot.services.DescansoService.ResultadoDespertar;
import com.gymprofit.bot.services.DescansoService.ResultadoDormir;
import com.gymprofit.bot.services.DescansoService.Desglose;
import com.gymprofit.bot.services.DescansoService.Vista;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.util.Duraciones;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.time.Instant;
import java.util.Locale;

/**
 * {@code /descansar} con subcomandos (dormir, despertar, estado). Dormir es un estado: te acuestas y
 * al despertar recuperas energía según lo que hayas dormido de verdad y según tu cama.
 *
 * <p>Los tres son <b>públicos</b>: no hay nada sensible y la economía se juega a la vista de todos.
 * Las vistas se construyen aquí y las reutiliza {@code DescansoListener} (patrón de
 * {@code PelearComando}).
 */
public final class DescansoComando implements Comando {

    private static final String NOMBRE = "descansar";

    /** customId del botón «seguir durmiendo» (lo maneja {@code DescansoListener}). */
    public static final String BOTON_SEGUIR = "descanso:seguir";
    /** customId del botón «despertar» (lo maneja {@code DescansoListener}). */
    public static final String BOTON_DESPERTAR = "descanso:despertar";

    private final DescansoService descanso;
    private final ReintentoRegistro reintentos;

    public DescansoComando(DescansoService descanso, ReintentoRegistro reintentos) {
        this.descanso = descanso;
        this.reintentos = reintentos;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData sitio = new OptionData(OptionType.STRING, "sitio",
                Messages.get(Messages.ES, "comando.descansar.opcion.sitio"), false)
                .addChoice(Messages.get(Messages.ES, "descansar.sitio.propio"), "propio")
                .addChoice(Messages.get(Messages.ES, "descansar.sitio.hotel"), "hotel")
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.descansar.opcion.sitio"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.descansar.familia"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.descansar.familia"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.descansar.familia"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("dormir", "comando.descansar.dormir").addOptions(sitio),
                        sub("despertar", "comando.descansar.despertar"),
                        sub("estado", "comando.descansar.estado"));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String sub = evento.getSubcommandName() == null ? "estado" : evento.getSubcommandName();
        switch (sub) {
            case "dormir" -> dormir(evento, locale);
            case "despertar" -> despertar(evento, locale);
            case "estado" -> estado(evento, locale);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
        }
    }

    private void dormir(SlashCommandInteractionEvent evento, Locale locale) {
        boolean hotel = evento.getOption("sitio") != null
                && "hotel".equals(evento.getOption("sitio").getAsString());

        evento.deferReply(false).queue();
        ResultadoDormir r = descanso.dormir(evento.getUser().getIdLong(), hotel, Instant.now());
        MessageEmbed embed = switch (r.estado()) {
            case OK -> EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "descansar.dormir.titulo"),
                    cuerpoDormir(locale, r)).build();
            case YA_DORMIDO -> EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "descansar.yadormido"));
            case SIN_SALDO -> EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "descansar.hotel.sinsaldo", Camas.PRECIO_HOTEL));
        };
        evento.getHook().sendMessageEmbeds(embed).queue();
    }

    private void despertar(SlashCommandInteractionEvent evento, Locale locale) {
        long userId = evento.getUser().getIdLong();
        evento.deferReply(false).queue();
        ResultadoDespertar r = descanso.despertar(userId, Instant.now());
        evento.getHook().sendMessageEmbeds(embedDespertar(locale, r)).queue();
        reintentar(reintentos, userId, locale, evento.getHook());
    }

    /**
     * Relanza la acción que se quedó bloqueada por el sueño, si la hay, como mensaje aparte tras el
     * de despertar. Va en dos mensajes a propósito: el primero cuenta el descanso y el segundo el
     * resultado de lo que ibas a hacer; mezclarlos escondería uno de los dos.
     *
     * <p>Lo comparte {@code DescansoListener}: se despierte por botón o por comando, se retoma igual.
     */
    public static void reintentar(ReintentoRegistro reintentos, long userId, Locale locale,
                                  InteractionHook hook) {
        reintentos.tomar(userId, Instant.now()).ifPresent(accion ->
                hook.sendMessage(MessageCreateBuilder.from(accion.ejecutar(locale))
                        .setContent(Messages.get(locale, "descansar.reintento")).build()).queue());
    }

    /** Embed de despertar; lo reutiliza {@code DescansoListener} desde el botón. */
    public static MessageEmbed embedDespertar(Locale locale, ResultadoDespertar r) {
        if (r.estado() == DescansoService.EstadoDespertar.NO_DORMIDO) {
            return EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "descansar.nodormido"));
        }
        String dormido = Duraciones.formatear(r.minutosDormidos() * 60);
        String cama = nombreCama(locale, r.cama());
        Desglose d = r.desglose();

        StringBuilder sb = new StringBuilder(
                Messages.get(locale, "descansar.despertar.linea", dormido, cama));
        sb.append(Messages.get(locale, "descansar.despertar.energia",
                d.energiaAntes(), d.energiaDespues(), d.ganada()));

        // Los avisos son la clave de todo esto: sin ellos, "+47 tras cuatro días" parece un fallo.
        if (d.recortadoPorHoras()) {
            sb.append(Messages.get(locale, "descansar.aviso.horas", DescansoService.MAX_HORAS));
        }
        if (d.bonoResistenciaPct() > 0) {
            sb.append(Messages.get(locale, "descansar.aviso.resistencia", d.bonoResistenciaPct()));
        }
        if (d.penalizadoPorSalud()) {
            sb.append(Messages.get(locale, "descansar.aviso.salud", DescansoService.SALUD_BAJA));
        }
        if (d.topeAlcanzado()) {
            sb.append(Messages.get(locale, "descansar.aviso.tope", r.cama().tope()));
            sb.append(mejora(locale, r.cama(), "descansar.dormir.mejora"));
        }
        return EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "descansar.despertar.titulo"), sb.toString()).build();
    }

    /** Cuerpo del embed de acostarse: dónde duerme, hasta dónde llega y cómo mejorar. */
    private static String cuerpoDormir(Locale locale, ResultadoDormir r) {
        Camas cama = r.cama();
        StringBuilder sb = new StringBuilder(Messages.get(locale, "descansar.dormir.ok",
                nombreCama(locale, cama), cama.energiaHora(), cama.tope(),
                DescansoService.MAX_HORAS));
        // Acostarse ya por encima del tope es tiempo perdido: mejor avisar antes, no al despertar.
        if (r.energiaActual() >= cama.tope()) {
            sb.append(Messages.get(locale, "descansar.dormir.yatope", r.energiaActual(),
                    cama.tope(), nombreCama(locale, cama)));
        }
        sb.append(mejora(locale, cama, "descansar.dormir.mejora"));
        return sb.toString();
    }

    /**
     * Línea de «cómo subir el tope»: la siguiente cama que mejora, con su precio, y el hotel como
     * atajo para quien aún no puede comprar nada. Cadena vacía si ya se está en el tope máximo.
     *
     * @param clave clave i18n de la línea de mejora (cambia según el embed)
     */
    private static String mejora(Locale locale, Camas cama, String clave) {
        StringBuilder sb = new StringBuilder();
        Camas siguiente = Camas.siguienteMejor(cama).orElse(null);
        if (siguiente == null) {
            return Messages.get(locale, "descansar.aviso.tope.max");
        }
        long precio = Items.porId(siguiente.itemId()).map(Items::precio).orElse(0L);
        sb.append(Messages.get(locale, clave, Messages.get(locale, "item." + siguiente.itemId()),
                precio, siguiente.tope()));
        // El hotel no se posee: es la única mejora inmediata para quien duerme en el suelo.
        if (cama.tope() < Camas.HOTEL.tope() && precio > Camas.PRECIO_HOTEL) {
            sb.append(Messages.get(locale, "descansar.dormir.hotel",
                    Camas.PRECIO_HOTEL, Camas.HOTEL.tope()));
        }
        return sb.toString();
    }

    private void estado(SlashCommandInteractionEvent evento, Locale locale) {
        evento.deferReply(false).queue();
        Vista v = descanso.estado(evento.getUser().getIdLong(), Instant.now());
        String cama = nombreCama(locale, v.cama());
        String desc = v.dormido()
                ? Messages.get(locale, "descansar.estado.durmiendo",
                        Duraciones.formatear(v.minutosDormidos() * 60), cama,
                        v.cama().energiaHora(), v.cama().tope())
                : Messages.get(locale, "descansar.estado.despierto", "", cama,
                        v.cama().energiaHora(), v.cama().tope());
        desc += Messages.get(locale, "descansar.estado.energia",
                v.energiaActual(), v.cama().tope());
        desc += mejora(locale, v.cama(), "descansar.estado.mejora");
        if (v.fatiga()) {
            desc += Messages.get(locale, "descansar.estado.fatiga");
        }
        evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "descansar.estado.titulo"), desc).build()).queue();
    }

    /**
     * Embed que sale al intentar actuar (currar, pelear, minar…) estando dormido. Lo usan los
     * comandos bloqueados y va siempre acompañado de {@link #botonesBloqueado}: dormir cuesta tiempo
     * de juego, así que el jugador decide en el sitio si le compensa seguir o levantarse.
     */
    public static MessageEmbed embedBloqueado(Locale locale) {
        return EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "descansar.bloqueado.titulo"),
                Messages.get(locale, "descansar.bloqueado.desc")).build();
    }

    /**
     * Botones del embed de bloqueo. El customId lleva el id del dueño ({@code descanso:<accion>:<id>})
     * porque el mensaje es público: sin eso, cualquiera podría despertar a otro.
     *
     * @param ownerId dueño del descanso; el único que puede pulsar
     */
    public static ActionRow botonesBloqueado(Locale locale, long ownerId) {
        return ActionRow.of(
                Button.secondary(BOTON_SEGUIR + ":" + ownerId,
                        Messages.get(locale, "descansar.boton.seguir")),
                Button.success(BOTON_DESPERTAR + ":" + ownerId,
                        Messages.get(locale, "descansar.boton.despertar")));
    }

    /** Nombre localizado de la cama: el ítem si lo hay, o «el suelo» / «un hotel». */
    private static String nombreCama(Locale locale, Camas cama) {
        if (cama == Camas.SUELO) {
            return Messages.get(locale, "descansar.cama.suelo");
        }
        if (cama == Camas.HOTEL) {
            return Messages.get(locale, "descansar.cama.hotel");
        }
        return Messages.get(locale, "item." + cama.itemId());
    }
}
