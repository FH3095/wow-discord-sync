package eu._4fh.wowsync.database;

import java.security.Key;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import jakarta.persistence.TemporalType;
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
	public final GroupedQueries groupedQueries = new GroupedQueries();

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

	public final class DiscordOnlineUserQueries {
		private DiscordOnlineUserQueries() {
		}

		public void updateLastOnline(final long guildId, final Set<Long> memberIds) {
			final Date now = new Date();
			try (final TransCnt trans = createTransaction()) {
				final Set<Long> alreadyExistentMembers = createQuery(trans,
						NamedQueries.discordOnlineUsersByGuildAndMemberIdsOnlyMemberId).setParameter("guildId", guildId)
								.setParameter("memberIds", memberIds).getResultStream().collect(Collectors.toSet());
				final Set<Long> notExistentMembers = new HashSet<>(memberIds);
				notExistentMembers.removeAll(alreadyExistentMembers);
				for (final long memberId : notExistentMembers) {
					trans.em.persist(new DiscordOnlineUser(guildId, memberId, now));
				}
				createUpdate(trans, NamedQueries.discordOnlineUsersUpdateLastOnline).setParameter("guildId", guildId)
						.setParameter("memberIds", alreadyExistentMembers)
						.setParameter("now", now, TemporalType.TIMESTAMP).executeUpdate();
				trans.commit();
			}
		}

		public List<DiscordOnlineUser> getLastOnlineBefore(final long guildIdLong, final Date date) {
			try (final TransCnt trans = createTransaction()) {
				return createQuery(trans, NamedQueries.discordOnlineUsersByGuildAndLastOnlineLessThan)
						.setParameter("guildId", guildIdLong).setParameter("date", date, TemporalType.TIMESTAMP)
						.getResultList();
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

		public List<Account> withoutGuildCharacterAddedBefore(final Date accountsLimitDate) {
			try (TransCnt trans = createTransaction()) {
				return createQuery(trans, NamedQueries.accountWithoutGuildCharacterAddedBefore)
						.setParameter("dateAdded", accountsLimitDate, TemporalType.TIMESTAMP).getResultList();
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
				return createQuery(trans, NamedQueries.charactersbyRemoteSystemAndRemoteId).setParameter("guild", guild)
						.setParameter("remoteSystem", remoteSystem).setParameter("remoteId", remoteId).getResultList();
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

	public final class GroupedQueries {
		private GroupedQueries() {
		}

		public Map<Long, Byte> remoteIdAndMinGuildRankByGuild(final RemoteSystem remoteSystem) {
			final String jpql = "SELECT ari.remoteId, MIN(c.rank) FROM AccountRemoteId ari INNER JOIN Character c ON ari.account = c.account WHERE ari.remoteSystem = :remoteSystem AND c.guild = :guild GROUP BY ari.remoteId";
			try (TransCnt trans = createTransaction()) {
				final TypedQuery<Object[]> query = trans.em.createQuery(jpql, Object[].class);
				final Stream<Object[]> stream = query.setParameter("remoteSystem", remoteSystem)
						.setParameter("guild", remoteSystem.guild).getResultStream();
				return stream.collect(Collectors.toUnmodifiableMap(result -> ((Number) result[0]).longValue(),
						result -> ((Number) result[1]).byteValue()));
			}
		}
	}
}
