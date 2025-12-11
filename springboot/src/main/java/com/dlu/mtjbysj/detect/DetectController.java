package com.dlu.mtjbysj.detect;

import com.dlu.mtjbysj.knowledge.Bhxx;
import com.dlu.mtjbysj.knowledge.BhxxRepository;
import com.dlu.mtjbysj.knowledge.Fzwp;
import com.dlu.mtjbysj.knowledge.FzwpRepository;
import com.dlu.mtjbysj.user.User;
import com.dlu.mtjbysj.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.net.MalformedURLException;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

@RestController
@RequestMapping("/api/detect")
@RequiredArgsConstructor
@CrossOrigin
@Slf4j
@SuppressWarnings("null")
public class DetectController {
    private final DetectResultRepository repo;
    private final DetectErrorFeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final BhxxRepository bhxxRepository;
    private final FzwpRepository fzwpRepository;
    private final RestTemplate rest = new RestTemplate();

    @Value("${ml.fastapi.url:http://127.0.0.1:8001}")
    private String fastapiBase;

    // 可选：通过配置指定一个可用的中文字体文件路径，例如 C:/Windows/Fonts/simhei.ttf 或 项目内的 .ttf 文件
    @Value("${pdf.cnFont.path:}")
    private String cnFontPathConfig;

