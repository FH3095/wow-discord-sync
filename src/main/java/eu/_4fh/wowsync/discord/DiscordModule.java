package eu._4fh.wowsync.discord;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.wowsync.database.Db;
import eu._4fh.wowsync.database.data.DiscordSettings;
import eu._4fh.wowsync.database.data.RemoteSystem;
import eu._4fh.wowsync.modules.Module;
import eu._4fh.wowsync.util.Singletons;

@DefaultAnnotation(NonNull.class)
public class DiscordModule implements Module {
	private final DiscordHandler handler;
	private final RemoteSystem remoteSystem;
	private final DiscordSettings settings;

	public DiscordModule(final RemoteSystem remoteSystem) {
		this.remoteSystem = remoteSystem;
		settings = Singletons.instance(Db.class).find(DiscordSettings.class, remoteSystem.id);
		handler = Singletons.instance(DiscordHandler.class);
		Optional.ofNullable(settings.reactionMessageId()).ifPresent(handler::addMessageToReactTo);
	}

	@Override
	public void close() {
		Optional.ofNullable(settings.reactionMessageId()).ifPresent(handler::removeMessageToReactoTo);
	}

	@Override
	public Map<Long, Set<String>> getAllUsersWithRoles() {
		return handler.getAllUsersWithRoles(remoteSystem.systemId);
	}

	@Override
	public void changeRoles(final Map<Long, RoleChange> roleChanges) {
		handler.changeRole(remoteSystem.systemId, roleChanges);
	}

	@Override
	public Set<String> getRolesForUser(final long userId) {
		return handler.getRolesForUser(remoteSystem.systemId, userId);
	}
}
