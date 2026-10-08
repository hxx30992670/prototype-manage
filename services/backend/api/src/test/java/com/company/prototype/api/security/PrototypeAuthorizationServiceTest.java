package com.company.prototype.api.security;

import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PrototypeAuthorizationServiceTest {

    private PrototypeAuthorizationService service;
    private CurrentUser admin;
    private CurrentUser creator;
    private CurrentUser viewer;

    private UserEntity creatorUser;
    private UserEntity otherUser;

    @BeforeEach
    void setUp() {
        service = new PrototypeAuthorizationService();
        admin = new CurrentUser(1L, "01ADMIN", "admin", "Admin", Set.of("ADMIN"), false);
        creator = new CurrentUser(2L, "01CREATOR", "creator", "Creator", Set.of("CREATOR"), false);
        viewer = new CurrentUser(3L, "01VIEWER", "viewer", "Viewer", Set.of("VIEWER"), false);

        creatorUser = new UserEntity();
        creatorUser.setId(2L);
        creatorUser.setPublicId("01CREATOR");

        otherUser = new UserEntity();
        otherUser.setId(4L);
        otherUser.setPublicId("01OTHER");
    }

    @Test
    void creatorCanManageOnlyCreatedOrOwnedPrototype() {
        PrototypeEntity createdPrototype = new PrototypeEntity();
        createdPrototype.setCreatedBy(creatorUser);
        createdPrototype.setOwner(otherUser);

        PrototypeEntity ownedPrototype = new PrototypeEntity();
        ownedPrototype.setCreatedBy(otherUser);
        ownedPrototype.setOwner(creatorUser);

        PrototypeEntity unrelatedPrototype = new PrototypeEntity();
        unrelatedPrototype.setCreatedBy(otherUser);
        unrelatedPrototype.setOwner(otherUser);

        assertThat(service.canManage(creator, createdPrototype)).isTrue();
        assertThat(service.canManage(creator, ownedPrototype)).isTrue();
        assertThat(service.canManage(creator, unrelatedPrototype)).isFalse();
        assertThat(service.canManage(admin, unrelatedPrototype)).isTrue();
        assertThat(service.canManage(viewer, createdPrototype)).isFalse();
    }

    @Test
    void coOwnerInOwnersSetCanManageAndViewRestricted() {
        UserEntity coOwnerUser = new UserEntity();
        coOwnerUser.setId(2L);
        coOwnerUser.setPublicId("01CREATOR");

        PrototypeEntity shared = new PrototypeEntity();
        shared.setCreatedBy(otherUser);
        shared.setOwner(otherUser);
        shared.setOwners(Set.of(coOwnerUser));
        shared.setVisibility("RESTRICTED");

        assertThat(service.canManage(creator, shared)).isTrue();
        assertThat(service.canView(creator, shared)).isTrue();
        assertThat(service.canView(viewer, shared)).isFalse();
    }

    @Test
    void canViewRespectsVisibility() {
        PrototypeEntity allInternal = new PrototypeEntity();
        allInternal.setVisibility("ALL_INTERNAL");
        allInternal.setCreatedBy(otherUser);
        allInternal.setOwner(otherUser);

        PrototypeEntity restricted = new PrototypeEntity();
        restricted.setVisibility("RESTRICTED");
        restricted.setCreatedBy(creatorUser);
        restricted.setOwner(otherUser);

        PrototypeEntity restrictedUnrelated = new PrototypeEntity();
        restrictedUnrelated.setVisibility("RESTRICTED");
        restrictedUnrelated.setCreatedBy(otherUser);
        restrictedUnrelated.setOwner(otherUser);

        assertThat(service.canView(viewer, allInternal)).isTrue();
        assertThat(service.canView(viewer, restrictedUnrelated)).isFalse();
        assertThat(service.canView(creator, restricted)).isTrue();
        assertThat(service.canView(admin, restrictedUnrelated)).isTrue();
    }

    @Test
    void selectedViewerCanViewRestrictedPrototypeWithoutManagingIt() {
        UserEntity allowedViewer = new UserEntity();
        allowedViewer.setId(viewer.id());
        allowedViewer.setPublicId(viewer.publicId());

        PrototypeEntity restricted = new PrototypeEntity();
        restricted.setVisibility("RESTRICTED");
        restricted.setCreatedBy(otherUser);
        restricted.setOwner(otherUser);
        restricted.setViewers(Set.of(allowedViewer));

        assertThat(service.canView(viewer, restricted)).isTrue();
        assertThat(service.canManage(viewer, restricted)).isFalse();

        CurrentUser stranger = new CurrentUser(5L, "01STRANGER", "stranger", "Stranger", Set.of("VIEWER"), false);
        assertThat(service.canView(stranger, restricted)).isFalse();

        restricted.setViewers(null);
        assertThat(service.canView(viewer, restricted)).isFalse();
    }

    @Test
    void managersCanDownloadEvenWhenExtraDownloadIsClosed() {
        PrototypeEntity restricted = new PrototypeEntity();
        restricted.setVisibility("RESTRICTED");
        restricted.setCreatedBy(creatorUser);
        restricted.setOwner(otherUser);
        restricted.setDownloadAccess("MANAGERS_ONLY");

        assertThat(service.canDownload(admin, restricted)).isTrue();
        assertThat(service.canDownload(creator, restricted)).isTrue();
        assertThat(service.canDownload(viewer, restricted)).isFalse();
        assertThat(service.canDownload(null, restricted)).isFalse();
    }

    @Test
    void allVisiblePeopleCanDownloadOnlyWhenTheyCanView() {
        PrototypeEntity internal = new PrototypeEntity();
        internal.setVisibility("ALL_INTERNAL");
        internal.setCreatedBy(otherUser);
        internal.setOwner(otherUser);
        internal.setDownloadAccess("ALL_VIEWERS");

        PrototypeEntity restricted = new PrototypeEntity();
        restricted.setVisibility("RESTRICTED");
        restricted.setCreatedBy(otherUser);
        restricted.setOwner(otherUser);
        restricted.setDownloadAccess("ALL_VIEWERS");

        assertThat(service.canDownload(viewer, internal)).isTrue();
        assertThat(service.canDownload(viewer, restricted)).isFalse();

        UserEntity allowedViewer = new UserEntity();
        allowedViewer.setId(viewer.id());
        restricted.setViewers(Set.of(allowedViewer));
        assertThat(service.canDownload(viewer, restricted)).isTrue();
    }

    @Test
    void selectedDownloaderMustStillBeAbleToView() {
        UserEntity allowedViewer = new UserEntity();
        allowedViewer.setId(viewer.id());

        PrototypeEntity restricted = new PrototypeEntity();
        restricted.setVisibility("RESTRICTED");
        restricted.setCreatedBy(otherUser);
        restricted.setOwner(otherUser);
        restricted.setDownloadAccess("SELECTED");
        restricted.setDownloaders(Set.of(allowedViewer));
        restricted.setViewers(Set.of());
        assertThat(service.canDownload(viewer, restricted)).isFalse();

        restricted.setViewers(Set.of(allowedViewer));
        assertThat(service.canDownload(viewer, restricted)).isTrue();

        PrototypeEntity internal = new PrototypeEntity();
        internal.setVisibility("ALL_INTERNAL");
        internal.setCreatedBy(otherUser);
        internal.setOwner(otherUser);
        internal.setDownloadAccess("SELECTED");
        internal.setDownloaders(Set.of(allowedViewer));
        assertThat(service.canDownload(viewer, internal)).isTrue();

        CurrentUser stranger = new CurrentUser(5L, "01STRANGER", "stranger", "Stranger", Set.of("VIEWER"), false);
        assertThat(service.canDownload(stranger, internal)).isFalse();

        internal.setDownloadAccess("MANAGERS_ONLY");
        assertThat(service.canDownload(viewer, internal)).isFalse();
    }
}
