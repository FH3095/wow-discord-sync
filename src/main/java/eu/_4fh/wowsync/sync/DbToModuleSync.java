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
import eu._4fh.wowsync.util.Range;
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
		final Set<String> allGroupsTmp = rankToGroups.values().stream().flatMap(Set::stream)
				.collect(Collectors.toCollection(HashSet::new));
		allGroupsTmp.add(remoteSystem.memberGroup);
		allGroups = Collections.unmodifiableSet(allGroupsTmp);
	}

	/*package for test*/ Map<Byte, Set<String>> buildRankToGroupsMap(final RemoteSystem remoteSystem) {
		try (Transaction.TransCnt transaction = db.createTransaction()) {
			final List<RemoteSystemRankToGroup> rank2Group = db.remoteSystemRankToGroup.byRemoteSystem(remoteSystem);
			if (rank2Group.isEmpty()) {
				return Collections.emptyMap();
			}
			final Map<Range<Byte>, String> rankRangesMap = rank2Group.stream()
					.collect(Collectors.toMap(r2g -> new Range<Byte>(r2g.guildRankFrom(), r2g.getGuildRankTo()),
							RemoteSystemRankToGroup::groupName));
			final byte maxRank = rankRangesMap.keySet().stream().map(r -> r.end)
					.collect(Collectors.maxBy(Comparator.naturalOrder())).orElseThrow();

			final Map<Byte, Set<String>> result = new HashMap<>(maxRank);
			for (byte rank = maxRank; rank >= 0; --rank) {
				final Set<String> rankGroups = new HashSet<>();
				rankGroups.add(remoteSystem.memberGroup);
				for (Map.Entry<Range<Byte>, String> rankRangeGroup : rankRangesMap.entrySet()) {
					if (rankRangeGroup.getKey().fits(rank)) {
						rankGroups.add(rankRangeGroup.getValue());
					}
				}
				result.put(rank, Collections.unmodifiableSet(rankGroups));
			}
			return Collections.unmodifiableMap(result);
		}
	}

	public boolean syncForUser(final long remoteUserId) {
		final Set<Byte> ranks;
		final List<String> sortedCharnames;
		try (Transaction.TransCnt transaction = db.createTransaction()) {
			final List<Character> characters = db.characters.byGuildAndRemoteSystemAndRemoteId(remoteSystem.guild,
					remoteSystem, remoteUserId);
			ranks = characters.stream().map(c -> c.rank).collect(Collectors.toUnmodifiableSet());
			sortedCharnames = characters.stream().sorted(Comparator.<Character>comparingInt(c -> c.rank)
					.thenComparing(c -> c.name).thenComparing(c -> c.server)).map(c -> c.name)
					.collect(Collectors.toUnmodifiableList());
		}
		if (ranks.isEmpty()) {
			return false;
		}
		final Set<String> actualRoles = module.getRolesForUser(remoteUserId);
		final Set<String> expectedRoles = ranks.stream().flatMap(
				rank -> rankToGroups.getOrDefault(rank, Collections.singleton(remoteSystem.memberGroup)).stream())
				.collect(Collectors.toUnmodifiableSet());
		final RoleChange change = calculateRoleChanges(actualRoles, expectedRoles);
		if (change == null || change.toAdd.isEmpty()) {
			return false;
		}
		module.setCharacterNames(remoteUserId, sortedCharnames);
		module.changeRoles(Collections.singletonMap(remoteUserId, change));
		return true;
	}

	public void syncToModule() {
		final Map<Long, Set<String>> expectedRolesPerUser = new HashMap<>();
		try (Transaction.TransCnt transaction = db.createTransaction()) {
			final Map<Long, List<Character>> charactersByRemoteAccountId = db.accountRemoteIds
					.remoteIdWithCharactersByGuildAndRemoteSystem(remoteSystem.guild, remoteSystem);
			final Set<String> memberGroupSet = Collections.singleton(remoteSystem.memberGroup);
			for (Map.Entry<Long, List<Character>> remoteIdWithCharacters : charactersByRemoteAccountId.entrySet()) {
				final Set<String> roles = remoteIdWithCharacters.getValue().stream()
						.flatMap(c -> rankToGroups.getOrDefault(c.rank, memberGroupSet).stream())
						.collect(Collectors.toUnmodifiableSet());
				expectedRolesPerUser.put(remoteIdWithCharacters.getKey(), roles);
			}
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
	RoleChange calculateRoleChanges(final @CheckForNull Set<String> allActualRoles, final Set<String> expectedRoles) {
		if (allActualRoles == null) {
			// We cant set any roles, because the user isnt on the server anymore.
			return null;
		}

		final Set<String> actualRoles = new HashSet<>(allActualRoles);
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
		if (remoteSystem.formerMemberGroup != null && allActualRoles.contains(remoteSystem.formerMemberGroup)) {
			toRemove.add(remoteSystem.formerMemberGroup);
		}
		return new RoleChange(toAdd, toRemove);
	}
}
