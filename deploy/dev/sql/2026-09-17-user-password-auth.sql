-- Replace email verification-code login with email/password authentication.
-- Existing development users receive the temporary password: SynCode123!

USE bitoj_dev;

UPDATE tb_user
SET email = LOWER(TRIM(email))
WHERE email IS NOT NULL;

-- Resolve missing legacy emails before applying the login-account constraint.
UPDATE tb_user
SET email = CONCAT('legacy_', user_id, '@syncode.local')
WHERE email IS NULL OR email = '';

-- Keep the oldest account when normalization reveals case-only duplicates.
-- Renamed legacy accounts can sign in with the temporary password below.
CREATE TEMPORARY TABLE duplicate_user_ids AS
SELECT DISTINCT duplicate_user.user_id
FROM tb_user duplicate_user
JOIN tb_user older_user
    ON duplicate_user.email = older_user.email
    AND duplicate_user.user_id > older_user.user_id;

UPDATE tb_user
JOIN duplicate_user_ids ON tb_user.user_id = duplicate_user_ids.user_id
SET tb_user.email = CONCAT('legacy_', tb_user.user_id, '@syncode.local');

DROP TEMPORARY TABLE duplicate_user_ids;

ALTER TABLE tb_user
    ADD COLUMN password char(60) NULL COMMENT 'bcrypt password' AFTER email;

UPDATE tb_user
SET password = '$2b$10$uWcGB3sflUauVAsYeEuDcuHLr7xYl5io/utC/yq.Rr591i0J6m2oa'
WHERE password IS NULL;

ALTER TABLE tb_user
    MODIFY COLUMN email varchar(100) NOT NULL COMMENT 'email login account',
    MODIFY COLUMN password char(60) NOT NULL COMMENT 'bcrypt password',
    ADD UNIQUE KEY uk_user_email (email),
    DROP COLUMN code;
