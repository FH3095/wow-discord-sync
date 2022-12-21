package eu._4fh.wowsync.database;

import java.security.Key;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.abstract_bnet_api.oauth2.BattleNetRegion;
import eu._4fh.wowsync.database.Transaction.TransCnt;
import eu._4fh.wowsync.database.data.Account;
import eu._4fh.wowsync.database.data.AccountRemoteId;
import eu._4fh.wowsync.database.data.Character;
import eu._4fh.wowsync.database.data.DiscordOnlineUser;
import eu._4fh.wowsync.database.data.Guild;
import eu._4fh.wowsync.database.data.RemoteSystem;
import eu._4fh.wowsync.database.data.RemoteSystemRankToGroup;
import eu._4fh.wowsync.util.Config;
import eu._4fh.wowsync.util.MacCalculator;
import eu._4fh.wowsync.util.Singletons;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

@DefaultAnnotation(NonNull.class)
public class Db {
	public TransCnt createTransaction() {
		return Transaction.create(sessionFactory);
	}

	private final EntityManagerFactory sessionFactory;
	public final DiscordOnlineUserQueries discordOnlineUsers = new DiscordOnlineUserQueries();
	public final AccountQueries accounts = new AccountQueries();
	public final CharacterQueries characters = new CharacterQueries();
	public final AccountRemoteIdQueries accountRemoteIds = new AccountRemoteIdQueries();
	public final GuildQueries guilds = new GuildQueries();
	public final RemoteSystemQueries remoteSystems = new RemoteSystemQueries();
	public final RemoteSystemRankToGroupQueries remoteSystemRankToGroup = new RemoteSystemRankToGroupQueries();

	private Db() {
		sessionFactory = Singletons.instance(Config.class).hibernateSessionFactory;
		try (final TransCnt trans = createTransaction()) {
			for (final NamedQueries.NamedQuery<?> query : NamedQueries.getAllQueries()) {
				final TypedQuery<?> typedQuery = trans.em.createQuery(query.jql, query.typeClass);
				sessionFactory.addNamedQuery(query.name, typedQuery);
			}
			for (final NamedQueries.NamedUpdate update : NamedQueries.getAllUpdates()) {
				final Query updateQuery = trans.em.createQuery(update.jql);
				sessionFactory.addNamedQuery(update.name, updateQuery);
			}
		}
	}

	private <T> TypedQuery<T> createQuery(final TransCnt trans, final NamedQueries.NamedQuery<T> query) {
		return trans.em.createNamedQuery(query.name, query.typeClass);
	}

	private Query createUpdate(final TransCnt trans, final NamedQueries.NamedUpdate updateQuery) {
		return trans.em.createNamedQuery(updateQuery.name);
	}

	public void save(final Object... objects) {
		try (TransCnt trans = createTransaction()) {
			for (final Object object : objects) {
				trans.em.persist(object);
			}
		}
	}

	public void refresh(final Object... objects) {
		try (TransCnt trans = createTransaction()) {
			for (final Object object : objects) {
				trans.em.refresh(object);
			}
		}
	}

	public <T> T find(final Class<T> clazz, final Object key) {
		try (TransCnt trans = createTransaction()) {
			return trans.em.find(clazz, key);
		}
	}

	public <T> List<T> forTestQuery(final Class<T> clazz, final String query) {
		try (TransCnt trans = createTransaction()) {
			return trans.em.createQuery(query, clazz).getResultList();
		}
	}

	public final class DiscordOnlineUserQueries {
		private DiscordOnlineUserQueries() {
		}

		public void updateLastOnline(final long guildId, final long memberId, final String memberName) {
			final LocalDate now = LocalDate.now(Clock.systemUTC());
			try (final TransCnt trans = createTransaction()) {
				final DiscordOnlineUser newUser = new DiscordOnlineUser(guildId, memberId, memberName, now);
				final @CheckForNull DiscordOnlineUser existingUser = trans.em.find(DiscordOnlineUser.class, newUser);
				if (existingUser == null) {
					trans.em.persist(newUser);
				} else {
					existingUser.lastOnline = now;
					trans.em.persist(existingUser);
				}
				trans.commit();
			}
		}

