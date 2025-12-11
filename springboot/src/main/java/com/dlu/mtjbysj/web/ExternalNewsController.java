package com.dlu.mtjbysj.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/external-news")
@CrossOrigin
public class ExternalNewsController {

    private static final String SOURCE_URL = "https://www.moa.gov.cn/xw/qg/";
    private static final String SOURCE_URL_HTTP = "http://www.moa.gov.cn/xw/qg/";

    private Document fetchDocument() throws IOException {
        IOException last = null;
        for (String url : new String[] { SOURCE_URL, SOURCE_URL_HTTP }) {
            try {
                return Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
                        .timeout(8000)
                        .ignoreContentType(true)
                        .get();
            } catch (IOException e) {
                last = e;
                log.warn("Failed to fetch external news page from {}", url, e);
            }
        }
        if (last != null) throw last;
        throw new IOException("Unknown error when fetching external news page");
    }

    @GetMapping
    public ResponseEntity<?> listExternalNews() {
        List<Map<String, Object>> items = new ArrayList<>();

        try {
            // 
            log.info("[ExternalNews] Fetching news data from ");

            Document doc = fetchDocument();
            Elements links = doc.select("a[href*=/xw/qg/]");
            Set<String> seen = new LinkedHashSet<>();

            for (Element a : links) {
                String href = a.absUrl("href");
                if (href == null || !href.contains("/xw/qg/") || !href.endsWith(".htm")) {
                    continue;
                }

                String title = a.text();
                if (title == null) {
                    continue;
                }
                title = title.replace('\u00A0', ' ').trim();
                if (title.isEmpty()) {
                    continue;
                }

                if (!seen.add(href)) {
                    continue;
                }

                Map<String, Object> m = new LinkedHashMap<>();
                m.put("title", title);
                m.put("url", href);
                items.add(m);

                if (items.size() >= 15) {
                    break;
                }
            }

            // 
            if (items.isEmpty()) {
                addFallbackNews(items);
            }

            log.info("[ExternalNews] Provided {} news items", items.size());

        } catch (Exception e) {
            log.error("Error in ExternalNewsController", e);
            items.clear();
            addFallbackNews(items);
        }

        return ResponseEntity.ok(items);
    }

    private void addFallbackNews(List<Map<String, Object>> items) {
        items.add(createSampleNews("全国信息联播：农业农村重点工作动态", SOURCE_URL));
        items.add(createSampleNews("全国信息联播：各地乡村振兴实践", SOURCE_URL));
        items.add(createSampleNews("全国信息联播：农产品丰收与市场信息", SOURCE_URL));
        items.add(createSampleNews("全国信息联播：农业科技与产业资讯", SOURCE_URL));
    }
    
    /**
     * 
     * 创建示例新闻项
     */
    private Map<String, Object> createSampleNews(String title, String url) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("url", url);
        return m;
    }
}
