package com.company.prototype.persistence.prototype;

import com.company.prototype.persistence.user.UserEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "prototype")
public class PrototypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 26)
    private String publicId;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "public_summary", length = 2000)
    private String publicSummary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryEntity category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private UserEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserEntity owner;

    @Column(nullable = false, length = 32)
    private String visibility = "ALL_INTERNAL";

    @Column(name = "download_access", nullable = false, length = 32)
    private String downloadAccess = "MANAGERS_ONLY";

    @Column(name = "review_status", nullable = false, length = 16)
    private String reviewStatus = "DRAFT";

    @Column(name = "current_version_id")
    private Long currentVersionId;

    @Column(name = "cover_object_key", length = 512)
    private String coverObjectKey;

    @Column(nullable = false)
    private boolean archived = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "prototype_tag_rel",
        joinColumns = @JoinColumn(name = "prototype_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<TagEntity> tags = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "prototype_owner_rel",
        joinColumns = @JoinColumn(name = "prototype_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<UserEntity> owners = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "prototype_viewer_rel",
        joinColumns = @JoinColumn(name = "prototype_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<UserEntity> viewers = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "prototype_downloader_rel",
        joinColumns = @JoinColumn(name = "prototype_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<UserEntity> downloaders = new HashSet<>();

    public PrototypeEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPublicSummary() { return publicSummary; }
    public void setPublicSummary(String publicSummary) { this.publicSummary = publicSummary; }
    public CategoryEntity getCategory() { return category; }
    public void setCategory(CategoryEntity category) { this.category = category; }
    public UserEntity getCreatedBy() { return createdBy; }
    public void setCreatedBy(UserEntity createdBy) { this.createdBy = createdBy; }
    public UserEntity getOwner() { return owner; }
    public void setOwner(UserEntity owner) { this.owner = owner; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getDownloadAccess() { return downloadAccess; }
    public void setDownloadAccess(String downloadAccess) { this.downloadAccess = downloadAccess; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public Long getCurrentVersionId() { return currentVersionId; }
    public void setCurrentVersionId(Long currentVersionId) { this.currentVersionId = currentVersionId; }
    public String getCoverObjectKey() { return coverObjectKey; }
    public void setCoverObjectKey(String coverObjectKey) { this.coverObjectKey = coverObjectKey; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public long getRowVersion() { return rowVersion; }
    public void setRowVersion(long rowVersion) { this.rowVersion = rowVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Set<TagEntity> getTags() { return tags; }
    public void setTags(Set<TagEntity> tags) { this.tags = tags; }
    public Set<UserEntity> getOwners() { return owners; }
    public void setOwners(Set<UserEntity> owners) { this.owners = owners != null ? owners : new HashSet<>(); }
    public Set<UserEntity> getViewers() { return viewers; }
    public void setViewers(Set<UserEntity> viewers) { this.viewers = viewers != null ? viewers : new HashSet<>(); }
    public Set<UserEntity> getDownloaders() { return downloaders; }
    public void setDownloaders(Set<UserEntity> downloaders) { this.downloaders = downloaders != null ? downloaders : new HashSet<>(); }
}
