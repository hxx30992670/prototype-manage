package com.company.prototype.persistence.version;

import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.user.UserEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "prototype_version", uniqueConstraints = {
    @UniqueConstraint(name = "uk_prototype_version_no", columnNames = {"prototype_id", "version_no"})
})
public class PrototypeVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", length = 26, nullable = false, unique = true)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prototype_id", nullable = false)
    private PrototypeEntity prototype;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "change_log", length = 1000, nullable = false)
    private String changeLog;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private VersionStatus status;

    @Column(name = "source_type", length = 8, nullable = false)
    private String sourceType;

    @Column(name = "source_object_key", length = 512, nullable = false)
    private String sourceObjectKey;

    @Column(name = "publish_prefix", length = 512)
    private String publishPrefix;

    @Column(name = "entry_path", length = 512)
    private String entryPath;

    @Column(name = "file_count")
    private Integer fileCount;

    @Column(name = "source_size", nullable = false)
    private Long sourceSize;

    @Column(name = "expanded_size")
    private Long expandedSize;

    @Column(name = "checksum", length = 64, nullable = false)
    private String checksum;

    @Column(name = "failure_stage", length = 32)
    private String failureStage;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private UserEntity createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

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

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getChangeLog() {
        return changeLog;
    }

    public void setChangeLog(String changeLog) {
        this.changeLog = changeLog;
    }

    public VersionStatus getStatus() {
        return status;
    }

    public void setStatus(VersionStatus status) {
        this.status = status;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceObjectKey() {
        return sourceObjectKey;
    }

    public void setSourceObjectKey(String sourceObjectKey) {
        this.sourceObjectKey = sourceObjectKey;
    }

    public String getPublishPrefix() {
        return publishPrefix;
    }

    public void setPublishPrefix(String publishPrefix) {
        this.publishPrefix = publishPrefix;
    }

    public String getEntryPath() {
        return entryPath;
    }

    public void setEntryPath(String entryPath) {
        this.entryPath = entryPath;
    }

    public Integer getFileCount() {
        return fileCount;
    }

    public void setFileCount(Integer fileCount) {
        this.fileCount = fileCount;
    }

    public Long getSourceSize() {
        return sourceSize;
    }

    public void setSourceSize(Long sourceSize) {
        this.sourceSize = sourceSize;
    }

    public Long getExpandedSize() {
        return expandedSize;
    }

    public void setExpandedSize(Long expandedSize) {
        this.expandedSize = expandedSize;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getFailureStage() {
        return failureStage;
    }

    public void setFailureStage(String failureStage) {
        this.failureStage = failureStage;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
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

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
