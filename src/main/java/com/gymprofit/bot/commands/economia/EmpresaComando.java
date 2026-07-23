package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.MiembroEmpresa;
import com.gymprofit.bot.db.Pendiente;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Ascensos;
import com.gymprofit.bot.services.EmpresaService;
import com.gymprofit.bot.services.EmpresaService.InfoEmpresa;
import com.gymprofit.bot.services.EmpresaService.ResultadoFundar;
import com.gymprofit.bot.services.EmpresaService.ResultadoIngreso;
import com.gymprofit.bot.services.EmpresaService.SalidaFundar;
import com.gymprofit.bot.services.TipoPendiente;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /empresa} con subcomandos (fundar, info, disolver, invitar, solicitar, pendientes). Es la
 * cara de las empresas de la Fase 1: fundar la entidad ligada a tu rama, verla, disolverla e ingresar
 * por las dos vías con consentimiento (te invitan o solicitas con motivo). Toda la lógica vive en
 * {@link EmpresaService}; aquí solo se traducen los enums de resultado a embeds i18n.
 *
 * <p>Visibilidad (SPEC §6, tono casual de economía): fundar y las aceptaciones son <b>públicas</b>
 * (se celebran a la vista del canal); los errores, confirmaciones y la gestión personal
 * (invitar/solicitar/pendientes/disolver) van <b>efímeros</b>, para no exponer las decisiones de cada
 * uno. La invitación se <b>publica en el canal</b> con mención + botones: así el invitado la ve aunque
 * tenga los MDs cerrados, y el {@link com.gymprofit.bot.events.EmpresaBotonesListener listener} valida
 * quién puede pulsar vía el service.
 *
 * <p>El comando recibe también el {@link EmpresaRepositorio} porque los listados de {@code pendientes}
 * y la resolución del id de una invitación recién creada son consultas puras (no reglas de negocio):
 * el service expone las operaciones, el repo las lecturas de apoyo.
 */
public final class EmpresaComando implements Comando {

    private static final String NOMBRE = "empresa";
    /** Discord admite como mucho 5 filas de botones por mensaje: tope de pendientes que se pintan. */
    private static final int MAX_PENDIENTES = 5;
    /** Prefijo del customId de resolver una pendiente: {@code empresa:resolver:<pendienteId>:<1|0>}. */
    public static final String BOTON_RESOLVER = "empresa:resolver";
    /** Prefijos del customId de la confirmación de disolver: {@code empresa:disolver:<accion>:<ownerId>}. */
    public static final String BOTON_DISOLVER_SI = "empresa:disolver:si";
    public static final String BOTON_DISOLVER_NO = "empresa:disolver:no";

    private final EmpresaService empresa;
    private final EmpresaRepositorio repo;

