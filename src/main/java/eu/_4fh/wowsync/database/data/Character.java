package eu._4fh.wowsync.database.data;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import eu._4fh.abstract_bnet_api.oauth2.BattleNetRegion;
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

@Entity
@Table(name = "characters", indexes = {
		@Index(name = "idx_characters_bnet_id_region", unique = true, columnList = "bnet_id, region"),
		@Index(name = "idx_characters_account_id", columnList = "account_id"),
		@Index(name = "idx_characters_guild_id", columnList = "guild_id") })
public class Character {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, updatable = false, insertable = false)
	public long id;

	@Column(name = "bnet_id", nullable = false)
	public long bnetId;

	@ManyToOne(fetch = FetchType.EAGER, optional = true)
	@JoinColumn(name = "account_id", nullable = true, foreignKey = @ForeignKey(name = "fk_characters_account_id"))
	public @CheckForNull Account account;

	@ManyToOne(fetch = FetchType.LAZY, optional = true)
	@JoinColumn(name = "guild_id", nullable = true, foreignKey = @ForeignKey(name = "fk_characters_guild_id"))
	public @CheckForNull Guild guild;

	@Column(name = "region", nullable = false, length = 2)
	@Enumerated(EnumType.STRING)
	public BattleNetRegion region;

	@Column(name = "server", nullable = false, length = 32)
	public String server;

	@Column(name = "name", nullable = false, length = 32)
	public String name;

	@Column(name = "rank", nullable = false)
	public byte rank;
}
