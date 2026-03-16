package com.rslima.ricash.ledgers;

import com.rslima.ricash.ledgers.accounts.AccountMapper;
import com.rslima.ricash.users.UserResource;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {AccountMapper.class})
public interface LedgerMapper {

    @Mapping(source = "userId", target = "user", qualifiedByName = "toUserResource")
    LedgerResource toResource(Ledger ledger);

    @Named("toUserResource")
    default UserResource toUserResource(String userId) {
        if (userId == null) {
            return null;
        }
        UserResource userResource = new UserResource();
        userResource.setId(userId);
        return userResource;
    }
}
