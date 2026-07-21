package com.leo.thumbbackend.constant;

public interface ThumbConstant {

    String USER_THUMB_KEY_PREFIX = "thumb:";

    String USER_THUMB_LOCK_KEY_PREFIX = "thumb:lock:";

    long USER_THUMB_CACHE_TTL_DAYS = 7L;

    String TEMP_THUMB_KEY_PREFIX = "thumb:temp:%s";

    String BLOG_EXISTS_KEY = "blog:exists";

    String BLOG_EXISTS_INIT_KEY_PREFIX = "blog:exists:init:%s";

    Long UN_THUMB_CONSTANT = 0L;

}