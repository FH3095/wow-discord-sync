package eu._4fh.wowsync.database.data;

import java.util.Date;

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
}
