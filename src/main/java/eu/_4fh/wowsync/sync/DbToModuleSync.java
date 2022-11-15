package eu._4fh.wowsync.sync;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.wowsync.database.Db;
import eu._4fh.wowsync.database.Transaction;
import eu._4fh.wowsync.database.data.Character;
import eu._4fh.wowsync.database.data.RemoteSystem;
import eu._4fh.wowsync.database.data.RemoteSystemRankToGroup;
import eu._4fh.wowsync.modules.Module;
import eu._4fh.wowsync.modules.Module.RoleChange;
import eu._4fh.wowsync.modules.ModuleService;
import eu._4fh.wowsync.util.Singletons;

@DefaultAnnotation(NonNull.class)
public class DbToModuleSync {
	private final Db db;
	private final RemoteSystem remoteSystem;
	private final Map<Byte, Set<String>> rankToGroups;
	private final Set<String> allGroups;
	private final Module module;

	public DbToModuleSync(final RemoteSystem remoteSystem) {
		this(remoteSystem,
				Singletons.instance(ModuleService.class).findModule(remoteSystem.type, remoteSystem.systemId));
	}

	/*package for test*/ DbToModuleSync(final RemoteSystem remoteSystem, final Module module) {
		this.db = Singletons.instance(Db.class);
		this.remoteSystem = remoteSystem;
		this.module = module;
		this.rankToGroups = buildRankToGroupsMap(remoteSystem);
		allGroups = Collections
				.unmodifiableSet(rankToGroups.getOrDefault((byte) 0, Collections.singleton(remoteSystem.memberGroup)));
	}

	/*package for test*/ Map<Byte, Set<String>> buildRankToGroupsMap(final RemoteSystem remoteSystem) {
		try (Transaction.TransCnt transaction = db.createTransaction()) {
			final List<RemoteSystemRankToGroup> rank2Group = db.remoteSystemRankToGroup.byRemoteSystem(remoteSystem);
			if (rank2Group.isEmpty()) {
				return Collections.emptyMap();
			}
			final Map<Byte, String> rankMap = rank2Group.stream()
					.collect(Collectors.toMap(RemoteSystemRankToGroup::guildRank, RemoteSystemRankToGroup::groupName));
			final byte maxRank = rankMap.keySet().stream().collect(Collectors.maxBy(Comparator.naturalOrder()))
					.orElseThrow();

			final Set<String> tmpGroups = new HashSet<>();
			tmpGroups.add(remoteSystem.memberGroup);
			final Map<Byte, Set<String>> result = new HashMap<>(maxRank);
			for (byte rank = maxRank; rank >= 0; --rank) {
				final @CheckForNull String rankGroup = rankMap.get(rank);
				if (rankGroup != null) {
					tmpGroups.add(rankGroup);
				}
				result.put(rank, Collections.unmodifiableSet(new HashSet<>(tmpGroups)));
			}
			return Collections.unmodifiableMap(result);
		}
	}

	public boolean syncForUser(final long remoteUserId) {
		final @CheckForNull Byte minRank;
		try (Transaction.TransCnt transaction = db.createTransaction()) {
			final List<Character> characters = db.characters.byGuildAndRemoteSystemAndRemoteId(remoteSystem.guild,
					remoteSystem, remoteUserId);
			minRank = characters.stream().map(c -> c.rank).collect(Collectors.minBy(Comparator.naturalOrder()))
					.orElse(null);
		}
		if (minRank == null) {
			return false;
		}
		final Set<String> actualRoles = module.getRolesForUser(remoteUserId);
		final Set<String> expectedRoles = rankToGroups.getOrDefault(minRank,
				Collections.singleton(remoteSystem.memberGroup));
		final RoleChange change = calculateRoleChanges(actualRoles, expectedRoles);
		if (change == null || change.toAdd.isEmpty()) {
			return false;
		}
		module.changeRoles(Collections.singletonMap(remoteUserId, change));
		return true;
	}

	public void syncToModule() {
		final Set<String> defaultGroup = Collections.singleton(remoteSystem.memberGroup);
		final Map<Long, Set<String>> expectedRolesPerUser;
		try (Transaction.TransCnt transaction = db.createTransaction()) {
			final Map<Long, Byte> remoteIdWithGuildRank = db.groupedQueries
					.remoteIdAndMinGuildRankByGuild(remoteSystem);
			expectedRolesPerUser = remoteIdWithGuildRank.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
					entry -> rankToGroups.getOrDefault(entry.getValue(), defaultGroup)));
		}
		final Map<Long, Set<String>> actualRolesPerUser = module.getAllUsersWithRoles();

		final Map<Long, RoleChange> roleChanges = new HashMap<>(expectedRolesPerUser.size());
		final Set<Long> allRemoteIds = new HashSet<>(expectedRolesPerUser.size());
		allRemoteIds.addAll(expectedRolesPerUser.keySet());
		allRemoteIds.addAll(actualRolesPerUser.keySet());
		for (final long remoteUserId : allRemoteIds) {
			final RoleChange change = calculateRoleChanges(actualRolesPerUser.get(remoteUserId),
					expectedRolesPerUser.getOrDefault(remoteUserId, Collections.emptySet()));
			if (change != null && (!change.toAdd.isEmpty() || !change.toRemove.isEmpty())) {
				roleChanges.put(remoteUserId, change);
			}
		}
		module.changeRoles(roleChanges);
	}

	/*package for test*/ @CheckForNull
	RoleChange calculateRoleChanges(@CheckForNull Set<String> actualRoles, final Set<String> expectedRoles) {
		if (actualRoles == null) {
			// We cant set any roles, because the user isnt on the server anymore.
			return null;
		}

		actualRoles = new HashSet<>(actualRoles);
		// First we remove all roles that we dont manage
		actualRoles.retainAll(allGroups);

		if (expectedRoles.isEmpty() && !actualRoles.isEmpty()) {
			// User has been removed -> Add former group and remove all other roles
			if (remoteSystem.formerMemberGroup != null) {
				return new RoleChange(Collections.singleton(remoteSystem.formerMemberGroup), actualRoles);
			} else {
				return new RoleChange(Collections.emptySet(), actualRoles);
			}
		}
		final Set<String> toAdd = new HashSet<>(expectedRoles);
		// Add all roles that are expected but not yet assigned
		toAdd.removeAll(actualRoles);
		final Set<String> toRemove = new HashSet<>(actualRoles);
		// Remove all roles that are expected
		toRemove.removeAll(expectedRoles);
		return new RoleChange(toAdd, toRemove);
	}
}
