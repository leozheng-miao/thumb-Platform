package com.leo.thumbbackend.util;

import com.leo.thumbbackend.constant.ThumbConstant;

public class RedisKeyUtil {
  
    public static String getUserThumbKey(Long userId) {  
        return ThumbConstant.USER_THUMB_KEY_PREFIX + userId;  
    }  
  
    /**  
     * 获取 临时点赞记录 key  
     */  
    public static String getTempThumbKey(String time) {  
        return ThumbConstant.TEMP_THUMB_KEY_PREFIX.formatted(time);  
    }

    public static String getThumbLockKey(Long userId, Long blogId) {
        return ThumbConstant.USER_THUMB_LOCK_KEY_PREFIX + userId + ":" + blogId;
    }
  
}