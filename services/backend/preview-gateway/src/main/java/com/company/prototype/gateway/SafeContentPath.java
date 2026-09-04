package com.company.prototype.gateway;

import java.nio.file.Path;
import java.nio.file.Paths;

public class SafeContentPath {

    public static String normalize(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }

        for (char c : relativePath.toCharArray()) {
            if (c < 32 || c == 127) {
                throw new IllegalArgumentException("Invalid characters in content path");
            }
        }

        String clean = relativePath.replace('\\', '/');
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }

        if (clean.matches("^[a-zA-Z]:.*")) {
            throw new IllegalArgumentException("Windows absolute path forbidden");
        }

        Path normalized = Paths.get("/root", clean).normalize();
        if (!normalized.startsWith("/root")) {
            throw new IllegalArgumentException("Path traversal attempt detected");
        }

        String result = normalized.toString().substring("/root".length());
        while (result.startsWith("/")) {
            result = result.substring(1);
        }

        return result;
    }
}
