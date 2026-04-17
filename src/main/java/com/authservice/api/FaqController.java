package com.authservice.api;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * FAQ endpoints (ONE-6). Content is DB-backed so it can be edited without
 * publishing a new app version.
 *
 * Public endpoints:
 *   GET /faq            — full FAQ grouped by category
 *   GET /faq/search?q=  — search within questions and answers
 *
 * Admin endpoints (internal, behind /internal/**):
 *   POST   /internal/faq/categories
 *   PUT    /internal/faq/categories/{id}
 *   POST   /internal/faq/items
 *   PUT    /internal/faq/items/{id}
 *   DELETE /internal/faq/items/{id}
 */
@RestController
public class FaqController {

    private final JdbcTemplate jdbc;

    public FaqController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Public ────────────────────────────────────────────────────────────

    @GetMapping("/faq")
    public ResponseEntity<List<Map<String, Object>>> getFaq(
            @RequestParam(required = false, defaultValue = "all") String lang) {

        List<Map<String, Object>> categories = jdbc.query(
                "SELECT * FROM faq_categories WHERE active = TRUE ORDER BY sort_order",
                (rs, n) -> {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("id", rs.getInt("id"));
                    if ("en".equals(lang)) {
                        c.put("name", rs.getString("name_en"));
                    } else if ("es".equals(lang)) {
                        c.put("name", rs.getString("name_es"));
                    } else {
                        c.put("nameEs", rs.getString("name_es"));
                        c.put("nameEn", rs.getString("name_en"));
                    }
                    c.put("icon", rs.getString("icon"));
                    return c;
                });

        List<Map<String, Object>> items = jdbc.query(
                "SELECT * FROM faq_items WHERE active = TRUE ORDER BY category_id, sort_order",
                (rs, n) -> {
                    Map<String, Object> i = new LinkedHashMap<>();
                    i.put("id", rs.getInt("id"));
                    i.put("categoryId", rs.getInt("category_id"));
                    if ("en".equals(lang)) {
                        i.put("question", rs.getString("question_en"));
                        i.put("answer", rs.getString("answer_en"));
                    } else if ("es".equals(lang)) {
                        i.put("question", rs.getString("question_es"));
                        i.put("answer", rs.getString("answer_es"));
                    } else {
                        i.put("questionEs", rs.getString("question_es"));
                        i.put("questionEn", rs.getString("question_en"));
                        i.put("answerEs", rs.getString("answer_es"));
                        i.put("answerEn", rs.getString("answer_en"));
                    }
                    return i;
                });

        Map<Integer, List<Map<String, Object>>> itemsByCat = items.stream()
                .collect(Collectors.groupingBy(i -> (Integer) i.get("categoryId"),
                         LinkedHashMap::new, Collectors.toList()));

        for (Map<String, Object> cat : categories) {
            int catId = (Integer) cat.get("id");
            List<Map<String, Object>> catItems = itemsByCat.getOrDefault(catId, List.of());
            catItems.forEach(i -> i.remove("categoryId"));
            cat.put("items", catItems);
        }

        return ResponseEntity.ok(categories);
    }

    @GetMapping("/faq/search")
    public ResponseEntity<List<Map<String, Object>>> searchFaq(
            @RequestParam String q,
            @RequestParam(required = false, defaultValue = "all") String lang) {
        if (q == null || q.isBlank()) return ResponseEntity.ok(List.of());
        String pattern = "%" + q.toLowerCase() + "%";

        return ResponseEntity.ok(jdbc.query(
                "SELECT i.*, c.name_es AS cat_name_es, c.name_en AS cat_name_en " +
                "FROM faq_items i JOIN faq_categories c ON i.category_id = c.id " +
                "WHERE i.active = TRUE AND c.active = TRUE " +
                "  AND (LOWER(i.question_es) LIKE ? OR LOWER(i.question_en) LIKE ? " +
                "       OR LOWER(i.answer_es) LIKE ? OR LOWER(i.answer_en) LIKE ?) " +
                "ORDER BY c.sort_order, i.sort_order",
                (rs, n) -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", rs.getInt("id"));
                    r.put("categoryNameEs", rs.getString("cat_name_es"));
                    r.put("categoryNameEn", rs.getString("cat_name_en"));
                    if ("en".equals(lang)) {
                        r.put("question", rs.getString("question_en"));
                        r.put("answer", rs.getString("answer_en"));
                    } else if ("es".equals(lang)) {
                        r.put("question", rs.getString("question_es"));
                        r.put("answer", rs.getString("answer_es"));
                    } else {
                        r.put("questionEs", rs.getString("question_es"));
                        r.put("questionEn", rs.getString("question_en"));
                        r.put("answerEs", rs.getString("answer_es"));
                        r.put("answerEn", rs.getString("answer_en"));
                    }
                    return r;
                },
                pattern, pattern, pattern, pattern));
    }

    // ── Admin (internal) ──────────────────────────────────────────────────

    @PostMapping("/internal/faq/categories")
    public ResponseEntity<Map<String, Object>> createCategory(@RequestBody Map<String, Object> body) {
        Integer id = jdbc.queryForObject(
                "INSERT INTO faq_categories (name_es, name_en, icon, sort_order) VALUES (?, ?, ?, ?) RETURNING id",
                Integer.class,
                body.get("nameEs"), body.get("nameEn"),
                body.getOrDefault("icon", null),
                body.getOrDefault("sortOrder", 0));
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PutMapping("/internal/faq/categories/{id}")
    public ResponseEntity<Void> updateCategory(@PathVariable int id, @RequestBody Map<String, Object> body) {
        jdbc.update(
                "UPDATE faq_categories SET name_es = COALESCE(?, name_es), name_en = COALESCE(?, name_en), " +
                "icon = COALESCE(?, icon), sort_order = COALESCE(CAST(? AS INTEGER), sort_order), " +
                "active = COALESCE(CAST(? AS BOOLEAN), active), updated_at = NOW() WHERE id = ?",
                body.get("nameEs"), body.get("nameEn"), body.get("icon"),
                body.get("sortOrder") != null ? body.get("sortOrder").toString() : null,
                body.get("active") != null ? body.get("active").toString() : null,
                id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/internal/faq/items")
    public ResponseEntity<Map<String, Object>> createItem(@RequestBody Map<String, Object> body) {
        Integer id = jdbc.queryForObject(
                "INSERT INTO faq_items (category_id, question_es, question_en, answer_es, answer_en, sort_order) " +
                "VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                Integer.class,
                body.get("categoryId"), body.get("questionEs"), body.get("questionEn"),
                body.get("answerEs"), body.get("answerEn"),
                body.getOrDefault("sortOrder", 0));
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PutMapping("/internal/faq/items/{id}")
    public ResponseEntity<Void> updateItem(@PathVariable int id, @RequestBody Map<String, Object> body) {
        jdbc.update(
                "UPDATE faq_items SET question_es = COALESCE(?, question_es), question_en = COALESCE(?, question_en), " +
                "answer_es = COALESCE(?, answer_es), answer_en = COALESCE(?, answer_en), " +
                "sort_order = COALESCE(CAST(? AS INTEGER), sort_order), " +
                "active = COALESCE(CAST(? AS BOOLEAN), active), updated_at = NOW() WHERE id = ?",
                body.get("questionEs"), body.get("questionEn"),
                body.get("answerEs"), body.get("answerEn"),
                body.get("sortOrder") != null ? body.get("sortOrder").toString() : null,
                body.get("active") != null ? body.get("active").toString() : null,
                id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/internal/faq/items/{id}")
    public ResponseEntity<Void> deactivateItem(@PathVariable int id) {
        jdbc.update("UPDATE faq_items SET active = FALSE, updated_at = NOW() WHERE id = ?", id);
        return ResponseEntity.noContent().build();
    }
}
