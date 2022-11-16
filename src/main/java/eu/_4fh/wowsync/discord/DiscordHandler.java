package eu._4fh.wowsync.discord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.CleanupObligation;
import edu.umd.cs.findbugs.annotations.CreatesObligation;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.DischargesObligation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.wowsync.database.Db;
import eu._4fh.wowsync.database.data.RemoteSystem.RemoteSystemType;
import eu._4fh.wowsync.modules.Module.RoleChange;
import eu._4fh.wowsync.modules.ModuleService;
import eu._4fh.wowsync.util.ClosableSingleton;
import eu._4fh.wowsync.util.Config;
import eu._4fh.wowsync.util.Singletons;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateOnlineStatusEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

@DefaultAnnotation(NonNull.class)
@CleanupObligation
/*package*/ class DiscordHandler extends ListenerAdapter implements ClosableSingleton {

	private final JDA jda;
	private final Db db;
	private final Set<Long> messageReactions = ConcurrentHashMap.newKeySet();

	@CreatesObligation
	private DiscordHandler() {
		jda = JDABuilder
				.createDefault(Singletons.instance(Config.class).discordToken, GatewayIntent.GUILD_MESSAGE_REACTIONS,
						GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_PRESENCES)
				.disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
				.enableCache(CacheFlag.ONLINE_STATUS).setAutoReconnect(true)
				.setMemberCachePolicy(MemberCachePolicy.ONLINE).setChunkingFilter(ChunkingFilter.NONE)
				.addEventListeners(this).build();
		try {
			jda.awaitReady();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		jda.upsertCommand("bnet-auth", "Authenticates yourself with battlenet").queue();
		db = Singletons.instance(Db.class);
	}

	/*package*/ void addMessageToReactTo(final long messageId) {
		messageReactions.add(messageId);
	}

	/*package*/ void removeMessageToReactoTo(final long messageId) {
		messageReactions.remove(messageId);
	}

	private static final Set<OnlineStatus> onlineStates = Collections
			.unmodifiableSet(EnumSet.of(OnlineStatus.ONLINE, OnlineStatus.IDLE, OnlineStatus.DO_NOT_DISTURB));

	@Override
	public void onUserUpdateOnlineStatus(final UserUpdateOnlineStatusEvent event) {
		// We have to test for user going offline, because to fire this event the user has to be cached before.
		// And we only cache online users.
		if (onlineStates.contains(event.getOldOnlineStatus())) {
			db.discordOnlineUsers.updateLastOnline(event.getGuild().getIdLong(), event.getMember().getIdLong(),
					event.getMember().getEffectiveName());
		}
	}

	@Override
	public void onMessageReactionAdd(final MessageReactionAddEvent event) {
		if (!messageReactions.contains(event.getMessageIdLong())) {
			return;
		}

		event.getUser().openPrivateChannel().queue(channel -> {
			channel.sendMessage(getAuthenticateStartText(event.getGuild().getIdLong(), event.getUserIdLong())).queue();
			channel.delete().queue();
		});
	}

	@Override
	public void onSlashCommandInteraction(final SlashCommandInteractionEvent event) {
		if (!event.getName().equals("bnet-auth")) {
			return;
		}
		event.deferReply(true).queue();
		event.getHook().setEphemeral(true)
				.sendMessage(getAuthenticateStartText(event.getGuild().getIdLong(), event.getUser().getIdLong()))
				.queue();
	}

	private String getAuthenticateStartText(final long guildId, final long userId) {
		return "To authenticate follow this link: " + Singletons.instance(ModuleService.class)
				.createAuthUri(db.remoteSystems.byTypeAndRemoteId(RemoteSystemType.Discord, guildId).id, userId);
	}

	@Override
	@DischargesObligation
	public void close() {
		jda.shutdown();
	}

	public Map<Long, Set<String>> getAllUsersWithRoles(final long guildId) {
		final Map<Long, Set<String>> result = new HashMap<>();
		final List<Member> members = jda.getGuildById(guildId).findMembers(m -> true).get();
		for (final Member member : members) {
			final Set<String> roles = member.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
			result.put(member.getIdLong(), Collections.unmodifiableSet(roles));
		}
		return result;
	}

	public Set<String> getRolesForUser(final long guildId, final long userId) {
		try {
			return Collections.unmodifiableSet(jda.getGuildById(guildId).retrieveMemberById(userId).submit().get()
					.getRoles().stream().map(Role::getName).collect(Collectors.toSet()));
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	public void changeRole(final long guildId, final Map<Long, RoleChange> roleChanges) {
		final Map<String, List<Role>> roles = new HashMap<>();
		for (final Role role : jda.getGuildById(guildId).getRoleCache().asList()) {
			roles.computeIfAbsent(role.getName(), name -> new ArrayList<>()).add(role);
		}
		final List<Member> members = jda.getGuildById(guildId).retrieveMembersByIds(roleChanges.keySet()).get();
		for (final Member member : members) {
			final RoleChange roleChange = roleChanges.get(member.getIdLong());
			final List<Role> rolesToAdd = collectRoles(roles, roleChange.toAdd);
			final List<Role> rolesToRemove = collectRoles(roles, roleChange.toRemove);
			jda.getGuildById(guildId).modifyMemberRoles(member, rolesToAdd, rolesToRemove).queue();
		}
	}

	private List<Role> collectRoles(final Map<String, List<Role>> roles, final Set<String> roleNamesToReturn) {
		return roles.entrySet().stream().filter(entry -> roleNamesToReturn.contains(entry.getKey()))
				.flatMap(entry -> entry.getValue().stream()).collect(Collectors.toList());

	}

	public void setNickname(final long guildId, final long userId, final String newNickname) {
		jda.getGuildById(guildId).retrieveMemberById(userId)
				.queue(member -> member.modifyNickname(newNickname).queue());
	}
}
