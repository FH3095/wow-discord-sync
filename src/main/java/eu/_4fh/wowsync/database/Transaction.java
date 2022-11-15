package eu._4fh.wowsync.database;

import java.util.concurrent.atomic.AtomicInteger;

import edu.umd.cs.findbugs.annotations.CleanupObligation;
import edu.umd.cs.findbugs.annotations.CreatesObligation;
import edu.umd.cs.findbugs.annotations.DischargesObligation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

@CleanupObligation
public class Transaction implements AutoCloseable {

	@CleanupObligation
	public class TransCnt implements AutoCloseable {
		/*package*/ final EntityManager em;

		@CreatesObligation
		private TransCnt() {
			counter.incrementAndGet();
			em = Transaction.this.em;
			if (!em.getTransaction().isActive()) {
				em.getTransaction().begin();
			}
		}

		@Override
		@DischargesObligation
		public void close() {
			final int numOpened = counter.decrementAndGet();
			if (numOpened <= 0) {
				Transaction.this.close();
			}
		}

		public void commit() {
			em.getTransaction().commit();
		}
	}

	/*package*/ static TransCnt create(final EntityManagerFactory sessionFactory) {
		// Transaction will be cleaned up when all TransCnt are closed
		Transaction transaction = threadTransaction.get();
		if (transaction == null) {
			transaction = new Transaction(sessionFactory);
		}
		return transaction.new TransCnt();
	}

	private static final ThreadLocal<Transaction> threadTransaction = new ThreadLocal<>();

	private final EntityManager em;
	private final AtomicInteger counter = new AtomicInteger(0);

	@CreatesObligation
	private Transaction(final EntityManagerFactory sessionFactory) {
		em = sessionFactory.createEntityManager();
		threadTransaction.set(this);
	}

	@Override
	@DischargesObligation
	public void close() {
		threadTransaction.remove();
		if (em.getTransaction().isActive()) {
			em.getTransaction().rollback();
		}
		em.close();
	}
}
