package eu._4fh.wowsync.modules;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.wowsync.util.ClosableSingleton;

@DefaultAnnotation(NonNull.class)
public interface Module extends ClosableSingleton {
	public static class RoleChange {
		public final Set<String> toAdd;
		public final Set<String> toRemove;

		public RoleChange(final Set<String> toAdd, final Set<String> toRemove) {
			this.toAdd = Collections.unmodifiableSet(new HashSet<>(toAdd));
			this.toRemove = Collections.unmodifiableSet(new HashSet<>(toRemove));
		}

		@Override
		public String toString() {
			return "RoleChange [toAdd=" + toAdd + ", toRemove=" + toRemove + "]";
		}

		@Override
		public int hashCode() {
			return Objects.hash(toAdd, toRemove);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof RoleChange)) {
				return false;
			}
			RoleChange other = (RoleChange) obj;
			return Objects.equals(toAdd, other.toAdd) && Objects.equals(toRemove, other.toRemove);
		}
	}

	/**
	 *
	 * @return Map. Keys=RemoteId des Users. Set&lt;String&gt; mit allen Rollen
	 *         des Benutzers. Leeres Set, wenn der Benutzer überhaupt auf dem
	 *         Server vorhanden ist.
	 */
	Map<Long, Set<String>> getAllUsersWithRoles();

	Set<String> getRolesForUser(final long userId);

	void changeRoles(final Map<Long, RoleChange> roleChanges);

	void setCharacterNames(final long remoteUserId, final List<String> sortedCharnames);
}
