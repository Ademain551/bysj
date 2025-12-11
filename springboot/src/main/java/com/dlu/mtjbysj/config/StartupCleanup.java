package com.dlu.mtjbysj.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StartupCleanup implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public StartupCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int deleted = jdbc.update("DELETE FROM detect_results WHERE user_id NOT IN (SELECT id FROM users)");
            if (deleted > 0) {
                log.warn("清理无效检测记录：删除 {} 条孤儿记录(user_id 不存在)", deleted);
            } else {
                log.info("检测记录外键检查通过，无需清理");
            }
        } catch (Exception e) {
            log.error("启动清理检测记录失败: {}", e.getMessage(), e);
        }
    }
}