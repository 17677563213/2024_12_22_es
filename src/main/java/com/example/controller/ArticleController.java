package com.example.controller;

import com.example.model.Article;
import com.example.service.ElasticsearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author wxy
 */
@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    @Autowired
    private ElasticsearchService elasticsearchService;

    @GetMapping("/search")
    public List<Article> search(@RequestParam String keyword) throws IOException {
        return elasticsearchService.searchWithHighlight(keyword);
    }

    @GetMapping("/nested")
    public List<Article> nestedSearch(@RequestParam Integer minCommentCount) throws IOException {
        return elasticsearchService.nestedQuery(minCommentCount);
    }

    @GetMapping("/categories")
    public Map<String, Long> getCategoryStats() throws IOException {
        return elasticsearchService.aggregateByCategory();
    }

    @GetMapping("/advanced-search")
    public List<Article> advancedSearch(
            @RequestParam String keyword,
            @RequestParam String category,
            @RequestParam Integer minViewCount) throws IOException {
        return elasticsearchService.boolQueryWithScoreControl(keyword, category, minViewCount);
    }
}
