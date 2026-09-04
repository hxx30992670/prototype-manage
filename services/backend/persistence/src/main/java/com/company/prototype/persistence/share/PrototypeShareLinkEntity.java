package com.company.prototype.persistence.share;

import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.user.UserEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "prototype_share_link")
public class PrototypeShareLinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", length = 26, nullable = false, unique = true)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prototype_id", nullable = false)
    private PrototypeEntity prototype;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "token_digest", length = 64, nullable = false, unique = true)
    private String tokenDigest;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "spec_scope", nullable = false, length = 16)
    private String specScope = "NONE";

    @Column(name = "allow_comment", nullable = false)
    private Boolean allowComment = false;

    @Column(name = "allow_public_attachment", nullable = false)
    private Boolean allowPublicAttachment = false;

    @Column(name = "auth_epoch", nullable = false)
    private Long authEpoch = 0L;

    @Column(name = "visit_count", nullable = false)
    private Long visitCount = 0L;

    @Column(name = "last_visited_at")
    private Instant lastVisitedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private UserEntity createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public PrototypeShareLinkEntity() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public PrototypeEntity getPrototype() {
        return prototype;
    }

    public void setPrototype(PrototypeEntity prototype) {
        this.prototype = prototype;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTokenDigest() {
        return tokenDigest;
    }

    public void setTokenDigest(String tokenDigest) {
        this.tokenDigest = tokenDigest;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSpecScope() {
        return specScope;
    }

    public void setSpecScope(String specScope) {
        this.specScope = specScope;
    }

    public Boolean getAllowComment() {
        return allowComment;
    }

    public void setAllowComment(Boolean allowComment) {
        this.allowComment = allowComment;
    }

    public Boolean getAllowPublicAttachment() {
        return allowPublicAttachment;
    }

    public void setAllowPublicAttachment(Boolean allowPublicAttachment) {
        this.allowPublicAttachment = allowPublicAttachment;
    }

    public Long getAuthEpoch() {
        return authEpoch;
    }

    public void setAuthEpoch(Long authEpoch) {
        this.authEpoch = authEpoch;
    }

    public Long getVisitCount() {
        return visitCount;
    }

    public void setVisitCount(Long visitCount) {
        this.visitCount = visitCount;
    }

    public Instant getLastVisitedAt() {
        return lastVisitedAt;
    }

    public void setLastVisitedAt(Instant lastVisitedAt) {
        this.lastVisitedAt = lastVisitedAt;
    }

    public UserEntity getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UserEntity createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
