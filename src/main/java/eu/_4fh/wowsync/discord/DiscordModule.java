package eu._4fh.wowsync.discord;

import java.util.Map;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.wowsync.database.data.RemoteSystem;
import eu._4fh.wowsync.modules.Module;
import eu._4fh.wowsync.util.Singletons;

@DefaultAnnotation(NonNull.class)
public class DiscordModule implements Module {
	private final DiscordHandler handler;
	private final RemoteSystem remoteSystem;

	public DiscordModule(final RemoteSystem remoteSystem) {
		this.remoteSystem = remoteSystem;
		handler = Singletons.instance(DiscordHandler.class);
	}

	@Override
	public void close() {
		// Nothing to do
	}

	@Override
	public Map<Long, Set<String>> getAllUsersWithRoles() {
		return handler.getAllUsersWithRoles(remoteSystem.guild.id());
	}

	@Override
	public void changeRoles(final Map<Long, RoleChange> roleChanges) {
		handler.changeRole(remoteSystem.guild.id(), roleChanges);
	}

	@Override
	public Set<String> getRolesForUser(final long userId) {
		return handler.getRolesForUser(remoteSystem.guild.id(), userId);
	}
}
