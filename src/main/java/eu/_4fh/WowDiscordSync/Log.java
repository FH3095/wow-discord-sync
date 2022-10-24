package eu._4fh.WowDiscordSync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;

@DefaultAnnotation(NonNull.class)
public class Log {
	public static Logger getLog(final Class<?> clazz) {
		return LoggerFactory.getLogger(clazz);
	}

	public static Logger getLog(final Object object) {
		return getLog(object.getClass());
	}

	private Log() {
	}
}
