package com.mowoo.mowoo_backend.attachment.storage;

public interface ObjectStorage {
    void put(String key, byte[] bytes, String mimeType);
    String signedUrl(String key, String mimeType);
    void delete(String key);
}
