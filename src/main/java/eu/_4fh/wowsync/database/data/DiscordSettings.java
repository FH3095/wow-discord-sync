package eu._4fh.wowsync.database.data;

import java.util.Objects;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "discord_settings")
public class DiscordSettings {
	@Id
	@OneToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "remote_system", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_discord_settings_remote_system"))
	private RemoteSystem remoteSystem;

	@Column(name = "reaction_message_id", nullable = true)
	private @CheckForNull Long reactionMessageId;

	@Column(name = "delete_user_after_inactive_days", nullable = false)
	private int deleteUserAfterInactiveDays;

	public RemoteSystem remoteSystem() {
		return remoteSystem;
	}

	public void setRemoteSystem(RemoteSystem remoteSystem) {
		this.remoteSystem = remoteSystem;
	}

	public @CheckForNull Long reactionMessageId() {
		return reactionMessageId;
	}

	public void setReactionMessageId(final Long reactionMessageId) {
		this.reactionMessageId = reactionMessageId;
	}

	public int getDeleteUserAfterInactiveDays() {
		return deleteUserAfterInactiveDays;
	}

	public void setDeleteUserAfterInactiveDays(int delteUserAfterInactiveDays) {
		this.deleteUserAfterInactiveDays = delteUserAfterInactiveDays;
	}

	@Override
	public String toString() {
		return "DiscordSettings [remoteSystem=" + remoteSystem + ", reactionMessageId=" + reactionMessageId + "]";
	}

	@Override
	public int hashCode() {
		return Objects.hash(remoteSystem);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof DiscordSettings)) {
			return false;
		}
		DiscordSettings other = (DiscordSettings) obj;
		return Objects.equals(remoteSystem, other.remoteSystem);
	}
}
