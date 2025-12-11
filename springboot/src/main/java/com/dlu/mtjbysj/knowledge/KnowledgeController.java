package com.dlu.mtjbysj.knowledge;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Collections;

@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin
@RequiredArgsConstructor
@SuppressWarnings("null")
public class KnowledgeController {

    private final BhxxRepository bhxxRepository;
    private final FzwpRepository fzwpRepository;

    @GetMapping("/diseases")
    public List<Map<String, Object>> listAll() {
        // 使用 bhxx 表作为知识库数据源，将分布与防治信息映射为通用 disease 结构
        return bhxxRepository.findAll().stream()
                .map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", b.getId());
                    m.put("plant", Optional.ofNullable(b.getPlantName()).orElse(""));
                    m.put("name", Optional.ofNullable(b.getDiseaseName()).orElse(""));
                    // 目前不再依赖模型标签表，这里留空即可
                    m.put("modelLabel", "");
                    // 将分布区域与时间拼接为描述信息
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
                    m.put("createdAt", b.getCreatedAt());
                    m.put("updatedAt", b.getUpdatedAt());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/bhxx")
    public List<Map<String, Object>> listBhxx() {
        return bhxxRepository.findAll().stream()
                .map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", b.getId());
                    m.put("plantName", Optional.ofNullable(b.getPlantName()).orElse(""));
                    m.put("diseaseName", Optional.ofNullable(b.getDiseaseName()).orElse(""));
                    m.put("distributionArea", Optional.ofNullable(b.getDistributionArea()).orElse(""));
                    m.put("distributionTime", Optional.ofNullable(b.getDistributionTime()).orElse(""));
                    m.put("preventionMethod", Optional.ofNullable(b.getPreventionMethod()).orElse(""));
                    m.put("updatedAt", b.getUpdatedAt());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/bhxx/plants")
    public List<String> listBhxxPlants() {
        return bhxxRepository.findAll().stream()
                .map(Bhxx::getPlantName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @GetMapping("/bhxx/diseases")
    public List<String> listBhxxDiseasesByPlant(@RequestParam("plant") String plantName) {
        if (plantName == null || plantName.isBlank()) {
            return Collections.emptyList();
        }
        return bhxxRepository.findByPlantName(plantName).stream()
                .map(Bhxx::getDiseaseName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @GetMapping("/fzwp")
    public Map<String, Object> listFzwp(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(value = "q", required = false) String q) {
        if (page < 0) {
            page = 0;
        }
        if (size <= 0) {
            size = 10;
        }
        if (size > 50) {
            size = 50;
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<Fzwp> p;
        if (q != null && !q.trim().isEmpty()) {
            String keyword = q.trim();
            p = fzwpRepository.findByItemNameContainingIgnoreCaseOrTargetDiseaseContainingIgnoreCase(keyword, keyword, pageable);
        } else {
            p = fzwpRepository.findAll(pageable);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", p.getTotalElements());
        List<Map<String, Object>> items = p.getContent().stream()
                .map(it -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", it.getId());
                    m.put("itemName", Optional.ofNullable(it.getItemName()).orElse(""));
                    m.put("plantName", Optional.ofNullable(it.getPlantName()).orElse(""));
                    m.put("price", it.getPrice());
                    m.put("imageUrl", Optional.ofNullable(it.getImageUrl()).orElse(""));
                    m.put("targetDisease", Optional.ofNullable(it.getTargetDisease()).orElse(""));
                    return m;
                })
                .collect(Collectors.toList());
        resp.put("items", items);
        return resp;
    }

    @GetMapping("/fzwp/{id}")
    public ResponseEntity<?> getFzwp(@PathVariable Long id) {
        Optional<Fzwp> opt = fzwpRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("物品不存在");
        }
        Fzwp it = opt.get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", it.getId());
        m.put("itemName", Optional.ofNullable(it.getItemName()).orElse(""));
        m.put("plantName", Optional.ofNullable(it.getPlantName()).orElse(""));
        m.put("price", it.getPrice());
        m.put("imageUrl", Optional.ofNullable(it.getImageUrl()).orElse(""));
        m.put("targetDisease", Optional.ofNullable(it.getTargetDisease()).orElse(""));
        m.put("createdAt", it.getCreatedAt());
        m.put("listedAt", it.getListedAt());
        return ResponseEntity.ok(m);
    }
}
