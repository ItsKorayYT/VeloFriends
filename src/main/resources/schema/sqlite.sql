CREATE TABLE IF NOT EXISTS players (
  uuid TEXT PRIMARY KEY,
  username TEXT NOT NULL,
  username_lower TEXT NOT NULL,
  first_seen INTEGER NOT NULL,
  last_seen INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_players_username_lower ON players(username_lower);

CREATE TABLE IF NOT EXISTS friends (
  player_uuid TEXT NOT NULL,
  friend_uuid TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  favorite INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (player_uuid, friend_uuid)
);

CREATE TABLE IF NOT EXISTS friend_requests (
  requester_uuid TEXT NOT NULL,
  target_uuid TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  PRIMARY KEY (requester_uuid, target_uuid)
);

CREATE INDEX IF NOT EXISTS idx_friend_requests_target ON friend_requests(target_uuid);

CREATE TABLE IF NOT EXISTS blocks (
  player_uuid TEXT NOT NULL,
  blocked_uuid TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (player_uuid, blocked_uuid)
);

CREATE TABLE IF NOT EXISTS ignores (
  player_uuid TEXT NOT NULL,
  ignored_uuid TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (player_uuid, ignored_uuid)
);

CREATE TABLE IF NOT EXISTS settings (
  player_uuid TEXT PRIMARY KEY,
  friend_requests_enabled INTEGER NOT NULL,
  dm_privacy TEXT NOT NULL,
  online_status_visibility TEXT NOT NULL,
  server_visibility TEXT NOT NULL,
  messages_enabled INTEGER NOT NULL,
  social_spy INTEGER NOT NULL,
  friend_notifications INTEGER NOT NULL,
  actionbar_dm INTEGER NOT NULL,
  sound_dm INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS notes (
  player_uuid TEXT NOT NULL,
  friend_uuid TEXT NOT NULL,
  note TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (player_uuid, friend_uuid)
);
