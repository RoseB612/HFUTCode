-- PostgreSQL migration for email/password user authentication.
-- Existing users receive the temporary password: SynCode123!

BEGIN;

CREATE TABLE IF NOT EXISTS tb_user (
    user_id bigint PRIMARY KEY,
    nick_name varchar(50),
    head_image varchar(200),
    sex smallint,
    phone varchar(20),
    email varchar(100),
    password varchar(60),
    wechat varchar(20),
    school_name varchar(50),
    major_name varchar(50),
    introduce varchar(500),
    status smallint NOT NULL DEFAULT 1,
    create_by bigint NOT NULL DEFAULT 1,
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by bigint,
    update_time timestamp
);

ALTER TABLE tb_user
    ADD COLUMN IF NOT EXISTS password varchar(60);

UPDATE tb_user
SET email = LOWER(BTRIM(email))
WHERE email IS NOT NULL;

UPDATE tb_user
SET email = CONCAT('legacy_', user_id, '@syncode.local')
WHERE email IS NULL OR email = '';

WITH duplicate_users AS (
    SELECT user_id,
           ROW_NUMBER() OVER (PARTITION BY email ORDER BY user_id) AS duplicate_number
    FROM tb_user
)
UPDATE tb_user
SET email = CONCAT('legacy_', tb_user.user_id, '@syncode.local')
FROM duplicate_users
WHERE tb_user.user_id = duplicate_users.user_id
  AND duplicate_users.duplicate_number > 1;

UPDATE tb_user
SET password = '$2b$10$uWcGB3sflUauVAsYeEuDcuHLr7xYl5io/utC/yq.Rr591i0J6m2oa'
WHERE password IS NULL OR password = '';

ALTER TABLE tb_user
    ALTER COLUMN email SET NOT NULL,
    ALTER COLUMN password SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_email ON tb_user (email);

ALTER TABLE tb_user
    DROP COLUMN IF EXISTS code;

COMMIT;
