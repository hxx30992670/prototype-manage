package com.company.prototype.common.id;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UlidPublicIdGeneratorTest {

    @Test
    void generatesValid26CharUlid() {
        UlidPublicIdGenerator generator = new UlidPublicIdGenerator();
        String id1 = generator.nextId();
        String id2 = generator.nextId();

        assertThat(id1).hasSize(26).matches("^[0123456789ABCDEFGHJKMNPQRSTVWXYZ]{26}$");
        assertThat(id2).hasSize(26).matches("^[0123456789ABCDEFGHJKMNPQRSTVWXYZ]{26}$");
        assertThat(id1).isNotEqualTo(id2);
    }
}
