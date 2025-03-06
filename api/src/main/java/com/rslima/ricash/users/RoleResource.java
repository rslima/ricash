package com.rslima.ricash.users;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.hateoas.RepresentationModel;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleResource extends RepresentationModel<RoleResource> {
    private String id;
    private String name;
    private String description;
    private Instant createdAt;
}
