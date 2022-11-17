package eu._4fh.wowsync.sync;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.CheckForNull;

import org.dmfs.httpessentials.exceptions.ProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.abstract_bnet_api.oauth2.BattleNetClient;
import eu._4fh.abstract_bnet_api.oauth2.BattleNetRegion;
import eu._4fh.abstract_bnet_api.restclient.AbstractBattleNetRequest;
import eu._4fh.abstract_bnet_api.restclient.RequestExecutor;
import eu._4fh.abstract_bnet_api.restclient.data.BattleNetProfileInfo;
import eu._4fh.abstract_bnet_api.restclient.data.BattleNetWowCharacter;
import eu._4fh.abstract_bnet_api.restclient.requests.BattleNetGuildMembersRequest;
import eu._4fh.abstract_bnet_api.restclient.requests.BattleNetProfileInfoRequest;
import eu._4fh.abstract_bnet_api.restclient.requests.BattleNetProfileWowCharactersRequest;
import eu._4fh.wowsync.database.Db;
import eu._4fh.wowsync.database.Transaction;
import eu._4fh.wowsync.database.data.Account;
import eu._4fh.wowsync.database.data.AccountRemoteId;
import eu._4fh.wowsync.database.data.Character;
import eu._4fh.wowsync.database.data.Guild;
import eu._4fh.wowsync.database.data.RemoteSystem;
import eu._4fh.wowsync.database.data.RemoteSystem.RemoteSystemType;
import eu._4fh.wowsync.util.Config;
import eu._4fh.wowsync.util.Singletons;

@DefaultAnnotation(NonNull.class)
public class BattleNetToDbSync {
	private static class RequestExecutionFailure extends Exception {
		private static final long serialVersionUID = 5203535381881507987L;

		public RequestExecutionFailure(final Throwable t) {
			super(t);
		}
	}

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Db db;
	private final Config config;

	public BattleNetToDbSync() {
		db = Singletons.instance(Db.class);
		config = Singletons.instance(Config.class);
	}

	public void updateAndDeleteAccounts() {
		try (final Transaction.TransCnt transaction = db.createTransaction()) {
			updateAccountsFromTokens();
			updateAccountsFromGuildList();
			removeUnusedAccounts();
			removeUnusedCharacters();
			transaction.commit();
		}
	}

	private void updateAccountsFromGuildList() {
		for (final BattleNetRegion region : BattleNetRegion.values()) {
			final List<Guild> guilds = db.guilds.byRegion(region);
			final RequestExecutor executor = new RequestExecutor(config.battleNetClients.getApiClient(region),
					region.locales.iterator().next().toString());
			for (final Guild guild : guilds) {
				log.debug("Request members for {} {} {}", region, guild.server(), guild.name());
				final String apiPath = BattleNetGuildMembersRequest.getApiPath(guild.server(), guild.name());
				try {
					final List<BattleNetWowCharacter> characters = executeRequest(executor, apiPath,
							new BattleNetGuildMembersRequest());
					final Set<Long> characterBnetIds = updateCharacters(region, null, guild, characters);
					final int removedFromGuild = db.characters.removeGuildReferenceWhereBnetIdNotIn(region, guild,
							characterBnetIds);
					log.debug("Removed {} characters from guild", removedFromGuild);
				} catch (RequestExecutionFailure e) {
					log.atError().setCause(e).setMessage("Cant fetch members for {} {} {}").addArgument(region)
							.addArgument(guild.server()).addArgument(guild.name()).log();
				}
			}
		}
	}

	private void updateAccountsFromTokens() {
		for (final BattleNetRegion region : BattleNetRegion.values()) {
			final List<BattleNetClient> clients = config.battleNetClients.getUserClients(region);
			log.debug("Update Accounts Region {}: {} Accounts", region, clients.size());
			for (final BattleNetClient client : clients) {
				if (!client.isAccessTokenValid()) {
					continue;
				}
				try {
					final RequestExecutor executor = new RequestExecutor(client,
							region.locales.iterator().next().toString());
					final BattleNetProfileInfo profileInfo = executeRequest(executor,
							BattleNetProfileInfoRequest.API_PATH, new BattleNetProfileInfoRequest());
					final Account account = insertOrUpdateAccount(profileInfo);
					final List<BattleNetWowCharacter> characters = executeRequest(executor,
							BattleNetProfileWowCharactersRequest.API_PATH, new BattleNetProfileWowCharactersRequest());
					updateCharacters(region, account, null, characters);
				} catch (RequestExecutionFailure e) {
					try {
						log.atError().setCause(e).setMessage("Cant fetch BattleNet-Data region {} for {}")
								.addArgument(region).addArgument(client.getAccessToken().accessToken()).log();
					} catch (ProtocolException e1) {
						log.error("Cant event fetch access token", e1);
					}
				}
			}
		}
	}

