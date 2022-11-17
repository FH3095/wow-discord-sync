package eu._4fh.wowsync.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.security.Key;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import eu._4fh.abstract_bnet_api.oauth2.BattleNetRegion;
import eu._4fh.wowsync.database.Transaction.TransCnt;
import eu._4fh.wowsync.database.data.Account;
import eu._4fh.wowsync.database.data.AccountRemoteId;
import eu._4fh.wowsync.database.data.Character;
import eu._4fh.wowsync.database.data.DiscordOnlineUser;
import eu._4fh.wowsync.database.data.Guild;
import eu._4fh.wowsync.database.data.RemoteSystem;
import eu._4fh.wowsync.database.data.RemoteSystem.RemoteSystemType;
import eu._4fh.wowsync.util.Singletons;
import eu._4fh.wowsync.util.TestBase;

class DbTest implements TestBase {
	private final Db db = Singletons.instance(Db.class);

	@Test
	void testSetLastOnline() throws InterruptedException {
		final long guildId = nextId();
		final long user1Id = nextId();
		final long user2Id = nextId();
		final long user3Id = nextId();
		final Date tomorrow = new Date(new Date().getTime() + TimeUnit.DAYS.toMillis(1));

		assertThat(db.discordOnlineUsers.getLastOnlineBefore(guildId, tomorrow)).isEmpty();
		db.discordOnlineUsers.updateLastOnline(guildId, user1Id, Long.toString(user1Id));
		db.discordOnlineUsers.updateLastOnline(guildId, user2Id, Long.toString(user2Id));
		TimeUnit.SECONDS.sleep(2);
		db.discordOnlineUsers.updateLastOnline(guildId, user2Id, Long.toString(user2Id));
		db.discordOnlineUsers.updateLastOnline(guildId, user3Id, Long.toString(user3Id));
		final List<DiscordOnlineUser> users = db.discordOnlineUsers.getLastOnlineBefore(guildId, tomorrow);
		assertThat(users).hasSize(3);
		final Date userOneDate = users.stream().filter(dou -> dou.memberId == user1Id).findAny().get().lastOnline;
		assertThat(users).filteredOn(dou -> dou.memberId != user1Id).extracting(dou -> dou.lastOnline)
				.allMatch(d -> d.after(userOneDate));
	}

	@Test
	void testGetRemoteSystemByTypeAndRemoteId() {
		final String keyBase64 = "pHHnUG2t7Jbsf9N11aQ7/itzdSJ7hXkMzdoT9LLXpiKcXNUzleOoaE3M9Fn7d1qYvyEKYvkReJlMeRh6eZCVZQ==";
		final long remoteSystemId = nextId();
		try (TransCnt trans = db.createTransaction()) {
			final Guild guild = new Guild();
			final RemoteSystem system = new RemoteSystem();
			guild.setRegion(BattleNetRegion.EU);
			guild.setServer(nextStr());
			guild.setName(nextStr());
			system.guild = guild;
			system.type = RemoteSystemType.Discord;
			system.systemId = remoteSystemId;
			system.nameOrLink = nextStr();
			system.memberGroup = "member";
			system.forTestSetKey(keyBase64);
			trans.em.persist(guild);
			trans.em.persist(system);
			trans.commit();
		}
		final RemoteSystem remoteSystem = db.remoteSystems.byTypeAndRemoteId(RemoteSystemType.Discord, remoteSystemId);
		assertThat(remoteSystem).isNotNull();
		assertThat(remoteSystem.id).isPositive();
		assertThat(remoteSystem.systemId).isEqualTo(remoteSystemId);
		final Key key = db.remoteSystems.hmacKeyById(remoteSystem.id);
		assertThat(Base64.getEncoder().encodeToString(key.getEncoded())).isEqualTo(keyBase64);
	}

