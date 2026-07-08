package com.rslima.ricash.ledgers;

import com.github.f4b6a3.uuid.UuidCreator;
import com.rslima.ricash.users.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class LedgerServiceBean implements LedgerService {
    private final LedgerRepository ledgerRepository;
    private final SlugService slugService;
    private final UserRepository userRepository;

    @Override
    public Page<Ledger> listUserLedgers(String userId, PageRequest pageRequest) {
        return ledgerRepository.listUserLedgers(userId, pageRequest);
    }

    @Override
    public Optional<Ledger> findBySlug(String userId, String slug) {
        return ledgerRepository.findBySlug(userId, slug);
    }

    @Override
    public Ledger create(String userId, CreateLedgerRequest request) {
        if (userRepository.findById(userId).isEmpty()) {
            userRepository.create(userId);
        }

        final var baseSlug = slugService.slugify(request.name());
        final var slug = generateUniqueSlug(userId, baseSlug);

        final var ledger = new Ledger(
                UuidCreator.getTimeOrderedEpoch().toString(),
                userId,
                slug,
                request.name(),
                request.description(),
                request.currency(),
                Instant.now(),
                List.of(),
                List.of()
        );
        return ledgerRepository.create(ledger);
    }

    @Override
    public Ledger update(String userId, String slug, UpdateLedgerRequest request) {
        // Verify ledger exists
        ledgerRepository.findBySlug(userId, slug)
                .orElseThrow(() -> new LedgerNotFoundException(slug));

        return ledgerRepository.update(userId, slug, request.name(), request.description());
    }

    private String generateUniqueSlug(String userId, String baseSlug) {
        String slug = baseSlug;
        int counter = 1;

        while (ledgerRepository.existsBySlug(userId, slug)) {
            slug = baseSlug + "-" + counter;
            counter++;
        }

        return slug;
    }
}
