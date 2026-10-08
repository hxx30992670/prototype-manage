package com.company.prototype.api.security;

import com.company.prototype.persistence.prototype.PrototypeEntity;
import org.springframework.stereotype.Service;

@Service
public class PrototypeAuthorizationService {

    public boolean canManage(CurrentUser user, PrototypeEntity prototype) {
        if (user == null || prototype == null) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        if (!user.isCreator()) {
            return false;
        }
        boolean isCreator = prototype.getCreatedBy() != null && user.id().equals(prototype.getCreatedBy().getId());
        return isCreator || isOwner(user, prototype);
    }

    public boolean canView(CurrentUser user, PrototypeEntity prototype) {
        if (user == null || prototype == null) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        if ("RESTRICTED".equalsIgnoreCase(prototype.getVisibility())) {
            boolean isCreator = prototype.getCreatedBy() != null && user.id().equals(prototype.getCreatedBy().getId());
            return isCreator || isOwner(user, prototype) || isViewer(user, prototype);
        }
        return true;
    }

    public boolean canDownload(CurrentUser user, PrototypeEntity prototype) {
        if (user == null || prototype == null) {
            return false;
        }
        if (canManage(user, prototype)) {
            return true;
        }
        String access = prototype.getDownloadAccess();
        if (access == null || "MANAGERS_ONLY".equalsIgnoreCase(access)) {
            return false;
        }
        if (!canView(user, prototype)) {
            return false;
        }
        if ("ALL_VIEWERS".equalsIgnoreCase(access)) {
            return true;
        }
        if ("SELECTED".equalsIgnoreCase(access)) {
            return isDownloader(user, prototype);
        }
        return false;
    }

    private boolean isOwner(CurrentUser user, PrototypeEntity prototype) {
        if (prototype.getOwner() != null && user.id().equals(prototype.getOwner().getId())) {
            return true;
        }
        if (prototype.getOwners() == null) {
            return false;
        }
        return prototype.getOwners().stream().anyMatch(owner -> user.id().equals(owner.getId()));
    }

    private boolean isViewer(CurrentUser user, PrototypeEntity prototype) {
        if (prototype.getViewers() == null || user.id() == null) {
            return false;
        }
        return prototype.getViewers().stream().anyMatch(viewer -> user.id().equals(viewer.getId()));
    }

    private boolean isDownloader(CurrentUser user, PrototypeEntity prototype) {
        if (prototype.getDownloaders() == null || user.id() == null) {
            return false;
        }
        return prototype.getDownloaders().stream().anyMatch(downloader -> user.id().equals(downloader.getId()));
    }
}
