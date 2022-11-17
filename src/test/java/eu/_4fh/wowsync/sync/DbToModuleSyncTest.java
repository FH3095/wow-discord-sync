package eu._4fh.wowsync.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.jupiter.api.Test;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import eu._4fh.abstract_bnet_api.oauth2.BattleNetRegion;
import eu._4fh.wowsync.database.Db;
import eu._4fh.wowsync.database.Transaction.TransCnt;
import eu._4fh.wowsync.database.data.Account;
import eu._4fh.wowsync.database.data.AccountRemoteId;
import eu._4fh.wowsync.database.data.Character;
import eu._4fh.wowsync.database.data.Guild;
import eu._4fh.wowsync.database.data.RemoteSystem;
import eu._4fh.wowsync.database.data.RemoteSystem.RemoteSystemType;
import eu._4fh.wowsync.database.data.RemoteSystemRankToGroup;
import eu._4fh.wowsync.modules.Module;
import eu._4fh.wowsync.modules.Module.RoleChange;
import eu._4fh.wowsync.util.Singletons;
import eu._4fh.wowsync.util.TestBase;

class DbToModuleSyncTest implements TestBase {
	private final String MEMBER_GROUP = getClass().getSimpleName() + "MemberGroup";
	private final String FORMER_MEMBER_GROUP = getClass().getSimpleName() + "FormerMemberGroup";

	private final Db db = Singletons.instance(Db.class);
	private final Module testModule;
	private final RemoteSystem remoteSystem;

	DbToModuleSyncTest() {
		testModule = EasyMock.strictMock(Module.class);
		testModule.close();
		EasyMock.expectLastCall().asStub();
		EasyMock.replay(testModule);

		final Guild guild = new Guild();
		guild.setRegion(BattleNetRegion.EU);
		guild.setServer(nextStr());
		guild.setName(nextStr());
		final RemoteSystem remoteSystem = new RemoteSystem();
		remoteSystem.guild = guild;
		remoteSystem.memberGroup = MEMBER_GROUP;
		remoteSystem.formerMemberGroup = FORMER_MEMBER_GROUP;
		remoteSystem.nameOrLink = nextStr();
		remoteSystem.type = RemoteSystemType.Discord;
		remoteSystem.systemId = nextId();
		remoteSystem.forTestSetKey(nextStr());
		try (TransCnt trans = db.createTransaction()) {
			db.save(guild, remoteSystem);
			trans.commit();
			db.refresh(remoteSystem);
		}
		this.remoteSystem = remoteSystem;
	}

	private RemoteSystemRankToGroup createRankToGroup(final int rank, final String group) {
		final RemoteSystemRankToGroup rankToGroup = new RemoteSystemRankToGroup();
		rankToGroup.setRemoteSystem(remoteSystem);
		rankToGroup.setGuildRank((byte) rank);
		rankToGroup.setGroupName(group);
		try (TransCnt trans = db.createTransaction()) {
			db.save(rankToGroup);
			trans.commit();
			db.refresh(rankToGroup);
		}
		return rankToGroup;
	}

	@Test
	void testBuildRankToGroupsMap() {
		final RemoteSystemRankToGroup r5Group = createRankToGroup(5, nextStr());
		final RemoteSystemRankToGroup r2Group = createRankToGroup(2, nextStr());
		final Set<String> r5Groups = Set.of(MEMBER_GROUP, r5Group.groupName());
		final Set<String> r2Groups = Set.of(MEMBER_GROUP, r5Group.groupName(), r2Group.groupName());

		final Map<Byte, Set<String>> map = new DbToModuleSync(remoteSystem, testModule)
				.buildRankToGroupsMap(remoteSystem);
		assertThat(map).containsOnly(entry((byte) 5, r5Groups), entry((byte) 4, r5Groups), entry((byte) 3, r5Groups),
				entry((byte) 2, r2Groups), entry((byte) 1, r2Groups), entry((byte) 0, r2Groups));
	}

	@Test
	void testCalculateRolesUserNotOnServer() {
		final DbToModuleSync sync = new DbToModuleSync(remoteSystem, testModule);
		assertThat(sync.calculateRoleChanges(null, Set.of(MEMBER_GROUP, "group1"))).isNull();
	}

	@Test
	void testCalculateRolesForRemovedUserNoFormerGroup() {
		final DbToModuleSync sync = new DbToModuleSync(remoteSystem, testModule);
		final RoleChange change = sync.calculateRoleChanges(Set.of(MEMBER_GROUP, "notManagedGroup"),
				Collections.emptySet());
		assertThat(change).isNotNull();
		assertThat(change.toRemove).containsExactlyInAnyOrder(MEMBER_GROUP);
		assertThat(change.toAdd).containsExactlyInAnyOrder(FORMER_MEMBER_GROUP);
	}

	@Test
	void testCalculateRolesForRemovedUserWithoFormerGroup() {
		final String formerMemberGroup = nextStr();
		try (TransCnt trans = db.createTransaction()) {
			db.refresh(remoteSystem);
			remoteSystem.formerMemberGroup = formerMemberGroup;
			db.save(remoteSystem);
			trans.commit();
		}
		final DbToModuleSync sync = new DbToModuleSync(remoteSystem, testModule);
		final RoleChange change = sync.calculateRoleChanges(Set.of(MEMBER_GROUP, "notManagedGroup"),
				Collections.emptySet());
		assertThat(change).isNotNull();
		assertThat(change.toRemove).containsExactlyInAnyOrder(MEMBER_GROUP);
		assertThat(change.toAdd).containsExactlyInAnyOrder(formerMemberGroup);
	}

