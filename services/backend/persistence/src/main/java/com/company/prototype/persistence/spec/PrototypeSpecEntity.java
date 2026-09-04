package com.company.prototype.persistence.spec;

import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.user.UserEntity;
import jakarta.persistence.*;

import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Entity
@Table(name = "prototype_spec")
public class PrototypeSpecEntity implements Persistable<Long> {

    @Id
    @Column(name = "prototype_id")
    private Long prototypeId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "prototype_id")
    private PrototypeEntity prototype;

    @Column(name = "goal", columnDefinition = "TEXT")
    private String goal;

    @Column(name = "core_flow", columnDefinition = "TEXT")
    private String coreFlow;

    @Column(name = "interaction_rules", columnDefinition = "TEXT")
    private String interactionRules;

    @Column(name = "business_constraints", columnDefinition = "TEXT")
    private String businessConstraints;

    @Column(name = "data_requirements", columnDefinition = "TEXT")
    private String dataRequirements;

    @Column(name = "acceptance_notes", columnDefinition = "TEXT")
    private String acceptanceNotes;

    @Column(name = "markdown_extra", columnDefinition = "MEDIUMTEXT")
    private String markdownExtra;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @Transient
    private boolean isNew = true;

    @Override
    public Long getId() {
        return prototypeId;
    }

    @Override
    public boolean isNew() {
        return isNew || rowVersion == null;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", nullable = false)
    private UserEntity updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public PrototypeSpecEntity() {}

    public Long getPrototypeId() {
        return prototypeId;
    }

    public void setPrototypeId(Long prototypeId) {
        this.prototypeId = prototypeId;
    }

    public PrototypeEntity getPrototype() {
        return prototype;
    }

    public void setPrototype(PrototypeEntity prototype) {
        this.prototype = prototype;
        if (prototype != null) {
            this.prototypeId = prototype.getId();
        }
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getCoreFlow() {
        return coreFlow;
    }

    public void setCoreFlow(String coreFlow) {
        this.coreFlow = coreFlow;
    }

    public String getInteractionRules() {
        return interactionRules;
    }

    public void setInteractionRules(String interactionRules) {
        this.interactionRules = interactionRules;
    }

    public String getBusinessConstraints() {
        return businessConstraints;
    }

    public void setBusinessConstraints(String businessConstraints) {
        this.businessConstraints = businessConstraints;
    }

    public String getDataRequirements() {
        return dataRequirements;
    }

    public void setDataRequirements(String dataRequirements) {
        this.dataRequirements = dataRequirements;
    }

    public String getAcceptanceNotes() {
        return acceptanceNotes;
    }

    public void setAcceptanceNotes(String acceptanceNotes) {
        this.acceptanceNotes = acceptanceNotes;
    }

    public String getMarkdownExtra() {
        return markdownExtra;
    }

    public void setMarkdownExtra(String markdownExtra) {
        this.markdownExtra = markdownExtra;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long rowVersion) {
        this.rowVersion = rowVersion;
    }

    public UserEntity getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UserEntity updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
