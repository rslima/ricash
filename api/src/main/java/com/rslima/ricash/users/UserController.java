package com.rslima.ricash.users;

import com.toedter.spring.hateoas.jsonapi.JsonApiError;
import com.toedter.spring.hateoas.jsonapi.JsonApiErrors;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping(value = "/api/v1/users", produces = JSON_API_VALUE)
public class UserController {
    private final UserService userService;

    public UserController(final UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    CollectionModel<?> listUsers(
            @RequestParam(name = "page[number]", required = false, defaultValue = "0") int page,
            @RequestParam(name = "page[size]", required = false, defaultValue = "20") int size) {

        final var pageable = PageRequest.of(page, size);

        var userResources = userService.listUsers(pageable).map(this::toUserResource);
        CollectionModel<EntityModel<UserResource>> entityModels = PagedModel.of(userResources);
        entityModels.add(linkTo(methodOn(UserController.class).listUsers(page, size)).withSelfRel());
        return entityModels;
    }

    @GetMapping("/{id}")
    EntityModel<UserResource> getUser(@PathVariable String id) {
        var userResource = userService.findUser(id).map(this::toUserResource);
        return userResource.orElseThrow(() -> new UserNotFoundException(id));
    }

    private EntityModel<UserResource> toUserResource(User u) {
        UserResource content = new UserResource(
                u.id(),
                u.name(),
                u.email(),
                u.status(),
                u.createdAt(),
                u.ledgers(),
                u.roles().stream()
                        .map(r -> EntityModel.of(new RoleResource(r.id(), r.name(), r.description(),
                                r.createdAt())))
                        .toList());

        EntityModel<UserResource> userResourceEntityModel = EntityModel.of(content);
        userResourceEntityModel.add(linkTo(methodOn(UserController.class).getUser(u.id())).withSelfRel());
        return userResourceEntityModel;
    }

    @ExceptionHandler
    public ResponseEntity<JsonApiErrors> handleUserNotFoundException(UserNotFoundException ex) {
        return ResponseEntity.status(NOT_FOUND).body(
                JsonApiErrors.create().withError(
                        JsonApiError.create()
                                .withStatus(String.valueOf(NOT_FOUND.value()))
                                .withTitle(NOT_FOUND.getReasonPhrase())
                                .withDetail(ex.getMessage())));
    }
}
