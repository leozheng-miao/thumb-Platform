package com.leo.thumbbackend.manager.cache;

public class AddResult {

    private final String expelledKey;
    private final boolean hotKey;
    private final String currentKey;

    public AddResult(String expelledKey, boolean hotKey, String currentKey) {
        this.expelledKey = expelledKey;
        this.hotKey = hotKey;
        this.currentKey = currentKey;
    }

    public String getExpelledKey() {
        return expelledKey;
    }

    public String expelledKey() {
        return expelledKey;
    }

    public boolean isHotKey() {
        return hotKey;
    }

    public String getCurrentKey() {
        return currentKey;
    }

    public String currentKey() {
        return currentKey;
    }
}
