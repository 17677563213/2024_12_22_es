# Article Mapping 字段说明

## 字段详解

### id
- 类型：keyword
- 说明：文章唯一标识符
- 特点：不分词，完全匹配，用于精确查询

### title
- 类型：text
- 分词器：
  - analyzer: ik_max_word（索引时使用最细粒度分词）
  - search_analyzer: ik_smart（搜索时使用智能分词）
- 说明：文章标题，支持全文检索

### content
- 类型：text
- 分词器：同title
- 说明：文章内容，使用ik分词器实现中文分词

### author
- 类型：keyword
- 说明：作者名称
- 特点：不分词，用于精确匹配作者名

### category
- 类型：text + keyword
- 结构：多字段类型，包含text和keyword两种类型
- 特点：
  - text类型支持分词搜索
  - keyword子字段支持聚合和精确匹配
- 使用场景：既可以全文搜索，也可以进行分类统计

### viewCount
- 类型：integer
- 说明：浏览次数
- 特点：支持范围查询和排序

### createTime
- 类型：date
- 说明：创建时间
- 特点：支持日期范围查询和排序

### commentInfo
- 类型：nested
- 说明：评论信息（嵌套对象）
- 特点：保持对象数组的独立性
- 子字段：
  - commentCount：评论数量（integer类型）
  - lastComment：最新评论内容（text类型）
- 使用场景：适用于需要精确匹配数组中对象的查询场景

## 查询示例

### 标题分词搜索
```json
GET /articles/_search
{
  "query": {
    "match": {
      "title": "搜索关键词"
    }
  }
}
```

### 分类聚合统计
```json
GET /articles/_search
{
  "aggs": {
    "category_count": {
      "terms": {
        "field": "category.keyword"
      }
    }
  }
}
```

### 嵌套查询评论
```json
GET /articles/_search
{
  "query": {
    "nested": {
      "path": "commentInfo",
      "query": {
        "range": {
          "commentInfo.commentCount": {
            "gte": 10
          }
        }
      }
    }
  }
}
```
