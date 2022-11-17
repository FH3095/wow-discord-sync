package eu._4fh.wowsync.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.abstract_bnet_api.oauth2.BattleNetRegion;
import eu._4fh.abstract_bnet_api.restclient.data.BattleNetProfileInfo;
import eu._4fh.abstract_bnet_api.restclient.data.BattleNetWowCharacter;
import eu._4fh.wowsync.database.Db;
import eu._4fh.wowsync.database.Transaction.TransCnt;
import eu._4fh.wowsync.database.data.Account;
import eu._4fh.wowsync.database.data.AccountRemoteId;
import eu._4fh.wowsync.database.data.Character;
import eu._4fh.wowsync.database.data.Guild;
import eu._4fh.wowsync.database.data.RemoteSystem;
import eu._4fh.wowsync.database.data.RemoteSystem.RemoteSystemType;
import eu._4fh.wowsync.util.Singletons;
import eu._4fh.wowsync.util.TestBase;

@DefaultAnnotation(NonNull.class)
class BattleNetToDbSyncTest implements TestBase {
	private final BattleNetToDbSync sync;
	private final Db db;

	public BattleNetToDbSyncTest() {
		sync = new BattleNetToDbSync();
		db = Singletons.instance(Db.class);
	}

	@Test
	void testInsertAndUpdateAccount() throws InterruptedException {
		final long bnetId = nextId();
		final String bnetTag1 = nextStr();
		final String bnetTag2 = nextStr();
		BattleNetProfileInfo info = new BattleNetProfileInfo(bnetId, bnetTag1);
		try (TransCnt trans = db.createTransaction()) {
			sync.insertOrUpdateAccount(info);
			trans.commit();
		}
		Account acc = db.accounts.byBnetId(bnetId);
		assertThat(acc).isNotNull();
		assertThat(acc.bnetTag()).isEqualTo(bnetTag1);

		TimeUnit.MILLISECONDS.sleep(10);
		final Date updatedAfter = new Date();
		info = new BattleNetProfileInfo(bnetId, bnetTag2);
		try (TransCnt trans = db.createTransaction()) {
			sync.insertOrUpdateAccount(info);
			trans.commit();
		}
		acc = db.accounts.byBnetId(bnetId);
		assertThat(acc).as("Sanity").isNotNull();
		assertThat(acc.bnetTag()).isEqualTo(bnetTag2);
		assertThat(acc.lastUpdate()).isAfterOrEqualTo(updatedAfter);
	}

	@Test
	void testInsertAndUpdateAccountRemoteId() {
		final long remoteUserId1 = nextId();
		final long remoteUserId2 = nextId();
		final Account account = new Account();
		account.setBnetId(nextId());
		account.setBnetTag(nextStr());
		account.setAdded(new Date());
		account.setLastUpdate(new Date());
		final Guild guild = new Guild();
		guild.setRegion(BattleNetRegion.EU);
		guild.setServer(nextStr());
		guild.setName(nextStr());
		final RemoteSystem remote = new RemoteSystem();
		remote.type = RemoteSystemType.Discord;
		remote.guild = guild;
		remote.memberGroup = "memberGroup";
		remote.nameOrLink = "TestDiscord";
		remote.systemId = nextId();
		remote.forTestSetKey(nextStr());
		try (TransCnt trans = db.createTransaction()) {
			db.save(account, guild, remote);
			trans.commit();
		}
		try (TransCnt trans = db.createTransaction()) {
			final AccountRemoteId accRemoteId = db.accountRemoteIds.byId(account, remote);
			assertThat(accRemoteId).isNull();
		}
		try (TransCnt trans = db.createTransaction()) {
			sync.insertOrUpdateAccountRemoteId(account, remote, remoteUserId1);
			trans.commit();
		}
		try (TransCnt trans = db.createTransaction()) {
			final AccountRemoteId accRemoteId = db.accountRemoteIds.byId(account, remote);
			assertThat(accRemoteId).isNotNull();
			assertThat(accRemoteId.remoteId).isEqualTo(remoteUserId1);
		}
		try (TransCnt trans = db.createTransaction()) {
			sync.insertOrUpdateAccountRemoteId(account, remote, remoteUserId2);
			trans.commit();
		}
		try (TransCnt trans = db.createTransaction()) {
			final AccountRemoteId accRemoteId = db.accountRemoteIds.byId(account, remote);
			assertThat(accRemoteId).isNotNull();
			assertThat(accRemoteId.remoteId).isEqualTo(remoteUserId2);
		}
	}

