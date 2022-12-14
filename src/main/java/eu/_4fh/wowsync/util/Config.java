package eu._4fh.wowsync.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Environment;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.CleanupObligation;
import edu.umd.cs.findbugs.annotations.CreatesObligation;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.DischargesObligation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.abstract_bnet_api.oauth2.BattleNetClients;
import eu._4fh.wowsync.database.data.Account;
import eu._4fh.wowsync.database.data.AccountRemoteId;
import eu._4fh.wowsync.database.data.Character;
import eu._4fh.wowsync.database.data.DiscordOnlineUser;
import eu._4fh.wowsync.database.data.DiscordSettings;
import eu._4fh.wowsync.database.data.Guild;
import eu._4fh.wowsync.database.data.RemoteSystem;
import eu._4fh.wowsync.database.data.RemoteSystemRankToGroup;
import jakarta.persistence.EntityManagerFactory;

@DefaultAnnotation(NonNull.class)
@CleanupObligation
public class Config implements ClosableSingleton {

	private static @CheckForNull String testDbUrl = null;

	/*package for test*/ static void forTestSetDbUrl(final @CheckForNull String dbUrl) {
		testDbUrl = dbUrl;
	}

	private final HikariDataSource dataSource;
	public final EntityManagerFactory hibernateSessionFactory;
	public final BattleNetClients battleNetClients;
	public final String discordToken;
	public final URI rootUri;
	public final String cssStyle;
	public final byte bnetNumRequestRetries;
	public final short keepNewAccountsWithoutGuildsForDays;
	public final short keepCharactersWithAccountButWithoutGuildForDays;

	@CreatesObligation
	private Config() {
		Path configDir;
		try {
			final javax.naming.Context initContext = new InitialContext();
			final Optional<String> obj = Optional.ofNullable(initContext.lookup("java:/comp/env/config-dir"))
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
		hikariConfig.setAutoCommit(false);
		dataSource = new HikariDataSource(hikariConfig);

		final StandardServiceRegistry hibernateRegistry = new StandardServiceRegistryBuilder()
				.configure(configDir.resolve("hibernate.cfg.xml").toFile())
				.applySetting(Environment.DATASOURCE, dataSource).build();
		hibernateSessionFactory = new MetadataSources(hibernateRegistry).addAnnotatedClasses(getEntityClasses())
				.buildMetadata().buildSessionFactory();

		final Properties main = readFile(configDir, "main.cfg");

		final String oAuthApiKey = nonNull(main, "bnet.oauth.api-key");
		final String oAuthApiSecret = nonNull(main, "bnet.oauth.api-secret");
		final int oAuthDefaultTokenDuration = Integer.parseInt(nonNull(main, "bnet.oauth.default-token-duration"));
		final String oAuthAuthRedirectTarget = nonNull(main, "bnet.oauth.auth-redirect-target");
		battleNetClients = new BattleNetClients(oAuthApiKey, oAuthApiSecret, oAuthDefaultTokenDuration,
				oAuthAuthRedirectTarget);
		bnetNumRequestRetries = Byte.parseByte(nonNull(main, "bnet.rest.num-retries"));
		if (bnetNumRequestRetries < 1) {
			throw new IllegalStateException(
					"Invalid value for bnet.rest.num-retries: " + bnetNumRequestRetries + " < 1");
		}
		keepNewAccountsWithoutGuildsForDays = Short
				.parseShort(nonNull(main, "eu._4fh.wowsync.sync.keepNewAccountsWithoutGuildsForDays"));
		if (keepNewAccountsWithoutGuildsForDays < 1) {
			throw new IllegalStateException(
					"Invalid value for eu._4fh.wowsync.sync.keepNewAccountsWithoutGuildsForDays "
							+ keepNewAccountsWithoutGuildsForDays + " < 1");
		}
		keepCharactersWithAccountButWithoutGuildForDays = Short
				.parseShort(nonNull(main, "eu._4fh.wowsync.sync.keepCharactersWithAccountButWithoutGuildForDays"));
		if (keepCharactersWithAccountButWithoutGuildForDays < 1) {
			throw new IllegalStateException(
					"Invalid value for eu._4fh.wowsync.sync.keepCharactersWithAccountButWithoutGuildForDays "
							+ keepCharactersWithAccountButWithoutGuildForDays + " < 1");
		}

		discordToken = nonNull(main, "discord.token");

		String rootUrlStr = nonNull(main, "rootUrl");
		if (rootUrlStr.endsWith("/")) {
			rootUrlStr = rootUrlStr.substring(0, rootUrlStr.length() - 1);
		}
		rootUri = URI.create(rootUrlStr);

		try {
			cssStyle = Files.readString(configDir.resolve("style.css"), StandardCharsets.UTF_8).trim();
		} catch (IOException e) {
			LoggerFactory.getLogger(getClass()).error("Cant read style.css", e);
			throw new RuntimeException(e);
		}
	}

	private Class<?>[] getEntityClasses() {
		//final String rootPackage = "eu._4fh.wowsync.database.data";
		// org.reflections (doesnt work on server)
		/*final Reflections reflections = new Reflections(rootPackage);
		return reflections.getTypesAnnotatedWith(Entity.class, true).stream().toArray(Class<?>[]::new);*/

		// Pure Java (only works for concrete package)
		/*final UnaryOperator<String> getClassNameFromFileName = filename -> filename.substring(0,
				filename.lastIndexOf('.'));
		try (final BufferedReader reader = new BufferedReader(new InputStreamReader(
				ClassLoader.getSystemClassLoader().getResourceAsStream(rootPackage.replace('.', '/'))))) {
			final Class<?>[] result = reader.lines().filter(line -> line.endsWith(".class"))
					.map(getClassNameFromFileName::apply).map(line -> {
						try {
							return Class.forName(rootPackage + "." + line);
						} catch (ClassNotFoundException e) {
							throw new RuntimeException(e);
						}
					}).toArray(Class<?>[]::new);
			LoggerFactory.getLogger(getClass()).debug("Found entity classes: {}", (Object[]) result);
			return result;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}*/

		// Google Guava
		/*try {
			return ClassPath.from(getClass().getClassLoader()).getTopLevelClassesRecursive(rootPackage).stream()
					.map(ClassInfo::load).filter(c -> c.isAnnotationPresent(Entity.class)).toArray(Class<?>[]::new);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}*/
		return new Class<?>[] { Account.class, AccountRemoteId.class, Character.class, DiscordOnlineUser.class,
				Guild.class, RemoteSystem.class, RemoteSystemRankToGroup.class, DiscordSettings.class };
	}

	private Properties readFile(final Path configDir, final String fileName) {
		final Properties properties = new Properties();
		final File file = configDir.resolve(fileName).toFile();
		try (final Reader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
			properties.load(reader);
		} catch (IOException e) {
			LoggerFactory.getLogger(getClass()).error("Cant read " + file.toString(), e);
			throw new RuntimeException("Cant read " + file.toString(), e);
		}
		if (testDbUrl != null && properties.containsKey("jdbcUrl")) {
			properties.setProperty("jdbcUrl", testDbUrl);
			System.out.println("For testing set jdbcurl to " + testDbUrl);
		}
		return properties;
	}

	private String nonNull(final Properties properties, final String propertyName) {
		final String result = properties.getProperty(propertyName);
		if (result == null || result.isBlank()) {
			throw new IllegalStateException("Missing config setting " + propertyName);
		}
		return result.trim();
	}

	@Override
	@DischargesObligation
	public void close() {
		hibernateSessionFactory.close();
		dataSource.close();
	}
}