	/*package for test*/ Set<Long> updateCharacters(final BattleNetRegion region, final @CheckForNull Account account,
			final @CheckForNull Guild guild, final Collection<BattleNetWowCharacter> charactersList) {
		if ((account != null && guild != null) || (account == null && guild == null)) {
			throw new IllegalStateException("Either account and guild are both set or missing. This is invalid. "
					+ Objects.toString(account) + " ; " + Objects.toString(guild));
		}
		final Map<Long, BattleNetWowCharacter> bnetCharacters = charactersList.stream()
				.collect(Collectors.toMap(c -> c.id, Function.identity()));
		final List<Character> existingCharacters = db.characters.byBnetIds(region, bnetCharacters.keySet());
		final Set<Long> existingCharactersIds = existingCharacters.stream().map(c -> c.bnetId)
				.collect(Collectors.toSet());
		{
			final Set<Long> nonExistentCharacterIds = new HashSet<>(bnetCharacters.keySet());
			nonExistentCharacterIds.removeAll(existingCharactersIds);
			for (final long characterId : nonExistentCharacterIds) {
				final BattleNetWowCharacter bnetCharacter = bnetCharacters.get(characterId);
				final Character character = new Character();
				character.region = region;
				character.server = bnetCharacter.realmSlug;
				character.name = bnetCharacter.name;
				character.bnetId = bnetCharacter.id;
				character.account = account;
				character.guild = guild;
				db.save(character);
			}
		}
		{
			for (final Character character : existingCharacters) {
				final BattleNetWowCharacter bnetCharacter = bnetCharacters.get(character.bnetId);
				boolean needToSave = false;
				if (isCharacterChanged(character, bnetCharacter)) {
					character.server = bnetCharacter.realmSlug;
					character.name = bnetCharacter.name;
					if (bnetCharacter.guildRank != null) {
						character.rank = bnetCharacter.guildRank;
					}
					needToSave = true;
				}
				if (account != null && (character.account == null || character.account.bnetId() != account.bnetId())) {
					character.account = account;
					needToSave = true;
				}
				if (guild != null && (character.guild == null || character.guild.id() != guild.id())) {
					character.guild = guild;
					needToSave = true;
				}
				if (needToSave) {
					db.save(character);
				}
			}
		}
		return bnetCharacters.keySet();
	}

	/*package for test*/ void removeUnusedAccounts() {
		final Date accountsLimitDate = Date
				.from(Instant.now().minus(config.keepNewAccountsWithoutGuildsForDays, ChronoUnit.DAYS));
		final List<Account> accounts = db.accounts.withoutGuildCharacterAddedBefore(accountsLimitDate);
		final int deletedCharactersWithAccounts = db.characters.deleteByAccounts(accounts);
		final int deletedAccounts = db.accounts.delete(accounts);
		final int deletedCharactersWithoutGuildAndAccount = db.characters.deleteWithoutGuildAndAccount();
		log.debug("Removed {} accounts with {} characters and {} characters without guild and account", deletedAccounts,
				deletedCharactersWithAccounts, deletedCharactersWithoutGuildAndAccount);
	}

	/*package for test*/ void removeUnusedCharacters() {
		final Date charactersLimitDate = Date
				.from(Instant.now().minus(config.keepCharactersWithAccountButWithoutGuildForDays, ChronoUnit.DAYS));
		final int deletedCharacters = db.characters.deleteWithoutGuildAndAccountLastUpdateBefore(charactersLimitDate);
		log.debug("Removed {} characters with account but without guild", deletedCharacters);
	}

	/*package for test*/ Account insertOrUpdateAccount(final BattleNetProfileInfo profileInfo) {
		Account account = db.accounts.byBnetId(profileInfo.id);
		if (account == null) {
			account = new Account();
			account.setBnetId(profileInfo.id);
			account.setBnetTag(profileInfo.battleTag);
			final Date now = new Date();
			account.setAdded(now);
			account.setLastUpdate(now);
			db.save(account);
		} else {
			account.setBnetTag(profileInfo.battleTag);
			account.setLastUpdate(new Date());
			db.save(account);
		}
		return account;
	}

