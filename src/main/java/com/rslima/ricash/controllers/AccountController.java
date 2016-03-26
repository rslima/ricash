package com.rslima.ricash.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/account")
public class AccountController {
    @RequestMapping("")
    public String getAll() {
        return "ok";
    }
}
