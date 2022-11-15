package eu._4fh.wowsync.database.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
	@ManyToOne(optional = false)
	@JoinColumn(name = "remote_system_id", nullable = false, foreignKey = @ForeignKey(name = "fk_remote_system_rank_to_group_remote_system_id"))
	public RemoteSystem remoteSystem;

	@Id
	@Column(name = "guild_rank", nullable = false)
	public byte guildRank;

	@Column(name = "group_name", nullable = false, length = 64)
	public String groupName;
}
