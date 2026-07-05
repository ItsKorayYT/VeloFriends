# VelocityFriends

VelocityFriends is a proxy-only friends and messaging plugin for Velocity networks.

It runs on the Velocity proxy, stores social data in SQLite or MySQL, gives Java players clickable chat menus, and gives Bedrock players Floodgate/Cumulus forms when Floodgate is installed on the proxy.

No backend Paper, Spigot, or Bukkit plugin is required.

## Requirements

- Velocity 3.x
- Java 21 or newer
- Optional: Floodgate on the Velocity proxy for Bedrock forms

VelocityFriends is not a Bukkit/Paper plugin. It does not use backend inventories, Protocolize, or server-side chest GUI APIs.

## Features

- Friend requests, friend lists, favorites, private notes, blocks, and ignores
- Direct messages, replies, `/togglemsg`, and per-player ignore lists
- Privacy settings for DMs, online status, friend requests, and visible server
- Friend join, leave, and server-change notifications
- Optional admin social spy
- SQLite by default, with MySQL/MariaDB support
- Java clickable chat menus with hover and command actions
- Bedrock forms through Floodgate/Cumulus

## Build

Linux/macOS:

```bash
./gradlew clean shadowJar
```

Windows:

```bat
gradlew.bat clean shadowJar
```

The jar is created at:

```text
build/libs/VelocityFriends-1.0.1.jar
```

The shaded jar includes SQLite support, but only bundles native SQLite libraries for `Linux/x86_64` and `Windows/x86_64` by default so the jar stays smaller.

To include a different SQLite native platform:

```bash
./gradlew clean shadowJar -PsqliteNativePlatforms=Linux/x86_64,Mac/aarch64
```

The wrapper uses Gradle 9.6.1, so you do not need a global Gradle install. If Gradle fails on a mapped/shared Windows drive with a FileHasher error, copy the project to a local drive and build it there.

## Install

1. Put `build/libs/VelocityFriends-1.0.1.jar` in the Velocity proxy `plugins` folder.
2. Restart the proxy.
3. Edit the generated files if needed.

Generated files:

```text
plugins/velocityfriends/config.yml
plugins/velocityfriends/messages.yml
plugins/velocityfriends/velocityfriends.db
```

## Menus

Java players use a clickable chat menu. It works entirely through Velocity Adventure components, so it does not need backend inventory APIs.

Bedrock players use Floodgate/Cumulus forms. Bedrock players are not sent to the Java clickable chat menu because Bedrock clients cannot reliably click those chat components.

If Bedrock forms do not open, check that Floodgate is installed on the proxy and that this is enabled:

```yaml
gui:
  bedrock-forms-enabled: true
```

## Storage

SQLite is the default:

```yaml
storage:
  type: sqlite
  sqlite-file: velocityfriends.db
```

MySQL/MariaDB:

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

Database work runs asynchronously so commands do not block the proxy event path.

## Commands

Basic player commands are available to everyone by default.

| Command | Description |
| --- | --- |
| `/friend`, `/friends`, `/f` | Open the friend/social menu |
| `/friend add <player>` | Send a friend request |
| `/friend remove <player>` | Remove a friend |
| `/friend accept <player>` | Accept a request |
| `/friend deny <player>` | Deny a request |
| `/friend cancel <player>` | Cancel an outgoing request |
| `/friend list` | Open the friend list |
| `/friend requests` | Open pending requests |
| `/friend block <player>` | Block a player |
| `/friend unblock <player>` | Unblock a player |
| `/friend toggle` | Toggle incoming friend requests |
| `/friend favorite <player>` | Favorite or unfavorite a friend |
| `/friend note <player> <note>` | Save a private note |
| `/friendsgui`, `/social` | Open the social menu |
| `/dm <player> <message>`, `/msg`, `/tell` | Send a direct message |
| `/reply <message>`, `/r` | Reply to the last DM |
| `/togglemsg` | Toggle receiving DMs |
| `/ignore <player>` | Ignore DMs from a player |
| `/unignore <player>` | Remove an ignore |

Admin commands:

| Command | Permission |
| --- | --- |
| `/socialspy` | `velocityfriends.command.socialspy` or `velocityfriends.admin.spy` |
| `/vf reload` | `velocityfriends.admin.reload` |
| `/vf info` | `velocityfriends.admin.reload` |
| `/vf debug <player>` | `velocityfriends.admin.spy` |
| `/vf forceadd <p1> <p2>` | `velocityfriends.admin.reload` |
| `/vf forceremove <p1> <p2>` | `velocityfriends.admin.reload` |
| `/vf purgeold <days>` | `velocityfriends.admin.reload` |
| `/vf migrate` | `velocityfriends.admin.reload` |

## Permissions

These permissions are optional controls for staff, bypasses, and larger friend limits:

```text
velocityfriends.command.socialspy
velocityfriends.admin.reload
velocityfriends.admin.spy
velocityfriends.bypass.cooldown
velocityfriends.bypass.privacy
velocityfriends.limit.250
velocityfriends.limit.500
```

Friend limits are configured in `config.yml`:

```yaml
friends:
  permission-limits:
    velocityfriends.limit.250: 250
    velocityfriends.limit.500: 500
```

Add more tiers by adding more permission nodes to that map.

## Messages

Player-facing messages live in `messages.yml` and use MiniMessage.

Common placeholders:

```text
{player}
{target}
{message}
{server}
{status}
{count}
{page}
{max_page}
{time}
```

DM message text is escaped before insertion into MiniMessage formats, so players cannot inject formatting tags into messages.

## Troubleshooting

- Install the jar on Velocity, not on backend servers.
- Use Java 21 or newer.
- Install Floodgate on the proxy if you want Bedrock forms.
- Keep `gui.bedrock-forms-enabled: true` for Bedrock menus.
- Offline player lookup works after a player has joined the proxy once.
- For MySQL, check credentials, database permissions, and network access from the proxy host.
- For non-default SQLite native platforms, rebuild with `-PsqliteNativePlatforms=<platform>`.

## Scope

VelocityFriends intentionally stays proxy-only. True chest inventory GUIs, backend resource-pack sounds, and backend-only APIs are outside the plugin unless a supported proxy-side protocol path exists.
