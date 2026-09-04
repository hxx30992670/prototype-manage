package com.company.prototype.api.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserTest {

    @Test
    void equalsMatchesByUserIdEvenIfRolesOrDisplayNameDiffer() {
        CurrentUser sessionUser = new CurrentUser(7L, "pub", "alice", "Alice", Set.of("CREATOR"), false);
        CurrentUser disabledProbe = new CurrentUser(7L, "pub", "alice", "Alice Updated", Set.of("ADMIN"), true);

        assertThat(sessionUser).isEqualTo(disabledProbe);
        assertThat(sessionUser.hashCode()).isEqualTo(disabledProbe.hashCode());
    }
}
