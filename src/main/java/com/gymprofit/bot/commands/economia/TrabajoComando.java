package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.ComandoAutocompletable;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.events.ReintentoRegistro;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Ascensos;
import com.gymprofit.bot.services.TrabajoService;
import com.gymprofit.bot.services.TrabajoService.InfoCarrera;
import com.gymprofit.bot.services.TrabajoService.ResultadoAscenso;
import com.gymprofit.bot.services.TrabajoService.ResultadoElegir;
import com.gymprofit.bot.services.TrabajoService.ResultadoWork;
import com.gymprofit.bot.services.Trabajos;
import com.gymprofit.bot.util.Duraciones;
import com.gymprofit.bot.util.Embeds;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /trabajo} con subcomandos (lista, elegir, currar, ascender, carrera). Consolida el empleo
 * en un solo comando para no gastar cupo de slash commands (límite de 100 de Discord).
 *
 * <p>{@code lista} muestra el catálogo por tier con sueldo y requisito, marcando con 🔒 los puestos
 * que se consiguen ascendiendo y no eligiendo; {@code elegir} fija tu empleo si cumples el nivel y te
 * da su rol cosmético; {@code currar} trabaja un turno (gana coins, gasta energía, con cooldown);
 * {@code ascender} sube al siguiente puesto de tu rama (con autocompletado de los puestos elegibles);
 * {@code carrera} pinta rama, tier y la checklist de lo que te falta para el próximo salto. Todos son
 * <b>públicos</b>: la economía se juega a la vista de todos; solo los errores de ascenso van efímeros.
 */
public final class TrabajoComando implements ComandoAutocompletable {

    private static final String NOMBRE = "trabajo";
    /** Discord admite como mucho 25 sugerencias de autocompletado. */
    private static final int MAX_SUGERENCIAS = 25;
    /** Discord corta el nombre de una sugerencia a 100 caracteres. */
    private static final int MAX_LARGO_SUGERENCIA = 100;
    /** Prefijo de los roles cosméticos de trabajo (identifica y limpia el anterior). */
    private static final String PREFIJO_ROL = "💼 ";
    private static final Color COLOR_ROL_TRABAJO = new Color(0x9B59B6);

    private final TrabajoService trabajos;
    private final ReintentoRegistro reintentos;

    public TrabajoComando(TrabajoService trabajos, ReintentoRegistro reintentos) {
        this.trabajos = trabajos;
        this.reintentos = reintentos;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData trabajo = new OptionData(OptionType.STRING, "trabajo",
                Messages.get(Messages.ES, "comando.elegirtrabajo.opcion.trabajo"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.elegirtrabajo.opcion.trabajo"));

        // Autocompletado en vez de choices: las opciones válidas dependen de la rama y el tier del
        // jugador, así que se calculan al vuelo (mismo patrón que el ítem de /pasivos).
        OptionData puesto = new OptionData(OptionType.STRING, "puesto",
                Messages.get(Messages.ES, "comando.trabajo.ascender.opcion.puesto"), true, true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.trabajo.ascender.opcion.puesto"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.trabajo.familia"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.trabajo.familia"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.trabajo.familia"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("lista", "comando.trabajos.descripcion"),
                        sub("elegir", "comando.elegirtrabajo.descripcion").addOptions(trabajo),
                        sub("currar", "comando.work.descripcion"),
                        sub("ascender", "comando.trabajo.ascender.descripcion").addOptions(puesto),
                        sub("carrera", "comando.trabajo.carrera.descripcion"));
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
            case "ascender" -> ascender(evento, locale);
            case "carrera" -> carrera(evento, locale);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
        }
    }

    private void lista(SlashCommandInteractionEvent evento, Locale locale) {
        // Se difiere porque la lista consulta la carrera del jugador (BD) para saber qué puestos
        // tiene ya alcanzados y cuáles marcar con candado.
        evento.deferReply(false).queue();
        long id = evento.getUser().getIdLong();
        // El tier alcanzado es constante por rama y hay solo 7 ramas, pero el catálogo tiene ~52
        // puestos: se memoiza por rama (una lectura a BD por rama, no una por fila) para no abrir 52
        // conexiones contra Aiven al pintar la lista.
        Map<Ascensos.Rama, Integer> tierPorRama = new EnumMap<>(Ascensos.Rama.class);
        List<String> lineas = new ArrayList<>();
        lineas.add(Messages.get(locale, "trabajos.intro"));
        int tierActual = 0;
        for (Trabajos t : Trabajos.CATALOGO) {
            if (t.tier() != tierActual) {
                tierActual = t.tier();
                lineas.add("");  // separación visual entre grupos de tier
                lineas.add("**" + Messages.get(locale, "trabajos.tier", tierActual) + "**");
            }
            // Los puestos por encima del tier alcanzado en su rama no se eligen, se ascienden: van con
            // candado y el texto que lo explica en vez de la línea normal.
            int alcanzado = tierPorRama.computeIfAbsent(Ascensos.ramaDe(t.sector()),
                    rama -> trabajos.tierAlcanzadoEn(id, rama));
            boolean bloqueado = t.tier() > alcanzado;
            String clave = bloqueado ? "trabajos.linea.bloqueada" : "trabajos.linea";
            lineas.add(Messages.get(locale, clave,
                    t.id(), Messages.get(locale, "trabajo." + t.id()), t.sector(),
                    t.salarioMin(), t.salarioMax(), t.requisitoNivel()));
        }
        // El catálogo (~52 puestos, casi todos bloqueados para un novato con líneas más largas) se
        // pasa del tope de 4096 de la descripción de un embed: se reparte en varios embeds. El título
        // solo va en el primero; los demás continúan la lista sin título.
        List<String> bloques = Embeds.partirEnBloques(lineas, Embeds.MAX_DESC);
        String titulo = Messages.get(locale, "trabajos.titulo");
        for (int i = 0; i < bloques.size(); i++) {
            var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                    i == 0 ? titulo : null, bloques.get(i)).build();
            evento.getHook().sendMessageEmbeds(embed).queue();
        }
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
            // Los puestos por encima del tier de entrada de su rama ya no se eligen: se ascienden.
            case TIER -> Messages.get(locale, "elegirtrabajo.tier");
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }

