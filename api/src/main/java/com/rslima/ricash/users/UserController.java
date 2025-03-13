package com.rslima.ricash.users;

import com.toedter.spring.hateoas.jsonapi.JsonApiError;
import com.toedter.spring.hateoas.jsonapi.JsonApiErrors;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
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
    PagedModel<EntityModel<UserResource>> listUsers(
            @RequestParam(name = "page[number]", required = false, defaultValue = "0") int page,
            @RequestParam(name = "page[size]", required = false, defaultValue = "20") int size) {

        final var pageable = PageRequest.of(page, size);

        var userResources = userService.listUsers(pageable).map(this::toUserResource);

        return buildPagedUserResponse(page, size, userResources);
    }

    private @NotNull PagedModel<EntityModel<UserResource>> buildPagedUserResponse(int page, int size, Page<EntityModel<UserResource>> userResources) {
        var entityModels = PagedModel.of(userResources.getContent(),
                new PagedModel.PageMetadata(
                        userResources.getSize(),
                        userResources.getNumber(),
                        userResources.getTotalElements(),
                        userResources.getTotalPages()));

        entityModels.add(linkTo(methodOn(UserController.class).listUsers(page, size)).withSelfRel());
        entityModels.add(linkTo(methodOn(UserController.class).listUsers(0, size)).withRel("first"));
        entityModels.add(linkTo(methodOn(UserController.class).listUsers(userResources.getTotalPages() - 1, size)).withRel("last"));
        if (userResources.hasNext()) {
            entityModels.add(linkTo(methodOn(UserController.class).listUsers(userResources.getNumber() + 1, size)).withRel("next"));
        }
        if (userResources.hasPrevious()) {
            entityModels.add(linkTo(methodOn(UserController.class).listUsers(userResources.getNumber() - 1, size)).withRel("prev"));
        }
        return entityModels;
    }

    @GetMapping("/{id}")
    EntityModel<UserResource> getUser(@PathVariable String id) {
        var userResource = userService.findUser(id).map(this::toUserResource);
        return userResource.orElseThrow(() -> new UserNotFoundException(id));
    }

    @PostMapping
    public ResponseEntity<EntityModel<UserResource>> createUser(@RequestBody EntityModel<User> userResource) {

        User createdUser = userService.createUser(userResource.getContent());
        EntityModel<UserResource> createdUserEntity = toUserResource(createdUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUserEntity);
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
