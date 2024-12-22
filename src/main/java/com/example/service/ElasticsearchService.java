package com.example.service;

import com.alibaba.fastjson.JSON;
import com.example.model.Article;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * @author wxy
 */
@Slf4j
@Service
public class ElasticsearchService {

    private static final String INDEX_NAME = "articles";
    
    @Autowired
    private RestHighLevelClient client;

    // 1. 分词查询 + 高亮显示
    public List<Article> searchWithHighlight(String keyword) throws IOException {
        SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 分词查询
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .should(QueryBuilders.matchQuery("title", keyword))
                .should(QueryBuilders.matchQuery("content", keyword));

        // 高亮设置
        HighlightBuilder highlightBuilder = new HighlightBuilder()
                .field("title")
                .field("content")
                .preTags("<em>")
                .postTags("</em>");

        sourceBuilder.query(boolQuery);
        sourceBuilder.highlighter(highlightBuilder);
        searchRequest.source(sourceBuilder);

        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        return parseHighlightResponse(response);
    }

    // 2. 嵌套查询
    public List<Article> nestedQuery(Integer minCommentCount) throws IOException {
        SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        sourceBuilder.query(QueryBuilders.nestedQuery(
                "commentInfo",
                QueryBuilders.rangeQuery("commentInfo.commentCount").gte(minCommentCount),
                ScoreMode.None));

        searchRequest.source(sourceBuilder);
        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        return parseResponse(response);
    }

    /**
     * 按文章分类进行聚合统计
     * 统计每个分类下的文章数量
     * 使用category.keyword进行精确聚合，因为keyword类型不会进行分词
     *
     * @return Map<分类名称, 文章数量>
     * @throws IOException 如果ES查询出现异常
     */
    public Map<String, Long> aggregateByCategory() throws IOException {
        // 1. 创建搜索请求
        SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 2. 构建聚合查询
        // terms聚合：根据字段值分组计数
        // field("category.keyword")：使用category字段的keyword子字段，确保精确匹配
        sourceBuilder.aggregation(AggregationBuilders
                .terms("category_agg")    // 聚合名称，用于后续获取结果
                .field("category.keyword"));
        
        // 3. 设置size为0，因为我们只关心聚合结果，不需要返回具体的文档
        sourceBuilder.size(0);
        searchRequest.source(sourceBuilder);

        // 4. 执行聚合查询
        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        
        // 5. 解析聚合结果
        // 通过聚合名称"category_agg"获取Terms类型的聚合结果
        Terms categoryAgg = response.getAggregations().get("category_agg");

        // 6. 转换结果格式
        Map<String, Long> result = new HashMap<>();
        // 遍历每个分类桶(bucket)，获取分类名称和文档计数
        for (Terms.Bucket bucket : categoryAgg.getBuckets()) {
            result.put(bucket.getKeyAsString(), bucket.getDocCount());
        }
        return result;
    }

    /**
     * 多条件复合查询，并对查询结果进行自定义评分控制
     * 包含以下功能：
     * 1. Bool查询：组合多个查询条件
     * 2. 自定义评分：根据浏览量和发布时间调整文档得分
     * 
     * @param keyword 标题关键词
     * @param category 文章分类（精确匹配）
     * @param minViewCount 最小浏览量
     * @return 符合条件的文章列表，按照自定义评分排序
     * @throws IOException ES查询异常
     */
    public List<Article> boolQueryWithScoreControl(String keyword, String category, Integer minViewCount) throws IOException {
        SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 1. 构建bool查询
        // must：必须匹配，参与评分
        // filter：必须匹配，不参与评分（性能更好）
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(QueryBuilders.matchQuery("title", keyword))        // 标题必须包含关键词
                .filter(QueryBuilders.termQuery("category.keyword", category))  // 分类精确匹配
                .filter(QueryBuilders.rangeQuery("viewCount").gte(minViewCount));  // 浏览量大于等于指定值

        // 2. 构建自定义评分查询
        FunctionScoreQueryBuilder functionScoreQuery = QueryBuilders.functionScoreQuery(
                boolQuery,  // 基础查询
                new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                        // 2.1 浏览量评分函数
                        // fieldValueFactor：基于字段值计算分数
                        // modifier：LOG1P表示使用log(1 + number)处理原始值，避免数值差异过大
                        // factor：加权因子，提高浏览量对最终分数的影响
                        new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                                ScoreFunctionBuilders.fieldValueFactorFunction("viewCount")
                                        .modifier(FieldValueFactorFunction.Modifier.LOG1P)
                                        .factor(1.2f)  // 提高20%的权重
                        ),
                        // 2.2 时间衰减函数
                        // exponentialDecayFunction：指数衰减函数
                        // origin：基准点，使用"now"表示当前时间
                        // scale：衰减速度，30天衰减一半
                        new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                                ScoreFunctionBuilders.exponentialDecayFunction(
                                        "createTime",  // 待计算字段
                                        "now",        // 基准点（当前时间）
                                        "30d"         // 30天衰减一半
                                )
                        )
                })
                // 3. 设置评分模式
                // MULTIPLY：最终分数 = 原始分数 * 所有评分函数的结果
                .boostMode(CombineFunction.MULTIPLY);

        // 4. 设置查询并执行
        sourceBuilder.query(functionScoreQuery);
        searchRequest.source(sourceBuilder);

        // 5. 执行查询并解析结果
        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        return parseResponse(response);
    }

    // 解析高亮响应
    private List<Article> parseHighlightResponse(SearchResponse response) {
        List<Article> articles = new ArrayList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            Article article = JSON.parseObject(hit.getSourceAsString(), Article.class);
            
            // 处理高亮字段
            Map<String, HighlightField> highlightFields = hit.getHighlightFields();
            if (highlightFields.containsKey("title")) {
                Text[] fragments = highlightFields.get("title").fragments();
                if (fragments != null && fragments.length > 0) {
                    article.setTitle(fragments[0].string());
                }
            }
            if (highlightFields.containsKey("content")) {
                Text[] fragments = highlightFields.get("content").fragments();
                if (fragments != null && fragments.length > 0) {
                    article.setContent(fragments[0].string());
                }
            }
            articles.add(article);
        }
        return articles;
    }

    // 解析普通响应
    private List<Article> parseResponse(SearchResponse response) {
        List<Article> articles = new ArrayList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            articles.add(JSON.parseObject(hit.getSourceAsString(), Article.class));
        }
        return articles;
    }
}
