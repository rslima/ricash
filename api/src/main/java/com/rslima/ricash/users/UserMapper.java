package com.rslima.ricash.users;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    UserResource toResource(User user);

    RoleResource toResource(Role role);

    List<RoleResource> toRoleResources(List<Role> roles);
}
