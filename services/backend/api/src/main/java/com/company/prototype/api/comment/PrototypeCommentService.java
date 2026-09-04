package com.company.prototype.api.comment;

import com.company.prototype.api.audit.Audited;
import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.api.security.PrototypeAuthorizationService;
import com.company.prototype.api.share.PrototypeShareService;
import com.company.prototype.api.share.ShareDtos;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.common.id.PublicIdGenerator;
import com.company.prototype.persistence.comment.PrototypeCommentEntity;
import com.company.prototype.persistence.comment.PrototypeCommentRepository;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.share.PrototypeShareLinkEntity;
import com.company.prototype.persistence.share.PrototypeShareLinkRepository;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import com.company.prototype.persistence.version.PrototypeVersionEntity;
import com.company.prototype.persistence.version.PrototypeVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class PrototypeCommentService {

    private final PrototypeRepository prototypeRepository;
    private final PrototypeVersionRepository versionRepository;
    private final PrototypeCommentRepository commentRepository;
    private final PrototypeShareLinkRepository shareLinkRepository;
    private final UserRepository userRepository;
    private final PrototypeAuthorizationService authorizationService;
    private final StringRedisTemplate redisTemplate;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public PrototypeCommentService(
        PrototypeRepository prototypeRepository,
        PrototypeVersionRepository versionRepository,
        PrototypeCommentRepository commentRepository,
        PrototypeShareLinkRepository shareLinkRepository,
        UserRepository userRepository,
        PrototypeAuthorizationService authorizationService,
        StringRedisTemplate redisTemplate,
        PublicIdGenerator idGenerator,
        @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.prototypeRepository = prototypeRepository;
        this.versionRepository = versionRepository;
        this.commentRepository = commentRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.redisTemplate = redisTemplate;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
    }

    @Audited(action = "COMMENT_CREATE", targetType = "COMMENT")
    @Transactional
    public CommentDtos.CommentItemResponse createComment(
        CurrentUser user,
        String prototypePublicId,
        CommentDtos.CreateCommentRequest req,
        String idempotencyKey,
        String clientIp
    ) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canView(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        if (proto.isArchived()) {
            throw new ApiException(ApiErrorCode.PROTOTYPE_ARCHIVED, "原型已归档，不可发表评论");
        }

        // Idempotency check
        String requestHash = PrototypeShareService.sha256(req.versionPublicId() + ":" + req.parentCommentPublicId() + ":" + req.content());
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String cached = redisTemplate.opsForValue().get("comment:idemp:" + idempotencyKey);
            if (cached != null) {
                String[] parts = cached.split("\\|", 2);
                if (parts[0].equals(requestHash)) {
                    return getCommentByPublicId(parts[1]);
                } else {
                    throw new ApiException(ApiErrorCode.IDEMPOTENCY_KEY_REUSED, "IDEMPOTENCY_KEY_REUSED: 幂等键已被不同请求使用");
                }
            }
        }

        // Rate limit: 10 per minute
        checkRateLimit("rate:comment:user:" + user.id());

        PrototypeVersionEntity version = versionRepository.findByPrototypeIdAndPublicId(proto.getId(), req.versionPublicId())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "关联版本不存在"));

        PrototypeCommentEntity parent = resolveParentComment(req.parentCommentPublicId(), proto, version);

        UserEntity authorUser = userRepository.findById(user.id())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        String publicId = idGenerator.nextId();
        PrototypeCommentEntity comment = new PrototypeCommentEntity();
        comment.setPublicId(publicId);
        comment.setPrototype(proto);
        comment.setVersion(version);
        comment.setParent(parent);
        comment.setContent(req.content());
        comment.setStatus(parent == null ? "OPEN" : null);
        comment.setAuthorType("USER");
        comment.setAuthorUser(authorUser);
        comment.setCreatedAt(Instant.now());

        comment = commentRepository.save(comment);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            redisTemplate.opsForValue().set("comment:idemp:" + idempotencyKey, requestHash + "|" + publicId, Duration.ofHours(24));
        }

        return toItemResponse(comment);
    }

    @Audited(action = "COMMENT_GUEST_CREATE", targetType = "COMMENT")
    @Transactional
    public CommentDtos.CommentItemResponse createGuestComment(
        String rawToken,
        String sessionCookie,
        String csrfToken,
        CommentDtos.CreateCommentRequest req,
        String idempotencyKey,
        String clientIp
    ) {
        PrototypeShareLinkEntity link = requireUsableShare(rawToken, sessionCookie);

        if (!Boolean.TRUE.equals(link.getAllowComment())) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED, "当前分享链接未开启评论功能");
        }

        ShareDtos.ShareSessionRecord session = getValidSession(sessionCookie, link);
        if (session == null) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED, "未检测到有效分享会话");
        }

        if (csrfToken == null || !PrototypeShareService.sha256(csrfToken).equals(session.csrfTokenDigest())) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED, "CSRF 校验失败");
        }

        PrototypeEntity proto = link.getPrototype();
        if (proto.isArchived()) {
            throw new ApiException(ApiErrorCode.PROTOTYPE_ARCHIVED, "原型已归档，不可发表评论");
        }

        checkRateLimit("rate:comment:ip:" + clientIp);

        // Idempotency check
        String requestHash = PrototypeShareService.sha256(req.versionPublicId() + ":" + req.parentCommentPublicId() + ":" + req.content());
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String cached = redisTemplate.opsForValue().get("comment:idemp:" + idempotencyKey);
            if (cached != null) {
                String[] parts = cached.split("\\|", 2);
                if (parts[0].equals(requestHash)) {
                    return getCommentByPublicId(parts[1]);
                } else {
                    throw new ApiException(ApiErrorCode.IDEMPOTENCY_KEY_REUSED, "IDEMPOTENCY_KEY_REUSED: 幂等键已被不同请求使用");
                }
            }
        }

        PrototypeVersionEntity version = resolveShareCommentVersion(proto, req.versionPublicId());

        PrototypeCommentEntity parent = resolveParentComment(req.parentCommentPublicId(), proto, version);

        String publicId = idGenerator.nextId();
        PrototypeCommentEntity comment = new PrototypeCommentEntity();
        comment.setPublicId(publicId);
        comment.setPrototype(proto);
        comment.setVersion(version);
        comment.setParent(parent);
        comment.setContent(req.content());
        comment.setStatus(parent == null ? "OPEN" : null);
        comment.setAuthorType("GUEST");
        comment.setGuestName(session.guestName());
        comment.setShareLink(link);
        comment.setCreatedAt(Instant.now());

        comment = commentRepository.save(comment);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            redisTemplate.opsForValue().set("comment:idemp:" + idempotencyKey, requestHash + "|" + publicId, Duration.ofHours(24));
        }

        return toItemResponse(comment);
    }

    @Transactional(readOnly = true)
    public List<CommentDtos.CommentItemResponse> listComments(
        CurrentUser user,
        String prototypePublicId,
        String versionPublicId,
        String status
    ) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canView(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        List<PrototypeCommentEntity> list;
        if (versionPublicId != null && !versionPublicId.isBlank()) {
            PrototypeVersionEntity ver = versionRepository.findByPrototypeIdAndPublicId(proto.getId(), versionPublicId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));
            list = commentRepository.findAllByPrototypeIdAndVersionIdAndParentIsNullOrderByCreatedAtDesc(proto.getId(), ver.getId());
        } else {
            list = commentRepository.findAllByPrototypeIdAndParentIsNullOrderByCreatedAtDesc(proto.getId());
        }

        return list.stream()
            .filter(c -> status == null || (c.getStatus() != null && c.getStatus().equalsIgnoreCase(status)))
            .map(this::toItemResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CommentDtos.CommentItemResponse> listPublicShareComments(String rawToken, String sessionCookie) {
        PrototypeShareLinkEntity link = requireUsableShare(rawToken, sessionCookie);

        PrototypeEntity proto = link.getPrototype();
        List<PrototypeCommentEntity> list = commentRepository.findAllByPrototypeIdAndParentIsNullOrderByCreatedAtDesc(proto.getId());
        return list.stream().map(this::toItemResponse).toList();
    }

    @Audited(action = "COMMENT_RESOLVE", targetType = "COMMENT")
    @Transactional
    public CommentDtos.CommentItemResponse resolveComment(
        CurrentUser user,
        String commentPublicId,
        CommentDtos.ResolveCommentRequest req
    ) {
        PrototypeCommentEntity comment = commentRepository.findByPublicId(commentPublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        PrototypeEntity proto = comment.getPrototype();
        if (!authorizationService.canManage(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED, "仅管理员、原型创建人和负责人可处理或重新打开评论");
        }

        if (!comment.getRowVersion().equals(req.rowVersion())) {
            throw new ApiException(ApiErrorCode.RESOURCE_VERSION_CONFLICT, "评论状态已被他人更新，请刷新");
        }

        UserEntity resolver = userRepository.findById(user.id())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        comment.setStatus(req.status().toUpperCase());
        comment.setResolvedBy(resolver);
        comment.setResolvedAt(Instant.now());
        comment.setResolveNote(req.resolveNote());

        comment = commentRepository.save(comment);
        return toItemResponse(comment);
    }

    @Transactional
    public void deleteComment(CurrentUser user, String commentPublicId) {
        PrototypeCommentEntity comment = commentRepository.findByPublicId(commentPublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        boolean isAuthor = comment.getAuthorUser() != null && comment.getAuthorUser().getId().equals(user.id());
        boolean canManage = authorizationService.canManage(user, comment.getPrototype());

        if (!isAuthor && !canManage) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        comment.setDeletedAt(Instant.now());
        commentRepository.save(comment);
    }

    private PrototypeShareLinkEntity requireUsableShare(String rawToken, String sessionCookie) {
        String tokenDigest = PrototypeShareService.sha256(rawToken);
        PrototypeShareLinkEntity link = shareLinkRepository.findByTokenDigest(tokenDigest)
            .orElseThrow(() -> new ApiException(ApiErrorCode.SHARE_LINK_NOT_FOUND, "分享链接不存在"));
        if (!"ACTIVE".equalsIgnoreCase(link.getStatus())) {
            throw new ApiException(ApiErrorCode.SHARE_LINK_DISABLED);
        }
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ApiErrorCode.SHARE_LINK_EXPIRED);
        }
        if (link.getPasswordHash() != null && !link.getPasswordHash().isBlank() && getValidSession(sessionCookie, link) == null) {
            throw new ApiException(ApiErrorCode.SHARE_PASSWORD_REQUIRED);
        }
        return link;
    }

    private PrototypeVersionEntity resolveShareCommentVersion(PrototypeEntity proto, String versionPublicId) {
        if (versionPublicId != null && !versionPublicId.isBlank()) {
            Optional<PrototypeVersionEntity> matched =
                versionRepository.findByPrototypeIdAndPublicId(proto.getId(), versionPublicId);
            if (matched.isPresent()) {
                return matched.get();
            }
        }
        if (proto.getCurrentVersionId() != null) {
            return versionRepository.findById(proto.getCurrentVersionId())
                .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "关联版本不存在"));
        }
        throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "关联版本不存在");
    }

    private PrototypeCommentEntity resolveParentComment(
        String parentCommentPublicId,
        PrototypeEntity proto,
        PrototypeVersionEntity version
    ) {
        if (parentCommentPublicId == null || parentCommentPublicId.isBlank()) {
            return null;
        }
        PrototypeCommentEntity parent = commentRepository.findByPublicId(parentCommentPublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "父评论不存在"));
        if (parent.getParent() != null) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "评论最多支持两级回复");
        }
        if (!parent.getPrototype().getId().equals(proto.getId())) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "父评论不属于当前原型");
        }
        if (parent.getVersion() == null || !parent.getVersion().getId().equals(version.getId())) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "父评论不属于当前版本");
        }
        return parent;
    }

    private void checkRateLimit(String rateKey) {
        Long count = redisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateKey, Duration.ofMinutes(1));
        }
        if (count != null && count > 10) {
            throw new ApiException(ApiErrorCode.COMMENT_RATE_LIMITED, "发表评论过于频繁，请稍候再试");
        }
    }

    private ShareDtos.ShareSessionRecord getValidSession(String sessionId, PrototypeShareLinkEntity link) {
        if (sessionId == null || sessionId.isBlank()) return null;
        String json = redisTemplate.opsForValue().get("share:session:" + sessionId);
        if (json == null) return null;

        try {
            ShareDtos.ShareSessionRecord session = objectMapper.readValue(json, ShareDtos.ShareSessionRecord.class);
            if (session.shareLinkId() == link.getId() && session.authEpoch() == link.getAuthEpoch()) {
                return session;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private CommentDtos.CommentItemResponse getCommentByPublicId(String publicId) {
        return commentRepository.findByPublicId(publicId)
            .map(this::toItemResponse)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));
    }

    private CommentDtos.CommentItemResponse toItemResponse(PrototypeCommentEntity c) {
        String authorName = c.getAuthorType().equals("GUEST")
            ? (c.getGuestName() != null ? c.getGuestName() : "匿名访客")
            : (c.getAuthorUser() != null ? c.getAuthorUser().getDisplayName() : "已注销用户");

        String resolvedBy = c.getResolvedBy() != null ? c.getResolvedBy().getDisplayName() : null;

        List<CommentDtos.CommentReplyItem> replies = (c.getReplies() != null ? c.getReplies() : List.<PrototypeCommentEntity>of()).stream()
            .map(r -> {
                String replyAuthorName = r.getAuthorType().equals("GUEST")
                    ? (r.getGuestName() != null ? r.getGuestName() : "匿名访客")
                    : (r.getAuthorUser() != null ? r.getAuthorUser().getDisplayName() : "已注销用户");
                boolean isDeleted = r.getDeletedAt() != null;
                return new CommentDtos.CommentReplyItem(
                    r.getPublicId(),
                    isDeleted ? "该回复已删除" : r.getContent(),
                    r.getAuthorType(),
                    replyAuthorName,
                    r.getCreatedAt(),
                    isDeleted
                );
            })
            .toList();

        boolean isDeleted = c.getDeletedAt() != null;
        return new CommentDtos.CommentItemResponse(
            c.getPublicId(),
            c.getPrototype().getPublicId(),
            c.getVersion().getPublicId(),
            c.getVersion().getVersionNo(),
            isDeleted ? "该评论已删除" : c.getContent(),
            c.getStatus(),
            c.getAuthorType(),
            authorName,
            resolvedBy,
            c.getResolvedAt(),
            c.getResolveNote(),
            c.getRowVersion(),
            c.getCreatedAt(),
            isDeleted,
            replies
        );
    }
}
