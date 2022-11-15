package eu._4fh.wowsync.util;

import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public interface TestBase {
	AtomicInteger dbCounter = new AtomicInteger(0);
	DecimalFormat dbCounterFormat = new DecimalFormat("000");
	AtomicLong idCounter = new AtomicLong(0);

	static long sNextId() {
		return idCounter.incrementAndGet();
	}

	default long nextId() {
		return sNextId();
	}

	static String sNextStr() {
		return Long.toString(idCounter.incrementAndGet());
	}

	default String nextStr() {
		return sNextStr();
	}

	@BeforeAll
	static void setupDatabaseStatic() {
		Config.forTestSetDbUrl("jdbc:hsqldb:mem:testdb" + dbCounterFormat.format(dbCounter.incrementAndGet()));
	}

	@AfterAll
	static void cleanupSingletons() {
		Singletons.forTestCloseAll();
	}
}
