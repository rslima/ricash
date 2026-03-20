package com.rslima.ricash.users;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.toedter.spring.hateoas.jsonapi.JsonApiId;
import com.toedter.spring.hateoas.jsonapi.JsonApiRelationships;
import com.toedter.spring.hateoas.jsonapi.JsonApiTypeForClass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.hateoas.RepresentationModel;

import java.time.Instant;
import java.util.List;


@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@JsonApiTypeForClass("users")
public class UserResource extends RepresentationModel<UserResource> {
    @JsonApiId
    private String id;
    private String name;
    private String email;
    private UserStatus status;
    private Instant createdAt;
    @JsonIgnore
    @JsonApiRelationships("roles")
    private List<RoleResource>   roles;
}
