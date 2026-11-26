package com.rslima.ricash.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagesTest {

    @Test
    void sizeWithinLimit_isKept() {
        assertThat(Pages.of(2, 200).getPageSize()).isEqualTo(200);
        assertThat(Pages.of(2, 200).getPageNumber()).isEqualTo(2);
    }

    @Test
    void oversizedPage_isClampedToMax() {
        assertThat(Pages.of(0, 1_000_000).getPageSize()).isEqualTo(Pages.MAX_PAGE_SIZE);
    }

    @Test
    void invalidValues_stillRejected() {
        // IllegalArgumentException renders as a 400 via GlobalExceptionHandler.
        assertThatThrownBy(() -> Pages.of(-1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Pages.of(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
