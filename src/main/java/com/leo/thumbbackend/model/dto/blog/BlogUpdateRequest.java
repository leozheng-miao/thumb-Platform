package com.leo.thumbbackend.model.dto.blog;

import lombok.Data;

import java.io.Serializable;

@Data
public class BlogUpdateRequest implements Serializable {

    /**
     * 博客 id
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 封面
     */
    private String coverImg;

    /**
     * 内容
     */
    private String content;

    private static final long serialVersionUID = 1L;
}