package eu._4fh.wowsync.database.data;

import java.util.Date;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = "discord_online_users", indexes = @Index(name = "idx_discord_online_users_guild_id", columnList = "guild_id"))
public class DiscordOnlineUser {
	@Id
	@Column(name = "guild_id", nullable = false)
	public long guildId;

	@Id
	@Column(name = "member_id", nullable = false)
	public long memberId;

	@Column(name = "last_online", nullable = false)
	@Temporal(TemporalType.TIMESTAMP)
	public Date lastOnline;

	@SuppressWarnings("unused")
	private DiscordOnlineUser() {
		// Used by Hibernate
	}

	public DiscordOnlineUser(final long guildId, final long memberId, final Date lastOnline) {
		this.guildId = guildId;
		this.memberId = memberId;
		this.lastOnline = lastOnline;
	}

	@Override
	public String toString() {
		return "DiscordOnlineUser [guildId=" + guildId + ", memberId=" + memberId + ", lastOnline=" + lastOnline + "]";
	}

	@Override
	public int hashCode() {
		return Objects.hash(guildId, memberId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof DiscordOnlineUser)) {
			return false;
		}
		DiscordOnlineUser other = (DiscordOnlineUser) obj;
		return guildId == other.guildId && memberId == other.memberId;
	}
}
