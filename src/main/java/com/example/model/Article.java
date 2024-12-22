package com.example.model;

import lombok.Data;

@Data
public class Article {
    private String id;
    private String title;
    private String content;
    private String author;
    private String category;
    private Integer viewCount;
    private Long createTime;
    
    // 嵌套对象
    private CommentInfo commentInfo;
    
    @Data
    public static class CommentInfo {
        private Integer commentCount;
        private String lastComment;
    }
}
