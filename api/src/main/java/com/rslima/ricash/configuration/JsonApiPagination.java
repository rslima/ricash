package com.rslima.ricash.configuration;

import org.springframework.data.domain.Page;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;

import java.util.function.IntFunction;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

/**
 * Assembles a JSON:API paged model with self/first/last/next/prev links.
 * The {@code controllerCall} produces a {@code methodOn(...)} invocation for a
 * given page number, e.g.
 * {@code p -> methodOn(LedgerController.class).listLedgers(principal, p, size)}.
 */
public final class JsonApiPagination {

    private JsonApiPagination() {
    }

    public static <T> PagedModel<EntityModel<T>> pagedModel(
            Page<EntityModel<T>> page,
            IntFunction<Object> controllerCall) {

        var pagedModel = PagedModel.of(
                page.getContent(),
                new PagedModel.PageMetadata(
                        page.getSize(),
                        page.getNumber(),
                        page.getTotalElements(),
                        page.getTotalPages()));

        pagedModel.add(linkTo(controllerCall.apply(page.getNumber())).withSelfRel());
        pagedModel.add(linkTo(controllerCall.apply(0)).withRel("first"));

        if (page.getTotalPages() > 0) {
            pagedModel.add(linkTo(controllerCall.apply(page.getTotalPages() - 1)).withRel("last"));
        }
        if (page.hasNext()) {
            pagedModel.add(linkTo(controllerCall.apply(page.getNumber() + 1)).withRel("next"));
        }
        if (page.hasPrevious()) {
            pagedModel.add(linkTo(controllerCall.apply(page.getNumber() - 1)).withRel("prev"));
        }

        return pagedModel;
    }
}