	@Test
	void testGetGuildByRemoteSystem() {
		final long remoteSystemId;
		final String guildName = nextStr();
		try (TransCnt trans = db.createTransaction()) {
			final Guild guild = new Guild();
			final RemoteSystem system = new RemoteSystem();
			guild.setRegion(BattleNetRegion.EU);
			guild.setServer(nextStr());
			guild.setName(guildName);
			system.guild = guild;
			system.type = RemoteSystemType.Discord;
			system.systemId = nextId();
			system.nameOrLink = nextStr();
			system.memberGroup = "member";
			system.forTestSetKey("1");
			trans.em.persist(guild);
			trans.em.persist(system);
			trans.commit();
			remoteSystemId = system.id;
		}
		System.out.println("Generated RemoteSystem-Id: " + remoteSystemId);
		final Guild guild = db.guilds.byRemoteSystem(remoteSystemId);
		assertThat(guild.region()).isEqualTo(BattleNetRegion.EU);
		assertThat(guild.name()).isEqualTo(guildName);
	}

	@Test
	void removeCharactersFromGuild() {
		final long char1BnetId = nextId();
		final long char2BnetId = nextId();
		final Guild guild = new Guild();
		guild.setRegion(BattleNetRegion.EU);
		guild.setServer(nextStr());
		guild.setName(nextStr());
		final Account account = new Account();
		account.setBnetId(nextId());
		account.setBnetTag(nextStr());
		account.setAdded(new Date());
		account.setLastUpdate(new Date());
		final Character char1 = new Character();
		final Character char2 = new Character();
		char1.region = char2.region = BattleNetRegion.EU;
		char1.server = char2.server = nextStr();
		char1.guild = char2.guild = guild;
		char1.account = char2.account = account;
		char1.name = nextStr();
		char1.bnetId = char1BnetId;
		char2.name = nextStr();
		char2.bnetId = char2BnetId;
		try (TransCnt trans = db.createTransaction()) {
			trans.em.persist(guild);
			trans.em.persist(account);
			trans.em.persist(char1);
			trans.em.persist(char2);
			trans.commit();
		}
		try (TransCnt trans = db.createTransaction()) {
			final int removed = db.characters.removeGuildReferenceWhereBnetIdNotIn(BattleNetRegion.EU, guild,
					Collections.singleton(char1BnetId));
			assertThat(removed).as("One character must be removed from the guild").isOne();
			trans.commit();
		}
		try (TransCnt trans = db.createTransaction()) {
			trans.em.refresh(char1);
			trans.em.refresh(char2);
			assertThat(char1.guild).isNotNull();
			assertThat(char2.guild).isNull();
		}
	}

	@Test
	void testRemoteIdAndMinGuildRankByGuild() {
		final byte minGuildRank = 5;
		final long accountRemoteSystemId = nextId();
		final Guild guild = new Guild();
		final RemoteSystem remoteSystem = new RemoteSystem();
		final Account account = new Account();
		final AccountRemoteId accountRemoteId = new AccountRemoteId();
		final Character char1 = new Character();
		final Character char2 = new Character();
		final Character char3 = new Character();
		guild.setRegion(BattleNetRegion.EU);
		guild.setServer(nextStr());
		guild.setName(nextStr());
		remoteSystem.guild = guild;
		remoteSystem.memberGroup = "member";
		remoteSystem.nameOrLink = nextStr();
		remoteSystem.type = RemoteSystemType.Discord;
		remoteSystem.systemId = nextId();
		remoteSystem.forTestSetKey(nextStr());
		account.setBnetId(nextId());
		account.setBnetTag(nextStr());
		account.setAdded(new Date());
		account.setLastUpdate(new Date());
		accountRemoteId.account = account;
		accountRemoteId.remoteSystem = remoteSystem;
		accountRemoteId.remoteId = accountRemoteSystemId;
		char1.account = char2.account = account;
		char1.guild = char2.guild = guild;
		char3.guild = null;
		char1.region = char2.region = char3.region = BattleNetRegion.EU;
		char1.server = char2.server = char3.server = nextStr();
		char1.name = nextStr();
		char2.name = nextStr();
		char3.name = nextStr();
		char1.bnetId = nextId();
		char2.bnetId = nextId();
		char3.bnetId = nextId();
		char1.rank = 7;
		char2.rank = minGuildRank;
		char3.rank = 1;
		try (TransCnt trans = db.createTransaction()) {
			db.save(guild, remoteSystem, account, accountRemoteId, char1, char2, char3);
			trans.commit();
		}

		final Map<Long, Byte> result = db.groupedQueries.remoteIdAndMinGuildRankByGuild(remoteSystem);
		assertThat(result).containsOnly(entry(accountRemoteSystemId, minGuildRank));
	}
}