		public List<DiscordOnlineUser> getLastOnlineBefore(final long guildIdLong, final LocalDate date) {
			try (final TransCnt trans = createTransaction()) {
				return createQuery(trans, NamedQueries.discordOnlineUsersByGuildAndLastOnlineLessThan)
						.setParameter("guildId", guildIdLong).setParameter("date", date).getResultList();
			}
		}
	}

	public final class AccountQueries {
		private AccountQueries() {
		}

		public @CheckForNull Account byBnetId(long bnetId) {
			try (TransCnt trans = createTransaction()) {
				try {
					return createQuery(trans, NamedQueries.accountByBnetId).setParameter("bnetId", bnetId)
							.getSingleResult();
				} catch (NoResultException e) {
					return null;
				}
			}
		}

		public List<Account> withoutGuildCharacterAddedBefore(final LocalDate accountsLimitDate) {
			try (TransCnt trans = createTransaction()) {
				return createQuery(trans, NamedQueries.accountWithoutGuildCharacterAddedBefore)
						.setParameter("dateAdded", accountsLimitDate).getResultList();
			}
		}

		public int delete(final List<Account> accounts) {
			try (TransCnt trans = createTransaction()) {
				createUpdate(trans, NamedQueries.accountRemoteIdDeleteByAccounts).setParameter("accounts", accounts)
						.executeUpdate();
				accounts.forEach(trans.em::remove);
			}
			return accounts.size();
		}
	}

	public final class CharacterQueries {
		private CharacterQueries() {
		}

		public List<Character> byBnetIds(final BattleNetRegion region, final Collection<Long> bnetIds) {
			try (TransCnt trans = createTransaction()) {
				return createQuery(trans, NamedQueries.charactersByBnetIds).setParameter("region", region)
						.setParameter("bnetIds", bnetIds).getResultList();
			}
		}

		public int removeGuildReferenceWhereBnetIdNotIn(final BattleNetRegion region, final Guild guild,
				final Collection<Long> bnetIds) {
			try (TransCnt trans = createTransaction()) {
				return createUpdate(trans, NamedQueries.charactersRemoveGuildReferenceWhereBnetIdNotIn)
						.setParameter("region", region).setParameter("guild", guild).setParameter("bnetIds", bnetIds)
						.executeUpdate();
			}
		}

		public int deleteByAccounts(final List<Account> accounts) {
			try (TransCnt trans = createTransaction()) {
				return createUpdate(trans, NamedQueries.charactersDeleteByAccounts).setParameter("accounts", accounts)
						.executeUpdate();
			}
		}

		public int deleteWithoutGuildAndAccount() {
			try (TransCnt trans = createTransaction()) {
				return createUpdate(trans, NamedQueries.charactersDeleteWhereAccountAndGuildNull).executeUpdate();
			}
		}

		public List<Character> byAccountAndGuild(final Account account, final Guild guild) {
			try (TransCnt trans = createTransaction()) {
				return createQuery(trans, NamedQueries.charactersByAccountAndGuild).setParameter("account", account)
						.setParameter("guild", guild).getResultList();
			}
		}

		public List<Character> byGuildAndRemoteSystemAndRemoteId(final Guild guild, final RemoteSystem remoteSystem,
				final long remoteId) {
			try (TransCnt trans = createTransaction()) {
				return createQuery(trans, NamedQueries.charactersByGuildAndRemoteSystemAndRemoteId)
						.setParameter("guild", guild).setParameter("remoteSystem", remoteSystem)
						.setParameter("remoteId", remoteId).getResultList();
			}
		}

