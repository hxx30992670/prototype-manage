package com.company.prototype.persistence.comment;

import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.share.PrototypeShareLinkEntity;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.version.PrototypeVersionEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "prototype_comment")
public class PrototypeCommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", length = 26, nullable = false, unique = true)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prototype_id", nullable = false)
    private PrototypeEntity prototype;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private PrototypeVersionEntity version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private PrototypeCommentEntity parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<PrototypeCommentEntity> replies = new ArrayList<>();

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(length = 16)
    private String status = "OPEN";

    @Column(name = "author_type", length = 16, nullable = false)
    private String authorType = "USER";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id")
    private UserEntity authorUser;

    @Column(name = "guest_name", length = 100)
    private String guestName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "share_link_id")
    private PrototypeShareLinkEntity shareLink;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private UserEntity resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolve_note", length = 500)
    private String resolveNote;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public PrototypeCommentEntity() {}

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

    public PrototypeVersionEntity getVersion() {
        return version;
    }

    public void setVersion(PrototypeVersionEntity version) {
        this.version = version;
    }

    public PrototypeCommentEntity getParent() {
        return parent;
    }

    public void setParent(PrototypeCommentEntity parent) {
        this.parent = parent;
    }

    public List<PrototypeCommentEntity> getReplies() {
        return replies;
    }

    public void setReplies(List<PrototypeCommentEntity> replies) {
        this.replies = replies;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAuthorType() {
        return authorType;
    }

    public void setAuthorType(String authorType) {
        this.authorType = authorType;
    }

    public UserEntity getAuthorUser() {
        return authorUser;
    }

    public void setAuthorUser(UserEntity authorUser) {
        this.authorUser = authorUser;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public PrototypeShareLinkEntity getShareLink() {
        return shareLink;
    }

    public void setShareLink(PrototypeShareLinkEntity shareLink) {
        this.shareLink = shareLink;
    }

    public UserEntity getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(UserEntity resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolveNote() {
        return resolveNote;
    }

    public void setResolveNote(String resolveNote) {
        this.resolveNote = resolveNote;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long rowVersion) {
        this.rowVersion = rowVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
