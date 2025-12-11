-- Migration: unify to `users` table, rewire FKs, cleanup schema
-- MySQL only. Review before running in production. Make a DB backup first!

DELIMITER $$
CREATE PROCEDURE migrate_users_cleanup()
BEGIN
  DECLARE v_db VARCHAR(128);
  DECLARE v_exists_user INT DEFAULT 0;

  -- Variables for iterating constraints referencing `user`
  DECLARE v_constraint_name VARCHAR(128);
  DECLARE v_table_name VARCHAR(128);
  DECLARE v_delete_rule VARCHAR(16);
  DECLARE v_update_rule VARCHAR(16);
  DECLARE done INT DEFAULT 0;

  DECLARE cur CURSOR FOR
    SELECT rc.CONSTRAINT_NAME, rc.TABLE_NAME, rc.DELETE_RULE, rc.UPDATE_RULE
    FROM information_schema.REFERENTIAL_CONSTRAINTS rc
    WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
      AND rc.REFERENCED_TABLE_NAME = 'user';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  SET v_db = DATABASE();

  -- Safety: disable FK checks during migration
  SET @old_fk_checks := @@FOREIGN_KEY_CHECKS;
  SET FOREIGN_KEY_CHECKS = 0;

  -- 0) Remove duplicate unique index on chat_memberships if present
  IF EXISTS (
    SELECT 1 FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = v_db AND s.TABLE_NAME = 'chat_memberships' AND s.INDEX_NAME = 'UK5vf9q12htnnl9t50329xno550'
  ) THEN
    SET @sql := 'ALTER TABLE `chat_memberships` DROP INDEX `UK5vf9q12htnnl9t50329xno550`';
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;

  -- 1) Align users.user_type default with application ('farmer')
  IF EXISTS (
    SELECT 1 FROM information_schema.COLUMNS c
    WHERE c.TABLE_SCHEMA = v_db AND c.TABLE_NAME = 'users' AND c.COLUMN_NAME = 'user_type' AND (c.COLUMN_DEFAULT IS NULL OR c.COLUMN_DEFAULT <> 'farmer')
  ) THEN
    SET @sql := 'ALTER TABLE `users` MODIFY COLUMN `user_type` varchar(32) NULL DEFAULT ''farmer''';
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;

  -- 2) Ensure detect_results.user_id FK uses ON DELETE CASCADE
  IF EXISTS (
    SELECT 1 FROM information_schema.KEY_COLUMN_USAGE k
    JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
      ON rc.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND rc.CONSTRAINT_NAME = k.CONSTRAINT_NAME
    WHERE k.TABLE_SCHEMA = v_db AND k.TABLE_NAME = 'detect_results' AND k.COLUMN_NAME = 'user_id' AND rc.REFERENCED_TABLE_NAME = 'users'
  ) THEN
    -- Find the FK name
    SELECT rc.CONSTRAINT_NAME INTO @dr_fk_name
    FROM information_schema.KEY_COLUMN_USAGE k
    JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
      ON rc.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND rc.CONSTRAINT_NAME = k.CONSTRAINT_NAME
    WHERE k.TABLE_SCHEMA = v_db AND k.TABLE_NAME = 'detect_results' AND k.COLUMN_NAME = 'user_id' AND rc.REFERENCED_TABLE_NAME = 'users'
    LIMIT 1;

    -- Drop and recreate with CASCADE if needed
    SELECT rc.DELETE_RULE INTO @dr_delete_rule
    FROM information_schema.REFERENTIAL_CONSTRAINTS rc
    WHERE rc.CONSTRAINT_SCHEMA = v_db AND rc.CONSTRAINT_NAME = @dr_fk_name;

    IF @dr_delete_rule <> 'CASCADE' THEN
      SET @sql := CONCAT('ALTER TABLE `detect_results` DROP FOREIGN KEY `', @dr_fk_name, '`');
      PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

      SET @sql := CONCAT(
        'ALTER TABLE `detect_results` ADD CONSTRAINT `', @dr_fk_name,
        '` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE RESTRICT'
      );
      PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
  END IF;

  -- 3) Ensure user_friendships FKs use ON DELETE CASCADE
  -- Drop and recreate both FKs referencing users
  -- Drop all FKs referencing users for this table
  SET @drop_fk_sql := (
    SELECT GROUP_CONCAT(CONCAT('ALTER TABLE `user_friendships` DROP FOREIGN KEY `', rc.CONSTRAINT_NAME, '`') SEPARATOR '; ')
    FROM information_schema.REFERENTIAL_CONSTRAINTS rc
    WHERE rc.CONSTRAINT_SCHEMA = v_db AND rc.TABLE_NAME = 'user_friendships' AND rc.REFERENCED_TABLE_NAME = 'users'
  );
  IF @drop_fk_sql IS NOT NULL AND LENGTH(@drop_fk_sql) > 0 THEN
    SET @sql := @drop_fk_sql; PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
  -- Recreate with CASCADE for (user_id) and (friend_id) if columns exist
  IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'user_friendships' AND COLUMN_NAME = 'user_id') THEN
    SET @sql := 'ALTER TABLE `user_friendships` ADD CONSTRAINT `fk_user_friendships_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE RESTRICT';
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'user_friendships' AND COLUMN_NAME = 'friend_id') THEN
    SET @sql := 'ALTER TABLE `user_friendships` ADD CONSTRAINT `fk_user_friendships_friend` FOREIGN KEY (`friend_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE RESTRICT';
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;

  -- 4) Rewire any FKs referencing legacy `user` table to `users`
  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO v_constraint_name, v_table_name, v_delete_rule, v_update_rule;
    IF done = 1 THEN LEAVE read_loop; END IF;

    -- Child/ref column lists
    SELECT GROUP_CONCAT(k.COLUMN_NAME ORDER BY k.ORDINAL_POSITION SEPARATOR '`, `') INTO @child_cols
    FROM information_schema.KEY_COLUMN_USAGE k
    WHERE k.CONSTRAINT_SCHEMA = v_db AND k.CONSTRAINT_NAME = v_constraint_name AND k.TABLE_NAME = v_table_name;

    SELECT GROUP_CONCAT(k.REFERENCED_COLUMN_NAME ORDER BY k.ORDINAL_POSITION SEPARATOR '`, `') INTO @ref_cols
    FROM information_schema.KEY_COLUMN_USAGE k
    WHERE k.CONSTRAINT_SCHEMA = v_db AND k.CONSTRAINT_NAME = v_constraint_name AND k.TABLE_NAME = v_table_name;

    -- Drop old FK
    SET @sql := CONCAT('ALTER TABLE `', v_table_name, '` DROP FOREIGN KEY `', v_constraint_name, '`');
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

    -- Add new FK referencing `users` with same delete/update rules
    SET @sql := CONCAT(
      'ALTER TABLE `', v_table_name, '` ADD CONSTRAINT `', v_constraint_name,
      '` FOREIGN KEY (`', @child_cols, '`) REFERENCES `users`(`', @ref_cols, '`)',
      ' ON DELETE ', v_delete_rule, ' ON UPDATE ', v_update_rule
    );
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE cur;

  -- 5) If legacy `user` table exists: backup and drop
  SELECT COUNT(*) INTO v_exists_user
  FROM information_schema.TABLES t
  WHERE t.TABLE_SCHEMA = v_db AND t.TABLE_NAME = 'user';

  IF v_exists_user > 0 THEN
    -- Backup
    SET @sql := 'CREATE TABLE IF NOT EXISTS `users_legacy_backup` AS SELECT * FROM `user`';
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

    -- Drop
    SET @sql := 'DROP TABLE `user`';
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;

  -- Restore FK checks
  SET FOREIGN_KEY_CHECKS = @old_fk_checks;
END $$
DELIMITER ;

-- Usage:
-- CALL migrate_users_cleanup();
-- DROP PROCEDURE IF EXISTS migrate_users_cleanup;
