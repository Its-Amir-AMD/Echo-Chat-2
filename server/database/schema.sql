-- Create the database in your hosting control panel, select it in phpMyAdmin, then import this file.

CREATE TABLE IF NOT EXISTS users (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  email VARCHAR(254) NOT NULL,
  username VARCHAR(24) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login_at TIMESTAMP NULL DEFAULT NULL,
  UNIQUE KEY uq_users_email (email),
  UNIQUE KEY uq_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auth_tokens (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  token_hash CHAR(64) NOT NULL,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NULL DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_used_at DATETIME NULL DEFAULT NULL,
  UNIQUE KEY uq_auth_token_hash (token_hash),
  KEY idx_tokens_user (user_id),
  KEY idx_tokens_expiry (expires_at),
  CONSTRAINT fk_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Authentication throttling: store keyed hashes, never raw email/IP values.
CREATE TABLE IF NOT EXISTS auth_attempts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  action VARCHAR(16) NOT NULL,
  identity_hash CHAR(64) NOT NULL,
  ip_hash CHAR(64) NOT NULL,
  was_successful TINYINT(1) NOT NULL DEFAULT 0,
  attempted_at DATETIME NOT NULL,
  KEY idx_auth_attempt_identity (action, identity_hash, attempted_at),
  KEY idx_auth_attempt_ip (action, ip_hash, attempted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rooms (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(255) NOT NULL DEFAULT '',
  icon_name VARCHAR(40) NOT NULL DEFAULT 'forum',
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversations (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  pair_key CHAR(64) NOT NULL,
  created_at_ms BIGINT NOT NULL,
  updated_at_ms BIGINT NOT NULL,
  UNIQUE KEY uq_conversation_pair (pair_key),
  KEY idx_conversation_updated (updated_at_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversation_members (
  conversation_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (conversation_id, user_id),
  KEY idx_member_user (user_id, conversation_id),
  CONSTRAINT fk_members_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
  CONSTRAINT fk_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS messages (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  room_id VARCHAR(64) NULL,
  conversation_id VARCHAR(64) NULL,
  sender_id VARCHAR(64) NOT NULL,
  sender_name VARCHAR(80) NOT NULL,
  content VARCHAR(10000) NOT NULL,
  created_at_ms BIGINT NOT NULL,
  KEY idx_room_messages (room_id, created_at_ms),
  KEY idx_conversation_messages (conversation_id, created_at_ms),
  KEY idx_sender_messages (sender_id, created_at_ms),
  CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_messages_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
  CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
  CONSTRAINT chk_message_target CHECK (
    (room_id IS NOT NULL AND conversation_id IS NULL) OR
    (room_id IS NULL AND conversation_id IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO rooms (id, name, description, icon_name) VALUES
  ('general', 'گفتگوی عمومی / General', 'بحث و گفتگو پیرامون موضوعات مختلف', 'forum'),
  ('tech', 'توسعه و فناوری / Tech Talk', 'برنامه‌نویسی اندروید، کاتلین و جت‌پک کامپوز', 'code'),
  ('design', 'طراحی و رابط کاربری / UI & UX', 'اصول طراحی، رنگ‌ها و تجربه کاربری مدرن', 'palette');


-- Social feed: text posts, per-user reactions/bookmarks, comments and expiring stories.
CREATE TABLE IF NOT EXISTS social_posts (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  author_id VARCHAR(64) NOT NULL,
  content VARCHAR(10000) NOT NULL,
  tags VARCHAR(500) NOT NULL DEFAULT '',
  category VARCHAR(16) NOT NULL DEFAULT 'tech',
  created_at_ms BIGINT NOT NULL,
  KEY idx_social_posts_created (created_at_ms, id),
  KEY idx_social_posts_author (author_id, created_at_ms),
  CONSTRAINT fk_social_posts_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS post_likes (
  post_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  created_at_ms BIGINT NOT NULL,
  PRIMARY KEY (post_id, user_id),
  KEY idx_post_likes_user (user_id, created_at_ms),
  CONSTRAINT fk_post_likes_post FOREIGN KEY (post_id) REFERENCES social_posts(id) ON DELETE CASCADE,
  CONSTRAINT fk_post_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS post_bookmarks (
  post_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  created_at_ms BIGINT NOT NULL,
  PRIMARY KEY (post_id, user_id),
  KEY idx_post_bookmarks_user (user_id, created_at_ms),
  CONSTRAINT fk_post_bookmarks_post FOREIGN KEY (post_id) REFERENCES social_posts(id) ON DELETE CASCADE,
  CONSTRAINT fk_post_bookmarks_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS post_comments (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  post_id VARCHAR(64) NOT NULL,
  author_id VARCHAR(64) NOT NULL,
  content VARCHAR(3000) NOT NULL,
  created_at_ms BIGINT NOT NULL,
  KEY idx_post_comments_post (post_id, created_at_ms),
  KEY idx_post_comments_author (author_id, created_at_ms),
  CONSTRAINT fk_post_comments_post FOREIGN KEY (post_id) REFERENCES social_posts(id) ON DELETE CASCADE,
  CONSTRAINT fk_post_comments_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_stories (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  author_id VARCHAR(64) NOT NULL,
  title VARCHAR(500) NOT NULL,
  created_at_ms BIGINT NOT NULL,
  expires_at_ms BIGINT NOT NULL,
  KEY idx_user_stories_expiry (expires_at_ms, created_at_ms),
  CONSTRAINT fk_user_stories_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS story_views (
  story_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  viewed_at_ms BIGINT NOT NULL,
  PRIMARY KEY (story_id, user_id),
  KEY idx_story_views_user (user_id, viewed_at_ms),
  CONSTRAINT fk_story_views_story FOREIGN KEY (story_id) REFERENCES user_stories(id) ON DELETE CASCADE,
  CONSTRAINT fk_story_views_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