    private void currar(SlashCommandInteractionEvent evento, Locale locale) {
        long userId = evento.getUser().getIdLong();
        evento.deferReply(false).queue();
        evento.getHook().sendMessage(currar(userId, locale)).queue();
    }

    /**
     * Curra un turno y devuelve el mensaje ya montado. Devuelve datos en vez de enviarlos porque lo
     * reutiliza el <b>reintento</b>: si estabas dormido, esta misma llamada se relanza al despertar
     * desde el botón (ver {@link ReintentoRegistro}).
     */
    private MessageCreateData currar(long userId, Locale locale) {
        ResultadoWork r = trabajos.trabajar(userId, Instant.now());
        // Dormido: en vez de un aviso seco, el embed con botones para seguir durmiendo o despertar,
        // y el turno de trabajo queda guardado para relanzarlo si decide levantarse.
        if (r.estado() == TrabajoService.EstadoWork.DORMIDO) {
            reintentos.guardar(userId, Instant.now(), loc -> currar(userId, loc));
            return new MessageCreateBuilder()
                    .setEmbeds(DescansoComando.embedBloqueado(locale))
                    .setComponents(DescansoComando.botonesBloqueado(locale, userId))
                    .build();
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
        return new MessageCreateBuilder().setEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA,
                locale, Messages.get(locale, "work.titulo"), desc).build()).build();
    }

    /**
     * Sugiere los puestos a los que el jugador <b>puede</b> ascender ahora mismo: los del siguiente
     * tier existente de su rama. Al venir ya filtradas, el error de «ese no es tu siguiente paso»
     * casi no puede ocurrir. La lista es corta (los puestos de un tier), así que no hace falta
     * filtrar por lo escrito, solo respetar el tope de 25 de Discord.
     */
    @Override
    public void autocompletar(CommandAutoCompleteInteractionEvent evento) {
        // Guarda por si el comando gana más opciones autocompletables: solo respondemos a "puesto".
        if (!"puesto".equals(evento.getFocusedOption().getName())) {
            evento.replyChoices(List.of()).queue();
            return;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        List<Command.Choice> opciones = trabajos.opcionesAscenso(evento.getUser().getIdLong())
                .stream()
                .map(t -> new Command.Choice(etiqueta(locale, t.id()), t.id()))
                .limit(MAX_SUGERENCIAS)
                .toList();
        evento.replyChoices(opciones).queue();
    }

    /** Nombre localizado del puesto para una sugerencia, recortado al máximo que admite Discord. */
    private static String etiqueta(Locale locale, String puestoId) {
        String texto = Messages.get(locale, "trabajo." + puestoId);
        return texto.length() > MAX_LARGO_SUGERENCIA
                ? texto.substring(0, MAX_LARGO_SUGERENCIA) : texto;
    }

    /**
     * Asciende al puesto elegido. El éxito es un embed <b>público</b> de celebración con mención (un
     * ascenso se presume); cualquier fallo va <b>efímero</b> con el dato exacto de lo que falta, para
     * no exponer las carencias del jugador ante el canal.
     */
    private void ascender(SlashCommandInteractionEvent evento, Locale locale) {
        String id = evento.getOption("puesto").getAsString();
        evento.deferReply(false).queue();
        ResultadoAscenso r = trabajos.ascender(evento.getUser().getIdLong(), id);
        if (r.estado() == TrabajoService.EstadoAscenso.OK) {
            if (evento.getMember() != null) {
                asignarRolTrabajo(evento.getGuild(), evento.getMember(), id);
            }
            evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "ascender.titulo"),
                    Messages.get(locale, "ascender.ok",
                            evento.getUser().getAsMention(),
                            Messages.get(locale, "trabajo." + id),
                            r.requisito().coins())).build()).queue();
            return;
        }
        String mensaje = switch (r.estado()) {
            case SIN_TRABAJO -> Messages.get(locale, "ascender.sintrabajo");
            case NO_EXISTE, DESTINO -> Messages.get(locale, "ascender.destino");
            case TOPE -> Messages.get(locale, "ascender.tope");
            case NIVEL -> Messages.get(locale, "ascender.nivel", r.actual());
            case TURNOS -> Messages.get(locale, "ascender.turnos", r.actual(), r.requisito().turnos());
            case ESTUDIOS -> Messages.get(locale, "ascender.estudios", r.actual(), r.requisito().estudios());
            case STAT -> Messages.get(locale, "ascender.stat", r.actual(), r.requisito().stat());
            case COINS -> Messages.get(locale, "ascender.coins", r.requisito().coins());
            case OK -> throw new IllegalStateException("OK ya tratado");
        };
        evento.getHook().sendMessageEmbeds(
                        EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje))
                .setEphemeral(true).queue();
    }

    /**
     * Pinta la carrera del jugador: rama, tier, antigüedad y —si aún puede ascender— la checklist de
     * los cuatro requisitos del siguiente salto (✅ cumplido / ❌ pendiente), comparando sus valores
     * actuales con lo que pide {@link Ascensos}. En el tope de la rama muestra el aviso de cima; sin
     * trabajo, invita a empezar por {@code /trabajo elegir}.
     */
    private void carrera(SlashCommandInteractionEvent evento, Locale locale) {
        evento.deferReply(false).queue();
        long usuario = evento.getUser().getIdLong();
        InfoCarrera info = trabajos.infoCarrera(usuario);
        if (info.puestoActual() == null) {
            enviarCarrera(evento, locale, Messages.get(locale, "carrera.sintrabajo"));
            return;
        }
        String cuerpo = Messages.get(locale, "carrera.cuerpo",
                Messages.get(locale, "rama." + info.rama().name().toLowerCase(Locale.ROOT)),
                info.tierAlcanzado(),
                Messages.get(locale, "trabajo." + info.puestoActual()),
                info.turnosPuesto());
        if (info.siguiente().isEmpty()) {
            cuerpo += Messages.get(locale, "carrera.tope");
        } else {
            Ascensos.Requisitos req = info.requisitos();
            String stat = Messages.get(locale, "atributo." + Ascensos.statDe(info.rama()));
            String checklist = String.join("\n",
                    lineaReq(locale, Messages.get(locale, "carrera.req.turnos"),
                            info.turnosPuesto(), req.turnos()),
                    lineaReq(locale, Messages.get(locale, "carrera.req.estudios"),
                            info.estudios(), req.estudios()),
                    lineaReq(locale, Messages.get(locale, "carrera.req.stat", stat),
                            info.stat(), req.stat()),
                    lineaReq(locale, Messages.get(locale, "carrera.req.coins"),
                            trabajos.saldo(usuario), req.coins()));
            cuerpo += Messages.get(locale, "carrera.requisitos", info.siguiente().get(), checklist);
        }
        enviarCarrera(evento, locale, cuerpo);
    }

    /** Una línea de la checklist: ✅/❌ según se cumpla, con el valor actual sobre el requerido. */
    private static String lineaReq(Locale locale, String etiqueta, long actual, long requerido) {
        String estado = actual >= requerido ? "✅" : "❌";
        return Messages.get(locale, "carrera.requisito.linea", estado, etiqueta, actual, requerido);
    }

    /** Envía el embed de carrera (título común) con el cuerpo ya montado. */
    private static void enviarCarrera(SlashCommandInteractionEvent evento, Locale locale, String cuerpo) {
        evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "carrera.titulo"), cuerpo).build()).queue();
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
