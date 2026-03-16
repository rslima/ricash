package com.rslima.ricash.ledgers.accounts;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AccountMapper {

    AccountResource toResource(Account account);

    List<AccountResource> toAccountResources(List<Account> accounts);
}
