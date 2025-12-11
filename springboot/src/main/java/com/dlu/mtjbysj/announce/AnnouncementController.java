package com.dlu.mtjbysj.announce;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/announcements")
@CrossOrigin
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementRepository announcementRepository;

    @GetMapping
    public List<Map<String, Object>> listPublished() {
        return announcementRepository.findByPublishedTrueOrderByCreatedAtDesc().stream()
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("title", a.getTitle());
                    m.put("content", a.getContent());
                    m.put("createdAt", a.getCreatedAt());
                    m.put("updatedAt", a.getUpdatedAt());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return announcementRepository.findByIdAndPublishedTrue(id)
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("title", a.getTitle());
                    m.put("content", a.getContent());
                    m.put("createdAt", a.getCreatedAt());
                    m.put("updatedAt", a.getUpdatedAt());
                    return ResponseEntity.ok(m);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
