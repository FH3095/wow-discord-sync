package eu._4fh.wowsync.rest;

import java.io.Serializable;
import java.net.URI;
import java.security.Key;

import org.dmfs.httpessentials.exceptions.ProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import eu._4fh.abstract_bnet_api.oauth2.BattleNetClient;
import eu._4fh.abstract_bnet_api.oauth2.BattleNetClients.InvalidScopeError;
import eu._4fh.abstract_bnet_api.oauth2.BattleNetClients.UserAuthorizationError;
import eu._4fh.abstract_bnet_api.oauth2.BattleNetClients.UserAuthorizationState;
import eu._4fh.wowsync.database.Db;
import eu._4fh.wowsync.database.data.Guild;
import eu._4fh.wowsync.database.data.RemoteSystem;
import eu._4fh.wowsync.rest.helper.HtmlHelper;
import eu._4fh.wowsync.rest.providers.RequiredParameterFilter.RequiredParam;
import eu._4fh.wowsync.sync.BattleNetToDbSync;
import eu._4fh.wowsync.sync.DbToModuleSync;
import eu._4fh.wowsync.util.Config;
import eu._4fh.wowsync.util.MacCalculator;
import eu._4fh.wowsync.util.Singletons;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

@DefaultAnnotation(NonNull.class)
@Path("auth")
public class Auth {
	@DefaultAnnotation(NonNull.class)
	private static final class AuthInformations implements Serializable {
		private static final long serialVersionUID = 2781228208547364912L;
		private static final String sessionKey = "bnetAuthInformations";

		public final long remoteSystemId;
		public final long remoteUserId;
		public final UserAuthorizationState authState;

		public AuthInformations(final long remoteSystemId, final long remoteUserId,
				final UserAuthorizationState authState) {
			this.remoteSystemId = remoteSystemId;
			this.remoteUserId = remoteUserId;
			this.authState = authState;
		}
	}

	private final Logger log = LoggerFactory.getLogger(getClass());
	private final Db db = Singletons.instance(Db.class);
	private final Config config = Singletons.instance(Config.class);

	@Context
	@SuppressFBWarnings("NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
	private HttpServletRequest request;

	@Context
	@SuppressFBWarnings("NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
	private UriInfo uriInfo;

	@GET
	@Path("start")
	@Produces(MediaType.TEXT_HTML)
	public Response start(final @QueryParam("systemId") @RequiredParam Long remoteSystemId,
			final @QueryParam("userId") @RequiredParam Long remoteUserId,
			final @QueryParam("mac") @RequiredParam String macIn) {

		final Key hmacKey = db.remoteSystems.hmacKeyById(remoteSystemId);
		MacCalculator.testMac(hmacKey, macIn, String.valueOf(remoteUserId));

		final Guild guild = db.guilds.byRemoteSystem(remoteSystemId);
		final UserAuthorizationState authState = config.battleNetClients.startUserAuthorizationProcess(guild.region());

		final HttpSession session = request.getSession(true);
		session.setMaxInactiveInterval(900);
		session.setAttribute(AuthInformations.sessionKey,
				new AuthInformations(remoteSystemId, remoteUserId, authState));

		final URI authorizationUrl = authState.getAuthorizationUrl();
		// Redirect as html. It seems there are multiple browsers out there that at least had problems with HTTP-Redirects with cookies
		// https://bugs.webkit.org/show_bug.cgi?id=3512
		// https://bugs.chromium.org/p/chromium/issues/detail?id=150066
		final String result = HtmlHelper
				.getHtmlHead("Redirect",
						"<meta http-equiv=\"refresh\" content=\"3; URL="
								+ HtmlHelper.encodeLinkForHref(authorizationUrl) + "\">\n")
				.append("<body>\n<p>Wait one moment please.</p>\n</body></html>").toString();
		return Response.ok(result).build();
	}

	@GET
	@Path("finish")
	public Response finish() {
		final @CheckForNull HttpSession session = request.getSession(false);
		if (session == null || session.getAttribute(AuthInformations.sessionKey) == null
				|| !(session.getAttribute(AuthInformations.sessionKey) instanceof AuthInformations)) {
			throw new ForbiddenException("Cant find your session, please try again");
		}

		final AuthInformations authInformations = (AuthInformations) session.getAttribute(AuthInformations.sessionKey);
		final BattleNetClient client;
		try {
			client = config.battleNetClients.finishUserAuthorizationProcess(authInformations.authState,
					uriInfo.getRequestUri().toASCIIString());
		} catch (UserAuthorizationError e) {
			log.atInfo().setCause(e).setMessage("Cant finish auth for {} to system {}")
					.addArgument(authInformations.remoteUserId).addArgument(authInformations.remoteSystemId).log();
			log.info("Cant finish auth for {} to system " + authInformations.remoteSystemId, e);
			return Response.serverError().type(MediaType.TEXT_PLAIN_TYPE).entity(e.getMessage()).build();
		} catch (InvalidScopeError e) {
			log.atDebug().setCause(e).setMessage("Cant finish auth for {} to system {}")
					.addArgument(authInformations.remoteUserId).addArgument(authInformations.remoteSystemId).log();
			final String responseText = HtmlHelper.getHtmlHead("Invalid scope")
					.append("You need to authorize access to your wow profile. Please revoke all access at ")
					.append("<a target=\"_blank\" href=\"https://account.blizzard.com/connections#authorized-applications\">https://account.blizzard.com/connections#authorized-applications</a> ")
					.append("and try again.").toString();
			return Response.status(Status.BAD_REQUEST).type(MediaType.TEXT_HTML_TYPE).entity(responseText).build();
		}

		final RemoteSystem remoteSystem = db.remoteSystems.byId(authInformations.remoteSystemId);
		final URI redirectTo = new BattleNetToDbSync().authFinished(remoteSystem, authInformations.remoteUserId,
				client);
		final boolean added = new DbToModuleSync(remoteSystem).syncForUser(authInformations.remoteUserId);
		try {
			log.info("Auth finished for {} to {}#{}. Token {} valid until {} for {}. Added {}. Redirecting to {}",
					authInformations.remoteUserId, remoteSystem.type.name(), remoteSystem.id,
					client.getAccessToken().accessToken(), client.getAccessToken().expirationDate(),
					client.getAccessToken().scope(), added, redirectTo);
		} catch (ProtocolException e) {
			log.error("Cant get token informations", e);
		}
		if (redirectTo != null) {
			return Response.seeOther(redirectTo).build();
		} else {
			final String result = HtmlHelper.getHtmlHead("Auth finished")
					.append("<body>\n<p>Auth finished. You can close this window now.</p>\n</body></html>").toString();
			return Response.ok(result, MediaType.TEXT_HTML_TYPE).build();
		}
	}
}
