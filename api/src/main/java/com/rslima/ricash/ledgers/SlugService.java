package com.rslima.ricash.ledgers;

import com.github.slugify.Slugify;
import org.springframework.stereotype.Component;

@Component
public class SlugService {
    private final Slugify slugify;

    public SlugService() {
        this.slugify = Slugify.builder().build();
    }

    public String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return slugify.slugify(input);
    }
}
