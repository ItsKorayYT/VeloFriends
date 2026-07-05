# VelocityFriends

VelocityFriends is a Velocity proxy plugin for friends, direct messages, privacy controls, Java clickable chat menus, and optional Bedrock forms through Floodgate/Cumulus.

## Compatibility

The requested target version `26.1.2` is not a valid Velocity API or Minecraft Java plugin API version. This project targets modern Velocity 3.x APIs and uses `com.velocitypowered:velocity-api:3.5.0-SNAPSHOT` with Java 21, matching current PaperMC Velocity development guidance. It does not use Bukkit, Spigot, or Paper APIs.

Floodgate is optional. If Floodgate is not installed, all players use the Java Adventure clickable chat menus. If Floodgate is installed and `gui.bedrock-forms-enabled` is true, Bedrock players are detected through Floodgate and receive native Cumulus forms where available.

## Building

```bash
gradle clean shadowJar
```

The production jar is created at:

```text
build/libs/VelocityFriends-1.0.0.jar
```

Copy that jar into your Velocity proxy `plugins/` directory and restart the proxy. On first start, VelocityFriends creates:

```text
plugins/velocityfriends/config.yml
plugins/velocityfriends/messages.yml
plugins/velocityfriends/velocityfriends.db
```

## Storage

SQLite is the default and requires no external setup. MySQL/MariaDB can be enabled in `config.yml`:

```yaml
storage:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    database: velocityfriends
    username: velocityfriends
    password: change-me
```

The plugin uses HikariCP and keeps database work asynchronous so commands and listeners do not block the Velocity event path.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/friend add <player>` | Send a friend request | `velocityfriends.command.friend` |
| `/friend remove <player>` | Remove a friend | `velocityfriends.command.friend` |
| `/friend accept <player>` | Accept a request | `velocityfriends.command.friend` |
| `/friend deny <player>` | Deny a request | `velocityfriends.command.friend` |
| `/friend cancel <player>` | Cancel outgoing request | `velocityfriends.command.friend` |
| `/friend list` | Open friend list | `velocityfriends.command.friend` |
| `/friend requests` | Open requests menu | `velocityfriends.command.friend` |
| `/friend block <player>` | Block a player | `velocityfriends.command.friend` |
| `/friend unblock <player>` | Unblock a player | `velocityfriends.command.friend` |
| `/friend toggle` | Toggle friend requests | `velocityfriends.command.friend` |
| `/friend favorite <player>` | Toggle favorite friend | `velocityfriends.command.friend` |
| `/friend note <player> <note>` | Set a private note | `velocityfriends.command.friend` |
| `/friends`, `/f` | Friend aliases | `velocityfriends.command.friend` |
| `/dm <player> <message>` | Direct message | `velocityfriends.command.dm` |
| `/msg`, `/tell` | DM aliases | `velocityfriends.command.dm` |
| `/reply <message>`, `/r <message>` | Reply to last DM | `velocityfriends.command.reply` |
| `/togglemsg` | Toggle receiving DMs | `velocityfriends.command.dm` |
| `/ignore <player>` | Ignore DMs from player | `velocityfriends.command.dm` |
| `/unignore <player>` | Remove ignore | `velocityfriends.command.dm` |
| `/socialspy` | Toggle social spy | `velocityfriends.command.socialspy` |
| `/friendsgui` | Open social menu | `velocityfriends.command.gui` |
| `/social` | Social menu alias | `velocityfriends.command.gui` |
| `/vf reload` | Reload config/messages | `velocityfriends.admin.reload` |
| `/vf info` | Show plugin info | `velocityfriends.admin.reload` |
| `/vf debug <player>` | Show social debug counts | `velocityfriends.admin.spy` |
| `/vf forceadd <p1> <p2>` | Force add friendship | `velocityfriends.admin.reload` |
| `/vf forceremove <p1> <p2>` | Force remove friendship | `velocityfriends.admin.reload` |
| `/vf purgeold <days>` | Purge old cached players | `velocityfriends.admin.reload` |
| `/vf migrate` | Confirms schema migration state | `velocityfriends.admin.reload` |

## Permissions

```text
velocityfriends.command.friend
velocityfriends.command.dm
velocityfriends.command.reply
velocityfriends.command.gui
velocityfriends.command.socialspy
velocityfriends.admin.reload
velocityfriends.admin.spy
velocityfriends.bypass.cooldown
velocityfriends.bypass.privacy
velocityfriends.limit.250
velocityfriends.limit.500
```

Friend limits are configured in `friends.permission-limits`. Add entries such as `velocityfriends.limit.1000: 1000` to support more tiers.

## Menus

Java players receive Adventure clickable chat menus with hover text and run-command actions.

Bedrock players using Floodgate receive native forms when possible:

- Main Social Menu: Friends, Requests, Direct Message, Privacy Settings, Blocked/Ignored, Favorites.
- Friends List: paginated status list with message, favorite, remove, and block actions.
- Requests: incoming accept/deny and outgoing cancel actions.
- Direct Message: online player selector plus CustomForm message input where Cumulus supports it.
- Privacy Settings: friend requests, DM privacy, online visibility, and server visibility.
- Blocked/Ignored: unblock and unignore actions.

If a Cumulus form method is unavailable because of an older Floodgate/Cumulus build, VelocityFriends falls back to the Java clickable menu instead of failing startup.

## Text and Formatting

All player-facing text lives in `messages.yml` and uses MiniMessage. Configurable placeholders include:

```text
{player} {target} {message} {server} {status} {count} {page} {max_page} {time}
```

DM message bodies are inserted as unparsed MiniMessage placeholders, so players cannot inject formatting tags into message formats.

## Example Screenshots, Described

Main Social Menu: a compact chat/form menu headed `Social Menu`, with clear entries for Friends, Requests, Direct Message, Privacy Settings, Blocked/Ignored, and Favorites.

Friends List: favorite friends appear first, then other friends sorted by name. Online friends show `online` and a visible server if their privacy allows it.

Requests Menu: incoming requests show Accept and Deny actions; outgoing requests show Cancel.

Bedrock Direct Message Form: a player selector opens a message input form with a single text field and submit action.

## Troubleshooting

- The plugin must be installed on the Velocity proxy, not backend Paper servers.
- Do not install this as a Bukkit/Paper plugin. It has no inventory GUI code and no backend API dependency.
- If Bedrock forms do not appear, verify Floodgate is installed on the proxy and `gui.bedrock-forms-enabled` is true.
- If MySQL fails, confirm credentials, database permissions, and that the proxy can reach the database host.
- If players cannot friend offline users, the target must have joined the proxy once so their UUID/name can be cached.
- Sound notifications are intentionally optional placeholders because Velocity cannot reliably play client sounds without backend server support.
- Actionbar DM notifications are proxy-safe and are controlled by the stored player setting.

## Optional Backend Support

VelocityFriends does not require backend plugins. Features that would need backend cooperation, such as custom sounds from server resource packs or inventory GUIs, are intentionally left out or exposed only as safe proxy-side placeholders.
# VeloFriends
# VeloFriends
# VeloFriends
