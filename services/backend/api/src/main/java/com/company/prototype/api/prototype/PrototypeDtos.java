package com.company.prototype.api.prototype;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class PrototypeDtos {

    private PrototypeDtos() {}

    public record CreatePrototypeRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,50}", message = "原型编码必须为2-50位字母、数字、下划线或连字符")
        String code,

        @NotBlank @Size(min = 2, max = 100, message = "原型名称长度必须在2-100之间")
        String name,

        @Size(max = 500, message = "原型描述最多500字符")
        String description,

        @Size(max = 2000, message = "公开摘要最多2000字符")
        String publicSummary,

        @NotBlank(message = "分类不能为空")
        String categoryId,

        String ownerId,

        Set<String> ownerIds,

        String visibility,

        Set<String> tagIds
    ) {}

    public record UpdatePrototypeRequest(
        @NotBlank @Size(min = 2, max = 100, message = "原型名称长度必须在2-100之间")
        String name,

        @Size(max = 500, message = "原型描述最多500字符")
        String description,

        @Size(max = 2000, message = "公开摘要最多2000字符")
        String publicSummary,

        @NotBlank(message = "分类不能为空")
        String categoryId,

        String ownerId,

        Set<String> ownerIds,

        String visibility,

        Set<String> tagIds
    ) {}

    public record UpdateReviewStatusRequest(
        @NotBlank(message = "评审状态不能为空")
        String reviewStatus,

        String note
    ) {}

    public record ArchiveRequest(
        boolean archived
    ) {}

    public record UserSummary(
        String publicId,
        String username,
        String displayName
    ) {}

    public record CategorySummary(
        String code,
        String name
    ) {}

    public record TagSummary(
        String name,
        String color
    ) {}

    public record PrototypeResponse(
        String publicId,
        String code,
        String name,
        String description,
        String publicSummary,
        String visibility,
        String reviewStatus,
        boolean archived,
        CategorySummary category,
        UserSummary createdBy,
        UserSummary owner,
        List<UserSummary> owners,
        Set<TagSummary> tags,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt,
        Integer currentVersionNo,
        String currentVersionStatus
    ) {}

    public record Pagination(
        int page,
        int pageSize,
        long total,
        int totalPages
    ) {}

    public record PrototypePageResponse(
        List<PrototypeResponse> data,
        Pagination pagination
    ) {}
}
