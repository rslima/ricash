package com.rslima.ricash.web;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.EntityModel;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

class PagedModelsTest {

    // Any controller-shaped class works for methodOn; a tiny fake keeps the test local.
    @org.springframework.web.bind.annotation.RequestMapping("/fake")
    static class FakeController {
        @SuppressWarnings("unused")
        @org.springframework.web.bind.annotation.GetMapping
        public Object list(@org.springframework.web.bind.annotation.RequestParam int page,
                           @org.springframework.web.bind.annotation.RequestParam int size) {
            return null;
        }
    }

    private Object invocation(int page) {
        return methodOn(FakeController.class).list(page, 5);
    }

    private void withRequestContext(Runnable runnable) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        try {
            runnable.run();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void middlePage_hasAllNavigationRels() {
        withRequestContext(() -> {
            var page = new PageImpl<>(List.of(EntityModel.of("a")), PageRequest.of(1, 5), 15);

            var model = PagedModels.toPagedModel(page, this::invocation);

            assertThat(model.getMetadata().getSize()).isEqualTo(5);
            assertThat(model.getMetadata().getNumber()).isEqualTo(1);
            assertThat(model.getMetadata().getTotalElements()).isEqualTo(15);
            assertThat(model.getMetadata().getTotalPages()).isEqualTo(3);
            assertThat(model.getLinks().stream().map(l -> l.getRel().value()))
                    .containsExactlyInAnyOrder("self", "first", "last", "next", "prev");
            assertThat(model.getRequiredLink("next").getHref()).contains("page=2");
            assertThat(model.getRequiredLink("prev").getHref()).contains("page=0");
            assertThat(model.getRequiredLink("last").getHref()).contains("page=2");
        });
    }

    @Test
    void singlePage_hasNoNextOrPrev() {
        withRequestContext(() -> {
            var page = new PageImpl<>(List.of(EntityModel.of("a")), PageRequest.of(0, 5), 1);

            var model = PagedModels.toPagedModel(page, this::invocation);

            assertThat(model.getLinks().stream().map(l -> l.getRel().value()))
                    .containsExactlyInAnyOrder("self", "first", "last");
        });
    }

    @Test
    void emptyResult_hasNoLastLink() {
        withRequestContext(() -> {
            var page = new PageImpl<>(List.<EntityModel<String>>of(), PageRequest.of(0, 5), 0);

            var model = PagedModels.toPagedModel(page, this::invocation);

            assertThat(model.getLinks().stream().map(l -> l.getRel().value()))
                    .containsExactlyInAnyOrder("self", "first");
        });
    }
}
