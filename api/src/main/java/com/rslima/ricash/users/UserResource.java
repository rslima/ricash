package com.rslima.ricash.users;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.RepresentationModel;

import java.time.Instant;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResource extends RepresentationModel<UserResource> {
    private String id;
    private String name;
    private String email;
    private UserStatus status;
    private Instant createdAt;
    private List<String> ledgers;
    private List<EntityModel<RoleResource>> roles;
}
