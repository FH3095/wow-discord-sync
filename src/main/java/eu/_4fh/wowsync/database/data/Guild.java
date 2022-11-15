package eu._4fh.wowsync.database.data;

import eu._4fh.abstract_bnet_api.oauth2.BattleNetRegion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "guilds", indexes = @Index(name = "idx_guilds_region_server_name", unique = true, columnList = "region, server, name"))
public class Guild {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(unique = true, nullable = false, updatable = false, insertable = false)
	private long id;

	@Column(name = "region", length = 2, nullable = false)
	@Enumerated(EnumType.STRING)
	private BattleNetRegion region;

	@Column(name = "server", length = 32, nullable = false)
	private String server;

	@Column(name = "name", length = 32, nullable = false)
	private String name;

	@Override
	public String toString() {
		return "Guild [id=" + id + ", region=" + region + ", server=" + server + ", name=" + name + "]";
	}

	public long id() {
		return id;
	}

	public BattleNetRegion region() {
		return region;
	}

	public void setRegion(BattleNetRegion region) {
		this.region = region;
	}

	public String server() {
		return server;
	}

	public void setServer(String server) {
		this.server = server;
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
