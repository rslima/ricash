package com.rslima.ricash.ledgers;

import com.rslima.ricash.users.UserResource;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface LedgerMapper {

    @Mapping(source = "userId", target = "user", qualifiedByName = "toUserResource")
    LedgerResource toResource(Ledger ledger);

    AccountResource toResource(Account account);

    List<AccountResource> toAccountResources(List<Account> accounts);

    EnvelopeResource toResource(Envelope envelope);

    List<EnvelopeResource> toEnvelopeResources(List<Envelope> envelopes);

    EnvelopeAllocationResource toResource(EnvelopeAllocation allocation);

    EnvelopeBalanceResource toResource(EnvelopeBalance balance);

    List<EnvelopeBalanceResource> toEnvelopeBalanceResources(List<EnvelopeBalance> balances);

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
