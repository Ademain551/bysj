package com.dlu.mtjbysj.guide;

import com.dlu.mtjbysj.chat.ChatService;
import com.dlu.mtjbysj.knowledge.Fzwp;
import com.dlu.mtjbysj.knowledge.FzwpRepository;
import com.dlu.mtjbysj.user.User;
import com.dlu.mtjbysj.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guide")
@CrossOrigin
@RequiredArgsConstructor
@SuppressWarnings("null")
public class GuideController {

    private final GuideArticleRepository articleRepository;
    private final GuideArticleCommentRepository commentRepository;
    private final GuideArticleRecommendationRepository recommendationRepository;
    private final GuideArticleFavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final FzwpRepository fzwpRepository;
    private final ChatService chatService;

    private User currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new IllegalStateException("未登录");
        }
        Object attr = session.getAttribute("LOGIN_USER");
        if (!(attr instanceof Map)) {
            throw new IllegalStateException("未登录");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) attr;
        Object usernameObj = map.get("username");
        if (usernameObj == null) {
            throw new IllegalStateException("未登录");
        }
        String username = usernameObj.toString();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("用户不存在"));
    }

    @GetMapping("/articles")
    public ResponseEntity<?> listArticles(@RequestParam(defaultValue = "0") int page,
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
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<GuideArticle> p;
        if (q != null && !q.trim().isEmpty()) {
            String keyword = q.trim();
            p = articleRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(keyword, keyword, pageable);
        } else {
            p = articleRepository.findAll(pageable);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", p.getTotalElements());
        List<Map<String, Object>> items = new ArrayList<>();
        for (GuideArticle a : p.getContent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("title", a.getTitle());
            String content = Optional.ofNullable(a.getContent()).orElse("");
            String summary = content.length() > 120 ? content.substring(0, 120) : content;
            m.put("summary", summary);
            m.put("coverImageUrl", a.getCoverImageUrl());
            m.put("createdAt", a.getCreatedAt());
            m.put("author", chatService.userBrief(a.getAuthor()));
            items.add(m);
        }
        resp.put("items", items);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/articles/{id}")
    public ResponseEntity<?> getArticle(@PathVariable Long id, HttpServletRequest request) {
        Optional<GuideArticle> opt = articleRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("文章不存在");
        }
        GuideArticle article = opt.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", article.getId());
        result.put("title", article.getTitle());
        result.put("content", article.getContent());
        result.put("coverImageUrl", article.getCoverImageUrl());
        result.put("imageUrls", parseImageUrls(article.getImageUrls()));
        result.put("createdAt", article.getCreatedAt());
        result.put("author", chatService.userBrief(article.getAuthor()));

        List<GuideArticleRecommendation> recs = recommendationRepository.findByArticleId(article.getId());
        List<Map<String, Object>> recDtos = new ArrayList<>();
        for (GuideArticleRecommendation r : recs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("note", r.getNote());
            Fzwp item = r.getItem();
            if (item != null) {
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("id", item.getId());
                im.put("itemName", item.getItemName());
                im.put("price", item.getPrice());
                im.put("imageUrl", item.getImageUrl());
                im.put("targetDisease", item.getTargetDisease());
                m.put("item", im);
            }
            recDtos.add(m);
        }
        result.put("recommendations", recDtos);

        List<GuideArticleComment> comments = commentRepository.findByArticleIdOrderByCreatedAtAsc(article.getId());
        result.put("comments", buildCommentTree(comments));

        boolean favorited = false;
        try {
            User user = currentUser(request);
            Optional<GuideArticleFavorite> favOpt = favoriteRepository.findByUserAndArticle(user, article);
            favorited = favOpt.isPresent();
        } catch (IllegalStateException ignored) {
            // 未登录时不返回错误，只标记为未收藏
        }
        result.put("favorited", favorited);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/articles")
    public ResponseEntity<?> createArticle(@RequestBody GuideCreateArticleRequest req) {
        String username = Optional.ofNullable(req.getAuthorUsername()).map(String::trim).orElse("");
        if (username.isEmpty()) {
            return ResponseEntity.badRequest().body("作者账号不能为空");
        }
        Optional<User> uOpt = userRepository.findByUsername(username);
        if (uOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("作者不存在");
        }
        User author = uOpt.get();
        if (!"expert".equals(author.getUserType())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("只有农林专家可以发布技术指导文章");
        }
        String title = Optional.ofNullable(req.getTitle()).map(String::trim).orElse("");
        String content = Optional.ofNullable(req.getContent()).map(String::trim).orElse("");
        if (title.isEmpty() || content.isEmpty()) {
            return ResponseEntity.badRequest().body("标题和内容不能为空");
        }
        GuideArticle article = GuideArticle.builder()
                .title(title)
                .content(content)
                .coverImageUrl(Optional.ofNullable(req.getCoverImageUrl()).map(String::trim).orElse(null))
                .imageUrls(serializeImageUrls(req.getImageUrls()))
                .author(author)
                .build();
        article = articleRepository.save(article);

        List<Long> itemIds = Optional.ofNullable(req.getRecommendedItemIds()).orElseGet(Collections::emptyList);
        if (!itemIds.isEmpty()) {
            List<Fzwp> items = fzwpRepository.findAllById(itemIds);
            List<GuideArticleRecommendation> toSave = new ArrayList<>();
            for (Fzwp it : items) {
                GuideArticleRecommendation rec = GuideArticleRecommendation.builder()
                        .article(article)
                        .item(it)
                        .note(null)
                        .build();
                toSave.add(rec);
            }
            recommendationRepository.saveAll(toSave);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", article.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(payload);
    }

    @PostMapping("/articles/{id}/comments")
    public ResponseEntity<?> createComment(@PathVariable Long id, @RequestBody GuideCreateCommentRequest req) {
        Optional<GuideArticle> aOpt = articleRepository.findById(id);
        if (aOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("文章不存在");
        }
        String username = Optional.ofNullable(req.getAuthorUsername()).map(String::trim).orElse("");
        if (username.isEmpty()) {
            return ResponseEntity.badRequest().body("评论用户不能为空");
        }
        Optional<User> uOpt = userRepository.findByUsername(username);
        if (uOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("用户不存在");
        }
        String content = Optional.ofNullable(req.getContent()).map(String::trim).orElse("");
        if (content.isEmpty()) {
            return ResponseEntity.badRequest().body("评论内容不能为空");
        }
        GuideArticleComment comment = new GuideArticleComment();
        comment.setArticle(aOpt.get());
        comment.setAuthor(uOpt.get());
        comment.setContent(content);
        if (req.getParentId() != null) {
            commentRepository.findById(req.getParentId()).ifPresent(comment::setParent);
        }
        comment = commentRepository.save(comment);

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", comment.getId());
        dto.put("content", comment.getContent());
        dto.put("createdAt", comment.getCreatedAt());
        dto.put("author", chatService.userBrief(comment.getAuthor()));
        dto.put("parentId", comment.getParent() != null ? comment.getParent().getId() : null);
        dto.put("replies", Collections.emptyList());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/articles/{id}/comments")
    public ResponseEntity<?> listComments(@PathVariable Long id) {
        if (!articleRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("文章不存在");
        }
        List<GuideArticleComment> comments = commentRepository.findByArticleIdOrderByCreatedAtAsc(id);
        return ResponseEntity.ok(buildCommentTree(comments));
    }

    @PostMapping("/articles/{id}/favorite/toggle")
    public ResponseEntity<?> toggleFavorite(@PathVariable Long id, HttpServletRequest request) {
        User user;
        try {
            user = currentUser(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }

        Optional<GuideArticle> opt = articleRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("文章不存在");
        }
        GuideArticle article = opt.get();

        Optional<GuideArticleFavorite> existing = favoriteRepository.findByUserAndArticle(user, article);
        boolean favorited;
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            favorited = false;
        } else {
            GuideArticleFavorite fav = GuideArticleFavorite.builder()
                    .user(user)
                    .article(article)
                    .createdAt(LocalDateTime.now())
                    .build();
            favoriteRepository.save(fav);
            favorited = true;
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("articleId", article.getId());
        resp.put("favorited", favorited);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/articles/favorites")
    public ResponseEntity<?> listFavorites(HttpServletRequest request) {
        User user;
        try {
            user = currentUser(request);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
        List<GuideArticleFavorite> list = favoriteRepository.findByUserOrderByCreatedAtDesc(user);
        List<Map<String, Object>> out = new ArrayList<>();
        for (GuideArticleFavorite fav : list) {
            GuideArticle article = fav.getArticle();
            if (article == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", fav.getId());
            m.put("articleId", article.getId());
            m.put("title", article.getTitle());
            m.put("coverImageUrl", article.getCoverImageUrl());
            m.put("createdAt", article.getCreatedAt());
            m.put("favoritedAt", fav.getCreatedAt());
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    private List<Map<String, Object>> buildCommentTree(List<GuideArticleComment> comments) {
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();
        for (GuideArticleComment c : comments) {
            Map<String, Object> dto = map.computeIfAbsent(c.getId(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", c.getId());
                m.put("content", c.getContent());
                m.put("createdAt", c.getCreatedAt());
                m.put("author", chatService.userBrief(c.getAuthor()));
                m.put("parentId", c.getParent() != null ? c.getParent().getId() : null);
                m.put("replies", new ArrayList<Map<String, Object>>());
                return m;
            });
            Long parentId = c.getParent() != null ? c.getParent().getId() : null;
            if (parentId == null) {
                roots.add(dto);
            } else {
                Map<String, Object> parentDto = map.get(parentId);
                if (parentDto == null) {
                    roots.add(dto);
                } else {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> replies = (List<Map<String, Object>>) parentDto.get("replies");
                    replies.add(dto);
                }
            }
        }
        return roots;
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
        List<String> cleaned = urls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (cleaned.isEmpty()) {
            return null;
        }
        String joined = cleaned.stream()
                .map(s -> "\"" + s.replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(","));
        return "[" + joined + "]";
    }
}
