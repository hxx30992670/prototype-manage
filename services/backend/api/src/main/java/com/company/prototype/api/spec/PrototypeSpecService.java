package com.company.prototype.api.spec;

import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.api.security.PrototypeAuthorizationService;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.spec.PrototypeSpecEntity;
import com.company.prototype.persistence.spec.PrototypeSpecRepository;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class PrototypeSpecService {

    private static final Pattern DANGEROUS_HTML_PATTERN = Pattern.compile(
        "<(script|iframe|object|embed|applet|form|base|link|meta)[^>]*>|javascript:|data:text/html|vbscript:",
        Pattern.CASE_INSENSITIVE
    );

    private final PrototypeRepository prototypeRepository;
    private final PrototypeSpecRepository specRepository;
    private final UserRepository userRepository;
    private final PrototypeAuthorizationService authorizationService;

    public PrototypeSpecService(
        PrototypeRepository prototypeRepository,
        PrototypeSpecRepository specRepository,
        UserRepository userRepository,
        PrototypeAuthorizationService authorizationService
    ) {
        this.prototypeRepository = prototypeRepository;
        this.specRepository = specRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public SpecDtos.SpecResponse getSpec(CurrentUser user, String prototypePublicId) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canView(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        return specRepository.findByPrototypeId(proto.getId())
            .map(s -> toResponse(s, proto))
            .orElseGet(() -> new SpecDtos.SpecResponse(
                proto.getPublicId(),
                "", "", "", "", "", "", "",
                0L,
                null,
                Instant.now()
            ));
    }

    @Transactional
    public SpecDtos.SpecResponse updateSpec(CurrentUser user, String prototypePublicId, SpecDtos.UpdateSpecRequest req) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canManage(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        if (proto.isArchived()) {
            throw new ApiException(ApiErrorCode.PROTOTYPE_ARCHIVED, "原型已归档，不可修改结构化说明");
        }

        validateMarkdownSecurity(req.markdownExtra());

        UserEntity userEntity = userRepository.findById(user.id())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        PrototypeSpecEntity spec = specRepository.findByPrototypeId(proto.getId()).orElse(null);
        if (spec == null) {
            if (req.rowVersion() != null && req.rowVersion() != 0L) {
                throw new ApiException(ApiErrorCode.RESOURCE_VERSION_CONFLICT, "说明版本冲突，请重新加载");
            }
            spec = new PrototypeSpecEntity();
            spec.setPrototype(proto);
        } else {
            long currentVersion = spec.getRowVersion() != null ? spec.getRowVersion() : 0L;
            if (!req.rowVersion().equals(currentVersion)) {
                throw new ApiException(ApiErrorCode.RESOURCE_VERSION_CONFLICT, "说明已被他人修改，请重新加载");
            }
        }

        spec.setGoal(req.goal());
        spec.setCoreFlow(req.coreFlow());
        spec.setInteractionRules(req.interactionRules());
        spec.setBusinessConstraints(req.businessConstraints());
        spec.setDataRequirements(req.dataRequirements());
        spec.setAcceptanceNotes(req.acceptanceNotes());
        spec.setMarkdownExtra(req.markdownExtra());
        spec.setUpdatedBy(userEntity);
        spec.setUpdatedAt(Instant.now());

        spec = specRepository.save(spec);
        return toResponse(spec, proto);
    }

    private void validateMarkdownSecurity(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return;
        }
        if (DANGEROUS_HTML_PATTERN.matcher(markdown).find()) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "Markdown 包含不受支持的 HTML 标签或危险脚本协议");
        }
    }

    private SpecDtos.SpecResponse toResponse(PrototypeSpecEntity spec, PrototypeEntity proto) {
        String updatedBy = spec.getUpdatedBy() != null ? spec.getUpdatedBy().getDisplayName() : null;
        return new SpecDtos.SpecResponse(
            proto.getPublicId(),
            spec.getGoal(),
            spec.getCoreFlow(),
            spec.getInteractionRules(),
            spec.getBusinessConstraints(),
            spec.getDataRequirements(),
            spec.getAcceptanceNotes(),
            spec.getMarkdownExtra(),
            spec.getRowVersion() != null ? spec.getRowVersion() : 0L,
            updatedBy,
            spec.getUpdatedAt()
        );
    }
}
