package eu._4fh.wowsync.rest;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.wowsync.database.Db;
import eu._4fh.wowsync.database.data.RemoteSystem;
import eu._4fh.wowsync.sync.BattleNetToDbSync;
import eu._4fh.wowsync.sync.DbToModuleSync;
import eu._4fh.wowsync.util.Singletons;

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
			new DbToModuleSync(remoteSystem).syncToModule();
		}
		return "Done";
	}
}
