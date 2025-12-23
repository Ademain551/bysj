package com.dlu.mtjbysj.admin;

import com.dlu.mtjbysj.user.User;
import com.dlu.mtjbysj.user.UserRepository;
import com.dlu.mtjbysj.detect.DetectResult;
import com.dlu.mtjbysj.detect.DetectResultRepository;
import com.dlu.mtjbysj.detect.DetectErrorFeedback;
import com.dlu.mtjbysj.detect.DetectErrorFeedbackRepository;
import com.dlu.mtjbysj.knowledge.Bhxx;
import com.dlu.mtjbysj.knowledge.BhxxRepository;
import com.dlu.mtjbysj.knowledge.Fzwp;
import com.dlu.mtjbysj.knowledge.FzwpRepository;
import com.dlu.mtjbysj.announce.Announcement;
import com.dlu.mtjbysj.announce.AnnouncementRepository;
import com.dlu.mtjbysj.chat.ChatService;
import com.dlu.mtjbysj.guide.GuideArticle;
import com.dlu.mtjbysj.guide.GuideArticleRepository;
import com.dlu.mtjbysj.guide.GuideArticleComment;
import com.dlu.mtjbysj.guide.GuideArticleCommentRepository;
import com.dlu.mtjbysj.guide.GuideArticleFavoriteRepository;
import com.dlu.mtjbysj.guide.GuideArticleRecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin
@SuppressWarnings("null")
public class AdminController {
    private final UserRepository userRepository;
    private final DetectResultRepository detectRepository;
    private final DetectErrorFeedbackRepository detectErrorFeedbackRepository;
    private final AnnouncementRepository announcementRepository;
    private final BhxxRepository bhxxRepository;
    private final FzwpRepository fzwpRepository;
    private final GuideArticleRepository guideArticleRepository;
    private final GuideArticleCommentRepository guideArticleCommentRepository;
    private final GuideArticleFavoriteRepository guideArticleFavoriteRepository;
    private final GuideArticleRecommendationRepository guideArticleRecommendationRepository;
    private final ChatService chatService;
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${ml.dataset.root-dir:../pymodel/data/PlantVillage}")
    private String datasetRootDir;

    @Value("${ml.train.working-dir:../pymodel}")
    private String trainWorkingDir;

    @Value("${ml.train.command:python train.py --task disease}")
    private String trainCommand;

    private boolean tableExists(String tableName) {
        try {
            Long cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                    Long.class, tableName);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // --- User management ---
    @GetMapping("/users")
    public ResponseEntity<?> listUsers() {
        List<User> all = userRepository.findAll();
        List<Map<String,Object>> resp = new ArrayList<>();
        for (User u : all) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("email", u.getEmail());
            m.put("avatarUrl", u.getAvatarUrl());
            m.put("createdAt", u.getCreatedAt());
            m.put("enabled", u.isEnabled());
            m.put("role", u.getRole());
            m.put("userType", u.getUserType());
            resp.add(m);
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> payload) {
        String username = (String) payload.get("username");
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名不能为空"));
        }
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名已存在"));
        }
        
        User user = User.builder()
                .username(username.trim())
                .passwordHash(passwordEncoder.encode((String) payload.getOrDefault("password", "123456")))
                .nickname((String) payload.getOrDefault("nickname", ""))
                .email((String) payload.getOrDefault("email", null))
                .role((String) payload.getOrDefault("role", "user"))
                .userType((String) payload.getOrDefault("userType", "farmer"))
                .enabled((Boolean) payload.getOrDefault("enabled", true))
                .createdAt(LocalDateTime.now())
                .build();
        