    @PostMapping
    public ResponseEntity<?> detect(@RequestParam("file") MultipartFile file,
                                    @RequestParam("username") String username) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("未选择图片");
        }
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body("缺少用户名");
        }
        Optional<User> userOpt = userRepository.findByUsername(username.trim());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("用户不存在");
        }
        String uploadsDir = "uploads";
        File dir = new File(uploadsDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("image.jpg");
        String ext = "";
        int idx = originalName.lastIndexOf('.');
        if (idx != -1) ext = originalName.substring(idx).toLowerCase();
        String filename = UUID.randomUUID().toString().replaceAll("-", "") + (ext.isEmpty() ? ".jpg" : ext);
        Path target = Path.of(uploadsDir, filename);
        
        // 确保uploads目录存在
        Files.createDirectories(target.getParent());
        
        // 安全地复制文件
        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        
        String imageUrl = "/" + uploadsDir + "/" + filename;

        // 转发到 FastAPI 获取真实模型推理
        log.info("Forwarding image '{}' ({} bytes) to FastAPI {}", originalName, file.getSize(), fastapiBase);
        Map<String, Object> fast = callFastApiPredict(file);
        log.info("FastAPI response: {}", fast);
        
        // 解析FastAPI响应：支持背景拦截和双模型分类两种格式
        String predicted;
        double confidence;
        List<Map<String, Object>> probabilities = new ArrayList<>();
        
        // 检查是否为背景无叶片拦截（有predictedClass字段且包含Background）
        if (fast.containsKey("predictedClass")) {
            predicted = String.valueOf(fast.get("predictedClass"));
            confidence = Double.parseDouble(String.valueOf(fast.getOrDefault("confidence", 0.0)));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> probs = (List<Map<String, Object>>) fast.getOrDefault("probabilities", Collections.emptyList());
            probabilities = probs;
        } else if (fast.containsKey("species") && fast.containsKey("disease")) {
            // 双模型格式：组合 species + "___" + disease
            @SuppressWarnings("unchecked")
            Map<String, Object> speciesMap = (Map<String, Object>) fast.get("species");
            @SuppressWarnings("unchecked")
            Map<String, Object> diseaseMap = (Map<String, Object>) fast.get("disease");
            
            String speciesName = String.valueOf(speciesMap.getOrDefault("predicted", "Unknown"));
            String diseaseName = String.valueOf(diseaseMap.getOrDefault("predicted", "healthy"));
            double speciesConf = Double.parseDouble(String.valueOf(speciesMap.getOrDefault("confidence", 0.0)));
            double diseaseConf = Double.parseDouble(String.valueOf(diseaseMap.getOrDefault("confidence", 0.0)));
            
            // 组合格式：Apple___healthy 或 Apple___Leaf_spot
            predicted = speciesName + "___" + diseaseName;
            // 置信度取两者平均值
            confidence = (speciesConf + diseaseConf) / 2.0;
            
            // 合并Top-5概率（组合species和disease）
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> speciesTop5 = (List<Map<String, Object>>) speciesMap.getOrDefault("top5", Collections.emptyList());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> diseaseTop5 = (List<Map<String, Object>>) diseaseMap.getOrDefault("top5", Collections.emptyList());
            
            // 创建组合概率列表（取前5个，按组合概率排序）
            for (Map<String, Object> sItem : speciesTop5) {
                for (Map<String, Object> dItem : diseaseTop5) {
                    String sClass = String.valueOf(sItem.get("class"));
                    String dClass = String.valueOf(dItem.get("class"));
                    double sProb = Double.parseDouble(String.valueOf(sItem.get("prob")));
                    double dProb = Double.parseDouble(String.valueOf(dItem.get("prob")));
                    double combinedProb = sProb * dProb; // 组合概率
                    
                    Map<String, Object> combined = new LinkedHashMap<>();
                    combined.put("class", sClass + "___" + dClass);
                    combined.put("prob", combinedProb);
                    probabilities.add(combined);
                }
            }
            // 按概率降序排序并取前5
            probabilities.sort((a, b) -> Double.compare(
                Double.parseDouble(String.valueOf(b.get("prob"))),
                Double.parseDouble(String.valueOf(a.get("prob")))
            ));
            if (probabilities.size() > 5) {
                probabilities = probabilities.subList(0, 5);
            }
        } else {
            predicted = "未知";
            confidence = 0.0;
            log.warn("FastAPI返回了未知格式的响应: {}", fast);
        }

        // 优先尝试从 bhxx 表中匹配该病害的防治方法；若找不到则回退到通用建议
        Optional<Bhxx> bhxxOpt = resolveBhxx(predicted);
        if (bhxxOpt.isEmpty()) {
            log.warn("未在分布知识库(bhxx)中找到模型类别: {}", predicted);
        }
        String advice = bhxxOpt
                .map(Bhxx::getPreventionMethod)
                .filter(a -> a != null && !a.isBlank())
                .orElseGet(() -> genAdvice(predicted));

        // 根据匹配到的 Bhxx 从防治物品表中查找推荐用药/物品（仅用于当前响应展示）
        List<Fzwp> recommendedItems = bhxxOpt.map(this::findRecommendedItems).orElseGet(Collections::emptyList);

        DetectResult saved;
        try {
            saved = repo.save(DetectResult.builder()
                .user(userOpt.get())
                .modelLabel(predicted)
                .predictedClass(predicted)
                .username(username.trim())
                .confidence(confidence)
                .advice(advice)
                .imageUrl(imageUrl)
                .build());
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            log.error("保存检测结果失败: {}", root.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("保存检测结果失败: " + (root.getMessage() == null ? e.toString() : root.getMessage()));
        }

        // 识别接口本身不再主动生成 PDF 报告，前端可根据需要单独调用生成接口
        String reportUrl = saved.getReportUrl();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", saved.getId());
        resp.put("predictedClass", saved.getPredictedClass());
        resp.put("confidence", confidence);
        resp.put("advice", advice);
        resp.put("createdAt", saved.getCreatedAt());
        resp.put("imageUrl", saved.getImageUrl());
        resp.put("reportUrl", reportUrl); // 添加报告URL
        
        Map<String, Object> bhxxPayload = bhxxOpt.map(this::buildBhxxPayload).orElse(null);
        resp.put("disease", bhxxPayload);
        if (bhxxPayload == null) {
            resp.put("knowledgeMissing", true);
        }
        
        // 添加推荐物品列表（使用统一的数据格式）
        List<Map<String, Object>> recPayload = recommendedItems.stream()
                .map(this::buildFzwpPayload)
                .collect(Collectors.toList());
        resp.put("recommendedItems", recPayload);
        // 透传 FastAPI Top-5 概率方便前端展示
        resp.put("probabilities", probabilities);
        return ResponseEntity.ok(resp);
    }

    /**
     * 按需为指定识别结果生成 PDF 报告
     */
    @PostMapping("/{id}/report")
    public ResponseEntity<?> generateReport(@PathVariable("id") Long id) {
        Optional<DetectResult> opt = repo.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("识别记录不存在");
        }

        DetectResult result = opt.get();

        // 如果之前已经生成过报告且文件仍然存在，则复用
        String existing = result.getReportUrl();
        if (existing != null && !existing.isBlank()) {
            try {
                String filename = existing.substring(existing.lastIndexOf('/') + 1);
                Path p = Path.of("uploads", "reports", filename);
                if (Files.exists(p)) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("reportUrl", existing);
                    return ResponseEntity.ok(out);
                }
            } catch (Exception ignore) {
                // 如果解析已有路径失败，则继续重新生成
            }
        }

        // 重新根据 predictedClass 匹配 Bhxx，并查找推荐物品
        Optional<Bhxx> bhxxOpt = resolveBhxx(result.getPredictedClass());
        List<Fzwp> items = bhxxOpt.map(this::findRecommendedItems).orElseGet(Collections::emptyList);

        String reportUrl;
        try {
            reportUrl = generateDetectReportPdf(result, bhxxOpt.orElse(null), items);
            result.setReportUrl(reportUrl);
            repo.save(result);
        } catch (Exception e) {
            log.error("生成PDF报告失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("生成PDF报告失败: " + e.getMessage());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reportUrl", reportUrl);
        return ResponseEntity.ok(out);
    }

    // 开发调试：仅返回结果，不写入数据库
    @PostMapping("/predict")
    public ResponseEntity<?> predict(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("未选择图片");
        }
        String uploadsDir = "uploads";
        File dir = new File(uploadsDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("image.jpg");
        String ext = "";
        int idx = originalName.lastIndexOf('.');
        if (idx != -1) ext = originalName.substring(idx).toLowerCase();
        String filename = UUID.randomUUID().toString().replaceAll("-", "") + (ext.isEmpty() ? ".jpg" : ext);
        Path target = Path.of(uploadsDir, filename);
        
        // 确保uploads目录存在
        Files.createDirectories(target.getParent());
        
        // 安全地复制文件
        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        
        String imageUrl = "/" + uploadsDir + "/" + filename;

        log.info("(predict) Forwarding image '{}' ({} bytes) to FastAPI {}", originalName, file.getSize(), fastapiBase);
        Map<String, Object> fast = callFastApiPredict(file);
        log.info("(predict) FastAPI response: {}", fast);
        
        // 解析FastAPI响应：支持背景拦截和双模型分类两种格式
        String predicted;
        double confidence;
        List<Map<String, Object>> probabilities = new ArrayList<>();
        
        // 检查是否为背景无叶片拦截（有predictedClass字段且包含Background）
        if (fast.containsKey("predictedClass")) {
            predicted = String.valueOf(fast.get("predictedClass"));
            confidence = Double.parseDouble(String.valueOf(fast.getOrDefault("confidence", 0.0)));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> probs = (List<Map<String, Object>>) fast.getOrDefault("probabilities", Collections.emptyList());
            probabilities = probs;
        } else if (fast.containsKey("species") && fast.containsKey("disease")) {
            // 双模型格式：组合 species + "___" + disease
            @SuppressWarnings("unchecked")
            Map<String, Object> speciesMap = (Map<String, Object>) fast.get("species");
            @SuppressWarnings("unchecked")
            Map<String, Object> diseaseMap = (Map<String, Object>) fast.get("disease");
            
            String speciesName = String.valueOf(speciesMap.getOrDefault("predicted", "Unknown"));
            String diseaseName = String.valueOf(diseaseMap.getOrDefault("predicted", "healthy"));
            double speciesConf = Double.parseDouble(String.valueOf(speciesMap.getOrDefault("confidence", 0.0)));
            double diseaseConf = Double.parseDouble(String.valueOf(diseaseMap.getOrDefault("confidence", 0.0)));
            
            // 组合格式：Apple___healthy 或 Apple___Leaf_spot
            predicted = speciesName + "___" + diseaseName;
            // 置信度取两者平均值
            confidence = (speciesConf + diseaseConf) / 2.0;
            
            // 合并Top-5概率（组合species和disease）
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> speciesTop5 = (List<Map<String, Object>>) speciesMap.getOrDefault("top5", Collections.emptyList());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> diseaseTop5 = (List<Map<String, Object>>) diseaseMap.getOrDefault("top5", Collections.emptyList());
            
            // 创建组合概率列表（取前5个，按组合概率排序）
            for (Map<String, Object> sItem : speciesTop5) {
                for (Map<String, Object> dItem : diseaseTop5) {
                    String sClass = String.valueOf(sItem.get("class"));
                    String dClass = String.valueOf(dItem.get("class"));
                    double sProb = Double.parseDouble(String.valueOf(sItem.get("prob")));
                    double dProb = Double.parseDouble(String.valueOf(dItem.get("prob")));
                    double combinedProb = sProb * dProb; // 组合概率
                    
                    Map<String, Object> combined = new LinkedHashMap<>();
                    combined.put("class", sClass + "___" + dClass);
                    combined.put("prob", combinedProb);
                    probabilities.add(combined);
                }
            }
            // 按概率降序排序并取前5
            probabilities.sort((a, b) -> Double.compare(
                Double.parseDouble(String.valueOf(b.get("prob"))),
                Double.parseDouble(String.valueOf(a.get("prob")))
            ));
            if (probabilities.size() > 5) {
                probabilities = probabilities.subList(0, 5);
            }
        } else {
            predicted = "未知";
            confidence = 0.0;
            log.warn("(predict) FastAPI返回了未知格式的响应: {}", fast);
        }

        Optional<Bhxx> bhxxOpt = resolveBhxx(predicted);
        if (bhxxOpt.isEmpty()) {
            log.warn("(predict) 未在分布知识库(bhxx)中找到模型类别: {}", predicted);
        }
        String advice = bhxxOpt
                .map(Bhxx::getPreventionMethod)
                .filter(a -> a != null && !a.isBlank())
                .orElseGet(() -> genAdvice(predicted));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("predictedClass", predicted);
        resp.put("confidence", confidence);
        resp.put("advice", advice);
        resp.put("createdAt", LocalDateTime.now());
        resp.put("imageUrl", imageUrl);
        resp.put("filename", originalName);
        resp.put("size", file.getSize());
        resp.put("probabilities", probabilities);
        resp.put("modelVersion", "mobilenetv2-fastapi");
        Map<String, Object> bhxxPayload = bhxxOpt.map(this::buildBhxxPayload).orElse(null);
        resp.put("disease", bhxxPayload);
        if (bhxxPayload == null) {
            resp.put("knowledgeMissing", true);
        }
        return ResponseEntity.ok(resp);
    }

    // Recent history (compat for DetectView limit)
    @GetMapping("/history/{username}")
    public ResponseEntity<?> historyLimit(@PathVariable String username,
                                     @RequestParam(value = "limit", required = false) Integer limit,
                                     @RequestParam(value = "page", required = false) Integer page,
                                     @RequestParam(value = "size", required = false) Integer size,
                                     @RequestParam(value = "q", required = false) String q) {
        if (limit != null) {
            int lim = (limit <= 0) ? 5 : limit;
            Page<DetectResult> p = repo.findByUserUsernameOrderByCreatedAtDesc(username, PageRequest.of(0, lim));
            List<Map<String, Object>> list = p.getContent().stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.getId());
                String rawLabel = Optional.ofNullable(r.getPredictedClass()).orElse(r.getModelLabel());
                String display = toChineseLabel(rawLabel);
                m.put("predictedClass", rawLabel);
                m.put("displayLabel", display);
                m.put("confidence", r.getConfidence());
                m.put("advice", r.getAdvice());
                m.put("createdAt", r.getCreatedAt());
                m.put("imageUrl", r.getImageUrl());
                return m;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(list);
        }
        int pg = (page == null || page < 0) ? 0 : page;
        int sz = (size == null || size <= 0 || size > 50) ? 10 : size;
        Page<DetectResult> p;
        if (q != null && !q.isBlank()) {
            // 使用模型标签关键字进行模糊搜索
            p = repo.findByUserUsernameAndModelLabelContainingIgnoreCaseOrderByCreatedAtDesc(username, q, PageRequest.of(pg, sz));
        } else {
            p = repo.findByUserUsernameOrderByCreatedAtDesc(username, PageRequest.of(pg, sz));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("page", pg);
        out.put("size", sz);
        out.put("total", p.getTotalElements());
        out.put("items", p.getContent().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            String rawLabel = Optional.ofNullable(r.getPredictedClass()).orElse(r.getModelLabel());
            String display = toChineseLabel(rawLabel);
            m.put("predictedClass", rawLabel);
            m.put("displayLabel", display);
            m.put("confidence", r.getConfidence());
            m.put("advice", r.getAdvice());
            m.put("createdAt", r.getCreatedAt());
            m.put("imageUrl", r.getImageUrl());
            return m;
        }).collect(Collectors.toList()));
        return ResponseEntity.ok(out);
    }

    @DeleteMapping("/history/{username}/{id}")
    @Transactional
    public ResponseEntity<?> deleteHistoryItem(@PathVariable String username, @PathVariable Long id) {
        Optional<DetectResult> opt = repo.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("记录不存在");
        }
        DetectResult r = opt.get();
        String recordUsername = Optional.ofNullable(r.getUsername())
                .orElseGet(() -> r.getUser() != null ? r.getUser().getUsername() : null);
        if (recordUsername == null || !recordUsername.equals(username)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("记录不存在");
        }
        // 先删除关联的纠错反馈，再删除识别记录，避免外键约束错误
        feedbackRepository.deleteByDetectResultIds(Collections.singletonList(id));
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/history/{username}")
    @Transactional
    public ResponseEntity<?> deleteHistoryBatch(@PathVariable String username,
                                                @RequestBody Map<String, List<Long>> body) {
        if (body == null || !body.containsKey("ids")) {
            return ResponseEntity.badRequest().body("缺少要删除的记录ID");
        }
        List<Long> ids = body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body("缺少要删除的记录ID");
        }
        List<DetectResult> list = repo.findAllById(ids);
        List<Long> toDelete = list.stream()
                .filter(r -> {
                    String recordUsername = Optional.ofNullable(r.getUsername())
                            .orElseGet(() -> r.getUser() != null ? r.getUser().getUsername() : null);
                    return recordUsername != null && recordUsername.equals(username);
                })
                .map(DetectResult::getId)
                .collect(Collectors.toList());
        if (toDelete.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("没有可删除的记录");
        }
        // 先删除这些记录关联的纠错反馈，再批量删除识别记录
        feedbackRepository.deleteByDetectResultIds(toDelete);
        repo.deleteAllById(toDelete);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deleted", toDelete.size());
        out.put("ids", toDelete);
        return ResponseEntity.ok(out);
    }

    /**
     * 根据ID获取单条识别结果详情，用于历史记录跳转查看
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getResultDetail(@PathVariable Long id) {
        Optional<DetectResult> opt = repo.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("识别记录不存在");
        }
        DetectResult r = opt.get();

        String predicted = Optional.ofNullable(r.getPredictedClass()).orElse(r.getModelLabel());
        Optional<Bhxx> bhxxOpt = resolveBhxx(predicted);
        List<Fzwp> recommendedItems = bhxxOpt.map(this::findRecommendedItems).orElseGet(Collections::emptyList);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", r.getId());
        resp.put("predictedClass", predicted);
        resp.put("confidence", r.getConfidence());
        resp.put("advice", r.getAdvice());
        resp.put("createdAt", r.getCreatedAt());
        resp.put("imageUrl", r.getImageUrl());
        resp.put("reportUrl", r.getReportUrl());

        Map<String, Object> bhxxPayload = bhxxOpt.map(this::buildBhxxPayload).orElse(null);
        resp.put("disease", bhxxPayload);
        if (bhxxPayload == null) {
            resp.put("knowledgeMissing", true);
        }

        List<Map<String, Object>> recPayload = recommendedItems.stream()
                .map(this::buildFzwpPayload)
                .collect(Collectors.toList());
        resp.put("recommendedItems", recPayload);
        // 历史详情暂不返回Top-5概率
        resp.put("probabilities", Collections.emptyList());

        // 反馈摘要：是否已有纠错反馈及其最新状态
        List<DetectErrorFeedback> feedbacks = Optional.ofNullable(
                feedbackRepository.findByDetectResult_IdOrderByCreatedAtDesc(r.getId())
        ).orElseGet(Collections::emptyList);
        boolean hasFeedback = !feedbacks.isEmpty();
        resp.put("hasFeedback", hasFeedback);
        if (hasFeedback) {
            DetectErrorFeedback latest = feedbacks.get(0);
            resp.put("latestFeedbackId", latest.getId());
            resp.put("latestFeedbackStatus", latest.getStatus());
        } else {
            resp.put("latestFeedbackId", null);
            resp.put("latestFeedbackStatus", null);
        }

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/stats/{username}")
    public ResponseEntity<?> stats(@PathVariable String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("用户不存在");
        }

        List<Object[]> monthly = repo.countMonthlyByUsername(username);
        List<String> months = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (Object[] row : monthly) {
            months.add(String.valueOf(row[0]));
            Number n = (Number) row[1];
            counts.add(n == null ? 0 : n.intValue());
        }

        List<Object[]> typeAgg = repo.countByTypeForUser(username);
        List<Map<String, Object>> byType = new ArrayList<>();
        for (Object[] row : typeAgg) {
            Map<String, Object> m = new LinkedHashMap<>();
            String raw = String.valueOf(row[0]);
            String display = toChineseLabel(raw);
            m.put("name", display);
            Number n = (Number) row[1];
            m.put("value", n == null ? 0 : n.intValue());
            byType.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("months", months);
        out.put("counts", counts);
        out.put("byType", byType);
        return ResponseEntity.ok(out);
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("spring", "ok");
        
        // 检查 FastAPI 服务状态
        try {
            String url = fastapiBase.endsWith("/") ? fastapiBase + "health" : fastapiBase + "/health";
            ResponseEntity<Map<String, Object>> resp = rest.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            out.put("fastapiStatus", resp.getStatusCode().value());
            out.put("fastapi", resp.getBody());
            out.put("fastapiAvailable", true);
        } catch (Exception e) {
            log.warn("FastAPI health check failed: {}", e.getMessage());
            out.put("fastapi", "down");
            out.put("fastapiAvailable", false);
            out.put("error", e.getMessage());
        }
        
        // Spring Boot 服务正常时始终返回 200，即使 FastAPI 不可用
        return ResponseEntity.ok(out);
    }

    private Map<String, Object> callFastApiPredict(MultipartFile file) throws IOException {
        String url = fastapiBase.endsWith("/") ? fastapiBase + "predict" : fastapiBase + "/predict";
        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return Optional.ofNullable(file.getOriginalFilename()).orElse("image.jpg");
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Object>> resp = rest.exchange(
                url,
                HttpMethod.POST,
                req,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        Map<String, Object> responseBody = resp.getBody();
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("FastAPI 推理失败: " + resp.getStatusCode() + ", 响应体: " + 
                (responseBody != null ? responseBody.toString() : "无"));
        }
        if (responseBody == null) {
            throw new RuntimeException("FastAPI 返回空响应体");
        }
        return responseBody;
    }

    private Optional<Bhxx> resolveBhxx(String predicted) {
        if (predicted == null) return Optional.empty();
        String label = predicted.trim();
        if (label.isEmpty()) return Optional.empty();
        String lower = label.toLowerCase();
        if (lower.contains("background") || "未知".equals(label)) {
            return Optional.empty();
        }

        // 1. 先根据中文标签尝试精确/模糊匹配 bhxx.病害名称
        String zhLabel = toChineseLabel(label);
        if (zhLabel != null && !zhLabel.isBlank()
                && !"未知".equals(zhLabel)
                && !"背景无叶片".equals(zhLabel)) {
            // 完整匹配：例如 "番茄晚疫病"
            List<Bhxx> zhList = bhxxRepository.findByDiseaseName(zhLabel);
            if (!zhList.isEmpty()) {
                return Optional.of(zhList.get(0));
            }
            // 包含匹配：兼容 "番茄尾孢叶斑病（Septoria叶斑病）" 这类带括号别名的情况
            zhList = bhxxRepository.findByDiseaseNameContainingIgnoreCase(zhLabel);
            if (!zhList.isEmpty()) {
                return Optional.of(zhList.get(0));
            }
        }

        // 2. 仍然保留按英文别名部分匹配作为兜底（兼容旧数据）
        String diseaseEn = label;
        int sep = label.indexOf("___");
        if (sep >= 0 && sep + 3 < label.length()) {
            diseaseEn = label.substring(sep + 3);
        }

        List<Bhxx> list = bhxxRepository.findByDiseaseNameContainingIgnoreCase(diseaseEn);
        if (!list.isEmpty()) return Optional.of(list.get(0));

        String[] parts = diseaseEn.split("[\\s/]+");
        for (String p : parts) {
            String key = p.trim();
            if (key.isEmpty()) continue;
            list = bhxxRepository.findByDiseaseNameContainingIgnoreCase(key);
            if (!list.isEmpty()) return Optional.of(list.get(0));
        }
        return Optional.empty();
    }

    private Map<String, Object> buildBhxxPayload(Bhxx b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("plant", Optional.ofNullable(b.getPlantName()).orElse(""));
        m.put("name", Optional.ofNullable(b.getDiseaseName()).orElse(""));
        m.put("modelLabel", "");
        String area = Optional.ofNullable(b.getDistributionArea()).orElse("");
        String time = Optional.ofNullable(b.getDistributionTime()).orElse("");
        String desc;
        if (!area.isBlank() || !time.isBlank()) {
            desc = String.join(" ", area, time).trim();
        } else {
            desc = "";
        }
        m.put("description", desc);
        m.put("advice", Optional.ofNullable(b.getPreventionMethod()).orElse(""));
        return m;
    }

    private String genAdvice(String predicted) {
        return switch (predicted) {
            case "叶斑病" -> "及时清除病叶，改善通风，喷施相应药剂";
            case "白粉病" -> "降低湿度，加强光照，使用针对性杀菌剂";
            case "锈病" -> "清除病残体，轮作种植，施用保护性药剂";
            default -> "注意田间管理，加强虫害监测与综合防治";
        };
    }

    /**
     * 根据知识库匹配到的 Bhxx 记录，查询推荐的防治物品
     */
    private List<Fzwp> findRecommendedItems(Bhxx bhxx) {
        if (bhxx == null) return Collections.emptyList();
        String plant = Optional.ofNullable(bhxx.getPlantName()).orElse("").trim();
        String disease = Optional.ofNullable(bhxx.getDiseaseName()).orElse("").trim();
        if (plant.isEmpty() && disease.isEmpty()) {
            return Collections.emptyList();
        }

        // 优先按 植物名称 + 病害名称 精确匹配
        List<Fzwp> items = Collections.emptyList();
        if (!plant.isEmpty() && !disease.isEmpty()) {
            items = fzwpRepository.findByPlantNameAndTargetDisease(plant, disease);
        }
        if (!items.isEmpty()) return items;

        // 退化为仅按植物名称匹配
        if (!plant.isEmpty()) {
            items = fzwpRepository.findByPlantName(plant);
            if (!items.isEmpty()) return items;
        }

        // 再退化为仅按病害名称匹配
        if (!disease.isEmpty()) {
            items = fzwpRepository.findByTargetDisease(disease);
        }
        return items;
    }

    private Map<String, Object> buildFzwpPayload(Fzwp it) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", it.getId());
        m.put("itemName", Optional.ofNullable(it.getItemName()).orElse(""));
        m.put("plantName", Optional.ofNullable(it.getPlantName()).orElse(""));
        m.put("targetDisease", Optional.ofNullable(it.getTargetDisease()).orElse(""));
        m.put("price", it.getPrice());
        m.put("imageUrl", Optional.ofNullable(it.getImageUrl()).orElse(""));
        return m;
    }

    /**
     * 生成识别结果 PDF 报告，返回供前端下载的 URL 路径
     */
    private String generateDetectReportPdf(DetectResult result, Bhxx bhxx, List<Fzwp> items) throws IOException {
        Path dir = Path.of("uploads", "reports");
        Files.createDirectories(dir);

        String username = Optional.ofNullable(result.getUsername())
                .filter(s -> !s.isBlank())
                .orElseGet(() -> result.getUser() != null ? result.getUser().getUsername() : "user");
        LocalDateTime created = Optional.ofNullable(result.getCreatedAt()).orElse(LocalDateTime.now());
        String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(created);
        String diseaseLabel = toChineseLabel(result.getPredictedClass());
        String safeDisease = diseaseLabel.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", "");
        String fileName = username + "_" + ts + "_" + safeDisease + ".pdf";
        Path pdfPath = dir.resolve(fileName);

        try (PDDocument doc = new PDDocument()) {
            // 优先尝试加载支持中文的系统字体，失败则回退到内置英文字体，避免抛异常
            PDType0Font cnFont = null;
            List<String> fontCandidates = new ArrayList<>();

            // 1) 优先使用配置项指定的字体路径（如果有）
            if (cnFontPathConfig != null && !cnFontPathConfig.isBlank()) {
                fontCandidates.add(cnFontPathConfig.trim());
            }

            // 2) 常见的 Windows 中文字体候选路径
            fontCandidates.add("C:/Windows/Fonts/simhei.ttf");   // 黑体
            fontCandidates.add("C:/Windows/Fonts/simhei.ttc");
            fontCandidates.add("C:/Windows/Fonts/msyh.ttc");     // 微软雅黑
            fontCandidates.add("C:/Windows/Fonts/msyh.ttf");
            fontCandidates.add("C:/Windows/Fonts/msyhbd.ttc");
            fontCandidates.add("C:/Windows/Fonts/msyhbd.ttf");
            fontCandidates.add("C:/Windows/Fonts/simsun.ttc");   // 宋体
            fontCandidates.add("C:/Windows/Fonts/simsun.ttf");

            for (String pathStr : fontCandidates) {
                if (pathStr == null || pathStr.isBlank()) continue;
                try {
                    File fontFile = new File(pathStr);
                    if (!fontFile.exists()) {
                        continue;
                    }
                    cnFont = PDType0Font.load(doc, fontFile);
                    log.info("已加载中文字体用于PDF: {}", fontFile.getAbsolutePath());
                    break;
                } catch (Exception e) {
                    log.warn("尝试加载中文字体失败 ({}): {}", pathStr, e.getMessage());
                }
            }

            if (cnFont == null) {
                log.warn("未能加载任何中文字体，PDF将使用内置英文字体（中文将无法正常显示）");
            }

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                float margin = 50;
                float pageWidth = page.getMediaBox().getWidth();
                float y = page.getMediaBox().getHeight() - margin;

                if (cnFont != null) {
                    // 中文字体可用：输出与前端识别结果卡片类似的中文内容
                    // 1. 标题「识别结果」
                    content.beginText();
                    content.setFont(cnFont, 18);
                    content.newLineAtOffset(margin, y);
                    content.showText("识别结果");
                    content.endText();

                    y -= 32;

                    // 2. 识别图片（如果存在）
                    String imageUrl = Optional.ofNullable(result.getImageUrl()).orElse("");
                    if (!imageUrl.isBlank()) {
                        try {
                            String rel = imageUrl.startsWith("/") ? imageUrl.substring(1) : imageUrl;
                            Path imagePath = Path.of(rel);
                            if (Files.exists(imagePath)) {
                                PDImageXObject image = PDImageXObject.createFromFileByContent(imagePath.toFile(), doc);
                                float imgMaxWidth = pageWidth - margin * 2;
                                float imgMaxHeight = 260f;
                                float scale = Math.min(imgMaxWidth / image.getWidth(), imgMaxHeight / image.getHeight());
                                if (scale <= 0) {
                                    scale = 1f;
                                }
                                float imgWidth = image.getWidth() * scale;
                                float imgHeight = image.getHeight() * scale;
                                float imgX = (pageWidth - imgWidth) / 2;
                                float imgY = y - imgHeight;
                                content.drawImage(image, imgX, imgY, imgWidth, imgHeight);
                                y = imgY - 24;
                            }
                        } catch (Exception e) {
                            log.warn("将识别图片绘制到PDF失败: {}", e.getMessage());
                        }
                    }

                    // 3. 文本信息：植物 / 病害 / 置信度 / 防治建议 / 识别时间
                    content.beginText();
                    content.setFont(cnFont, 12);
                    content.newLineAtOffset(margin, y);

                    String plant = "-";
                    String disease = diseaseLabel;
                    if (bhxx != null) {
                        plant = Optional.ofNullable(bhxx.getPlantName()).orElse("-");
                        disease = Optional.ofNullable(bhxx.getDiseaseName()).orElse(diseaseLabel);
                    }

                    content.showText("植物: " + plant);
                    content.newLineAtOffset(0, -18);
                    content.showText("病害: " + disease);

                    String confText = String.format(java.util.Locale.CHINA, "%.1f%%", result.getConfidence() * 100.0);
                    content.newLineAtOffset(0, -18);
                    content.showText("置信度: " + confText);

                    String advice = Optional.ofNullable(result.getAdvice()).orElse("");
                    if (!advice.isBlank()) {
                        content.newLineAtOffset(0, -24);
                        content.showText("防治建议:");

                        String text = advice.replace('\n', ' ');
                        int maxLen = 35;
                        for (int i = 0; i < text.length(); i += maxLen) {
                            String line = text.substring(i, Math.min(text.length(), i + maxLen));
                            content.newLineAtOffset(0, -16);
                            content.showText(line);
                        }
                    }

                    String timeStr = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").format(created);
                    content.newLineAtOffset(0, -24);
                    content.showText("识别时间: " + timeStr);

                    content.endText();
                } else {
                    // 无可用中文字体：输出只包含英文/ASCII 的精简报告，避免字体编码异常
                    content.beginText();
                    content.setFont(PDType1Font.HELVETICA_BOLD, 16);
                    content.newLineAtOffset(margin, y);
                    content.showText("Plant disease report");

                    content.setFont(PDType1Font.HELVETICA, 12);
                    y -= 24;
                    content.newLineAtOffset(0, -24);
                    content.showText("User: " + username);

                    y -= 16;
                    content.newLineAtOffset(0, -16);
                    content.showText("Created: " + created.toString());

                    y -= 16;
                    content.newLineAtOffset(0, -16);
                    String rawLabel = Optional.ofNullable(result.getPredictedClass()).orElse("unknown");
                    content.showText("Model label: " + rawLabel);

                    y -= 16;
                    content.newLineAtOffset(0, -16);
                    content.showText("Confidence: " + result.getConfidence());

                    content.endText();
                }
            }

            doc.save(pdfPath.toFile());
        }

        // 返回可访问的URL路径，而不是本地文件路径
        return "/api/detect/reports/" + fileName;
    }

    private String toChineseLabel(String raw) {
        if (raw == null || raw.isBlank()) return "未知";
        String lower = raw.toLowerCase();
        if (lower.contains("background")) return "背景无叶片";
        if ("unknown".equals(lower) || "未知".equals(raw)) return "未知";

        String species = null;
        String disease = null;
        int sep = raw.indexOf("___");
        if (sep >= 0) {
            species = raw.substring(0, sep);
            disease = raw.substring(sep + 3);
        } else {
            disease = raw;
        }

        String speciesZh = species;
        if (species != null) {
            String s = species.toLowerCase();
            if (s.contains("apple")) speciesZh = "苹果";
            else if (s.contains("blueberry")) speciesZh = "蓝莓";
            else if (s.contains("cherry")) speciesZh = "樱桃";
            else if (s.contains("corn")) speciesZh = "玉米";
            else if (s.contains("grape")) speciesZh = "葡萄";
            else if (s.contains("orange")) speciesZh = "橙子";
            else if (s.contains("peach")) speciesZh = "桃";
            else if (s.contains("pepper")) speciesZh = "柿子椒";
            else if (s.contains("potato")) speciesZh = "马铃薯";
            else if (s.contains("raspberry")) speciesZh = "覆盆子";
            else if (s.contains("soybean")) speciesZh = "大豆";
            else if (s.contains("squash")) speciesZh = "南瓜";
            else if (s.contains("strawberry")) speciesZh = "草莓";
            else if (s.contains("tomato")) speciesZh = "番茄";
        }

        String dZh = disease;
        if (disease != null) {
            String d = disease.toLowerCase();
            if (d.contains("healthy")) dZh = "健康";
            else if (d.contains("bacterial_spot")) dZh = "细菌性斑点病";
            else if (d.contains("early_blight")) dZh = "早疫病";
            else if (d.contains("late_blight")) dZh = "晚疫病";
            else if (d.contains("leaf_scorch")) dZh = "叶灼病";
            else if (d.contains("leaf_mold")) dZh = "叶霉病";
            else if (d.contains("septoria_leaf_spot")) dZh = "尾孢叶斑病";
            else if (d.contains("target_spot")) dZh = "靶斑病";
            else if (d.contains("isariopsis_leaf_spot")) dZh = "叶枯病";
            else if (d.contains("black_rot")) dZh = "黑腐病";
            else if (d.contains("apple_scab")) dZh = "苹果黑星病";
            else if (d.contains("cedar_apple_rust")) dZh = "雪松苹果锈";
            else if (d.contains("powdery_mildew")) dZh = "白粉病";
            else if (d.contains("haunglongbing") || d.contains("citrus_greening")) dZh = "黄龙病";
            else if (d.contains("common_rust")) dZh = "普通锈病";
            else if (d.contains("gray_leaf_spot") || d.contains("cercospora_leaf_spot")) dZh = "灰斑病";
            else if (d.contains("two-spotted_spider_mite")) dZh = "二斑叶螨";
            else dZh = disease.replace('_', ' ');
        }

        if (speciesZh == null) {
            return dZh != null ? dZh : raw;
        }
        if ("健康".equals(dZh)) return speciesZh + "健康";
        return speciesZh + dZh;
    }

    @GetMapping("/reports/{filename}")
    public ResponseEntity<Resource> downloadReport(@PathVariable String filename) throws MalformedURLException {
        // 构建文件路径
        Path filePath = Path.of("uploads", "reports", filename);
        
        // 检查文件是否存在
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }
        
        // 创建资源
        Resource resource = new UrlResource(filePath.toUri());
        
        // 设置响应头 - 对于查看操作使用inline，下载操作会通过前端设置download属性
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + filename)
                .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                .body(resource);
    }
}