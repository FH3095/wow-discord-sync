package eu._4fh.wowsync.modules;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.wowsync.util.ClosableSingleton;

@DefaultAnnotation(NonNull.class)
public interface Module extends ClosableSingleton {
	public static class RoleChange {
		public final Set<String> toAdd;
		public final Set<String> toRemove;

		private RoleChange(final Set<String> toAdd, final Set<String> toRemove) {
			this.toAdd = Collections.unmodifiableSet(new HashSet<>(toAdd));
			this.toRemove = Collections.unmodifiableSet(new HashSet<>(toRemove));
		}
	}

	Map<Long, Set<String>> getAllUsersWithRoles();

	Set<String> getRolesForUser(final long userId);

	void changeRoles(final Map<Long, RoleChange> roleChanges);
}
