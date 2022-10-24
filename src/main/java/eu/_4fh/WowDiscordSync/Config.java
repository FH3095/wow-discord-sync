package eu._4fh.WowDiscordSync;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@DefaultAnnotation(NonNull.class)
public class Config {
	private static @CheckForNull Config instance;

	public final HikariDataSource dataSource;

	public final String oAuthScope;
	public final String oAuthApiKey;
	public final String oAuthApiSecret;
	public final int oAuthDefaultTokenDuration;
	public final String oAuthAuthRedirectTarget;

	private Config() {
		Path configDir;
		try {
			final javax.naming.Context initContext = new InitialContext();
			final Optional<String> obj = Optional.ofNullable(initContext.lookup("java:/comp/env/conf/conf-file"))
					.map(e -> e.toString().trim());
			if (obj.isEmpty() || obj.get().isEmpty()) {
				configDir = Path.of("config");
			} else {
				configDir = Path.of(obj.get());
			}
		} catch (NamingException e) {
			configDir = Path.of("config");
		}
		configDir = configDir.toAbsolutePath();

		final HikariConfig hikariConfig = new HikariConfig(readFile(configDir, "hikari.cfg"));
		dataSource = new HikariDataSource(hikariConfig);

		final Properties main = readFile(configDir, "main.cfg");
		oAuthScope = main.getProperty("bnet.oauth.scope");
		oAuthApiKey = main.getProperty("bnet.oauth.api-key");
		oAuthApiSecret = main.getProperty("bnet.oauth.api-secret");
		oAuthDefaultTokenDuration = Integer.parseInt(main.getProperty("bnet.oauth.default-token-duration"));
		oAuthAuthRedirectTarget = main.getProperty("bnet.oauth.auth-redirect-target");
	}

	private Properties readFile(final Path configDir, final String fileName) {
		final Properties properties = new Properties();
		final File file = configDir.resolve(fileName).toFile();
		try (final Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (IOException e) {
			Log.getLog(this).error("Cant read " + file.toString(), e);
			throw new RuntimeException("Cant read " + file.toString(), e);
		}
		return properties;
	}

	@SuppressFBWarnings(value = "NP_NONNULL_RETURN_VIOLATION", justification = "instance cant be null after initialization")
	public static synchronized Config instance() {
		if (instance == null) {
			instance = new Config();
		}
		return instance;
	}
}
