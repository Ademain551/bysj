package com.dlu.mtjbysj.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class StartupMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public StartupMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Map<String, Object> col = jdbc.queryForMap(
                    "SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'detect_results' AND column_name = 'disease_id'"
            );
            String nullable = String.valueOf(col.getOrDefault("IS_NULLABLE", "YES"));
            if ("NO".equalsIgnoreCase(nullable)) {
                log.warn("检测到 detect_results.disease_id 为 NOT NULL，尝试迁移为可空...");
                jdbc.execute("ALTER TABLE detect_results MODIFY COLUMN disease_id BIGINT NULL");
                log.info("已将 detect_results.disease_id 修改为可空");
            } else {
                log.info("detect_results.disease_id 已为可空，无需迁移");
            }
        } catch (Exception e) {
            log.error("启动迁移(allow null for disease_id)失败: {}", e.getMessage(), e);
        }

        // 兼容历史表结构：detect_results.username（若存在且为 NOT NULL）改为可空，并补齐缺失值
        try {
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'detect_results' AND column_name = 'username'",
                    Integer.class
            );
            if (cnt != null && cnt > 0) {
                Map<String, Object> usernameCol = jdbc.queryForMap(
                        "SELECT IS_NULLABLE, DATA_TYPE, IFNULL(CHARACTER_MAXIMUM_LENGTH, 0) AS LEN FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'detect_results' AND column_name = 'username'"
                );
                String uNullable = String.valueOf(usernameCol.getOrDefault("IS_NULLABLE", "YES"));
                String dataType = String.valueOf(usernameCol.getOrDefault("DATA_TYPE", "varchar"));
                int len = 0;
                try { len = Integer.parseInt(String.valueOf(usernameCol.get("LEN"))); } catch (Exception ignore) {}
                String typeDef = "varchar".equalsIgnoreCase(dataType) && len > 0 ? ("VARCHAR(" + len + ")") : "VARCHAR(128)";
                if ("NO".equalsIgnoreCase(uNullable)) {
                    log.warn("检测到 detect_results.username 为 NOT NULL，尝试迁移为可空...");
                    jdbc.execute("ALTER TABLE detect_results MODIFY COLUMN username " + typeDef + " NULL DEFAULT NULL");
                    log.info("已将 detect_results.username 修改为可空");
                } else {
                    log.info("detect_results.username 已为可空或不存在 NOT NULL 约束");
                }
                // 尝试用 users 表补齐缺失的 username 值（仅在列存在时）
                int updated = jdbc.update("UPDATE detect_results dr JOIN users u ON dr.user_id = u.id SET dr.username = u.username WHERE dr.username IS NULL AND u.username IS NOT NULL");
                if (updated > 0) {
                    log.info("同步 detect_results.username：填充 {} 条记录", updated);
                }
                
                // 为没有user_id但username为空的记录设置默认值
                int defaultRows = jdbc.update("UPDATE detect_results SET username = 'system' WHERE username IS NULL");
                if (defaultRows > 0) {
                    log.info("已为 {} 条记录设置默认用户名", defaultRows);
                }
            } else {
                log.info("detect_results.username 列不存在，无需兼容处理");
            }
        } catch (Exception e) {
            log.error("启动迁移(username 列兼容)失败: {}", e.getMessage(), e);
        }
    }
}