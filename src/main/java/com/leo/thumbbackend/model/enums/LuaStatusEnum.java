package com.leo.thumbbackend.model.enums;

import lombok.Getter;

@Getter
public enum LuaStatusEnum {  
    // 成功  
    SUCCESS(1L),  
    // 失败  
    FAIL(-1L),
    // 博客不存在
    BLOG_NOT_EXIST(-2L);
  
    private final long value;  
  
    LuaStatusEnum(long value) {  
        this.value = value;  
    }  
}
