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
            return isCreator || isOwner(user, prototype);
        }
        return true;
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
}