    public EmpresaComando(EmpresaService empresa, EmpresaRepositorio repo) {
        this.empresa = empresa;
        this.repo = repo;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData nombreFundar = new OptionData(OptionType.STRING, "nombre",
                Messages.get(Messages.ES, "comando.empresa.fundar.opcion.nombre"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.fundar.opcion.nombre"));
        OptionData nombreInfo = new OptionData(OptionType.STRING, "nombre",
                Messages.get(Messages.ES, "comando.empresa.info.opcion.nombre"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.info.opcion.nombre"));
        OptionData usuarioInvitar = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.empresa.invitar.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.invitar.opcion.usuario"));
        OptionData nombreSolicitar = new OptionData(OptionType.STRING, "nombre",
                Messages.get(Messages.ES, "comando.empresa.solicitar.opcion.nombre"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.solicitar.opcion.nombre"));
        OptionData motivoSolicitar = new OptionData(OptionType.STRING, "motivo",
                Messages.get(Messages.ES, "comando.empresa.solicitar.opcion.motivo"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.solicitar.opcion.motivo"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.empresa.familia"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.empresa.familia"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.familia"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("fundar", "comando.empresa.fundar.descripcion").addOptions(nombreFundar),
                        sub("info", "comando.empresa.info.descripcion").addOptions(nombreInfo),
                        sub("disolver", "comando.empresa.disolver.descripcion"),
                        sub("invitar", "comando.empresa.invitar.descripcion").addOptions(usuarioInvitar),
                        sub("solicitar", "comando.empresa.solicitar.descripcion")
                                .addOptions(nombreSolicitar, motivoSolicitar),
                        sub("pendientes", "comando.empresa.pendientes.descripcion"));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String sub = evento.getSubcommandName() == null ? "info" : evento.getSubcommandName();
        switch (sub) {
            case "fundar" -> fundar(evento, locale);
            case "info" -> info(evento, locale);
            case "disolver" -> disolver(evento, locale);
            case "invitar" -> invitar(evento, locale);
            case "solicitar" -> solicitar(evento, locale);
            case "pendientes" -> pendientes(evento, locale);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
        }
    }

    /**
     * Funda una empresa en la rama de tu trabajo. El éxito es un embed <b>público</b> de celebración
     * con mención, nombre y coins quemados; cualquier fallo va <b>efímero</b> con el dato exacto.
     */
    private void fundar(SlashCommandInteractionEvent evento, Locale locale) {
        String nombre = evento.getOption("nombre").getAsString();
        evento.deferReply(false).queue();
        SalidaFundar r = empresa.fundar(evento.getUser().getIdLong(), nombre);
        if (r.estado() == ResultadoFundar.OK) {
            evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.fundada.titulo"),
                    Messages.get(locale, "empresa.fundada.desc",
                            evento.getUser().getAsMention(), nombre, r.costeQuemado())).build()).queue();
            return;
        }
        String mensaje = switch (r.estado()) {
            case SIN_TRABAJO -> Messages.get(locale, "empresa.fundar.sin_trabajo");
            case NO_ES_TIER4 -> Messages.get(locale, "empresa.fundar.no_es_tier4");
            case YA_EN_EMPRESA -> Messages.get(locale, "empresa.fundar.ya_en_empresa");
            case NOMBRE_EN_USO -> Messages.get(locale, "empresa.fundar.nombre_en_uso");
            case SIN_SALDO -> Messages.get(locale, "empresa.fundar.sin_saldo", EmpresaService.COSTE_FUNDAR);
            case OK -> throw new IllegalStateException("OK ya tratado");
        };
        evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje))
                .setEphemeral(true).queue();
    }

    /**
     * Pinta una empresa: nombre, rama, dueño, nivel y la lista de miembros con su rango. Sin nombre,
     * la tuya; con nombre, la busca por su nombre en cualquier rama (los nombres son únicos por rama,
     * así que la primera coincidencia basta en F1). Es pública (la economía se juega a la vista).
     */
    private void info(SlashCommandInteractionEvent evento, Locale locale) {
        evento.deferReply(false).queue();
        OptionMapping nombreOpt = evento.getOption("nombre");
        Optional<InfoEmpresa> infoOpt;
        if (nombreOpt == null) {
            infoOpt = empresa.infoDe(evento.getUser().getIdLong());
        } else {
            infoOpt = buscarPorNombre(nombreOpt.getAsString());
        }
        if (infoOpt.isEmpty()) {
            evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.sin_empresa"))).setEphemeral(true).queue();
            return;
        }
        Empresa e = infoOpt.get().empresa();
        List<MiembroEmpresa> miembros = infoOpt.get().miembros();
        StringBuilder lista = new StringBuilder();
        for (MiembroEmpresa m : miembros) {
            lista.append(Messages.get(locale, "empresa.info.miembro",
                    "<@" + m.discordId() + ">", Messages.get(locale, m.rango().claveI18n()))).append('\n');
        }
        String cuerpo = Messages.get(locale, "empresa.info.cuerpo",
                e.nombre(),
                Messages.get(locale, "rama." + e.rama().toLowerCase(Locale.ROOT)),
                "<@" + e.duenoId() + ">",
                e.nivel(),
                miembros.size(),
                lista.toString().strip());
        evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "empresa.info.titulo"), cuerpo).build()).queue();
    }

