package eu._4fh.wowsync.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.wowsync.modules.ModuleService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
@DefaultAnnotation(NonNull.class)
public class Singletons implements ServletContextListener {
	private static final Map<Class<?>, Object> singletons = new LinkedHashMap<>();

	@SuppressWarnings("unchecked")
	public static synchronized <T> T instance(final Class<T> clazz) {
		try {
			T result = (T) singletons.getOrDefault(clazz, null);
			if (result == null) {
				final Constructor<T> constructor = clazz.getDeclaredConstructor();
				constructor.setAccessible(true);
				result = constructor.newInstance();
				singletons.put(clazz, result);
			}
			return result;
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void contextDestroyed(final ServletContextEvent sce) {
		closeAll();
	}

	/*package for test*/ static void forTestCloseAll() {
		closeAll();
	}

	private static synchronized void closeAll() {
		final Logger log = LoggerFactory.getLogger(Singletons.class);
		final List<Object> closables = new ArrayList<>(singletons.values());
		singletons.clear();
		// We go reverse through that list. Classes that were created first, are closed last.
		for (final ListIterator<Object> it = closables.listIterator(closables.size()); it.hasPrevious();) {
			final Object obj = it.previous();
			try {
				if (obj instanceof ClosableSingleton) {
					((ClosableSingleton) obj).close();
				} else if (obj instanceof AutoCloseable) {
					((AutoCloseable) obj).close();
				}
			} catch (Exception e) {
				log.warn("Cant close singleton " + obj.toString(), e);
			}
		}
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		instance(ModuleService.class);
	}
}
