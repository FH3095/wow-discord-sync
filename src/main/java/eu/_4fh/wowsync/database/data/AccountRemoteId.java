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
@Table(name = "account_remote_ids", indexes = @Index(name = "idx_account_remote_ids_remote_system_id_remote_id", unique = true, columnList = "remote_system_id, remote_id"))
public class AccountRemoteId {
	@Id
	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "account_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_account_remote_ids_account_id"))
	public Account account;

	@Id
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "remote_system_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_account_remote_ids_remote_system_id"))
	public RemoteSystem remoteSystem;

	@Column(name = "remote_id", nullable = false)
	public long remoteId;

	@Override
	public String toString() {
		return "AccountRemoteId [account=" + account + ", remoteSystem=" + remoteSystem + ", remoteId=" + remoteId
				+ "]";
	}

	@Override
	public int hashCode() {
		return Objects.hash(account, remoteSystem);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof AccountRemoteId)) {
			return false;
		}
		AccountRemoteId other = (AccountRemoteId) obj;
		return Objects.equals(account, other.account) && Objects.equals(remoteSystem, other.remoteSystem);
	}
}
