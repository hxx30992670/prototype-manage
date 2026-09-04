package com.company.prototype.persistence.publish;

import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.version.PrototypeVersionEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "publish_job")
public class PublishJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "prototype_id", nullable = false)
    private PrototypeEntity prototype;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "version_id", nullable = false, unique = true)
    private PrototypeVersionEntity version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PublishJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 32, nullable = false)
    private PublishStage stage;

    @Column(name = "progress", nullable = false)
    private Integer progress = 0;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public PublishJobStatus getStatus() {
        return status;
    }

    public void setStatus(PublishJobStatus status) {
        this.status = status;
    }

    public PublishStage getStage() {
        return stage;
    }

    public void setStage(PublishStage stage) {
        this.stage = stage;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public void setErrorDetail(String errorDetail) {
        this.errorDetail = errorDetail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
