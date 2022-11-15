package eu._4fh.wowsync.sync;

import java.util.List;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.wowsync.database.Db;
import eu._4fh.wowsync.database.Transaction;
import eu._4fh.wowsync.util.Singletons;

@DefaultAnnotation(NonNull.class)
public class DbToModuleSync {
	private final Db db;
	private final Module module;

	public DbToModuleSync(final Module module) {
		this.db = Singletons.instance(Db.class);
		this.module = module;
	}

	public List<Long> getAllRemoteIdsByRemoteSystem(final String remoteSystemName) {
		try (final Transaction.TransCnt trans = db.createTransaction()) {
			//return db.accountsWithCharacterGetByRemoteSystem(remoteSystemName);
			throw new RuntimeException();
		}
	}

	public boolean isOfficer(final String remoteSystemName, final Long remoteId) {
		try (final Transaction.TransCnt trans = db.createTransaction()) {
			throw new RuntimeException();
			/*final Integer rank = db.accountGetMinRankByRemoteSystemId(remoteSystemName, remoteId);
			if (rank == null) {
				return false;
			}
			if (rank <= config.officerMaxRank()) {
				return true;
			} else {
				return false;
			}*/
		}
	}

}
