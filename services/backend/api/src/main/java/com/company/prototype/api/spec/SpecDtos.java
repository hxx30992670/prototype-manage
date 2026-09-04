package com.company.prototype.api.spec;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public class SpecDtos {

    public record UpdateSpecRequest(
        String goal,
        String coreFlow,
        String interactionRules,
        String businessConstraints,
        String dataRequirements,
        String acceptanceNotes,
        String markdownExtra,
        @NotNull Long rowVersion
    ) {}

    public record SpecResponse(
        String prototypePublicId,
        String goal,
        String coreFlow,
        String interactionRules,
        String businessConstraints,
        String dataRequirements,
        String acceptanceNotes,
        String markdownExtra,
        Long rowVersion,
        String updatedBy,
        Instant updatedAt
    ) {}
}
