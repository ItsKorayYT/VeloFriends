CREATE TABLE IF NOT EXISTS players (
  uuid CHAR(36) PRIMARY KEY,
  username VARCHAR(16) NOT NULL,
  username_lower VARCHAR(16) NOT NULL,
  first_seen BIGINT NOT NULL,
  last_seen BIGINT NOT NULL,
  INDEX idx_players_username_lower (username_lower)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friends (
  player_uuid CHAR(36) NOT NULL,
  friend_uuid CHAR(36) NOT NULL,
  created_at BIGINT NOT NULL,
  favorite TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (player_uuid, friend_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friend_requests (
  requester_uuid CHAR(36) NOT NULL,
  target_uuid CHAR(36) NOT NULL,
  created_at BIGINT NOT NULL,
  expires_at BIGINT NOT NULL,
  PRIMARY KEY (requester_uuid, target_uuid),
  INDEX idx_friend_requests_target (target_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS blocks (
  player_uuid CHAR(36) NOT NULL,
  blocked_uuid CHAR(36) NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (player_uuid, blocked_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ignores (
  player_uuid CHAR(36) NOT NULL,
  ignored_uuid CHAR(36) NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (player_uuid, ignored_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS settings (
  player_uuid CHAR(36) PRIMARY KEY,
  friend_requests_enabled TINYINT(1) NOT NULL,
  dm_privacy VARCHAR(20) NOT NULL,
  online_status_visibility VARCHAR(20) NOT NULL,
  server_visibility VARCHAR(20) NOT NULL,
  messages_enabled TINYINT(1) NOT NULL,
  social_spy TINYINT(1) NOT NULL,
  friend_notifications TINYINT(1) NOT NULL,
  actionbar_dm TINYINT(1) NOT NULL,
  sound_dm TINYINT(1) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notes (
  player_uuid CHAR(36) NOT NULL,
  friend_uuid CHAR(36) NOT NULL,
  note TEXT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (player_uuid, friend_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
