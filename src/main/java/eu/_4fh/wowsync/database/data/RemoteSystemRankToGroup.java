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
	@Column(name = "guild_rank", nullable = false)
	private byte guildRank;

	@Column(name = "group_name", nullable = false, length = 64)
	private String groupName;

	@Override
	public String toString() {
		return "RemoteSystemRankToGroup [remoteSystem=" + remoteSystem + ", guildRank=" + guildRank + ", groupName="
				+ groupName + "]";
	}

	@Override
	public int hashCode() {
		return Objects.hash(guildRank, remoteSystem);
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
		return guildRank == other.guildRank && Objects.equals(remoteSystem, other.remoteSystem);
	}

	public RemoteSystem remoteSystem() {
		return remoteSystem;
	}

	public void setRemoteSystem(RemoteSystem remoteSystem) {
		this.remoteSystem = remoteSystem;
	}

	public byte guildRank() {
		return guildRank;
	}

	public void setGuildRank(byte guildRank) {
		this.guildRank = guildRank;
	}

	public String groupName() {
		return groupName;
	}

	public void setGroupName(String groupName) {
		this.groupName = groupName;
	}
}
