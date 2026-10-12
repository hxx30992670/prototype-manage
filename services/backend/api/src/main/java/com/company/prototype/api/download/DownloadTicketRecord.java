package com.company.prototype.api.download;

import java.util.Optional;

public record DownloadTicketRecord(Type type, String prototypePublicId, String resourcePublicId) {

    private static final String PREFIX = "internal-v1";

    public enum Type {
        VERSION,
        ATTACHMENT
    }

    public String encode() {
        return String.join("|", PREFIX, type.name(), prototypePublicId, resourcePublicId);
    }

    public static Optional<DownloadTicketRecord> parse(String value) {
        if (value == null || !value.startsWith(PREFIX + "|")) {
            return Optional.empty();
        }
        String[] parts = value.split("\\|", -1);
        if (parts.length != 4 || parts[2].isBlank() || parts[3].isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new DownloadTicketRecord(Type.valueOf(parts[1]), parts[2], parts[3]));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
