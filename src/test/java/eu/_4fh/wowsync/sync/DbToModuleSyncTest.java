package eu._4fh.wowsync.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import eu._4fh.abstract_bnet_api.oauth2.BattleNetRegion;
import eu._4fh.wowsync.database.Db;
import eu._4fh.wowsync.database.Transaction.TransCnt;
import eu._4fh.wowsync.database.data.Account;
import eu._4fh.wowsync.database.data.AccountRemoteId;
import eu._4fh.wowsync.database.data.Character;
import eu._4fh.wowsync.database.data.DiscordOnlineUser;
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

	private Db db;
	private Module testModule;
	private RemoteSystem remoteSystem;

	@BeforeEach
	void setup() {
		db = Singletons.instance(Db.class);

		testModule = EasyMock.strictMock(Module.class);
		testModule.close();
		EasyMock.expectLastCall().asStub();
		EasyMock.replay(testModule);

		final Guild guild = new Guild();
		guild.setRegion(BattleNetRegion.EU);
		guild.setServer(TestBase.sNextStr());
		guild.setName(TestBase.sNextStr());
		remoteSystem = new RemoteSystem();
		remoteSystem.guild = guild;
		remoteSystem.memberGroup = MEMBER_GROUP;
		remoteSystem.formerMemberGroup = FORMER_MEMBER_GROUP;
		remoteSystem.nameOrLink = TestBase.sNextStr();
		remoteSystem.type = RemoteSystemType.Discord;
		remoteSystem.systemId = TestBase.sNextId();
		remoteSystem.forTestSetKey(TestBase.sNextStr());
		try (TransCnt trans = db.createTransaction()) {
			db.save(guild, remoteSystem);
			trans.commit();
			db.refresh(remoteSystem);
		}
	}

	@AfterEach
	void teardown() {
		EasyMock.verify(testModule);
	}

	private RemoteSystemRankToGroup createRankToGroup(final int rankFrom, final int rankTo, final String group) {
		final RemoteSystemRankToGroup rankToGroup = new RemoteSystemRankToGroup();
		rankToGroup.setRemoteSystem(remoteSystem);
		rankToGroup.setGuildRankFrom((byte) rankFrom);
		rankToGroup.setGuildRankTo((byte) rankTo);
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
		final RemoteSystemRankToGroup r5Group = createRankToGroup(0, 5, nextStr());
		final RemoteSystemRankToGroup r2Group = createRankToGroup(0, 2, nextStr());
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
	void testCalculateRolesForRemovedUserWithoutFormerGroup() {
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
			rank.setGuildRankFrom((byte) 0);
			rank.setGuildRankTo((byte) 5);
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
		final Account acc;
		final AccountRemoteId ari;
		try (TransCnt trans = db.createTransaction()) {
			final List<AccountRemoteId> remoteIds = db.forTestQuery(AccountRemoteId.class,
					"SELECT ari FROM AccountRemoteId ari WHERE ari.remoteId = " + remoteUserId);
			assertThat(remoteIds).size().isLessThanOrEqualTo(1);

			if (remoteIds.size() == 1) {
				ari = remoteIds.get(0);
				acc = ari.account;
			} else {
				acc = new Account();
				acc.setBnetId(nextId());
				acc.setBnetTag(nextStr());
				acc.setAdded(LocalDate.now());
				acc.setLastUpdate(LocalDate.now());
				ari = new AccountRemoteId();
				ari.account = acc;
				ari.remoteSystem = remoteSystem;
				ari.remoteId = remoteUserId;
			}
		}

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
			rankGroup.setGuildRankFrom((byte) 0);
			rankGroup.setGuildRankTo((byte) 5);
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

	@Test
	void testGroupOnlyForRange() {
		final RemoteSystemRankToGroup r0To1Group = createRankToGroup(0, 1, nextStr());
		final RemoteSystemRankToGroup r2To5Group = createRankToGroup(2, 5, nextStr());
		final RemoteSystemRankToGroup r3To5Group = createRankToGroup(3, 5, nextStr());
		final Set<String> r0To1Groups = Set.of(MEMBER_GROUP, r0To1Group.groupName());
		final Set<String> r2Groups = Set.of(MEMBER_GROUP, r2To5Group.groupName());
		final Set<String> r3To5Groups = Set.of(MEMBER_GROUP, r2To5Group.groupName(), r3To5Group.groupName());

		final Map<Byte, Set<String>> map = new DbToModuleSync(remoteSystem, testModule)
				.buildRankToGroupsMap(remoteSystem);
		assertThat(map).containsOnly(entry((byte) 5, r3To5Groups), entry((byte) 4, r3To5Groups),
				entry((byte) 3, r3To5Groups), entry((byte) 2, r2Groups), entry((byte) 1, r0To1Groups),
				entry((byte) 0, r0To1Groups));

	}

	@Test
	void testSyncToModuleOnlyMemberGroup() {
		final long user1Id = nextId();
		final long user2Id = nextId();
		createCharacter(user2Id, remoteSystem.guild);

		final Module testModule = EasyMock.strictMock(Module.class);
		testModule.close();
		EasyMock.expectLastCall().asStub();
		EasyMock.expect(testModule.getAllUsersWithRoles())
				.andStubReturn(Map.of(user1Id, Set.of(MEMBER_GROUP), user2Id, Set.of()));
		testModule.changeRoles(Map.of(user1Id, new RoleChange(Set.of(FORMER_MEMBER_GROUP), Set.of(MEMBER_GROUP)),
				user2Id, new RoleChange(Set.of(MEMBER_GROUP), Set.of())));
		EasyMock.expectLastCall().once();
		EasyMock.replay(testModule);

		new DbToModuleSync(remoteSystem, testModule).syncToModule();

		EasyMock.verify(testModule);
	}

	@Test
	void testSyncToModuleMultipleRanksWithGroups() {
		final String GROUP1 = "group1";
		final String GROUP2 = "group2";
		final long userId = nextId();
		createRankToGroup(1, 1, GROUP1);
		createRankToGroup(2, 2, GROUP2);

		try (final TransCnt trans = db.createTransaction()) {
			final Character char1 = createCharacter(userId, remoteSystem.guild);
			final Character char2 = createCharacter(userId, remoteSystem.guild);
			char1.rank = 1;
			char2.rank = 2;
			db.save(char1, char2);
			trans.commit();
		}

		final Module testModule = EasyMock.strictMock(Module.class);
		testModule.close();
		EasyMock.expectLastCall().asStub();
		EasyMock.expect(testModule.getAllUsersWithRoles()).andStubReturn(Map.of(userId, Set.of(MEMBER_GROUP)));
		testModule.changeRoles(Map.of(userId, new RoleChange(Set.of(GROUP1, GROUP2), Set.of())));
		EasyMock.expectLastCall().once();
		EasyMock.replay(testModule);

		new DbToModuleSync(remoteSystem, testModule).syncToModule();

		EasyMock.verify(testModule);
	}

	@Test
	void testSyncsyncForUser() {
		final String GROUP1 = "group1";
		final String GROUP2 = "group2";
		createRankToGroup(0, 0, GROUP1);
		createRankToGroup(1, 1, GROUP2);

		final long userId = nextId();
		final Character char1, char2, char3;
		try (TransCnt trans = db.createTransaction()) {
			char1 = createCharacter(userId, remoteSystem.guild);
			char2 = createCharacter(userId, remoteSystem.guild);
			char3 = createCharacter(userId, remoteSystem.guild);
			char1.name = "char4";
			char2.name = "char2";
			char3.name = "char3";
			char3.rank = 0;
			db.save(char1, char2, char3);
			trans.commit();
		}

		final Module testModule = EasyMock.strictMock(Module.class);
		testModule.close();
		EasyMock.expectLastCall().asStub();
		EasyMock.expect(testModule.getRolesForUser(userId)).andStubReturn(Set.of());
		testModule.setCharacterNames(userId, List.of(char3.name, char2.name, char1.name));
		EasyMock.expectLastCall().once();
		testModule.changeRoles(Map.of(userId, new RoleChange(Set.of(MEMBER_GROUP, GROUP1), Set.of())));
		EasyMock.expectLastCall().once();
		EasyMock.replay(testModule);

		assertThat(new DbToModuleSync(remoteSystem, testModule).syncForUser(userId)).isTrue();

		EasyMock.verify(testModule);
	}

	@Test
	void testSyncsyncForUserNotMember() {
		final long userId = nextId();
		createCharacter(userId, null);

		final Module testModule = EasyMock.strictMock(Module.class);
		testModule.close();
		EasyMock.expectLastCall().asStub();
		EasyMock.expect(testModule.getRolesForUser(userId)).andStubReturn(Set.of());
		EasyMock.replay(testModule);

		assertThat(new DbToModuleSync(remoteSystem, testModule).syncForUser(userId)).isFalse();

		EasyMock.verify(testModule);
	}

	private DiscordOnlineUser createDcOnlineUser(final long id, final LocalDate lastOnline) {
		return new DiscordOnlineUser(remoteSystem.systemId, id, Long.toUnsignedString(id), lastOnline);
	}

	@Test
	void deleteInactiveUsers() {
		final long userToday = nextId();
		final long userYesterday = nextId();
		final long userDayBeforeYesterday = nextId();
		final long userInGroup = nextId(); // User in a managed group, should not be kicked.
		final long userNotYetSeen = nextId(); // User that is not yet in DiscordOnlineUser. Should not be kicked.

		final String GROUP = "group";
		createRankToGroup(0, 0, GROUP);

		final LocalDate today = LocalDate.now(Clock.systemUTC()), yesterday = today.minusDays(1),
				dayBeforeYesterday = today.minusDays(2);

		try (TransCnt trans = db.createTransaction()) {
			db.save(createDcOnlineUser(userToday, today), createDcOnlineUser(userYesterday, yesterday),
					createDcOnlineUser(userDayBeforeYesterday, dayBeforeYesterday),
					createDcOnlineUser(userInGroup, dayBeforeYesterday));
			trans.commit();
		}

		final Module testModule = EasyMock.strictMock(Module.class);
		testModule.close();
		EasyMock.expectLastCall().asStub();
		EasyMock.expect(testModule.deleteUsersAfterInactiveDays()).andStubReturn(1);
		EasyMock.expect(testModule.getAllUsersWithRoles()).andStubReturn(Map.of(userToday, Set.of(), userYesterday,
				Set.of(), userDayBeforeYesterday, Set.of(), userInGroup, Set.of(GROUP), userNotYetSeen, Set.of()));
		EasyMock.expect(testModule.deleteInactiveUsers(EasyMock.eq(Set.of(userDayBeforeYesterday)))).andReturn(1)
				.once();
		EasyMock.replay(testModule);

		final int numDeleted = new DbToModuleSync(remoteSystem, testModule).deleteInactiveUsers();

		EasyMock.verify(testModule);
		assertThat(numDeleted).isOne();
	}

	@Test
	void testNoInactiveUserDeletionActivated() {
		final Module testModule = EasyMock.strictMock(Module.class);
		testModule.close();
		EasyMock.expectLastCall().asStub();
		EasyMock.expect(testModule.deleteUsersAfterInactiveDays()).andStubReturn(0);
		EasyMock.replay(testModule);

		final int numDeleted = new DbToModuleSync(remoteSystem, testModule).deleteInactiveUsers();

		EasyMock.verify(testModule);
		assertThat(numDeleted).isZero();
	}
}