        userRepository.save(user);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        
        User user = opt.get();
        if (payload.containsKey("nickname")) user.setNickname((String) payload.get("nickname"));
        if (payload.containsKey("email")) user.setEmail((String) payload.get("email"));
        if (payload.containsKey("role")) user.setRole((String) payload.get("role"));
        if (payload.containsKey("userType")) user.setUserType((String) payload.get("userType"));
        if (payload.containsKey("enabled")) user.setEnabled((Boolean) payload.get("enabled"));
        if (payload.containsKey("password")) {
            String newPassword = (String) payload.get("password");
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                user.setPasswordHash(passwordEncoder.encode(newPassword));
            }
        }
        
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/{id}/enabled")
    public ResponseEntity<?> setUserEnabled(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        User u = opt.get();
        Object v = body.get("enabled");
        boolean enabled = (v instanceof Boolean) ? (Boolean)v : true;
        u.setEnabled(enabled);
        userRepository.save(u);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) return ResponseEntity.notFound().build();
        try {
            // 先删除该用户的所有关联数据，再删除用户，避免外键约束错误
            // 聊天相关
            if (tableExists("chat_messages")) {
                jdbcTemplate.update("DELETE FROM chat_messages WHERE sender_id = ?", id);
            }
            if (tableExists("chat_memberships")) {
                jdbcTemplate.update("DELETE FROM chat_memberships WHERE user_id = ?", id);
            }
            if (tableExists("user_friendships")) {
                jdbcTemplate.update("DELETE FROM user_friendships WHERE user_id = ? OR friend_id = ?", id, id);
            }
            // 检测相关
            if (tableExists("detect_error_feedbacks")) {
                jdbcTemplate.update("DELETE FROM detect_error_feedbacks WHERE farmer_id = ? OR expert_id = ?", id, id);
                if (tableExists("detect_results")) {
                    jdbcTemplate.update("DELETE FROM detect_error_feedbacks WHERE detect_result_id IN (SELECT id FROM detect_results WHERE user_id = ?)", id);
                }
            }
            if (tableExists("detect_results")) {
                jdbcTemplate.update("DELETE FROM detect_results WHERE user_id = ?", id);
            }
            // 技术指导相关
            if (tableExists("guide_article_favorites")) {
                jdbcTemplate.update("DELETE FROM guide_article_favorites WHERE user_id = ?", id);
            }
            if (tableExists("guide_article_comments")) {
                jdbcTemplate.update("DELETE FROM guide_article_comments WHERE author_id = ?", id);
            }
            // 商店相关
            if (tableExists("shop_order_items")) {
                // 先删除订单项，因为它们有对订单的外键引用
                jdbcTemplate.update("DELETE FROM shop_order_items WHERE order_id IN (SELECT id FROM shop_orders WHERE user_id = ?)", id);
            }
            if (tableExists("shop_orders")) {
                jdbcTemplate.update("DELETE FROM shop_orders WHERE user_id = ?", id);
            }
            if (tableExists("favorite_items")) {
                jdbcTemplate.update("DELETE FROM favorite_items WHERE user_id = ?", id);
            }
            if (tableExists("cart_items")) {
                jdbcTemplate.update("DELETE FROM cart_items WHERE user_id = ?", id);
            }
            // 公告相关
            if (tableExists("announcement_reads")) {
                jdbcTemplate.update("DELETE FROM announcement_reads WHERE user_id = ?", id);
            }
            // 通知相关
            if (tableExists("notifications")) {
                jdbcTemplate.update("DELETE FROM notifications WHERE recipient_id = ?", id);
            }
            // 系统相关
            if (tableExists("sessions")) {
                jdbcTemplate.update("DELETE FROM sessions WHERE user_id = ?", id);
            }
            if (tableExists("oauth2_authorized_client")) {
                jdbcTemplate.update("DELETE FROM oauth2_authorized_client WHERE principal_name = ?", id);
            }
            // 最后删除用户
            userRepository.deleteById(id);
            return ResponseEntity.ok().build();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 捕获外键约束错误并返回更详细的信息
            String errorMsg = "删除失败：存在关联数据";
            if (e.getMessage() != null) {
                errorMsg += ": " + e.getMessage().substring(0, Math.min(200, e.getMessage().length()));
            }
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", errorMsg));
        } catch (Exception e) {
            // 捕获其他错误并返回详细信息
            String errorMsg = "删除失败：" + e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", errorMsg, "exception", e.getClass().getSimpleName()));
        }
    }


    // --- Detect logs ---
    @GetMapping("/detect/logs")
    public ResponseEntity<?> detectLogs(@RequestParam(value="page", required=false) Integer page,
                                        @RequestParam(value="size", required=false) Integer size,
                                        @RequestParam(value="q", required=false) String q) {
        int pg = (page == null || page < 0) ? 0 : page;
        int sz = (size == null || size <= 0 || size > 50) ? 10 : size;
        Page<DetectResult> p = (q != null && !q.isBlank())
                ? detectRepository.findByModelLabelContainingIgnoreCaseOrderByCreatedAtDesc(q, PageRequest.of(pg, sz))
                : detectRepository.findAll(PageRequest.of(pg, sz));
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("page", pg);
        out.put("size", sz);
        out.put("total", p.getTotalElements());
        out.put("items", p.getContent());
        return ResponseEntity.ok(out);
    }

    @DeleteMapping("/detect/logs/{id}")
    @Transactional
    public ResponseEntity<?> deleteDetectLog(@PathVariable Long id) {
        Optional<DetectResult> opt = detectRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "记录不存在"));
        }
        Long detectId = opt.get().getId();
        // 先删除关联的纠错反馈，再删除识别记录，避免外键约束错误
        detectErrorFeedbackRepository.deleteByDetectResultIds(java.util.Collections.singletonList(detectId));
        detectRepository.deleteById(detectId);
        return ResponseEntity.ok(Map.of("deleted", 1, "id", detectId));
    }

    // --- Announcements ---
    @GetMapping("/announcements")
    public ResponseEntity<?> listAnnouncements() {
        return ResponseEntity.ok(announcementRepository.findAll());
    }

    @PostMapping("/announcements")
    public ResponseEntity<?> createAnnouncement(@RequestBody Announcement a) {
        a.setId(null);
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(a.getCreatedAt());
        Announcement saved = announcementRepository.save(a);
        if (saved.isPublished()) {
            chatService.sendSystemNotificationToAllUsers(saved.getTitle(), saved.getContent());
        }
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/announcements/{id}")
    public ResponseEntity<?> updateAnnouncement(@PathVariable Long id, @RequestBody Announcement payload) {
        Optional<Announcement> opt = announcementRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Announcement a = opt.get();
        boolean wasPublished = a.isPublished();
        if (payload.getTitle() != null) a.setTitle(payload.getTitle());
        if (payload.getContent() != null) a.setContent(payload.getContent());
        a.setPublished(payload.isPublished());
        a.setUpdatedAt(LocalDateTime.now());
        announcementRepository.save(a);
        if (!wasPublished && a.isPublished()) {
            chatService.sendSystemNotificationToAllUsers(a.getTitle(), a.getContent());
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/announcements/{id}")
    public ResponseEntity<?> deleteAnnouncement(@PathVariable Long id) {
        if (!announcementRepository.existsById(id)) return ResponseEntity.notFound().build();
        announcementRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // --- Model Management ---
    @GetMapping("/model/status")
    public ResponseEntity<?> getModelStatus() {
        try {
            // 检查 FastAPI 服务状态
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://127.0.0.1:8001/health"))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();
            
            java.net.http.HttpResponse<String> response = client.send(request, 
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("fastapiAvailable", response.statusCode() == 200);
            status.put("statusCode", response.statusCode());
            
            // 可以添加更多模型信息
            status.put("modelPath", "pymodel/saved_models/best_model.pth");
            status.put("lastTrainTime", "N/A"); // 可以从文件系统获取
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("fastapiAvailable", false);
            status.put("error", e.getMessage());
            return ResponseEntity.ok(status);
        }
    }

    @PostMapping("/model/train")
    public ResponseEntity<?> triggerTraining(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String osName = System.getProperty("os.name");
            java.util.List<String> cmd = new ArrayList<>();
            if (osName != null && osName.toLowerCase(java.util.Locale.ROOT).contains("win")) {
                cmd.add("cmd");
                cmd.add("/c");
                cmd.add(trainCommand);
            } else {
                cmd.add("bash");
                cmd.add("-lc");
                cmd.add(trainCommand);
            }

            java.io.File workDir = new java.io.File(trainWorkingDir).getAbsoluteFile();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workDir);
            pb.redirectErrorStream(true);
            pb.start();

            result.put("message", "模型训练进程已启动");
            result.put("status", "started");
            result.put("workingDir", workDir.getPath());
            result.put("command", trainCommand);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("message", "启动模型训练失败");
            result.put("status", "failed");
            result.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    @GetMapping("/model/errors")
    public ResponseEntity<?> listModelErrors() {
        List<DetectErrorFeedback> list = detectErrorFeedbackRepository
                .findByStatusAndAddedToDatasetFalseOrderByCreatedAtDesc("CONFIRMED_WRONG");
        List<Map<String, Object>> items = new ArrayList<>();
        for (DetectErrorFeedback f : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("status", f.getStatus());
            m.put("correctPlant", f.getCorrectPlant());
            m.put("correctDisease", f.getCorrectDisease());
            m.put("correctModelLabel", f.getCorrectModelLabel());
            m.put("farmerComment", f.getFarmerComment());
            m.put("expertComment", f.getExpertComment());
            m.put("replyMessage", f.getReplyMessage());
            m.put("createdAt", f.getCreatedAt());
            m.put("updatedAt", f.getUpdatedAt());
            DetectResult r = f.getDetectResult();
            if (r != null) {
                Map<String, Object> dr = new LinkedHashMap<>();
                dr.put("id", r.getId());
                dr.put("predictedClass", r.getPredictedClass());
                dr.put("confidence", r.getConfidence());
                dr.put("imageUrl", r.getImageUrl());
                dr.put("createdAt", r.getCreatedAt());
                m.put("detectResult", dr);
            }
            User farmer = f.getFarmer();
            if (farmer != null) {
                Map<String, Object> fa = new LinkedHashMap<>();
                fa.put("username", farmer.getUsername());
                fa.put("nickname", farmer.getNickname());
                fa.put("avatarUrl", farmer.getAvatarUrl());
                m.put("farmer", fa);
            }
            User expert = f.getExpert();
            if (expert != null) {
                Map<String, Object> ex = new LinkedHashMap<>();
                ex.put("username", expert.getUsername());
                ex.put("nickname", expert.getNickname());
                ex.put("avatarUrl", expert.getAvatarUrl());
                m.put("expert", ex);
            }
            items.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", items.size());
        out.put("items", items);
        return ResponseEntity.ok(out);
    }

    @PostMapping("/model/errors/{id}/add-to-dataset")
    public ResponseEntity<?> addErrorSampleToDataset(@PathVariable Long id,
                                                     @RequestBody(required = false) Map<String, Object> payload) {
        Optional<DetectErrorFeedback> opt = detectErrorFeedbackRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "反馈不存在"));
        }
        DetectErrorFeedback f = opt.get();
        if (!"CONFIRMED_WRONG".equals(f.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "当前状态不可加入数据集"));
        }
        if (f.isAddedToDataset()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "该样本已加入数据集"));
        }
        DetectResult r = f.getDetectResult();
        if (r == null || r.getImageUrl() == null || r.getImageUrl().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "缺少原始图片信息"));
        }
        String label = f.getCorrectModelLabel();
        if (label == null || label.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "缺少正确模型类别"));
        }
        try {
            String imageUrl = r.getImageUrl();
            String rel = imageUrl.startsWith("/") ? imageUrl.substring(1) : imageUrl;
            Path src = Path.of(rel);
            if (!Files.exists(src)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "原始图片文件不存在"));
            }
            Path root = Path.of(datasetRootDir).toAbsolutePath().normalize();
            Path targetDir = root.resolve(label);
            Files.createDirectories(targetDir);
            String srcName = src.getFileName().toString();
            String ext = "";
            int idx = srcName.lastIndexOf('.');
            if (idx != -1) {
                ext = srcName.substring(idx);
            } else {
                ext = ".jpg";
            }
            String filename = "feedback_" + id + "_" + System.currentTimeMillis() + ext;
            Path target = targetDir.resolve(filename);
            Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
            boolean retrainNow = false;
            if (payload != null && payload.get("retrainNow") instanceof Boolean) {
                retrainNow = (Boolean) payload.get("retrainNow");
            }
            f.setAddedToDataset(true);
            f.setRetrainRequired(true);
            f.setStatus("DATASET_ADDED");
            if (retrainNow) {
                f.setRetrainTriggered(true);
            }
            detectErrorFeedbackRepository.save(f);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("id", f.getId());
            resp.put("status", f.getStatus());
            resp.put("addedToDataset", f.isAddedToDataset());
            resp.put("retrainRequired", f.isRetrainRequired());
            resp.put("retrainTriggered", f.isRetrainTriggered());
            resp.put("savedPath", target.toString());
            return ResponseEntity.ok(resp);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "复制图片到数据集失败: " + e.getMessage()));
        }
    }

    @PostMapping("/model/errors/{id}/ignore")
    public ResponseEntity<?> ignoreErrorSample(@PathVariable Long id) {
        Optional<DetectErrorFeedback> opt = detectErrorFeedbackRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "反馈不存在"));
        }
        DetectErrorFeedback f = opt.get();
        if (!"CONFIRMED_WRONG".equals(f.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "当前状态不可忽略"));
        }
        if (f.isAddedToDataset()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "该样本已加入数据集，无法忽略"));
        }
        f.setStatus("IGNORED");
        f.setRetrainRequired(false);
        detectErrorFeedbackRepository.save(f);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", f.getId());
        resp.put("status", f.getStatus());
        return ResponseEntity.ok(resp);
    }

    // --- Database Management ---
    @GetMapping("/database/tables")
    public ResponseEntity<?> listTables() {
        try {
            String sql = "SELECT TABLE_NAME, TABLE_ROWS, DATA_LENGTH, CREATE_TIME " +
                        "FROM information_schema.TABLES " +
                        "WHERE TABLE_SCHEMA = DATABASE() " +
                        "ORDER BY TABLE_NAME";
            List<Map<String, Object>> tables = jdbcTemplate.queryForList(sql);
            return ResponseEntity.ok(tables);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/database/tables/{tableName}/info")
    public ResponseEntity<?> getTableInfo(@PathVariable String tableName) {
        try {
            String sql = "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT " +
                        "FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? " +
                        "ORDER BY ORDINAL_POSITION";
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql, tableName);
            return ResponseEntity.ok(columns);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/database/tables/{tableName}/count")
    public ResponseEntity<?> getTableCount(@PathVariable String tableName) {
        try {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
            return ResponseEntity.ok(Map.of("tableName", tableName, "count", count));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/database/tables/{tableName}/clear")
    public ResponseEntity<?> clearTable(@PathVariable String tableName) {
        try {
            // 安全限制：不允许删除系统关键表
            Set<String> protectedTables = Set.of("users");
            if (protectedTables.contains(tableName.toLowerCase())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "不允许清空受保护的表"));
            }
            
            jdbcTemplate.execute("DELETE FROM " + tableName);
            return ResponseEntity.ok(Map.of("message", "表已清空"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // --- Bhxx Management (植物病害分布情况) ---
    @GetMapping("/bhxx")
    public ResponseEntity<?> listBhxx(@RequestParam(value="page", required=false) Integer page,
                                      @RequestParam(value="size", required=false) Integer size) {
        int pg = (page == null || page < 0) ? 0 : page;
        int sz = (size == null || size <= 0 || size > 50) ? 10 : size;
        Page<Bhxx> p = bhxxRepository.findAll(PageRequest.of(pg, sz));
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("page", pg);
        out.put("size", sz);
        out.put("total", p.getTotalElements());
        out.put("items", p.getContent());
        return ResponseEntity.ok(out);
    }

    @PostMapping("/bhxx")
    public ResponseEntity<?> createBhxx(@RequestBody Bhxx bhxx) {
        bhxx.setId(null);
        bhxxRepository.save(bhxx);
        return ResponseEntity.ok(bhxx);
    }

    @PutMapping("/bhxx/{id}")
    public ResponseEntity<?> updateBhxx(@PathVariable Long id, @RequestBody Bhxx payload) {
        Optional<Bhxx> opt = bhxxRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Bhxx bhxx = opt.get();
        if (payload.getPlantName() != null) bhxx.setPlantName(payload.getPlantName());
        if (payload.getDiseaseName() != null) bhxx.setDiseaseName(payload.getDiseaseName());
        if (payload.getDistributionArea() != null) bhxx.setDistributionArea(payload.getDistributionArea());
        if (payload.getDistributionTime() != null) bhxx.setDistributionTime(payload.getDistributionTime());
        if (payload.getPreventionMethod() != null) bhxx.setPreventionMethod(payload.getPreventionMethod());
        bhxxRepository.save(bhxx);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/bhxx/{id}")
    public ResponseEntity<?> deleteBhxx(@PathVariable Long id) {
        if (!bhxxRepository.existsById(id)) return ResponseEntity.notFound().build();
        bhxxRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // --- Fzwp Management (防治物品) ---
    @GetMapping("/fzwp")
    public ResponseEntity<?> listFzwp(@RequestParam(value = "page", required = false) Integer page,
                                      @RequestParam(value = "size", required = false) Integer size,
                                      @RequestParam(value = "q", required = false) String q) {
        int pg = (page == null || page < 0) ? 0 : page;
        int sz = (size == null || size <= 0 || size > 50) ? 10 : size;
        Page<Fzwp> p = (q != null && !q.isBlank())
                ? fzwpRepository.findByItemNameContainingIgnoreCaseOrTargetDiseaseContainingIgnoreCase(q, q, PageRequest.of(pg, sz))
                : fzwpRepository.findAll(PageRequest.of(pg, sz));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("page", pg);
        out.put("size", sz);
        out.put("total", p.getTotalElements());
        out.put("items", p.getContent());
        return ResponseEntity.ok(out);
    }

    @PostMapping("/fzwp")
    public ResponseEntity<?> createFzwp(@RequestBody Fzwp payload) {
        payload.setId(null);
        if (payload.getListedAt() == null) {
            payload.setListedAt(LocalDateTime.now());
        }
        Fzwp saved = fzwpRepository.save(payload);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/fzwp/{id}")
    public ResponseEntity<?> updateFzwp(@PathVariable Long id, @RequestBody Fzwp payload) {
        Optional<Fzwp> opt = fzwpRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Fzwp f = opt.get();
        if (payload.getItemName() != null) f.setItemName(payload.getItemName());
        if (payload.getPrice() != null) f.setPrice(payload.getPrice());
        if (payload.getListedAt() != null) f.setListedAt(payload.getListedAt());
        if (payload.getImageUrl() != null) f.setImageUrl(payload.getImageUrl());
        if (payload.getPlantName() != null) f.setPlantName(payload.getPlantName());
        if (payload.getTargetDisease() != null) f.setTargetDisease(payload.getTargetDisease());
        fzwpRepository.save(f);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/fzwp/{id}")
    @Transactional
    public ResponseEntity<?> deleteFzwp(@PathVariable Long id) {
        if (!fzwpRepository.existsById(id)) return ResponseEntity.notFound().build();
        try {
            // 先清理与该物品相关的业务数据，避免外键约束错误
            if (tableExists("cart_items")) {
                jdbcTemplate.update("DELETE FROM cart_items WHERE item_id = ?", id);
            }
            if (tableExists("favorite_items")) {
                jdbcTemplate.update("DELETE FROM favorite_items WHERE item_id = ?", id);
            }
            if (tableExists("shop_order_items")) {
                jdbcTemplate.update("DELETE FROM shop_order_items WHERE item_id = ?", id);
            }
            if (tableExists("guide_article_recommendations")) {
                jdbcTemplate.update("DELETE FROM guide_article_recommendations WHERE fzwp_id = ?", id);
            }

            fzwpRepository.deleteById(id);
            return ResponseEntity.ok().build();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "存在关联数据，无法删除该物品"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/fzwp/image")
    public ResponseEntity<?> uploadFzwpImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "请选择图片文件"));
        }
        try {
            String url = storeFzwpImage(file);
            return ResponseEntity.ok(Map.of("imageUrl", url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "图片保存失败"));
        }
    }

    private String storeFzwpImage(MultipartFile file) throws IOException {
        String ct = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(java.util.Locale.ROOT);
        boolean ok = ct.startsWith("image/");
        if (!ok) {
            String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(java.util.Locale.ROOT);
            ok = name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                    || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp")
                    || name.endsWith(".dng");
        }
        if (!ok) {
            throw new IllegalArgumentException("仅支持图片格式: jpg, jpeg, png, gif, webp, bmp, dng");
        }
        Path dir = Path.of("uploads", "fzwp");
        Files.createDirectories(dir);
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("fzwp");
        String ext = "";
        int idx = original.lastIndexOf('.');
        if (idx != -1) {
            ext = original.substring(idx).toLowerCase(java.util.Locale.ROOT);
        } else {
            if (ct.contains("jpeg")) ext = ".jpg";
            else if (ct.contains("png")) ext = ".png";
            else if (ct.contains("gif")) ext = ".gif";
            else if (ct.contains("webp")) ext = ".webp";
            else if (ct.contains("bmp")) ext = ".bmp";
            else if (ct.contains("dng")) ext = ".dng";
        }
        String filename = java.util.UUID.randomUUID().toString().replaceAll("-", "") + (ext.isEmpty() ? ".jpg" : ext);
        Path target = dir.resolve(filename);
        try (var in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return "/uploads/fzwp/" + filename;
    }

    // --- Guide Articles Management (技术指导文章与评论) ---

    @GetMapping("/guide/articles")
    public ResponseEntity<?> listGuideArticles(@RequestParam(value = "page", required = false) Integer page,
                                               @RequestParam(value = "size", required = false) Integer size,
                                               @RequestParam(value = "q", required = false) String q) {
        int pg = (page == null || page < 0) ? 0 : page;
        int sz = (size == null || size <= 0 || size > 50) ? 10 : size;
        Pageable pageable = PageRequest.of(pg, sz, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<GuideArticle> p;
        if (q != null && !q.isBlank()) {
            p = guideArticleRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(q, q, pageable);
        } else {
            p = guideArticleRepository.findAll(pageable);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("page", pg);
        out.put("size", sz);
        out.put("total", p.getTotalElements());
        List<Map<String, Object>> items = new ArrayList<>();
        for (GuideArticle a : p.getContent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("title", a.getTitle());
            m.put("content", a.getContent());
            m.put("coverImageUrl", a.getCoverImageUrl());
            m.put("createdAt", a.getCreatedAt());
            User author = a.getAuthor();
            if (author != null) {
                Map<String, Object> au = new LinkedHashMap<>();
                au.put("username", author.getUsername());
                au.put("nickname", author.getNickname());
                au.put("userType", author.getUserType());
                m.put("author", au);
            }
            m.put("imageUrls", parseImageUrls(a.getImageUrls()));
            long commentCount = guideArticleCommentRepository.countByArticleId(a.getId());
            m.put("commentCount", commentCount);
            items.add(m);
        }
        out.put("items", items);
        return ResponseEntity.ok(out);
    }

    @PutMapping("/guide/articles/{id}")
    public ResponseEntity<?> updateGuideArticle(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Optional<GuideArticle> opt = guideArticleRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "文章不存在"));
        }
        GuideArticle a = opt.get();
        Object t = payload.get("title");
        if (t instanceof String) {
            String title = ((String) t).trim();
            if (!title.isEmpty()) {
                a.setTitle(title);
            }
        }
        Object c = payload.get("content");
        if (c instanceof String) {
            String content = ((String) c).trim();
            if (!content.isEmpty()) {
                a.setContent(content);
            }
        }
        if (payload.containsKey("coverImageUrl")) {
            Object v = payload.get("coverImageUrl");
            if (v == null) {
                a.setCoverImageUrl(null);
            } else if (v instanceof String) {
                String url = ((String) v).trim();
                a.setCoverImageUrl(url.isEmpty() ? null : url);
            }
        }
        if (payload.containsKey("imageUrls")) {
            Object imgs = payload.get("imageUrls");
            List<String> list = new ArrayList<>();
            if (imgs instanceof List<?>) {
                for (Object o : (List<?>) imgs) {
                    if (o != null) {
                        String s = o.toString().trim();
                        if (!s.isEmpty()) {
                            list.add(s);
                        }
                    }
                }
            } else if (imgs instanceof String) {
                list = parseImageUrls(((String) imgs));
            }
            a.setImageUrls(serializeImageUrls(list));
        }
        guideArticleRepository.save(a);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/guide/articles/{id}")
    @Transactional
    public ResponseEntity<?> deleteGuideArticle(@PathVariable Long id) {
        if (!guideArticleRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "文章不存在"));
        }
        guideArticleCommentRepository.deleteAllByArticleId(id);
        guideArticleRecommendationRepository.deleteAllByArticleId(id);
        guideArticleFavoriteRepository.deleteAllByArticleId(id);
        guideArticleRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/guide/articles/{id}/comments")
    public ResponseEntity<?> listGuideComments(@PathVariable Long id) {
        if (!guideArticleRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "文章不存在"));
        }
        List<GuideArticleComment> comments = guideArticleCommentRepository.findByArticleIdOrderByCreatedAtAsc(id);
        List<Map<String, Object>> items = new ArrayList<>();
        for (GuideArticleComment c : comments) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("content", c.getContent());
            m.put("createdAt", c.getCreatedAt());
            User au = c.getAuthor();
            if (au != null) {
                Map<String, Object> auDto = new LinkedHashMap<>();
                auDto.put("username", au.getUsername());
                auDto.put("nickname", au.getNickname());
                auDto.put("userType", au.getUserType());
                m.put("author", auDto);
            }
            m.put("parentId", c.getParent() != null ? c.getParent().getId() : null);
            items.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", items.size());
        out.put("items", items);
        return ResponseEntity.ok(out);
    }

    @DeleteMapping("/guide/comments/{id}")
    public ResponseEntity<?> deleteGuideComment(@PathVariable Long id) {
        Optional<GuideArticleComment> opt = guideArticleCommentRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "评论不存在"));
        }
        guideArticleCommentRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    private List<String> parseImageUrls(String raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return Collections.emptyList();
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = value.split(",");
        List<String> list = new ArrayList<>();
        for (String part : parts) {
            String s = part.trim();
            if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
                s = s.substring(1, s.length() - 1);
            }
            if (!s.isEmpty()) {
                list.add(s);
            }
        }
        return list;
    }

    private String serializeImageUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        List<String> cleaned = new ArrayList<>();
        for (String s : urls) {
            if (s != null) {
                String v = s.trim();
                if (!v.isEmpty()) {
                    cleaned.add(v);
                }
            }
        }
        if (cleaned.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (String s : cleaned) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            String escaped = s.replace("\"", "\\\"");
            sb.append('"').append(escaped).append('"');
        }
        sb.append(']');
        return sb.toString();
    }
}