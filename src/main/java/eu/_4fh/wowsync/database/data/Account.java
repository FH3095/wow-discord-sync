package eu._4fh.wowsync.database.data;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = "accounts", indexes = { @Index(name = "idx_accounts_bnet_id", unique = true, columnList = "bnet_id"),
		@Index(name = "idx_accounts_last_update", columnList = "last_update") })
public class Account {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, unique = true, updatable = false, insertable = false)
	private long id;

	@Column(name = "bnet_id", nullable = false)
	private long bnetId;

	@Column(name = "bnet_tag", nullable = false, length = 32)
	private String bnetTag;

	@Column(name = "added", nullable = false, updatable = false)
	@Temporal(TemporalType.TIMESTAMP)
	private Date added;

	@Column(name = "last_update", nullable = false)
	@Temporal(TemporalType.TIMESTAMP)
	private Date lastUpdate;

	@Override
	public String toString() {
		return "Account [id=" + id + ", bnetId=" + bnetId + ", bnetTag=" + bnetTag + ", added=" + added
				+ ", lastUpdate=" + lastUpdate + "]";
	}

	public long id() {
		return id;
	}

	public long bnetId() {
		return bnetId;
	}

	public void setBnetId(long bnetId) {
		this.bnetId = bnetId;
	}

	public String bnetTag() {
		return bnetTag;
	}

	public void setBnetTag(String bnetTag) {
		this.bnetTag = bnetTag;
	}

	public Date added() {
		return added;
	}

	public void setAdded(Date added) {
		this.added = added;
	}

	public Date lastUpdate() {
		return lastUpdate;
	}

	public void setLastUpdate(Date lastUpdate) {
		this.lastUpdate = lastUpdate;
	}
}
