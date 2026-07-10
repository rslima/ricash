package com.rslima.ricash.web;

import org.springframework.data.domain.Page;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;

import java.util.function.IntFunction;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

/**
 * Builds the JSON:API paged response shared by every list endpoint: page
 * metadata plus self/first/last/next/prev links.
 */
public final class PagedModels {

    private PagedModels() {
    }

    /**
     * @param page the page of already-wrapped entity models
     * @param pageInvocation returns a {@code methodOn(...)} recorded invocation
     *        of the list endpoint for the given page number; used to build each
     *        navigation link, so path variables and remaining parameters stay
     *        exactly as the caller supplied them
     */
    public static <T> PagedModel<EntityModel<T>> toPagedModel(
            Page<EntityModel<T>> page, IntFunction<Object> pageInvocation) {

        var pagedModel = PagedModel.of(
                page.getContent(),
                new PagedModel.PageMetadata(
                        page.getSize(),
                        page.getNumber(),
                        page.getTotalElements(),
                        page.getTotalPages()));

        pagedModel.add(linkTo(pageInvocation.apply(page.getNumber())).withSelfRel());
        pagedModel.add(linkTo(pageInvocation.apply(0)).withRel("first"));

        if (page.getTotalPages() > 0) {
            pagedModel.add(linkTo(pageInvocation.apply(page.getTotalPages() - 1)).withRel("last"));
        }
        if (page.hasNext()) {
            pagedModel.add(linkTo(pageInvocation.apply(page.getNumber() + 1)).withRel("next"));
        }
        if (page.hasPrevious()) {
            pagedModel.add(linkTo(pageInvocation.apply(page.getNumber() - 1)).withRel("prev"));
        }

        return pagedModel;
    }
}
