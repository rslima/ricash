package com.rslima.ricash.web.account;

import com.rslima.ricash.domain.account.Account;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/account")
public class AccountController {
    @RequestMapping("/user/{userId}")
    public List<Account> getAll(@PathVariable UUID userId) {
        return Collections.emptyList();
    }
}
