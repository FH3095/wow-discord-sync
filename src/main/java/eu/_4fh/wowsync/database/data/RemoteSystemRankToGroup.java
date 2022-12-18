package eu._4fh.wowsync.database.data;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "remote_system_rank_to_group", indexes = @Index(name = "idx_remote_system_rank_to_group_remote_system_id", columnList = "remote_system_id"))
public class RemoteSystemRankToGroup {
	@Id
	@ManyToOne(optional = false, fetch = FetchType.EAGER)
	@JoinColumn(name = "remote_system_id", nullable = false, foreignKey = @ForeignKey(name = "fk_remote_system_rank_to_group_remote_system_id"))
	private RemoteSystem remoteSystem;

	@Id
	@Column(name = "guild_rank_from", nullable = false)
	private byte guildRankFrom;

	@Id
	@Column(name = "guild_rank_to", nullable = false)
	private byte guildRankTo;

	@Column(name = "group_name", nullable = false, length = 64)
	private String groupName;

	@Override
	public String toString() {
		return "RemoteSystemRankToGroup [remoteSystem=" + remoteSystem + ", guildRankFrom=" + guildRankFrom
				+ ", guildRankTo=" + guildRankTo + ", groupName=" + groupName + "]";
	}

	@Override
	public int hashCode() {
		return Objects.hash(guildRankFrom, guildRankTo, remoteSystem);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof RemoteSystemRankToGroup)) {
			return false;
		}
		RemoteSystemRankToGroup other = (RemoteSystemRankToGroup) obj;
		return guildRankFrom == other.guildRankFrom && guildRankTo == other.guildRankTo
				&& Objects.equals(remoteSystem, other.remoteSystem);
	}

	public RemoteSystem remoteSystem() {
		return remoteSystem;
	}

	public void setRemoteSystem(RemoteSystem remoteSystem) {
		this.remoteSystem = remoteSystem;
	}

	public byte guildRankFrom() {
		return guildRankFrom;
	}

	public void setGuildRankFrom(byte guildRankFrom) {
		this.guildRankFrom = guildRankFrom;
	}

	public byte getGuildRankTo() {
		return guildRankTo;
	}

	public void setGuildRankTo(byte guildRankTo) {
		this.guildRankTo = guildRankTo;
	}

	public String groupName() {
		return groupName;
	}

	public void setGroupName(String groupName) {
		this.groupName = groupName;
	}
}
