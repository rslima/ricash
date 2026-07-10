package com.rslima.ricash.ledgers;

import com.github.slugify.Slugify;

import java.util.function.Predicate;

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

    /**
     * Returns {@code baseSlug} if it is free according to {@code exists},
     * otherwise probes {@code baseSlug-1}, {@code baseSlug-2}, ... until a
     * free slug is found.
     */
    public static String uniqueSlug(String baseSlug, Predicate<String> exists) {
        String slug = baseSlug;
        int counter = 1;

        while (exists.test(slug)) {
            slug = baseSlug + "-" + counter;
            counter++;
        }

        return slug;
    }
}