	private boolean isCharacterChanged(final Character character, final BattleNetWowCharacter bnetCharacter) {
		assert character.bnetId == bnetCharacter.id;
		if ((bnetCharacter.guildRank == null || character.rank == bnetCharacter.guildRank)
				&& Objects.equals(character.server, bnetCharacter.realmSlug)
				&& Objects.equals(character.name, bnetCharacter.name)) {
			return false;
		}
		return true;
	}

	private <R> R executeRequest(final RequestExecutor executor, final String path,
			final AbstractBattleNetRequest<R> request) throws RequestExecutionFailure {
		log.debug("Request {} ({})", path, request);
		@CheckForNull
		Throwable exception = null;
		int numTries = 0;
		do {
			numTries++;
			try {
				return executor.executeRequest(path, request);
			} catch (Exception e) {
				log.debug("Cant execute " + path + " " + request.toString() + ". Retry "
						+ Boolean.toString(numTries < config.bnetNumRequestRetries), e);
				exception = e;
			}
			try {
				TimeUnit.SECONDS.sleep(1);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				// Otherwise ignore
			}
		} while (numTries < config.bnetNumRequestRetries);
		log.atError().setCause(exception).setMessage("Cant execute request {} {}").addArgument(path)
				.addArgument(request).log();
		throw new RequestExecutionFailure(exception);
	}

	public @CheckForNull URI authFinished(final RemoteSystem remoteSystem, final long remoteUserId,
			final BattleNetClient client) {
		if (!client.isAccessTokenValid()) {
			log.error("Just requested accessToken is invalid. RemoteSystem {} for RemoteUserId {}", remoteSystem.id,
					remoteUserId);
			throw new IllegalStateException("Just requested accessToken is invalid");
		}
		final Account account;
		try (Transaction.TransCnt trans = db.createTransaction()) {
			if (!remoteSystem.guild.region().equals(client.getRegion())) {
				log.error(
						"Invalid region {} for guild {} in region {}. Therefor cant finish auth for user {} to remote system {}.",
						client.getRegion(), remoteSystem.guild.id(), remoteSystem.guild.region(), remoteUserId,
						remoteSystem.id);
				throw new IllegalStateException("Got access for region " + client.getRegion().getRegionName()
						+ " but guild is in region " + remoteSystem.guild.region().getRegionName());
			}
			final BattleNetRegion region = remoteSystem.guild.region();
			final RequestExecutor executor = new RequestExecutor(client, region.locales.iterator().next().toString());
			final BattleNetProfileInfo profileInfo = executeRequest(executor, BattleNetProfileInfoRequest.API_PATH,
					new BattleNetProfileInfoRequest());
			account = insertOrUpdateAccount(profileInfo);
			insertOrUpdateAccountRemoteId(account, remoteSystem, remoteUserId);

			final List<BattleNetWowCharacter> characters = executeRequest(executor,
					BattleNetProfileWowCharactersRequest.API_PATH, new BattleNetProfileWowCharactersRequest());
			updateCharacters(region, account, null, characters);
			trans.commit();
		} catch (RequestExecutionFailure e) {
			throw new RuntimeException(e);
		}
		return RemoteSystemType.Forum.equals(remoteSystem.type) ? URI.create(remoteSystem.nameOrLink) : null;
	}

	/*package for test*/ void insertOrUpdateAccountRemoteId(final Account account, final RemoteSystem remoteSystem,
			final long remoteUserId) {
		try (Transaction.TransCnt trans = db.createTransaction()) {
			AccountRemoteId accountRemoteId = db.accountRemoteIds.byId(account, remoteSystem);
			if (accountRemoteId == null) {
				accountRemoteId = new AccountRemoteId();
				accountRemoteId.account = account;
				accountRemoteId.remoteSystem = remoteSystem;
				accountRemoteId.remoteId = remoteUserId;
				db.save(accountRemoteId);
			} else if (accountRemoteId.remoteId != remoteUserId) {
				accountRemoteId.remoteId = remoteUserId;
				db.save(accountRemoteId);
			}
		}
	}
}
