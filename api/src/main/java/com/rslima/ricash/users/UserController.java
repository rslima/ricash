package com.rslima.ricash.users;

import com.toedter.spring.hateoas.jsonapi.JsonApiError;
import com.toedter.spring.hateoas.jsonapi.JsonApiErrors;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController @RequestMapping(value = "/api/v1/users", produces = JSON_API_VALUE) public class UserController {
    private final UserService userService;

    public UserController(final UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public PagedModel<EntityModel<UserResource>> listUsers(
            JwtAuthenticationToken principal,
            @RequestParam(name = "page[number]", required = false, defaultValue = "0") int page,
            @RequestParam(name = "page[size]", required = false, defaultValue = "20") int size) {

        final var pageable = PageRequest.of(page, size);
        var userResources = userService.listUsers(pageable).map(this::toEntityModel);

        return buildPagedUserResponse(page, size, userResources);
    }

    @GetMapping("/{id}")
    public EntityModel<UserResource> getUser(
            JwtAuthenticationToken principal,
            @PathVariable String id) {

        log.debug("User {} fetching user {}", principal.getName(), id);
        return userService.findUser(id)
                .map(this::toEntityModel)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @PostMapping
    public ResponseEntity<EntityModel<UserResource>> createUser(
            JwtAuthenticationToken principal,
            @Valid @RequestBody CreateUserRequest request) {

        log.debug("User {} creating new user with email {}", principal.getName(), request.email());
        User createdUser = userService.createUser(request);
        EntityModel<UserResource> createdUserEntity = toEntityModel(createdUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(createdUserEntity);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<UserResource>> updateUser(
            JwtAuthenticationToken principal,
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRequest request) {

        log.debug("User {} updating user {}", principal.getName(), id);
        User updatedUser = userService.updateUser(id, request);
        EntityModel<UserResource> updatedUserEntity = toEntityModel(updatedUser);

        return ResponseEntity.ok(updatedUserEntity);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            JwtAuthenticationToken principal,
            @PathVariable String id) {

        log.debug("User {} deleting user {}", principal.getName(), id);
        userService.deleteUser(id);

        return ResponseEntity.noContent().build();
    }

            int page,
            int size,
            Page<EntityModel<UserResource>> userResources) {

        var pagedModel = PagedModel.of(
                userResources.getContent(),
                new PagedModel.PageMetadata(
                        userResources.getSize(),
                        userResources.getNumber(),
                        userResources.getTotalElements(),
                        userResources.getTotalPages()));

        pagedModel.add(linkTo(methodOn(UserController.class).listUsers(null, page, size)).withSelfRel());
        pagedModel.add(linkTo(methodOn(UserController.class).listUsers(null, 0, size)).withRel("first"));

        if (userResources.getTotalPages() > 0) {
            pagedModel.add(linkTo(methodOn(UserController.class).listUsers(
                    null,
                    userResources.getTotalPages() - 1,
                    size)).withRel("last"));
        }
        if (userResources.hasNext()) {
            pagedModel.add(linkTo(methodOn(UserController.class).listUsers(
                    null,
                    userResources.getNumber() + 1,
                    size)).withRel("next"));
        }
        if (userResources.hasPrevious()) {
            pagedModel.add(linkTo(methodOn(UserController.class).listUsers(
                    null,
                    userResources.getNumber() - 1,
                    size)).withRel("prev"));
        }

        return pagedModel;
    }

    private EntityModel<UserResource> toUserResource(User u) {
        UserResource content = new UserResource(
                u.id(),
                u.name(),
                u.email(),
                u.status(),
                u.createdAt(),
                u.roles()
                        .stream()
                        .map(r -> new RoleResource(r.id(), r.name(), r.description(), r.createdAt()))
                        .toList());

        EntityModel<UserResource> userResourceEntityModel = EntityModel.of(content);
        userResourceEntityModel.add(linkTo(methodOn(UserController.class).getUser(u.id())).withSelfRel());
        return userResourceEntityModel;
    }

    @ExceptionHandler public ResponseEntity<JsonApiErrors> handleUserNotFoundException(UserNotFoundException ex) {
        return ResponseEntity.status(NOT_FOUND)
                .body(JsonApiErrors.create()
                        .withError(JsonApiError.create()
                                .withStatus(String.valueOf(NOT_FOUND.value()))
                                .withTitle(NOT_FOUND.getReasonPhrase())
                                .withDetail(ex.getMessage())));
    }


}
