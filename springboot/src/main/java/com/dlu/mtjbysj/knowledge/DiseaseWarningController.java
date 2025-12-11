package com.dlu.mtjbysj.knowledge;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/warn")
@CrossOrigin
@RequiredArgsConstructor
public class DiseaseWarningController {

    private final BhxxRepository bhxxRepository;

    @GetMapping("/diseases")
    public List<Map<String, Object>> warn(
            @RequestParam(value = "area", required = false) String area,
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "weather", required = false) String weather
    ) {
        int m = (month == null || month < 1 || month > 12) ? LocalDate.now().getMonthValue() : month;
        int lim = (limit == null || limit <= 0 || limit > 50) ? 6 : limit;

        String areaNorm = normalizeArea(Optional.ofNullable(area).orElse(""));
        List<Bhxx> candidates = new ArrayList<>();
        if (!areaNorm.isBlank()) {
            // 先用原始关键词匹配
            candidates.addAll(bhxxRepository.findByDistributionAreaContaining(areaNorm));
            if (candidates.isEmpty()) {
                // 尝试去掉后缀匹配
                String shorter = stripSuffix(areaNorm);
                if (!shorter.equals(areaNorm)) {
                    candidates.addAll(bhxxRepository.findByDistributionAreaContaining(shorter));
                }
            }
            // 若区域仍未命中（如只获取到省级：云南），退化为全量后再按时间/天气筛选
            if (candidates.isEmpty()) {
                candidates.addAll(bhxxRepository.findAll());
            }
        } else {
            // 如果没有提供区域，则退化为全部
            candidates.addAll(bhxxRepository.findAll());
        }

        // 过滤分布时间，并根据天气打分
        List<Map<String, Object>> out = new ArrayList<>();
        for (Bhxx b : candidates) {
            String distTime = Optional.ofNullable(b.getDistributionTime()).orElse("");
            if (matchesMonth(distTime, m)) {
                int score = weatherScore(distTime, weather);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("plantName", Optional.ofNullable(b.getPlantName()).orElse(""));
                item.put("diseaseName", Optional.ofNullable(b.getDiseaseName()).orElse(""));
                item.put("preventionMethod", Optional.ofNullable(b.getPreventionMethod()).orElse(""));
                Map<String, Object> match = new LinkedHashMap<>();
                match.put("distributionArea", Optional.ofNullable(b.getDistributionArea()).orElse(""));
                match.put("distributionTime", distTime);
                match.put("areaMatched", true);
                match.put("month", m);
                match.put("weatherMatched", score > 0);
                item.put("match", match);
                item.put("score", score);
                item.put("weatherMatched", score > 0);
                out.add(item);
            }
        }
        // 若按月份过滤后无结果，则退化为忽略月份，仅按天气相关度排序
        if (out.isEmpty()) {
            List<Map<String, Object>> fallback = new ArrayList<>();
            for (Bhxx b : candidates) {
                String distTime = Optional.ofNullable(b.getDistributionTime()).orElse("");
                int score = weatherScore(distTime, weather);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("plantName", Optional.ofNullable(b.getPlantName()).orElse(""));
                item.put("diseaseName", Optional.ofNullable(b.getDiseaseName()).orElse(""));
                item.put("preventionMethod", Optional.ofNullable(b.getPreventionMethod()).orElse(""));
                Map<String, Object> match = new LinkedHashMap<>();
                match.put("distributionArea", Optional.ofNullable(b.getDistributionArea()).orElse(""));
                match.put("distributionTime", distTime);
                match.put("areaMatched", true);
                match.put("month", m);
                match.put("fallbackNoMonth", true);
                item.put("match", match);
                item.put("score", score);
                item.put("weatherMatched", score > 0);
                fallback.add(item);
            }
            fallback.sort((a, b) -> {
                int as = ((Number) a.getOrDefault("score", 0)).intValue();
                int bs = ((Number) b.getOrDefault("score", 0)).intValue();
                return Integer.compare(bs, as);
            });
            if (fallback.size() > lim) return fallback.subList(0, lim);
            return fallback;
        }

        // 按分数降序，限制条数
        out.sort((a, b) -> {
            int as = ((Number) a.getOrDefault("score", 0)).intValue();
            int bs = ((Number) b.getOrDefault("score", 0)).intValue();
            return Integer.compare(bs, as);
        });
        if (out.size() > lim) return out.subList(0, lim);
        return out;
    }

    private static String normalizeArea(String s) {
        if (s == null) return "";
        String t = s.trim();
        t = t.replace("自治区", "");
        t = t.replace("特别行政区", "");
        t = t.replace("市", "");
        t = t.replace("省", "");
        return t;
    }

    private static String stripSuffix(String s) {
        if (s.endsWith("市") || s.endsWith("省")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static boolean matchesMonth(String timeStr, int month) {
        if (timeStr == null || timeStr.isBlank()) return true; // 无时间信息则默认不过滤
        String s = timeStr.replace(" ", "");
        if (s.contains("全年") || s.contains("四季")) return true;

        // 季节判断
        if (s.contains("春")) { if (inMonths(month, 3,4,5)) return true; }
        if (s.contains("夏")) { if (inMonths(month, 6,7,8)) return true; }
        if (s.contains("秋")) { if (inMonths(month, 9,10,11)) return true; }
        if (s.contains("冬")) { if (inMonths(month, 12,1,2)) return true; }

        // 解析 5-8月、7月、7、7-9、3–5 等（包含 unicode 短横/长横）
        Pattern range = Pattern.compile("(1[0-2]|[1-9])\\s*[-~—–至到]+\\s*(1[0-2]|[1-9])\\s*月?");
        Matcher mr = range.matcher(s);
        while (mr.find()) {
            int a = Integer.parseInt(mr.group(1));
            int b = Integer.parseInt(mr.group(2));
            if (monthInRange(month, a, b)) return true;
        }
        Pattern single = Pattern.compile("(?<![0-9])(1[0-2]|[1-9])\\s*月(?![0-9])");
        Matcher ms = single.matcher(s);
        while (ms.find()) {
            int a = Integer.parseInt(ms.group(1));
            if (a == month) return true;
        }
        // 逗号/顿号分隔的月
        Pattern list = Pattern.compile("(1[0-2]|[1-9])");
        if (s.contains("月")) {
            Matcher ml = list.matcher(s);
            while (ml.find()) {
                int a = Integer.parseInt(ml.group(1));
                if (a == month) return true;
            }
        }
        return false;
    }

    private static boolean monthInRange(int m, int a, int b) {
        if (a <= b) return m >= a && m <= b;
        // 跨年区间，如 11-2
        return (m >= a && m <= 12) || (m >= 1 && m <= b);
    }

    private static boolean inMonths(int m, int... months) {
        for (int x : months) if (x == m) return true;
        return false;
    }

    private static int weatherScore(String distTime, String weatherText) {
        if (weatherText == null || weatherText.isBlank()) return 0;
        String w = weatherText;
        String t = Optional.ofNullable(distTime).orElse("");
        int score = 0;
        // 简单关联：雨/雪/阴/雾 -> 湿；晴 -> 干
        boolean isWet = containsAny(w, "雨", "雪", "阴", "雾");
        boolean isDry = w.contains("晴");
        if (isWet && containsAny(t, "湿", "雨", "潮")) score += 1;
        if (isDry && containsAny(t, "干", "旱", "晴")) score += 1;
        // 温度倾向（若文本中有所体现）
        if (containsAny(w, "热") && containsAny(t, "热", "高温", "温暖")) score += 1;
        if (containsAny(w, "冷") && containsAny(t, "冷", "低温", "凉")) score += 1;
        return score;
    }

    private static boolean containsAny(String s, String... keys) {
        if (s == null) return false;
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }
}