    /** Busca una empresa por nombre recorriendo las ramas (nombre único por rama; primera coincide). */
    private Optional<InfoEmpresa> buscarPorNombre(String nombre) {
        for (Ascensos.Rama r : Ascensos.Rama.values()) {
            Optional<InfoEmpresa> o = empresa.infoPorNombreYRama(nombre, r.name());
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    /**
     * Disolver es <b>destructivo</b> e irreversible (se borra la empresa y sus miembros en cascada),
     * así que se pide confirmación por botón, en efímero (decisión del dueño, no hay nada que celebrar).
     * La disolución real la ejecuta {@link com.gymprofit.bot.events.EmpresaBotonesListener}.
     */
    private void disolver(SlashCommandInteractionEvent evento, Locale locale) {
        long userId = evento.getUser().getIdLong();
        Optional<InfoEmpresa> infoOpt = empresa.infoDe(userId);
        if (infoOpt.isEmpty()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.disolver.sin_empresa"))).setEphemeral(true).queue();
            return;
        }
        Empresa e = infoOpt.get().empresa();
        if (e.duenoId() != userId) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.disolver.no_eres_dueno"))).setEphemeral(true).queue();
            return;
        }
        evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                        Messages.get(locale, "empresa.disolver.confirmar", e.nombre())))
                .setComponents(botonesDisolver(locale, userId))
                .setEphemeral(true).queue();
    }

    /**
     * Botones Sí/No de la confirmación de disolver. El customId lleva el id del dueño
     * ({@code empresa:disolver:<accion>:<ownerId>}), mismo patrón que la dimisión de {@code /trabajo}.
     */
    public static ActionRow botonesDisolver(Locale locale, long ownerId) {
        return ActionRow.of(
                Button.danger(BOTON_DISOLVER_SI + ":" + ownerId, Messages.get(locale, "empresa.boton.si")),
                Button.secondary(BOTON_DISOLVER_NO + ":" + ownerId, Messages.get(locale, "empresa.boton.no")));
    }

    /**
     * El dueño invita a un jugador. La confirmación al que invita va <b>efímera</b>; la invitación en
     * sí se <b>publica en el canal</b> con la mención del invitado y botones Aceptar/Rechazar, para que
     * la vea aunque tenga los MDs cerrados y no dependa de estar mirando el chat justo entonces.
     */
    private void invitar(SlashCommandInteractionEvent evento, Locale locale) {
        long duenoId = evento.getUser().getIdLong();
        User objetivo = evento.getOption("usuario").getAsUser();
        evento.deferReply(true).queue();
        ResultadoIngreso r = empresa.invitar(duenoId, objetivo.getIdLong());
        if (r != ResultadoIngreso.OK) {
            evento.getHook().sendMessageEmbeds(
                    EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensajeIngreso(locale, r))).queue();
            return;
        }
        // Confirmación efímera al que invita.
        evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "empresa.invitacion.enviada", objetivo.getAsMention()))).queue();
        // Notificación pública al invitado, con los botones para resolverla. Se resuelve el id de la
        // pendiente recién creada buscando entre las invitaciones del objetivo la de esta empresa.
        Empresa e = empresa.infoDe(duenoId).map(InfoEmpresa::empresa).orElse(null);
        if (e == null) {
            return;
        }
        Empresa emp = e;
        repo.invitacionesA(objetivo.getIdLong()).stream()
                .filter(p -> p.empresaId() == emp.id())
                .findFirst()
                .ifPresent(p -> evento.getChannel().sendMessage(objetivo.getAsMention())
                        .setEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                                Messages.get(locale, "empresa.invitacion.recibida.titulo"),
                                Messages.get(locale, "empresa.invitacion.recibida.desc",
                                        objetivo.getAsMention(), emp.nombre())).build())
                        .setComponents(botonesResolver(locale, p.id(), "empresa.boton.aceptar"))
                        .queue());
    }

    /**
     * El jugador solicita entrar en una empresa de su rama, con un motivo opcional. Va todo
     * <b>efímero</b>: el dueño la verá cuando haga {@code /empresa pendientes} (no se le hace ping para
     * no ensuciar el canal; decisión conservadora de F1).
     */
    private void solicitar(SlashCommandInteractionEvent evento, Locale locale) {
        String nombre = evento.getOption("nombre").getAsString();
        OptionMapping motivoOpt = evento.getOption("motivo");
        String motivo = motivoOpt == null ? null : motivoOpt.getAsString();
        evento.deferReply(true).queue();
        ResultadoIngreso r = empresa.solicitar(evento.getUser().getIdLong(), nombre, motivo);
        String mensaje = r == ResultadoIngreso.OK
                ? Messages.get(locale, "empresa.solicitud.enviada", nombre)
                : mensajeIngreso(locale, r);
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }

    /**
     * Lista tus pendientes, en efímero (gestión personal). Si eres <b>dueño</b>, las solicitudes de
     * ingreso a tu empresa (con su motivo) con botones Aprobar/Rechazar; si no, tus invitaciones
     * recibidas con Aceptar/Rechazar. Cada pendiente va en su propio mensaje para que sus botones
     * queden inequívocamente ligados a ella.
     */
    private void pendientes(SlashCommandInteractionEvent evento, Locale locale) {
        long userId = evento.getUser().getIdLong();
        evento.deferReply(true).queue();
        Optional<InfoEmpresa> infoOpt = empresa.infoDe(userId);
        boolean esDueno = infoOpt.map(i -> i.empresa().duenoId() == userId).orElse(false);
        if (esDueno) {
            List<Pendiente> solicitudes = repo.pendientesDe(infoOpt.get().empresa().id(),
                    TipoPendiente.SOLICITUD);
            if (solicitudes.isEmpty()) {
                avisarSinPendientes(evento, locale);
                return;
            }
            solicitudes.stream().limit(MAX_PENDIENTES).forEach(p -> evento.getHook().sendMessageEmbeds(
                            EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                                    Messages.get(locale, "empresa.pendientes.titulo"),
                                    Messages.get(locale, "empresa.pendiente.solicitud",
                                            "<@" + p.discordId() + ">", motivoTexto(locale, p.motivo()))).build())
                    .setComponents(botonesResolver(locale, p.id(), "empresa.boton.aprobar")).queue());
        } else {
            List<Pendiente> invitaciones = repo.invitacionesA(userId);
            if (invitaciones.isEmpty()) {
                avisarSinPendientes(evento, locale);
                return;
            }
            invitaciones.stream().limit(MAX_PENDIENTES).forEach(p -> {
                String nombreEmpresa = repo.porId(p.empresaId()).map(Empresa::nombre).orElse("?");
                evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                                Messages.get(locale, "empresa.pendientes.titulo"),
                                Messages.get(locale, "empresa.pendiente.invitacion", nombreEmpresa)).build())
                        .setComponents(botonesResolver(locale, p.id(), "empresa.boton.aceptar")).queue();
            });
        }
    }

    private static void avisarSinPendientes(SlashCommandInteractionEvent evento, Locale locale) {
        evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "empresa.sin_pendientes"))).queue();
    }

    /** Motivo de una solicitud, o un guion si no lo dejó. */
    private static String motivoTexto(Locale locale, String motivo) {
        return (motivo == null || motivo.isBlank())
                ? Messages.get(locale, "empresa.pendiente.sin_motivo") : motivo;
    }

    /**
     * Botones Aceptar/Rechazar de una pendiente. El customId codifica el id de la pendiente y la acción
     * ({@code empresa:resolver:<pendienteId>:<1|0>}); la potestad (quién puede resolverla) la valida
     * {@link EmpresaService#resolver} en el listener, no el botón.
     *
     * @param aceptarKey clave i18n de la etiqueta positiva (aceptar una invitación / aprobar una solicitud)
     */
    private static ActionRow botonesResolver(Locale locale, long pendienteId, String aceptarKey) {
        return ActionRow.of(
                Button.success(BOTON_RESOLVER + ":" + pendienteId + ":1", Messages.get(locale, aceptarKey)),
                Button.danger(BOTON_RESOLVER + ":" + pendienteId + ":0",
                        Messages.get(locale, "empresa.boton.rechazar")));
    }

    /** Traduce un resultado de ingreso (invitar/solicitar) fallido a su mensaje i18n. */
    private static String mensajeIngreso(Locale locale, ResultadoIngreso r) {
        return switch (r) {
            case SIN_TRABAJO -> Messages.get(locale, "empresa.ingreso.sin_trabajo");
            case OTRA_RAMA -> Messages.get(locale, "empresa.ingreso.otra_rama");
            case YA_EN_EMPRESA -> Messages.get(locale, "empresa.ingreso.ya_en_empresa");
            case EMPRESA_NO_EXISTE -> Messages.get(locale, "empresa.ingreso.empresa_no_existe");
            case YA_PENDIENTE -> Messages.get(locale, "empresa.ingreso.ya_pendiente");
            case ES_MISMO -> Messages.get(locale, "empresa.ingreso.es_mismo");
            case OK -> throw new IllegalStateException("OK ya tratado");
        };
    }
}