	@Test
	void testCalculateRolesForNewUser() {
		final DbToModuleSync sync = new DbToModuleSync(remoteSystem, testModule);
		final RoleChange change = sync.calculateRoleChanges(Collections.singleton(FORMER_MEMBER_GROUP),
				Set.of(MEMBER_GROUP, "group1"));
		assertThat(change).isNotNull();
		assertThat(change.toRemove).containsExactlyInAnyOrder(FORMER_MEMBER_GROUP);
		assertThat(change.toAdd).containsExactlyInAnyOrder(MEMBER_GROUP, "group1");
	}

	@Test
	void testCalculateRolesForExistingUser() {
		final String groupName = nextStr() + "NewGroupName";
		try (TransCnt trans = db.createTransaction()) {
			final RemoteSystemRankToGroup rank = new RemoteSystemRankToGroup();
			rank.setRemoteSystem(remoteSystem);
			rank.setGuildRank((byte) 5);
			rank.setGroupName(groupName);
			db.save(rank);
			trans.commit();
		}

		final DbToModuleSync sync = new DbToModuleSync(remoteSystem, testModule);
		final RoleChange change = sync.calculateRoleChanges(Set.of(MEMBER_GROUP, "notManagedGroup"),
				Set.of(MEMBER_GROUP, groupName));
		assertThat(change).isNotNull();
		assertThat(change.toRemove).isEmpty();
		assertThat(change.toAdd).containsExactlyInAnyOrder(groupName);
	}

	private Character createCharacter(final long remoteUserId, final @CheckForNull Guild guild) {
		final Account acc = new Account();
		acc.setBnetId(nextId());
		acc.setBnetTag(nextStr());
		acc.setAdded(new Date());
		final AccountRemoteId ari = new AccountRemoteId();
		ari.account = acc;
		ari.remoteSystem = remoteSystem;
		ari.remoteId = remoteUserId;
		final Character character = new Character();
		character.account = acc;
		character.guild = guild;
		character.bnetId = nextId();
		character.region = BattleNetRegion.EU;
		character.server = nextStr();
		character.name = nextStr();
		character.rank = Byte.MAX_VALUE;
		try (TransCnt trans = db.createTransaction()) {
			db.save(acc, ari, character);
			trans.commit();
			db.refresh(character);
		}
		return character;
	}

	@Test
	void testSingleUserNoChange() {
		final long remoteUserId = nextId();
		createCharacter(remoteUserId, null);
		final DbToModuleSync sync = new DbToModuleSync(remoteSystem, testModule);
		final boolean result = sync.syncForUser(remoteUserId);
		assertThat(result).isFalse();
	}

	@Test
	void testSingleUserAdd() {
		final long remoteUserId = nextId();
		createCharacter(remoteUserId, remoteSystem.guild);

		final Module module = EasyMock.strictMock(Module.class);
		expect(module.getRolesForUser(remoteUserId)).andStubReturn(Collections.emptySet());
		module.setCharacterNames(EasyMock.eq(remoteUserId), EasyMock.anyObject());
		expectLastCall();
		final Capture<Map<Long, RoleChange>> roleChangesCapture = EasyMock.newCapture(CaptureType.ALL);
		module.changeRoles(EasyMock.capture(roleChangesCapture));
		expectLastCall();
		EasyMock.replay(module);

		final DbToModuleSync sync = new DbToModuleSync(remoteSystem, module);
		final boolean result = sync.syncForUser(remoteUserId);
		EasyMock.verify(module);
		assertThat(result).isTrue();
		final List<Map<Long, RoleChange>> roleChanges = roleChangesCapture.getValues();
		assertThat(roleChanges).hasSize(1);
		final Map<Long, RoleChange> roleChange = roleChanges.get(0);
		assertThat(roleChange).containsOnlyKeys(remoteUserId);
		assertThat(roleChange.get(remoteUserId).toRemove).isEmpty();
		assertThat(roleChange.get(remoteUserId).toAdd).containsExactlyInAnyOrder(MEMBER_GROUP);
	}

	@Test
	void testSingleUserModify() {
		final long remoteUserId = nextId();
		final String groupName = getClass().getSimpleName() + "NewGroup";
		final Character character = createCharacter(remoteUserId, remoteSystem.guild);
		try (TransCnt trans = db.createTransaction()) {
			db.refresh(character);
			final RemoteSystemRankToGroup rankGroup = new RemoteSystemRankToGroup();
			rankGroup.setRemoteSystem(remoteSystem);
			rankGroup.setGuildRank((byte) 5);
			rankGroup.setGroupName(groupName);
			character.rank = 5;
			db.save(character, rankGroup);
			trans.commit();
		}

		final Module module = EasyMock.strictMock(Module.class);
		expect(module.getRolesForUser(remoteUserId)).andStubReturn(Collections.singleton(MEMBER_GROUP));
		module.setCharacterNames(EasyMock.eq(remoteUserId), EasyMock.anyObject());
		expectLastCall();
		final Capture<Map<Long, RoleChange>> roleChangesCapture = EasyMock.newCapture(CaptureType.ALL);
		module.changeRoles(EasyMock.capture(roleChangesCapture));
		expectLastCall();
		EasyMock.replay(module);

		final DbToModuleSync sync = new DbToModuleSync(remoteSystem, module);
		final boolean result = sync.syncForUser(remoteUserId);
		EasyMock.verify(module);
		assertThat(result).isTrue();
		final List<Map<Long, RoleChange>> roleChanges = roleChangesCapture.getValues();
		assertThat(roleChanges).hasSize(1);
		final Map<Long, RoleChange> roleChange = roleChanges.get(0);
		assertThat(roleChange).containsOnlyKeys(remoteUserId);
		assertThat(roleChange.get(remoteUserId).toRemove).isEmpty();
		assertThat(roleChange.get(remoteUserId).toAdd).containsExactlyInAnyOrder(groupName);
	}
}
