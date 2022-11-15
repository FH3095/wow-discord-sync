package eu._4fh.wowsync.database.data;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@DefaultAnnotation(NonNull.class)
@Entity
@Table(name = "remote_systems", indexes = {
		@Index(name = "idx_remote_systems_type_system_id_name_link", unique = true, columnList = "type, system_id, name_link"),
		@Index(name = "idx_remote_systems_guild_id", columnList = "guild_id") })
public class RemoteSystem {
	public enum RemoteSystemType {
		Discord,
		Teamspeak,
		Forum,
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, updatable = false, insertable = false)
	public long id;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "guild_id", nullable = false, foreignKey = @ForeignKey(name = "fk_remote_systems_guild_id"))
	public Guild guild;

	@Column(name = "type", length = 32, nullable = false)
	@Enumerated(EnumType.STRING)
	public RemoteSystemType type;

	@Column(name = "system_id", nullable = false)
	public long systemId;

	@Column(name = "name_link", length = 255, nullable = false)
	public String nameOrLink;

	@Column(name = "member_group", length = 64, nullable = false)
	public String memberGroup;

	@Column(name = "former_member_group", length = 64, nullable = true)
	public @CheckForNull String formerMemberGroup;

	@SuppressFBWarnings(value = "URF_UNREAD_FIELD", justification = "Only here to be registered by hibernate")
	@Column(name = "hmac_key", length = 88, nullable = false, updatable = false)
	private String hmacKey;

	public void forTestSetKey(final String key) {
		this.hmacKey = key;
	}
}