		public int deleteWithoutGuildAndAccountLastUpdateBefore(final LocalDate lastUpdate) {
			try (TransCnt trans = createTransaction()) {
				return createUpdate(trans, NamedQueries.charactersDeleteWithoutGuildAndAccountLastUpdateBefore)
						.setParameter("lastUpdate", lastUpdate).executeUpdate();
			}
		}
	}

	public final class AccountRemoteIdQueries {
		private AccountRemoteIdQueries() {
		}

		public @CheckForNull AccountRemoteId byId(final Account account, final RemoteSystem remoteSystem) {
			try (TransCnt trans = createTransaction()) {
				final AccountRemoteId primaryKey = new AccountRemoteId();
				primaryKey.account = account;
				primaryKey.remoteSystem = remoteSystem;
				return trans.em.find(AccountRemoteId.class, primaryKey);
			}
		}

		public Map<Long, List<Character>> remoteIdWithCharactersByGuildAndRemoteSystem(Guild guild,
				RemoteSystem remoteSystem) {
			try (TransCnt trans = createTransaction()) {
				final Map<Long, List<Character>> result = new HashMap<>();
				createQuery(trans, NamedQueries.accountRemoteIdWithCharactersByGuildAndRemoteSystem)
						.setParameter("guild", guild).setParameter("remoteSystem", remoteSystem).getResultStream()
						.forEach(data -> result.computeIfAbsent(((Number) data[0]).longValue(), id -> new ArrayList<>())
								.add((Character) data[1]));
				return result;
			}
		}
	}

	public final class GuildQueries {
		private GuildQueries() {
		}

		public Guild byRemoteSystem(final long remoteSystemId) {
			try (final TransCnt trans = createTransaction()) {
				return createQuery(trans, NamedQueries.guildByRemoteSystemId).setParameter("id", remoteSystemId)
						.getSingleResult();
			}
		}

		public List<Guild> byRegion(final BattleNetRegion region) {
			try (TransCnt trans = createTransaction()) {
				return createQuery(trans, NamedQueries.guildsByRegion).setParameter("region", region).getResultList();
			}
		}
	}

	public final class RemoteSystemQueries {
		private RemoteSystemQueries() {
		}

		public RemoteSystem byId(final long id) {
			try (TransCnt trans = createTransaction()) {
				return Objects.requireNonNull(trans.em.find(RemoteSystem.class, id));
			}
		}

		public List<RemoteSystem> all() {
			try (TransCnt trans = createTransaction()) {
				return createQuery(trans, NamedQueries.remoteSystemAll).getResultList();
			}
		}

		public List<RemoteSystem> byGuild(final long guildId) {
			try (TransCnt trans = createTransaction()) {
				return createQuery(trans, NamedQueries.remoteSystemByGuild).setParameter("guildId", guildId)
						.getResultList();
			}
		}

		public Key hmacKeyById(final long id) {
			try (TransCnt trans = createTransaction()) {
				final String hmacKeyStr = createQuery(trans, NamedQueries.remoteSystemOnlyHmacKey)
						.setParameter("id", id).getSingleResult();
				return MacCalculator.fromString(hmacKeyStr);
			}
		}

		public RemoteSystem byTypeAndRemoteId(final RemoteSystem.RemoteSystemType type, final long systemId) {
			try (TransCnt trans = createTransaction()) {
				return createQuery(trans, NamedQueries.remoteSystemByTypeAndRemoteId).setParameter("type", type)
						.setParameter("systemId", systemId).getSingleResult();
			}
		}
	}

	public final class RemoteSystemRankToGroupQueries {
		private RemoteSystemRankToGroupQueries() {
		}

		public List<RemoteSystemRankToGroup> byRemoteSystem(final RemoteSystem remoteSystem) {
			try (TransCnt trans = createTransaction()) {
				return createQuery(trans, NamedQueries.remoteSystemRankToGroupByRemoteSystem)
						.setParameter("remoteSystem", remoteSystem).getResultList();
			}
		}
	}
}