	@Test
	void testRemoveUnusedAccounts() {
		final Account acc1 = new Account();
		acc1.setBnetId(nextId());
		acc1.setBnetTag(nextStr());
		acc1.setAdded(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)));
		acc1.setLastUpdate(new Date());
		final Account acc2 = new Account();
		acc2.setBnetId(nextId());
		acc2.setBnetTag(nextStr());
		acc2.setAdded(Date.from(Instant.now().minus(20, ChronoUnit.DAYS)));
		acc2.setLastUpdate(new Date());
		final Character char1 = createChar(acc1, null);
		final Character char2 = createChar(acc2, null);
		final Character charWithoutAccount = createChar(null, null);
		try (TransCnt trans = db.createTransaction()) {
			db.save(acc1, acc2, char1, char2, charWithoutAccount);
			trans.commit();
			db.refresh(acc1, acc2, char1, char2, charWithoutAccount);
		}
		try (TransCnt trans = db.createTransaction()) {
			sync.removeUnusedAccounts();
			trans.commit();
		}
		try (TransCnt trans = db.createTransaction()) {
			assertThatNoException().isThrownBy(() -> db.refresh(acc1));
			assertThatNoException().isThrownBy(() -> db.refresh(char1));
			assertThat(db.find(Account.class, acc2.id())).isNull();
			assertThat(db.find(Character.class, char2.id)).isNull();
			assertThat(db.find(Character.class, charWithoutAccount.id)).isNull();
		}
	}

	private Character createChar(final @CheckForNull Account account, final @CheckForNull Guild guild) {
		final Character c = new Character();
		c.bnetId = nextId();
		c.region = BattleNetRegion.EU;
		c.server = nextStr();
		c.name = nextStr();
		c.account = account;
		c.guild = guild;
		return c;
	}

	private Account createAccount(final long bnetId) {
		final Account acc = new Account();
		acc.setBnetId(bnetId);
		acc.setBnetTag(nextStr());
		acc.setAdded(new Date());
		acc.setLastUpdate(new Date());
		return acc;
	}

	private Guild createGuild(final String name) {
		final Guild guild = new Guild();
		guild.setRegion(BattleNetRegion.EU);
		guild.setServer("1");
		guild.setName(name);
		return guild;
	}

	@Test
	void testInsertAndUpdateCharacter() {
		final Guild guild = createGuild(nextStr());
		final Account acc = createAccount(nextId());
		final long newCharNameBnetId = nextId();
		final Character rankChar = createChar(acc, guild);
		final Character nameChar = createChar(acc, guild);
		final String nameCharNewName = nextStr();
		try (TransCnt trans = db.createTransaction()) {
			db.save(acc, guild, rankChar, nameChar);
			trans.commit();
			db.refresh(acc, guild, rankChar, nameChar);
		}
		try (TransCnt trans = db.createTransaction()) {
			final List<BattleNetWowCharacter> charList = List.of(
					new BattleNetWowCharacter(newCharNameBnetId, nextStr(), "1", (byte) 1),
					new BattleNetWowCharacter(rankChar.bnetId, rankChar.name, rankChar.server, (byte) 7),
					new BattleNetWowCharacter(nameChar.bnetId, nameCharNewName, nameChar.server, (byte) 1));
			sync.updateCharacters(BattleNetRegion.EU, null, guild, charList);
			trans.commit();
		}
		try (TransCnt trans = db.createTransaction()) {
			final Character newTest = db.characters
					.byBnetIds(BattleNetRegion.EU, Collections.singleton(newCharNameBnetId)).get(0);
			assertThat(newTest).isNotNull();
			assertThat(newTest.guild.id()).isEqualTo(guild.id());
			assertThat(newTest.bnetId).isEqualTo(newCharNameBnetId);
			final Character rankTest = db.find(Character.class, rankChar.id);
			assertThat(rankTest).isNotNull();
			assertThat(rankTest.rank).isEqualTo((byte) 7);
			final Character nameTest = db.find(Character.class, nameChar.id);
			assertThat(nameTest).isNotNull();
			assertThat(nameTest.name).isEqualTo(nameCharNewName);
		}
	}

	@Test
	void testUpdateCharactersSetGuild() {
		final Guild guild = createGuild(nextStr());
		final Account acc = createAccount(nextId());
		final Character character = createChar(acc, null);
		try (TransCnt trans = db.createTransaction()) {
			db.save(guild, acc, character);
			trans.commit();
			db.refresh(guild, acc, character);
		}
		try (TransCnt trans = db.createTransaction()) {
			sync.updateCharacters(BattleNetRegion.EU, null, guild, Collections.singleton(
					new BattleNetWowCharacter(character.bnetId, character.name, character.server, (byte) 1)));
			trans.commit();
		}
		try (TransCnt trans = db.createTransaction()) {
			final Character changedChar = db.find(Character.class, character.id);
			assertThat(changedChar).isNotNull();
			assertThat(changedChar.guild.id()).isEqualTo(guild.id());
			assertThat(changedChar.account.id()).as("Sanity").isEqualTo(acc.id());
		}
	}

	@Test
	void testUpdateCharactersSetAccount() {
		final Guild guild = createGuild(nextStr());
		final Account acc = createAccount(nextId());
		final Character character = createChar(null, guild);
		try (TransCnt trans = db.createTransaction()) {
			db.save(guild, acc, character);
			trans.commit();
			db.refresh(guild, acc, character);
		}
		try (TransCnt trans = db.createTransaction()) {
			sync.updateCharacters(BattleNetRegion.EU, acc, null, Collections.singleton(
					new BattleNetWowCharacter(character.bnetId, character.name, character.server, (byte) 1)));
			trans.commit();
		}
		try (TransCnt trans = db.createTransaction()) {
			final Character changedChar = db.find(Character.class, character.id);
			assertThat(changedChar).isNotNull();
			assertThat(changedChar.account.id()).isEqualTo(acc.id());
			assertThat(changedChar.guild.id()).as("Sanity").isEqualTo(guild.id());
		}
	}
}
