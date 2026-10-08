package com.company.prototype.api.prototype;

import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.api.security.PrototypeAuthorizationService;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.common.id.PublicIdGenerator;
import com.company.prototype.persistence.audit.AuditLogEntity;
import com.company.prototype.persistence.audit.AuditLogRepository;
import com.company.prototype.persistence.prototype.CategoryEntity;
import com.company.prototype.persistence.prototype.CategoryRepository;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.prototype.TagEntity;
import com.company.prototype.persistence.prototype.TagRepository;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import com.company.prototype.persistence.version.PrototypeVersionEntity;
import com.company.prototype.persistence.version.PrototypeVersionRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PrototypeService {

    private static final int MAX_OWNERS = 20;
    private static final int MAX_VIEWERS = 50;
    private static final int MAX_DOWNLOADERS = 50;
    private static final Set<String> DOWNLOAD_ACCESS = Set.of("MANAGERS_ONLY", "ALL_VIEWERS", "SELECTED");

    private final PrototypeRepository prototypeRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PrototypeAuthorizationService authorizationService;
    private final PublicIdGenerator publicIdGenerator;
    private final com.company.prototype.api.share.PrototypeShareService shareService;
    private final PrototypeVersionRepository versionRepository;

    public PrototypeService(
        PrototypeRepository prototypeRepository,
        CategoryRepository categoryRepository,
        TagRepository tagRepository,
        UserRepository userRepository,
        AuditLogRepository auditLogRepository,
        PrototypeAuthorizationService authorizationService,
        PublicIdGenerator publicIdGenerator,
        com.company.prototype.api.share.PrototypeShareService shareService,
        PrototypeVersionRepository versionRepository
    ) {
        this.prototypeRepository = prototypeRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.authorizationService = authorizationService;
        this.publicIdGenerator = publicIdGenerator;
        this.shareService = shareService;
        this.versionRepository = versionRepository;
    }

    @Transactional
    public PrototypeDtos.PrototypeResponse createPrototype(PrototypeDtos.CreatePrototypeRequest req, CurrentUser currentUser) {
        if (!currentUser.hasRole("ADMIN") && !currentUser.hasRole("CREATOR")) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        if (prototypeRepository.existsByCodeAndDeletedAtIsNull(req.code())) {
            throw new ApiException(ApiErrorCode.PROTOTYPE_CODE_EXISTS);
        }

        CategoryEntity category = findCategory(req.categoryId());

        UserEntity createdBy = userRepository.findById(currentUser.id())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "当前用户不存在"));
        Set<UserEntity> owners = resolveOwners(req.ownerIds(), req.ownerId(), createdBy, true);

        Set<TagEntity> tags = findTags(req.tagIds());

        PrototypeEntity entity = new PrototypeEntity();
        entity.setPublicId(publicIdGenerator.nextId());
        entity.setCode(req.code());
        entity.setName(req.name());
        entity.setDescription(req.description());
        entity.setPublicSummary(req.publicSummary());
        entity.setCategory(category);
        entity.setCreatedBy(createdBy);
        applyOwners(entity, owners, createdBy);
        String visibility = req.visibility() != null ? req.visibility() : "ALL_INTERNAL";
        entity.setVisibility(visibility);
        applyViewers(entity, visibility, req.viewerIds(), true);
        applyDownloadAccess(entity, visibility, req.downloadAccess(), req.downloaderIds(), true);
        entity.setReviewStatus("DRAFT");
        entity.setArchived(false);
        entity.setTags(tags);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        entity = prototypeRepository.save(entity);

        logAudit("PROTOTYPE_CREATE", entity.getPublicId(), "SUCCESS", currentUser.id(), "创建原型: " + entity.getName());
        return mapToDto(entity);
    }

    @Transactional
    public PrototypeDtos.PrototypeResponse updatePrototype(
        String publicId,
        PrototypeDtos.UpdatePrototypeRequest req,
        Long ifMatchVersion,
        CurrentUser currentUser
    ) {
        PrototypeEntity entity = prototypeRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "原型不存在"));

        if (!authorizationService.canManage(currentUser, entity)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        if (entity.isArchived()) {
            throw new ApiException(ApiErrorCode.PROTOTYPE_ARCHIVED);
        }

        if (ifMatchVersion == null || ifMatchVersion != entity.getRowVersion()) {
            throw new ApiException(ApiErrorCode.RESOURCE_VERSION_CONFLICT, "原型已被他人修改，请刷新后重试");
        }

        CategoryEntity category = findCategory(req.categoryId());
        Set<UserEntity> owners = resolveOwners(req.ownerIds(), req.ownerId(), entity.getCreatedBy(), false);
        if (owners == null) {
            owners = entity.getOwners() != null && !entity.getOwners().isEmpty()
                ? entity.getOwners()
                : Set.of(entity.getOwner() != null ? entity.getOwner() : entity.getCreatedBy());
        }

        Set<TagEntity> tags = findTags(req.tagIds());

        entity.setName(req.name());
        entity.setDescription(req.description());
        entity.setPublicSummary(req.publicSummary());
        entity.setCategory(category);
        applyOwners(entity, owners, entity.getCreatedBy());
        String visibility = req.visibility() != null ? req.visibility() : entity.getVisibility();
        if (req.visibility() != null) {
            entity.setVisibility(req.visibility());
        }
        applyViewers(entity, visibility, req.viewerIds(), false);
        applyDownloadAccess(entity, visibility, req.downloadAccess(), req.downloaderIds(), false);
        entity.setTags(tags);
        entity.setUpdatedAt(Instant.now());

        entity = prototypeRepository.save(entity);

        logAudit("PROTOTYPE_UPDATE", entity.getPublicId(), "SUCCESS", currentUser.id(), "更新原型基本资料: " + entity.getName());
        return mapToDto(entity);
    }

    @Transactional
    public PrototypeDtos.PrototypeResponse updateReviewStatus(
        String publicId,
        PrototypeDtos.UpdateReviewStatusRequest req,
        CurrentUser currentUser
    ) {
        PrototypeEntity entity = prototypeRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "原型不存在"));

        if (!authorizationService.canManage(currentUser, entity)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        if (entity.isArchived()) {
            throw new ApiException(ApiErrorCode.PROTOTYPE_ARCHIVED);
        }

        String oldStatus = entity.getReviewStatus();
        entity.setReviewStatus(req.reviewStatus());
        entity.setUpdatedAt(Instant.now());
        entity = prototypeRepository.save(entity);

        logAudit("PROTOTYPE_REVIEW_STATUS", entity.getPublicId(), "SUCCESS", currentUser.id(),
            "变更评审状态: %s -> %s%s".formatted(oldStatus, req.reviewStatus(), req.note() != null ? " (" + req.note() + ")" : ""));
        return mapToDto(entity);
    }

    @Transactional
    public PrototypeDtos.PrototypeResponse archivePrototype(
        String publicId,
        PrototypeDtos.ArchiveRequest req,
        CurrentUser currentUser
    ) {
        PrototypeEntity entity = prototypeRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "原型不存在"));

        if (!authorizationService.canManage(currentUser, entity)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        entity.setArchived(req.archived());
        entity.setUpdatedAt(Instant.now());
        entity = prototypeRepository.save(entity);

        if (req.archived()) {
            shareService.disableSharesForPrototype(entity.getId());
        }

        String action = req.archived() ? "PROTOTYPE_ARCHIVE" : "PROTOTYPE_UNARCHIVE";
        logAudit(action, entity.getPublicId(), "SUCCESS", currentUser.id(), (req.archived() ? "归档" : "取消归档") + "原型");
        return mapToDto(entity);
    }

    @Transactional
    public void deletePrototype(String publicId, CurrentUser currentUser) {
        PrototypeEntity entity = prototypeRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "原型不存在"));

        if (!authorizationService.canManage(currentUser, entity)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        entity.setDeletedAt(Instant.now());
        prototypeRepository.save(entity);

        // 删除进回收站：停用全部分享、递增 authEpoch、撤销分享会话与票据
        int disabledShares = shareService.disableSharesForPrototype(entity.getId());

        logAudit("PROTOTYPE_DELETE", entity.getPublicId(), "SUCCESS", currentUser.id(),
            "软删除原型至回收站，停用分享链接 " + disabledShares + " 个");
    }

    @Transactional(readOnly = true)
    public PrototypeDtos.PrototypeResponse getPrototype(String publicId, CurrentUser currentUser) {
        PrototypeEntity entity = prototypeRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "原型不存在"));

        if (!authorizationService.canView(currentUser, entity)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        return mapToDto(entity);
    }

    @Transactional(readOnly = true)
    public PrototypeDtos.PrototypePageResponse queryPrototypes(
        String keyword,
        String categoryId,
        String tagId,
        String reviewStatus,
        Boolean archived,
        String createdBy,
        String ownerId,
        int page,
        int pageSize,
        String sortBy,
        String sortOrder,
        CurrentUser currentUser
    ) {
        int validatedPage = Math.max(page, 1);
        int validatedPageSize = Math.min(Math.max(pageSize, 1), 100);

        String sortField = "updatedAt";
        if ("createdAt".equalsIgnoreCase(sortBy) || "name".equalsIgnoreCase(sortBy)) {
            sortField = sortBy;
        }
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(validatedPage - 1, validatedPageSize, Sort.by(direction, sortField));

        Specification<PrototypeEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Not deleted
            predicates.add(cb.isNull(root.get("deletedAt")));

            // 2. Security visibility scope
            if (currentUser == null || !currentUser.hasRole("ADMIN")) {
                Long currentUserId = currentUser != null ? currentUser.id() : -1L;
                Predicate notRestricted = cb.notEqual(root.get("visibility"), "RESTRICTED");
                Predicate isCreator = cb.equal(root.get("createdBy").get("id"), currentUserId);
                predicates.add(cb.or(
                    notRestricted,
                    isCreator,
                    isAssignedOwner(root, query, cb, currentUserId, null),
                    isAssignedViewer(root, query, cb, currentUserId)
                ));
            }

            // 3. Keyword
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim() + "%";
                Predicate nameLike = cb.like(root.get("name"), pattern);
                Predicate codeLike = cb.like(root.get("code"), pattern);
                Predicate descLike = cb.like(root.get("description"), pattern);
                predicates.add(cb.or(nameLike, codeLike, descLike));
            }

            // 4. Category
            if (categoryId != null && !categoryId.isBlank()) {
                predicates.add(cb.equal(root.get("category").get("code"), categoryId.trim()));
            }

            // 5. ReviewStatus
            if (reviewStatus != null && !reviewStatus.isBlank()) {
                predicates.add(cb.equal(root.get("reviewStatus"), reviewStatus.trim()));
            }

            // 6. Archived
            if (archived != null) {
                predicates.add(cb.equal(root.get("archived"), archived));
            }

            // 7. CreatedBy
            if (createdBy != null && !createdBy.isBlank()) {
                if ("me".equalsIgnoreCase(createdBy.trim()) && currentUser != null) {
                    predicates.add(cb.equal(root.get("createdBy").get("id"), currentUser.id()));
                } else {
                    predicates.add(cb.equal(root.get("createdBy").get("publicId"), createdBy.trim()));
                }
            }

            // 8. OwnerId
            if (ownerId != null && !ownerId.isBlank()) {
                if ("me".equalsIgnoreCase(ownerId.trim()) && currentUser != null) {
                    predicates.add(isAssignedOwner(root, query, cb, currentUser.id(), null));
                } else {
                    predicates.add(isAssignedOwner(root, query, cb, null, ownerId.trim()));
                }
            }

            // 9. Tag
            if (tagId != null && !tagId.isBlank()) {
                Join<PrototypeEntity, TagEntity> tagJoin = root.join("tags", JoinType.INNER);
                predicates.add(cb.equal(tagJoin.get("name"), tagId.trim()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<PrototypeEntity> pagedResult = prototypeRepository.findAll(spec, pageable);

        List<PrototypeDtos.PrototypeResponse> list = pagedResult.getContent().stream()
            .map(this::mapToDto)
            .toList();

        PrototypeDtos.Pagination pagination = new PrototypeDtos.Pagination(
            validatedPage,
            validatedPageSize,
            pagedResult.getTotalElements(),
            pagedResult.getTotalPages()
        );

        return new PrototypeDtos.PrototypePageResponse(list, pagination);
    }

    private CategoryEntity findCategory(String categoryRef) {
        return categoryRepository.findByCode(categoryRef)
            .or(() -> {
                try {
                    return categoryRepository.findById(Long.parseLong(categoryRef));
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
            })
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "分类不存在"));
    }

    private Predicate isAssignedOwner(
        Root<PrototypeEntity> root,
        jakarta.persistence.criteria.CriteriaQuery<?> query,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        Long userId,
        String ownerPublicId
    ) {
        Subquery<Long> owned = query.subquery(Long.class);
        Root<PrototypeEntity> ownedRoot = owned.from(PrototypeEntity.class);
        Join<PrototypeEntity, UserEntity> owners = ownedRoot.join("owners", JoinType.INNER);
        List<Predicate> ownedPredicates = new ArrayList<>();
        ownedPredicates.add(cb.equal(ownedRoot.get("id"), root.get("id")));
        if (userId != null) {
            ownedPredicates.add(cb.equal(owners.get("id"), userId));
        } else {
            ownedPredicates.add(cb.equal(owners.get("publicId"), ownerPublicId));
        }
        owned.select(ownedRoot.get("id")).where(ownedPredicates.toArray(new Predicate[0]));

        if (userId != null) {
            return cb.or(cb.equal(root.get("owner").get("id"), userId), root.get("id").in(owned));
        }
        return cb.or(cb.equal(root.get("owner").get("publicId"), ownerPublicId), root.get("id").in(owned));
    }

    private Predicate isAssignedViewer(
        Root<PrototypeEntity> root,
        jakarta.persistence.criteria.CriteriaQuery<?> query,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        Long userId
    ) {
        Subquery<Long> visible = query.subquery(Long.class);
        Root<PrototypeEntity> visibleRoot = visible.from(PrototypeEntity.class);
        Join<PrototypeEntity, UserEntity> viewers = visibleRoot.join("viewers", JoinType.INNER);
        visible.select(visibleRoot.get("id")).where(
            cb.equal(visibleRoot.get("id"), root.get("id")),
            cb.equal(viewers.get("id"), userId)
        );
        return root.get("id").in(visible);
    }

    private Set<UserEntity> resolveOwners(Set<String> ownerIds, String ownerId, UserEntity createdBy, boolean required) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (ownerIds != null) {
            ownerIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .forEach(ids::add);
        }
        if (ownerId != null && !ownerId.isBlank()) {
            ids.add(ownerId.trim());
        }
        if (ids.isEmpty()) {
            if (!required) {
                return null;
            }
            ids.add(createdBy.getPublicId());
        }
        if (ids.size() > MAX_OWNERS) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "负责人最多选择" + MAX_OWNERS + "人");
        }

        List<UserEntity> found = userRepository.findByPublicIdIn(ids);
        Map<String, UserEntity> byPublicId = found.stream()
            .collect(Collectors.toMap(UserEntity::getPublicId, user -> user));

        LinkedHashSet<UserEntity> owners = new LinkedHashSet<>();
        for (String id : ids) {
            UserEntity owner = byPublicId.get(id);
            if (owner == null) {
                throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "原型负责人不存在");
            }
            validateEligibleOwner(owner);
            owners.add(owner);
        }
        return owners;
    }

    private void applyOwners(PrototypeEntity entity, Set<UserEntity> owners, UserEntity createdBy) {
        Set<UserEntity> resolved = owners == null || owners.isEmpty() ? Set.of(createdBy) : owners;
        entity.setOwners(new HashSet<>(resolved));
        entity.setOwner(primaryOwner(resolved, createdBy));
    }

    private UserEntity primaryOwner(Set<UserEntity> owners, UserEntity createdBy) {
        return owners.stream()
            .filter(owner -> createdBy != null && createdBy.getId() != null && createdBy.getId().equals(owner.getId()))
            .findFirst()
            .orElseGet(() -> owners.iterator().next());
    }

    private void applyViewers(PrototypeEntity entity, String visibility, Set<String> viewerIds, boolean creating) {
        if (!"RESTRICTED".equalsIgnoreCase(visibility)) {
            entity.setViewers(new HashSet<>());
            return;
        }
        Set<UserEntity> viewers = resolveViewers(viewerIds);
        if (viewers == null) {
            if (creating || entity.getViewers() == null) {
                entity.setViewers(new HashSet<>());
            }
            return;
        }
        entity.setViewers(viewers);
    }

    private Set<UserEntity> resolveViewers(Set<String> viewerIds) {
        if (viewerIds == null) {
            return null;
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        viewerIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .map(String::trim)
            .forEach(ids::add);
        if (ids.isEmpty()) {
            return new LinkedHashSet<>();
        }
        if (ids.size() > MAX_VIEWERS) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "可查看人员最多选择" + MAX_VIEWERS + "人");
        }

        List<UserEntity> found = userRepository.findByPublicIdIn(ids);
        Map<String, UserEntity> byPublicId = found.stream()
            .collect(Collectors.toMap(UserEntity::getPublicId, user -> user));

        LinkedHashSet<UserEntity> viewers = new LinkedHashSet<>();
        for (String id : ids) {
            UserEntity viewer = byPublicId.get(id);
            if (viewer == null) {
                throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "可查看人员不存在");
            }
            if (!"ACTIVE".equals(viewer.getStatus())) {
                throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "可查看人员账号已禁用");
            }
            viewers.add(viewer);
        }
        return viewers;
    }

    private void applyDownloadAccess(
        PrototypeEntity entity,
        String visibility,
        String downloadAccess,
        Set<String> downloaderIds,
        boolean creating
    ) {
        String access = downloadAccess == null || downloadAccess.isBlank()
            ? (creating || entity.getDownloadAccess() == null || entity.getDownloadAccess().isBlank()
                ? "MANAGERS_ONLY"
                : entity.getDownloadAccess())
            : downloadAccess.trim().toUpperCase(Locale.ROOT);
        if (!DOWNLOAD_ACCESS.contains(access)) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "下载权限取值无效");
        }
        entity.setDownloadAccess(access);
        if (!"SELECTED".equals(access)) {
            entity.setDownloaders(new HashSet<>());
            return;
        }

        Set<UserEntity> requested = resolveDownloaders(downloaderIds);
        if (requested == null) {
            requested = creating || entity.getDownloaders() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(entity.getDownloaders());
        }

        Set<Long> visibleIds = restrictedVisibleIds(entity);
        LinkedHashSet<UserEntity> kept = new LinkedHashSet<>();
        for (UserEntity user : requested) {
            if (!"RESTRICTED".equalsIgnoreCase(visibility) || (user.getId() != null && visibleIds.contains(user.getId()))) {
                kept.add(user);
            }
        }
        entity.setDownloaders(kept);
    }

    private Set<Long> restrictedVisibleIds(PrototypeEntity entity) {
        Set<Long> ids = new HashSet<>();
        if (entity.getCreatedBy() != null && entity.getCreatedBy().getId() != null) {
            ids.add(entity.getCreatedBy().getId());
        }
        if (entity.getOwner() != null && entity.getOwner().getId() != null) {
            ids.add(entity.getOwner().getId());
        }
        if (entity.getOwners() != null) {
            entity.getOwners().stream()
                .map(UserEntity::getId)
                .filter(Objects::nonNull)
                .forEach(ids::add);
        }
        if (entity.getViewers() != null) {
            entity.getViewers().stream()
                .map(UserEntity::getId)
                .filter(Objects::nonNull)
                .forEach(ids::add);
        }
        return ids;
    }

    private Set<UserEntity> resolveDownloaders(Set<String> downloaderIds) {
        if (downloaderIds == null) {
            return null;
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        downloaderIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .map(String::trim)
            .forEach(ids::add);
        if (ids.isEmpty()) {
            return new LinkedHashSet<>();
        }
        if (ids.size() > MAX_DOWNLOADERS) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "可下载人员最多选择" + MAX_DOWNLOADERS + "人");
        }

        List<UserEntity> found = userRepository.findByPublicIdIn(ids);
        Map<String, UserEntity> byPublicId = found.stream()
            .collect(Collectors.toMap(UserEntity::getPublicId, user -> user));

        LinkedHashSet<UserEntity> downloaders = new LinkedHashSet<>();
        for (String id : ids) {
            UserEntity downloader = byPublicId.get(id);
            if (downloader == null) {
                throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "可下载人员不存在");
            }
            if (!"ACTIVE".equals(downloader.getStatus())) {
                throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "可下载人员账号已禁用");
            }
            downloaders.add(downloader);
        }
        return downloaders;
    }

    private void validateEligibleOwner(UserEntity owner) {
        boolean ownerHasCreator = owner.getRoles().stream()
            .anyMatch(r -> "CREATOR".equals(r.getCode()) || "ADMIN".equals(r.getCode()));
        if (!ownerHasCreator) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "原型负责人必须拥有创建者角色");
        }
        if (!"ACTIVE".equals(owner.getStatus())) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "原型负责人账号已禁用");
        }
    }

    private Set<TagEntity> findTags(Set<String> tagRefs) {
        if (tagRefs == null || tagRefs.isEmpty()) {
            return new HashSet<>();
        }
        return tagRepository.findByNameIn(tagRefs);
    }

    private PrototypeDtos.PrototypeResponse mapToDto(PrototypeEntity e) {
        PrototypeDtos.CategorySummary catSummary = e.getCategory() != null
            ? new PrototypeDtos.CategorySummary(e.getCategory().getCode(), e.getCategory().getName())
            : null;

        PrototypeDtos.UserSummary createdBySummary = e.getCreatedBy() != null
            ? new PrototypeDtos.UserSummary(e.getCreatedBy().getPublicId(), e.getCreatedBy().getUsername(), e.getCreatedBy().getDisplayName())
            : null;

        PrototypeDtos.UserSummary ownerSummary = e.getOwner() != null
            ? new PrototypeDtos.UserSummary(e.getOwner().getPublicId(), e.getOwner().getUsername(), e.getOwner().getDisplayName())
            : null;

        List<PrototypeDtos.UserSummary> ownerSummaries;
        if (e.getOwners() != null && !e.getOwners().isEmpty()) {
            ownerSummaries = e.getOwners().stream()
                .sorted(Comparator.comparing(UserEntity::getDisplayName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(UserEntity::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(owner -> new PrototypeDtos.UserSummary(owner.getPublicId(), owner.getUsername(), owner.getDisplayName()))
                .toList();
        } else if (ownerSummary != null) {
            ownerSummaries = List.of(ownerSummary);
        } else {
            ownerSummaries = List.of();
        }

        List<PrototypeDtos.UserSummary> viewerSummaries = e.getViewers() == null
            ? List.of()
            : e.getViewers().stream()
                .sorted(Comparator.comparing(UserEntity::getDisplayName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(UserEntity::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(viewer -> new PrototypeDtos.UserSummary(viewer.getPublicId(), viewer.getUsername(), viewer.getDisplayName()))
                .toList();

        List<PrototypeDtos.UserSummary> downloaderSummaries = e.getDownloaders() == null
            ? List.of()
            : e.getDownloaders().stream()
                .sorted(Comparator.comparing(UserEntity::getDisplayName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(UserEntity::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(downloader -> new PrototypeDtos.UserSummary(
                    downloader.getPublicId(), downloader.getUsername(), downloader.getDisplayName()))
                .toList();
        String downloadAccess = e.getDownloadAccess() == null || e.getDownloadAccess().isBlank()
            ? "MANAGERS_ONLY"
            : e.getDownloadAccess();

        Set<PrototypeDtos.TagSummary> tagSummaries = e.getTags() != null
            ? e.getTags().stream()
                .map(t -> new PrototypeDtos.TagSummary(t.getName(), t.getColor()))
                .collect(Collectors.toSet())
            : Collections.emptySet();

        Integer currentVersionNo = null;
        String currentVersionStatus = null;
        if (e.getCurrentVersionId() != null) {
            PrototypeVersionEntity current = versionRepository.findById(e.getCurrentVersionId()).orElse(null);
            if (current != null) {
                currentVersionNo = current.getVersionNo();
                currentVersionStatus = current.getStatus() != null ? current.getStatus().name() : null;
            }
        }

        return new PrototypeDtos.PrototypeResponse(
            e.getPublicId(),
            e.getCode(),
            e.getName(),
            e.getDescription(),
            e.getPublicSummary(),
            e.getVisibility(),
            e.getReviewStatus(),
            e.isArchived(),
            catSummary,
            createdBySummary,
            ownerSummary,
            ownerSummaries,
            tagSummaries,
            e.getRowVersion(),
            e.getCreatedAt(),
            e.getUpdatedAt(),
            currentVersionNo,
            currentVersionStatus,
            viewerSummaries,
            downloadAccess,
            downloaderSummaries
        );
    }

    private void logAudit(String action, String targetId, String result, Long actorId, String summary) {
        AuditLogEntity log = new AuditLogEntity();
        log.setTraceId(UUID.randomUUID().toString());
        log.setAction(action);
        log.setTargetType("PROTOTYPE");
        log.setTargetId(targetId);
        log.setActorType("USER");
        log.setActorId(actorId);
        log.setResult(result);
        log.setSummary(summary);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }
}
