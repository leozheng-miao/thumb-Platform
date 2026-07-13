package com.leo.thumbbackend.mapper;

import com.leo.thumbbackend.model.entity.Blog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
* @author zhengsmacbook
* @description 针对表【blog】的数据库操作Mapper
* @createDate 2026-07-05 18:21:27
* @Entity com.leo.thumbbackend.model.entity.Blog
*/
public interface BlogMapper extends BaseMapper<Blog> {
    void batchUpdateThumbCount(@Param("countMap") Map<Long, Long> countMap);
}