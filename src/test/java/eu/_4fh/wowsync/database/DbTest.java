package eu._4fh.wowsync.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Key;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import eu._4fh.abstract_bnet_api.oauth2.BattleNetRegion;
import eu._4fh.wowsync.database.Transaction.TransCnt;
import eu._4fh.wowsync.database.data.Account;
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
		final Date tomorrow = new Date(new Date().getTime() + TimeUnit.DAYS.toMillis(1));
		assertThat(db.discordOnlineUsers.getLastOnlineBefore(-1L, tomorrow)).isEmpty();
		db.discordOnlineUsers.updateLastOnline(-1L, Set.of(-1L, -2L));
		TimeUnit.SECONDS.sleep(2);
		db.discordOnlineUsers.updateLastOnline(-1L, Set.of(-2L, -3L));
		final List<DiscordOnlineUser> users = db.discordOnlineUsers.getLastOnlineBefore(-1L, tomorrow);
		assertThat(users).hasSize(3);
		final Date userOneDate = users.stream().filter(dou -> dou.memberId == -1L).findAny().get().lastOnline;
		assertThat(users).filteredOn(dou -> dou.memberId != -1L).extracting(dou -> dou.lastOnline)
				.allMatch(d -> d.after(userOneDate));
	}

	@Test
	void testGetRemoteSystemByTypeAndRemoteId() {
		final String keyBase64 = "pHHnUG2t7Jbsf9N11aQ7/itzdSJ7hXkMzdoT9LLXpiKcXNUzleOoaE3M9Fn7d1qYvyEKYvkReJlMeRh6eZCVZQ==";
		try (TransCnt trans = db.createTransaction()) {
			final Guild guild = new Guild();
			final RemoteSystem system = new RemoteSystem();
			guild.setRegion(BattleNetRegion.EU);
			guild.setServer("1");
			guild.setName("1");
			system.guild = guild;
			system.type = RemoteSystemType.Discord;
			system.systemId = -1;
			system.nameOrLink = "1";
			system.memberGroup = "member";
			system.forTestSetKey(keyBase64);
			trans.em.persist(guild);
			trans.em.persist(system);
			trans.commit();
		}
		final RemoteSystem remoteSystem = db.remoteSystems.byTypeAndRemoteId(RemoteSystemType.Discord, -1L);
		assertThat(remoteSystem).isNotNull();
		assertThat(remoteSystem.id).isPositive();
		assertThat(remoteSystem.systemId).isEqualTo(-1L);
		final Key key = db.remoteSystems.hmacKeyById(remoteSystem.id);
		assertThat(Base64.getEncoder().encodeToString(key.getEncoded())).isEqualTo(keyBase64);
	}

	@Test
	void testGetGuildByRemoteSystem() {
		final long remoteSystemId;
		try (TransCnt trans = db.createTransaction()) {
			final Guild guild = new Guild();
			final RemoteSystem system = new RemoteSystem();
			guild.setRegion(BattleNetRegion.EU);
			guild.setServer("1");
			guild.setName("2");
			system.guild = guild;
			system.type = RemoteSystemType.Discord;
			system.systemId = 2;
			system.nameOrLink = "1";
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
		assertThat(guild.server()).isEqualTo("1");
		assertThat(guild.name()).isEqualTo("2");
	}

	@Test
	void removeCharactersFromGuild() {
		final Guild guild = new Guild();
		guild.setRegion(BattleNetRegion.EU);
		guild.setServer("1");
		guild.setName("4");
		final Account account = new Account();
		account.setBnetId(4);
		account.setBnetTag("4#4");
		account.setAdded(new Date());
		final Character char1 = new Character();
		final Character char2 = new Character();
		char1.region = char2.region = BattleNetRegion.EU;
		char1.server = char2.server = "1";
		char1.guild = char2.guild = guild;
		char1.account = char2.account = account;
		char1.name = "40";
		char1.bnetId = 40;
		char2.name = "41";
		char2.bnetId = 41;
		try (TransCnt trans = db.createTransaction()) {
			trans.em.persist(guild);
			trans.em.persist(account);
			trans.em.persist(char1);
			trans.em.persist(char2);
			trans.commit();
		}
		try (TransCnt trans = db.createTransaction()) {
			final int removed = db.characters.removeGuildReferenceWhereBnetIdNotIn(BattleNetRegion.EU, guild,
					Collections.singleton(40L));
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
}
