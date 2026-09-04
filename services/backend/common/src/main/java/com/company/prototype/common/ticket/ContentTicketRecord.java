package com.company.prototype.common.ticket;

import java.time.Instant;

public record ContentTicketRecord(
    String ticketDigest,
    String authorizationType,
    String sessionId,
    long versionId,
    String publishPrefix,
    String entryPath,
    long authEpoch,
    Instant expiresAt
) {}
