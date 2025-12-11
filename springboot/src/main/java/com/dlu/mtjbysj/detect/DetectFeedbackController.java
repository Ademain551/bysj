package com.dlu.mtjbysj.detect;

import com.dlu.mtjbysj.user.User;
import com.dlu.mtjbysj.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/detect")
@RequiredArgsConstructor
@CrossOrigin
@SuppressWarnings("null")
public class DetectFeedbackController {
    private final DetectResultRepository detectResultRepository;
    private final DetectErrorFeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    @PostMapping("/{id}/feedback")
    public ResponseEntity<?> createFeedback(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (body == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("请求体不能为空");
        }
        Object u = body.get("username");
        Object e = body.get("expertUsername");
        String username = u instanceof String ? ((String) u).trim() : null;
        String expertUsername = e instanceof String ? ((String) e).trim() : null;
        if (username == null || username.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少用户名");
        }
        if (expertUsername == null || expertUsername.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("请选择专家");
        }
        Optional<DetectResult> drOpt = detectResultRepository.findById(id);
        if (drOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("识别记录不存在");
        }
        DetectResult result = drOpt.get();
        String recordUsername = Optional.ofNullable(result.getUsername())
                .orElseGet(() -> result.getUser() != null ? result.getUser().getUsername() : null);
        if (recordUsername == null || !recordUsername.equals(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权对该识别结果提交反馈");
        }
        Optional<User> farmerOpt = userRepository.findByUsername(username);
        if (farmerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("用户不存在");
        }
        Optional<User> expertOpt = userRepository.findByUsername(expertUsername);
        if (expertOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("专家不存在");
        }
        User expert = expertOpt.get();
        if (!"expert".equals(expert.getUserType()) || !expert.isEnabled()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("所选用户不是有效的专家");
        }
        Object c = body.get("comment");
        String comment = c instanceof String ? ((String) c).trim() : null;
        DetectErrorFeedback feedback = DetectErrorFeedback.builder()
                .detectResult(result)
                .farmer(farmerOpt.get())
                .expert(expert)
                .status("PENDING_EXPERT")
                .farmerComment(comment)
                .addedToDataset(false)
                .retrainRequired(false)
                .retrainTriggered(false)
                .build();
        DetectErrorFeedback saved = feedbackRepository.save(feedback);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", saved.getId());
        resp.put("status", saved.getStatus());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/feedback/my")
    public ResponseEntity<?> listMyFeedback(@RequestParam("username") String username) {
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少用户名");
        }
        List<DetectErrorFeedback> list = feedbackRepository.findByFarmerUsernameOrderByCreatedAtDesc(username.trim());
        List<Map<String, Object>> items = new ArrayList<>();
        for (DetectErrorFeedback f : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("status", f.getStatus());
            m.put("farmerComment", f.getFarmerComment());
            m.put("expertComment", f.getExpertComment());
            m.put("replyMessage", f.getReplyMessage());
            m.put("correctPlant", f.getCorrectPlant());
            m.put("correctDisease", f.getCorrectDisease());
            m.put("correctModelLabel", f.getCorrectModelLabel());
            m.put("addedToDataset", f.isAddedToDataset());
            m.put("retrainRequired", f.isRetrainRequired());
            m.put("retrainTriggered", f.isRetrainTriggered());
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

    @GetMapping("/feedback/expert/{username}")
    public ResponseEntity<?> listExpertFeedback(@PathVariable String username,
                                                @RequestParam(value = "status", required = false) String status) {
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少用户名");
        }
        String st = status == null || status.trim().isEmpty() ? "PENDING_EXPERT" : status.trim();
        List<DetectErrorFeedback> list = feedbackRepository.findByExpertUsernameAndStatusOrderByCreatedAtDesc(username.trim(), st);
        List<Map<String, Object>> items = new ArrayList<>();
        for (DetectErrorFeedback f : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("status", f.getStatus());
            m.put("farmerComment", f.getFarmerComment());
            m.put("expertComment", f.getExpertComment());
            m.put("replyMessage", f.getReplyMessage());
            m.put("correctPlant", f.getCorrectPlant());
            m.put("correctDisease", f.getCorrectDisease());
            m.put("correctModelLabel", f.getCorrectModelLabel());
            m.put("addedToDataset", f.isAddedToDataset());
            m.put("retrainRequired", f.isRetrainRequired());
            m.put("retrainTriggered", f.isRetrainTriggered());
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
            items.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", items.size());
        out.put("items", items);
        return ResponseEntity.ok(out);
    }

    @PostMapping("/feedback/{id}/confirm-correct")
    public ResponseEntity<?> confirmCorrect(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<DetectErrorFeedback> opt = feedbackRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("反馈不存在");
        }
        DetectErrorFeedback f = opt.get();
        Object u = body.get("expertUsername");
        String expertUsername = u instanceof String ? ((String) u).trim() : null;
        if (expertUsername == null || expertUsername.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少专家用户名");
        }
        User expert = f.getExpert();
        if (expert == null || !expertUsername.equals(expert.getUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权处理该反馈");
        }
        if (!"PENDING_EXPERT".equals(f.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("当前状态不可确认无误");
        }
        Object c = body.get("expertComment");
        String comment = c instanceof String ? ((String) c).trim() : null;
        f.setExpertComment(comment);
        f.setStatus("CONFIRMED_CORRECT");
        f.setReplyMessage("您所提交的识别错误问题并不存在，感谢您对本平台的支持与帮助！");
        feedbackRepository.save(f);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", f.getId());
        resp.put("status", f.getStatus());
        resp.put("replyMessage", f.getReplyMessage());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/feedback/{id}/confirm-wrong")
    public ResponseEntity<?> confirmWrong(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<DetectErrorFeedback> opt = feedbackRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("反馈不存在");
        }
        DetectErrorFeedback f = opt.get();
        Object u = body.get("expertUsername");
        String expertUsername = u instanceof String ? ((String) u).trim() : null;
        if (expertUsername == null || expertUsername.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少专家用户名");
        }
        User expert = f.getExpert();
        if (expert == null || !expertUsername.equals(expert.getUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权处理该反馈");
        }
        if (!"PENDING_EXPERT".equals(f.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("当前状态不可确认错误");
        }
        Object p = body.get("plantName");
        Object d = body.get("diseaseName");
        String plantName = p instanceof String ? ((String) p).trim() : null;
        String diseaseName = d instanceof String ? ((String) d).trim() : null;
        if (plantName == null || plantName.isEmpty() || diseaseName == null || diseaseName.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("请填写植物名称和病害名称");
        }
        String speciesEn = mapSpeciesToEnglish(plantName);
        String diseaseEn = mapDiseaseToEnglish(plantName, diseaseName);
        if (diseaseEn == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("无法根据植物名称和病害名称确定模型类别");
        }
        String label;
        if ("Background_without_leaves".equals(diseaseEn)) {
            label = diseaseEn;
        } else {
            if (speciesEn == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("无法根据植物名称确定模型物种类别");
            }
            label = speciesEn + "___" + diseaseEn;
        }
        Object c = body.get("expertComment");
        String comment = c instanceof String ? ((String) c).trim() : null;
        f.setExpertComment(comment);
        f.setCorrectPlant(plantName);
        f.setCorrectDisease(diseaseName);
        f.setCorrectModelLabel(label);
        f.setStatus("CONFIRMED_WRONG");
        f.setReplyMessage("您的反馈我们已经知晓，并且正在加急改正，感谢您的指正！");
        f.setRetrainRequired(true);
        f.setAddedToDataset(false);
        f.setRetrainTriggered(false);
        feedbackRepository.save(f);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", f.getId());
        resp.put("status", f.getStatus());
        resp.put("correctPlant", f.getCorrectPlant());
        resp.put("correctDisease", f.getCorrectDisease());
        resp.put("correctModelLabel", f.getCorrectModelLabel());
        resp.put("replyMessage", f.getReplyMessage());
        return ResponseEntity.ok(resp);
    }

    private String mapSpeciesToEnglish(String plantName) {
        String v = plantName == null ? "" : plantName.trim();
        if (v.isEmpty()) {
            return null;
        }
        if (v.contains("苹果")) {
            return "Apple";
        }
        if (v.contains("番茄")) {
            return "Tomato";
        }
        if (v.contains("草莓")) {
            return "Strawberry";
        }
        if (v.contains("南瓜")) {
            return "Squash";
        }
        if (v.contains("大豆")) {
            return "Soybean";
        }
        if (v.contains("树莓")) {
            return "Raspberry";
        }
        if (v.contains("马铃薯") || v.contains("土豆")) {
            return "Potato";
        }
        if (v.contains("柿子椒") || v.contains("甜椒") || v.contains("辣椒")) {
            return "Pepper,_bell";
        }
        if (v.contains("桃")) {
            return "Peach";
        }
        if (v.contains("橙") || v.contains("柑橘")) {
            return "Orange";
        }
        if (v.contains("葡萄")) {
            return "Grape";
        }
        if (v.contains("玉米")) {
            return "Corn";
        }
        if (v.contains("樱桃")) {
            return "Cherry";
        }
        if (v.contains("蓝莓")) {
            return "Blueberry";
        }
        if (v.contains("背景") || v.contains("无植物")) {
            return null;
        }
        return null;
    }

    private String mapDiseaseToEnglish(String plantName, String diseaseName) {
        String d = diseaseName == null ? "" : diseaseName.trim();
        if (d.isEmpty()) {
            return null;
        }
        if (d.contains("健康")) {
            return "healthy";
        }
        if (d.contains("苹果黑星")) {
            return "Apple_scab";
        }
        if (d.contains("黑腐")) {
            return "Black_rot";
        }
        if (d.contains("雪松苹果锈") || d.contains("赤锈")) {
            return "Cedar_apple_rust";
        }
        if (d.contains("黄化曲叶")) {
            return "Tomato_Yellow_Leaf_Curl_Virus";
        }
        if (d.contains("花叶病毒")) {
            return "Tomato_mosaic_virus";
        }
        if (d.contains("靶斑")) {
            return "Target_Spot";
        }
        if (d.contains("二斑叶螨")) {
            return "Spider_mites Two-spotted_spider_mite";
        }
        if (d.contains("尾孢叶斑")) {
            return "Septoria_leaf_spot";
        }
        if (d.contains("叶霉")) {
            return "Leaf_Mold";
        }
        if (d.contains("晚疫")) {
            return "Late_blight";
        }
        if (d.contains("早疫")) {
            return "Early_blight";
        }
        if (d.contains("细菌性斑点")) {
            return "Bacterial_spot";
        }
        if (d.contains("叶枯") || d.contains("叶灼")) {
            if (plantName != null && plantName.contains("草莓")) {
                return "Leaf_scorch";
            }
            if (plantName != null && plantName.contains("葡萄")) {
                return "Leaf_blight_(Isariopsis_Leaf_Spot)";
            }
        }
        if (d.contains("白粉")) {
            return "Powdery_mildew";
        }
        if (d.contains("黑麻疹") || d.contains("虎眼") || d.contains("Esca")) {
            return "Esca_(Black_Measles)";
        }
        if (d.contains("黄龙") || d.contains("绿化病")) {
            return "Haunglongbing_(Citrus_greening)";
        }
        if (d.contains("北方叶枯")) {
            return "Northern_Leaf_Blight";
        }
        if (d.contains("普通锈")) {
            return "Common_rust";
        }
        if (d.contains("灰斑")) {
            return "Cercospora_leaf_spot Gray_leaf_spot";
        }
        if (d.contains("背景无叶") || d.contains("背景") || d.contains("无叶片")) {
            return "Background_without_leaves";
        }
        return null;
    }
}
