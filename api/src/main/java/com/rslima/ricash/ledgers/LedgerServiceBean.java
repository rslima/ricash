package com.rslima.ricash.ledgers;

import com.github.f4b6a3.uuid.UuidCreator;
import com.rslima.ricash.users.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

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
    public Page<Ledger> listUserLedgers(String userId, Pageable pageRequest) {
        return ledgerRepository.listUserLedgers(userId, pageRequest);
    }

    @Override
    public Optional<Ledger> findBySlug(String userId, String slug) {
        return ledgerRepository.findBySlug(userId, slug);
    }

    @Override
    @Transactional
    public Ledger create(String userId, CreateLedgerRequest request) {
        if (userRepository.findById(userId).isEmpty()) {
            userRepository.create(userId);
        }

        final var baseSlug = slugService.slugify(request.name());
        final var slug = SlugService.uniqueSlug(baseSlug, s -> ledgerRepository.existsBySlug(userId, s));

        final var ledger = new Ledger(
                UuidCreator.getTimeOrderedEpoch().toString(),
                userId,
                slug,
                request.name(),
                request.description(),
                request.currency(),
                Instant.now(),
                List.of()
        );
        return ledgerRepository.create(ledger);
    }

    @Override
    @Transactional
    public Ledger update(String userId, String slug, UpdateLedgerRequest request) {
        // Verify ledger exists
        ledgerRepository.findBySlug(userId, slug)
                .orElseThrow(() -> new LedgerNotFoundException(slug));

        return ledgerRepository.update(userId, slug, request.name(), request.description());
    }
}
