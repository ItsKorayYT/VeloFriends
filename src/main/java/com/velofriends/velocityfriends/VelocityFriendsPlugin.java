package com.velofriends.velocityfriends;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velofriends.velocityfriends.command.AdminCommand;
import com.velofriends.velocityfriends.command.DirectMessageCommand;
import com.velofriends.velocityfriends.command.FriendCommand;
import com.velofriends.velocityfriends.command.IgnoreCommand;
import com.velofriends.velocityfriends.command.ReplyCommand;
import com.velofriends.velocityfriends.command.SocialMenuCommand;
import com.velofriends.velocityfriends.command.SocialSpyCommand;
import com.velofriends.velocityfriends.command.ToggleMsgCommand;
import com.velofriends.velocityfriends.config.ConfigManager;
import com.velofriends.velocityfriends.config.MessageManager;
import com.velofriends.velocityfriends.config.PluginConfig;
import com.velofriends.velocityfriends.listener.PlayerListener;
import com.velofriends.velocityfriends.menu.FloodgateIntegration;
import com.velofriends.velocityfriends.menu.JavaMenuRenderer;
import com.velofriends.velocityfriends.menu.SocialMenuService;
import com.velofriends.velocityfriends.service.FriendService;
import com.velofriends.velocityfriends.service.MessagingService;
import com.velofriends.velocityfriends.service.NameResolver;
import com.velofriends.velocityfriends.service.NotificationService;
import com.velofriends.velocityfriends.service.PermissionLimitResolver;
import com.velofriends.velocityfriends.service.SettingsService;
import com.velofriends.velocityfriends.storage.Database;
import com.velofriends.velocityfriends.storage.JdbcDatabase;
import com.velofriends.velocityfriends.storage.PlayerRepository;
import com.velofriends.velocityfriends.storage.SocialRepository;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VelocityFriendsPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final ExecutorService serviceExecutor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "VelocityFriends-Service");
        thread.setDaemon(true);
        return thread;
    });

    private Database database;
    private SocialRepository socialRepository;
    private String version = "1.0.0";

    @Inject
    public VelocityFriendsPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.configManager = new ConfigManager(dataDirectory, logger);
        this.messageManager = new MessageManager(dataDirectory, logger);
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        loadVersion();
        reloadConfiguration();
        PluginConfig config = configManager.config();
        database = new JdbcDatabase(dataDirectory, config, logger);
        database.initialize().join();

        PlayerRepository playerRepository = new PlayerRepository(database);
        socialRepository = new SocialRepository(database, config.privacyDefaults());
        NameResolver nameResolver = new NameResolver(proxy, playerRepository);
        PermissionLimitResolver limitResolver = new PermissionLimitResolver(config.friends());
        FloodgateIntegration floodgate = new FloodgateIntegration(logger);
        FriendService friendService = new FriendService(proxy, playerRepository, socialRepository, nameResolver, limitResolver, messageManager, config, serviceExecutor);
        MessagingService messagingService = new MessagingService(proxy, socialRepository, nameResolver, messageManager, config, serviceExecutor);
        SettingsService settingsService = new SettingsService(socialRepository, nameResolver, serviceExecutor);
        NotificationService notificationService = new NotificationService(proxy, socialRepository, messageManager, config, serviceExecutor);
        JavaMenuRenderer javaMenus = new JavaMenuRenderer(friendService, settingsService, messageManager, config);
        SocialMenuService menus = new SocialMenuService(proxy, config, messageManager, floodgate, javaMenus, friendService, messagingService, settingsService);

        registerCommands(friendService, messagingService, settingsService, menus, floodgate);
        proxy.getEventManager().register(this, new PlayerListener(playerRepository, notificationService, logger));
        proxy.getScheduler().buildTask(this, () -> socialRepository.deleteExpiredRequests()
                .exceptionally(throwable -> {
                    logger.warn("Unable to delete expired friend requests", throwable);
                    return 0;
                })).repeat(Duration.ofMinutes(30)).schedule();
        logger.info("VelocityFriends {} enabled with {} storage", version, database.dialect());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (database != null) {
            try {
                database.close();
            } catch (Exception exception) {
                logger.warn("Error while closing VelocityFriends storage", exception);
            }
        }
        serviceExecutor.shutdown();
        logger.info("VelocityFriends disabled");
    }

    public void reloadConfiguration() {
        configManager.load();
        messageManager.load();
    }

    private void registerCommands(FriendService friends, MessagingService messaging, SettingsService settings,
                                  SocialMenuService menus, FloodgateIntegration floodgate) {
        CommandManager commands = proxy.getCommandManager();
        commands.register(commands.metaBuilder("friend").aliases("friends", "f").plugin(this).build(), new FriendCommand(friends, menus, messageManager));
        commands.register(commands.metaBuilder("dm").aliases("msg", "tell").plugin(this).build(), new DirectMessageCommand(messaging, messageManager));
        commands.register(commands.metaBuilder("reply").aliases("r").plugin(this).build(), new ReplyCommand(messaging, messageManager));
        commands.register(commands.metaBuilder("togglemsg").plugin(this).build(), new ToggleMsgCommand(messaging, messageManager));
        commands.register(commands.metaBuilder("ignore").plugin(this).build(), new IgnoreCommand(messaging, messageManager, true));
        commands.register(commands.metaBuilder("unignore").plugin(this).build(), new IgnoreCommand(messaging, messageManager, false));
        commands.register(commands.metaBuilder("socialspy").plugin(this).build(), new SocialSpyCommand(messaging, messageManager));
        commands.register(commands.metaBuilder("friendsgui").aliases("social").plugin(this).build(), new SocialMenuCommand(menus, settings, messageManager));
        commands.register(commands.metaBuilder("vf").plugin(this).build(), new AdminCommand(configManager, messageManager, database, floodgate, friends, version, this::reloadConfiguration));
    }

    private void loadVersion() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("velocityfriends.properties")) {
            if (input == null) {
                return;
            }
            Properties properties = new Properties();
            properties.load(input);
            version = properties.getProperty("version", version);
        } catch (IOException exception) {
            logger.debug("Unable to read version resource", exception);
        }
    }
}
