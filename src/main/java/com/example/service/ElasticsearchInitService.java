package com.example.service;

import com.alibaba.fastjson.JSON;
import com.example.model.Article;
import com.example.util.ResourceUtil;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author wxy
 */
@Slf4j
@Service
public class ElasticsearchInitService {

    private static final String INDEX_NAME = "articles";
    private static final String MAPPING_FILE = "es/article-mapping.json";

    @Autowired
    private RestHighLevelClient client;

    @EventListener(ApplicationReadyEvent.class)
    public void initIndex() throws IOException {
        try {
            // 检查索引是否存在
            boolean exists = client.indices().exists(new GetIndexRequest(INDEX_NAME), RequestOptions.DEFAULT);
            if (exists) {
                // 如果存在，删除旧索引
                client.indices().delete(new DeleteIndexRequest(INDEX_NAME), RequestOptions.DEFAULT);
            }

            // 创建新索引
            CreateIndexRequest createIndexRequest = new CreateIndexRequest(INDEX_NAME);
            createIndexRequest.settings(Settings.builder()
                    .put("index.number_of_shards", 3)
                    .put("index.number_of_replicas", 1)
                    .build());

            // 从文件加载mapping
            String mapping = ResourceUtil.readResourceFile(MAPPING_FILE);
            createIndexRequest.mapping(mapping, XContentType.JSON);
            
            client.indices().create(createIndexRequest, RequestOptions.DEFAULT);

            // 初始化测试数据
            initTestData();
            
            log.info("Elasticsearch index initialization completed successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Elasticsearch index", e);
            throw e;
        }
    }

    private void initTestData() throws IOException {
        List<Article> articles = createTestArticles();
        BulkRequest bulkRequest = new BulkRequest();

        for (Article article : articles) {
            IndexRequest indexRequest = new IndexRequest(INDEX_NAME)
                    .id(article.getId())
                    .source(JSON.toJSONString(article), XContentType.JSON);
            bulkRequest.add(indexRequest);
        }

        BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
        if (bulkResponse.hasFailures()) {
            log.error("Bulk index has failures: {}", bulkResponse.buildFailureMessage());
        }
    }

    private List<Article> createTestArticles() {
        List<Article> articles = new ArrayList<>();
        
        // 技术类文章
        addArticle(articles, "Spring Boot 最佳实践", 
                "Spring Boot 是一个流行的Java框架，本文将介绍其最佳实践和使用技巧...",
                "技术", "John Doe", 1500);
        
        addArticle(articles, "Elasticsearch 深入浅出", 
                "Elasticsearch是一个强大的搜索引擎，本文将详细讲解其核心概念...",
                "技术", "Jane Smith", 2000);
        
        addArticle(articles, "Docker 容器化部署指南", 
                "Docker让应用部署变得更简单，本文将介绍Docker的基本概念和实践...",
                "技术", "Mike Johnson", 1800);

        // 设计类文章
        addArticle(articles, "UI设计趋势2024", 
                "2024年的UI设计将更注重用户体验，本文将分析最新的设计趋势...",
                "设计", "Lisa Wang", 1200);
        
        addArticle(articles, "响应式设计实战", 
                "如何打造完美的响应式网站？本文将分享实战经验和技巧...",
                "设计", "Tom Wilson", 900);

        // 产品类文章
        addArticle(articles, "产品经理成长之路", 
                "作为一名产品经理，需要具备哪些核心能力？本文将为你解答...",
                "产品", "Sarah Chen", 2500);
        
        addArticle(articles, "用户调研方法论", 
                "好的产品离不开深入的用户调研，本文将介绍实用的调研方法...",
                "产品", "David Lee", 1700);

        return articles;
    }

    private void addArticle(List<Article> articles, String title, String content, 
            String category, String author, int viewCount) {
        Article article = new Article();
        article.setId(UUID.randomUUID().toString());
        article.setTitle(title);
        article.setContent(content);
        article.setCategory(category);
        article.setAuthor(author);
        article.setViewCount(viewCount);
        article.setCreateTime(System.currentTimeMillis());

        // 设置评论信息
        Article.CommentInfo commentInfo = new Article.CommentInfo();
        commentInfo.setCommentCount((int)(Math.random() * 100));
        commentInfo.setLastComment("这是一个很好的文章！");
        article.setCommentInfo(commentInfo);

        articles.add(article);
    }
}
