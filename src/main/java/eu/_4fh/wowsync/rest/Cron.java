package eu._4fh.wowsync.rest;

import java.util.List;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.wowsync.database.Db;
import eu._4fh.wowsync.database.data.RemoteSystem;
import eu._4fh.wowsync.sync.BattleNetToDbSync;
import eu._4fh.wowsync.sync.DbToModuleSync;
import eu._4fh.wowsync.util.Singletons;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@DefaultAnnotation(NonNull.class)
@Path("cron")
public class Cron {

	@GET
	@Path("run")
	@Produces(MediaType.TEXT_PLAIN)
	public String run() {
		new BattleNetToDbSync().updateAndDeleteAccounts();
		final List<RemoteSystem> remoteSystems = Singletons.instance(Db.class).remoteSystems.all();
		for (final RemoteSystem remoteSystem : remoteSystems) {
			final DbToModuleSync sync = new DbToModuleSync(remoteSystem);
			sync.deleteInactiveUsers();
			sync.syncToModule();
		}
		return "";
	}
}
