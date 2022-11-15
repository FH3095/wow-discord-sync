package eu._4fh.wowsync.database;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.wowsync.database.data.Account;
import eu._4fh.wowsync.database.data.Character;
import eu._4fh.wowsync.database.data.DiscordOnlineUser;
import eu._4fh.wowsync.database.data.Guild;
import eu._4fh.wowsync.database.data.RemoteSystem;

@DefaultAnnotation(NonNull.class)
/*package*/ class NamedQueries {
	private NamedQueries() {
	}

	/*package*/ static class NamedQuery<T> {
		public final Class<T> typeClass;
		public final String name;
		public final String jql;

		private NamedQuery(final Class<T> typeClass, final String name, final String jql) {
			this.name = typeClass.getSimpleName() + "." + name;
			this.typeClass = typeClass;
			this.jql = jql;
			allQueries.add(this);
		}
	}

	/*package*/ static class NamedUpdate {
		public final String name;
		public final String jql;

		private NamedUpdate(final String name, final String jql) {
			this.name = name;
			this.jql = jql;
			allUpdates.add(this);
		}
	}

	private static final List<NamedQuery<?>> allQueries = new LinkedList<>();
	private static final List<NamedUpdate> allUpdates = new LinkedList<>();

	public static List<NamedQuery<?>> getAllQueries() {
		return Collections.unmodifiableList(allQueries);
	}

	public static List<NamedUpdate> getAllUpdates() {
		return Collections.unmodifiableList(allUpdates);
	}

	public static final NamedQuery<Long> discordOnlineUsersByGuildAndMemberIdsOnlyMemberId = new NamedQuery<>(
			Long.class, "ByGuildAndMemberIdsOnlyMemberId",
			"SELECT dou.memberId FROM DiscordOnlineUser dou WHERE dou.guildId = :guildId AND dou.memberId IN :memberIds");

	public static final NamedQuery<DiscordOnlineUser> discordOnlineUsersByGuildAndLastOnlineLessThan = new NamedQuery<>(
			DiscordOnlineUser.class, "ByGuildAndLastOnlineLessThan",
			"SELECT dou FROM DiscordOnlineUser dou WHERE dou.guildId = :guildId AND dou.lastOnline <= :date");

	public static final NamedUpdate discordOnlineUsersUpdateLastOnline = new NamedUpdate(
			"DiscordOnlineUsersUpdateLastOnline",
			"UPDATE DiscordOnlineUser dou SET dou.lastOnline = :now WHERE dou.guildId = :guildId AND dou.memberId in :memberIds");

	public static final NamedQuery<RemoteSystem> remoteSystemAll = new NamedQuery<>(RemoteSystem.class, "All",
			"SELECT rs FROM RemoteSystem rs");

	public static final NamedQuery<String> remoteSystemOnlyHmacKey = new NamedQuery<>(String.class, "OnlyHmacKey",
			"SELECT hmacKey FROM RemoteSystem rs WHERE id = :id");

	public static final NamedQuery<RemoteSystem> remoteSystemByGuild = new NamedQuery<>(RemoteSystem.class, "ByGuild",
			"SELECT rs FROM RemoteSystem rs WHERE guild = :guildId");

	public static final NamedQuery<RemoteSystem> remoteSystemByTypeAndRemoteId = new NamedQuery<>(RemoteSystem.class,
			"ByTypeAndId", "SELECT rs FROM RemoteSystem rs WHERE type = :type AND systemId = :systemId");

	public static final NamedQuery<Guild> guildByRemoteSystemId = new NamedQuery<>(Guild.class, "ByRemoteSystemId",
			"SELECT g FROM Guild g WHERE g = (SELECT rs.guild FROM RemoteSystem rs WHERE rs.id = :id)");

	public static final NamedQuery<Guild> guildsByRegion = new NamedQuery<>(Guild.class, "ByRegion",
			"SELECT g FROM Guild g WHERE region = :region");

	public static final NamedQuery<Account> accountByBnetId = new NamedQuery<>(Account.class, "ByBnetId",
			"SELECT a FROM Account a WHERE bnetId = :bnetId");

	public static final NamedQuery<Account> accountWithoutGuildCharacterAddedBefore = new NamedQuery<>(Account.class,
			"WithoutGuildCharacterAddedBefore",
			"SELECT a FROM Account a WHERE a.added < :dateAdded AND a NOT IN (SELECT c.account FROM Character c WHERE c.guild IS NOT NULL AND c.account IS NOT NULL)");

	public static final NamedUpdate accountRemoteIdDeleteByAccounts = new NamedUpdate("accountRemoteIdDeleteByAccounts",
			"DELETE FROM AccountRemoteId ari WHERE ari.account IN :accounts");

	public static final NamedQuery<Character> charactersByBnetIds = new NamedQuery<>(Character.class, "ByBnetIds",
			"SELECT c FROM Character c WHERE region = :region AND bnetId IN :bnetIds");

	public static final NamedUpdate charactersRemoveGuildReferenceWhereBnetIdNotIn = new NamedUpdate(
			"charactersRemoveGuildReferenceWhereBnetIdNotIn",
			"UPDATE Character c SET c.guild = NULL WHERE c.region = :region AND c.guild = :guild AND c.bnetId NOT IN :bnetIds");

	public static final NamedUpdate charactersDeleteWhereAccountAndGuildNull = new NamedUpdate(
			"charactersDeleteWhereAccountAndGuildNull",
			"DELETE FROM Character c WHERE c.guild = NULL AND c.account = NULL");

	public static final NamedUpdate charactersDeleteByAccounts = new NamedUpdate("charactersDeleteByAccounts",
			"DELETE FROM Character c WHERE c.account IN :accounts");

	public static final NamedQuery<Character> charactersByAccountAndGuild = new NamedQuery<>(Character.class,
			"byAccountAndGuild", "SELECT c FROM Character c WHERE c.account = :account AND c.guild = :guild");
}
