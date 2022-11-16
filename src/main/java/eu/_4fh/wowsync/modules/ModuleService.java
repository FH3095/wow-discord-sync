package eu._4fh.wowsync.modules;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Key;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.hc.core5.net.URIBuilder;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.CleanupObligation;
import edu.umd.cs.findbugs.annotations.CreatesObligation;
import edu.umd.cs.findbugs.annotations.DischargesObligation;
import eu._4fh.abstract_bnet_api.util.Pair;
import eu._4fh.wowsync.database.Db;
import eu._4fh.wowsync.database.data.RemoteSystem;
import eu._4fh.wowsync.database.data.RemoteSystem.RemoteSystemType;
import eu._4fh.wowsync.discord.DiscordModule;
import eu._4fh.wowsync.util.ClosableSingleton;
import eu._4fh.wowsync.util.Config;
import eu._4fh.wowsync.util.MacCalculator;
import eu._4fh.wowsync.util.Singletons;

@CleanupObligation
public class ModuleService implements ClosableSingleton {

	private final Db db;
	private final Map<Pair<RemoteSystemType, Long>, Module> modules;

	@CreatesObligation
	private ModuleService() {
		db = Singletons.instance(Db.class);
		modules = Collections.unmodifiableMap(startModules());
	}

	private Map<Pair<RemoteSystemType, Long>, Module> startModules() {
		final Map<Pair<RemoteSystemType, Long>, Module> tmp = new ConcurrentHashMap<>();

		final List<RemoteSystem> systems = db.remoteSystems.all();
		for (final RemoteSystem system : systems) {
			if (RemoteSystemType.Discord.equals(system.type)) {
				tmp.put(new Pair<>(RemoteSystemType.Discord, system.systemId), new DiscordModule(system));
			} else {
				throw new IllegalStateException("System-Type " + system.type.name() + " not yet implemented");
			}
		}
		return tmp;
	}

	@Override
	@DischargesObligation
	public void close() {
		modules.values().forEach(ClosableSingleton::close);
	}

	public Module findModule(final RemoteSystemType remoteSystemType, final long remoteSystemId) {
		final @CheckForNull Module module = modules.get(new Pair<>(remoteSystemType, remoteSystemId));
		if (module == null) {
			throw new IllegalStateException(
					"Missing remote system for " + remoteSystemType.name() + "#" + remoteSystemId);
		}
		return module;
	}

	public URI createAuthUri(final long remoteSystemId, final long remoteUserId) {
		try {
			final Config config = Singletons.instance(Config.class);
			final String remoteUserIdStr = Long.toString(remoteUserId);
			final Key hmacKey = db.remoteSystems.hmacKeyById(remoteSystemId);
			final String mac = MacCalculator.generateHmac(hmacKey, remoteUserIdStr);
			return new URIBuilder(config.rootUri).appendPathSegments("auth", "start")
					.addParameter("systemId", Long.toString(remoteSystemId)).addParameter("userId", remoteUserIdStr)
					.addParameter("mac", mac).build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}
}
