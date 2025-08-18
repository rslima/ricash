package com.rslima.ricash.users;

import com.rslima.ricash.ledgers.Ledger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.SimplePropertyRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;


@RequiredArgsConstructor
@Slf4j
public class UserJdbcRepository implements UserRepository {
    private final JdbcClient jdbcClient;

    @Override public Page<User> list(@NotNull Pageable pageable) {

        final var querySpec = pageable.isUnpaged() ? jdbcClient.sql("""
                select
                    *
                from
                    public.users
                """) : jdbcClient.sql("""
                select
                    *
                from
                    public.users
                limit :limit
                offset :offset
                """).param("limit", pageable.getPageSize()).param("offset", pageable.getOffset());

        final var usersList = querySpec.query(new SimplePropertyRowMapper<>(UserRow.class)).list();

        final var userIds = usersList.stream().map(UserRow::id).toList();

        final Map<String, List<Ledger>> userLedgers = !usersList.isEmpty() ? jdbcClient.sql("""
                        SELECT
                            user_id,
                            id ledger_id,
                            name ledger_name,
                            description ledger_description,
                            currency ledger_currency,
                            created_at ledger_created_at
                        FROM
                            public.ledgers
                        WHERE
                            user_id in (:userIds)
                        """)
                .param("userIds", userIds)
                .query(new SimplePropertyRowMapper<>(UserLedger.class))
                .stream()
                .collect(groupingBy(
                        UserLedger::userId, mapping(
                                ul -> new Ledger(
                                        ul.ledgerId(),
                                        ul.ledgerName(),
                                        ul.ledgerDescription(),
                                        ul.ledgerCurrency(),
                                        ul.ledgerCreatedAt(),
                                        List.of(),
                                        List.of()), toList()))) : Map.of();

        final Map<String, List<Role>> userRoles = !usersList.isEmpty() ? jdbcClient.sql("""
                        SELECT
                            user_roles.user_id,
                            roles.id AS r_id,
                            roles.name AS role_name,
                            roles.description,
                            roles.created_at AS role_created_at
                        FROM
                            public.user_roles
                        JOIN
                            public.roles ON user_roles.role_id = roles.id
                        WHERE
                            user_roles.user_id in (:userIds)
                        """)
                .param("userIds", userIds)
                .query(new SimplePropertyRowMapper<>(RoleAndUserId.class))
                .stream()
                .collect(groupingBy(
                        RoleAndUserId::userId, mapping(
                                role -> new Role(role.rId(), role.roleName(), role.description(), role.roleCreatedAt()),
                                toList()))) : Map.of();


        final var users = usersList.stream().map(userRow -> new User(
                userRow.id(),
                userRow.username(),
                userRow.email(),
                userRow.password(),
                userRow.salt(),
                UserStatus.valueOf(userRow.status()),
                userRow.createdAt(),
                userLedgers.getOrDefault(userRow.id(), List.of()),
                userRoles.getOrDefault(userRow.id(), List.of()))).toList();

        final var totalUsers = jdbcClient.sql("SELECT COUNT(*) FROM public.users").query(Long.class).single();

        return new PageImpl<>(users, pageable, totalUsers);
    }

    record UserRow(String id,
                   String username,
                   String email,
                   String password,
                   String salt,
                   String status,
                   Instant createdAt) {
    }

    record UserLedger(String userId,
                      String ledgerId,
                      String ledgerName,
                      String ledgerDescription,
                      String ledgerCurrency,
                      Instant ledgerCreatedAt) {
    }

    record RoleAndUserId(String userId, String rId, String roleName, String description, Instant roleCreatedAt) {
    }

    @Override public Optional<User> findById(String id) {

        final var userRoles = jdbcClient.sql("""
                SELECT
                    users.id u_id,
                    users.username,
                    users.password,
                    users.salt,
                    users.status,
                    users.email,
                    users.created_at user_created_at,
                    roles.id r_id,
                    roles.name role_name,
                    roles.description,
                    roles.created_at role_created_at
                FROM
                    public.users
                LEFT JOIN
                        public.user_roles ON
                            users.id = user_roles.user_id
                JOIN
                        public.roles ON
                            user_roles.role_id = roles.id
                WHERE
                    users.id = :id
                ORDER BY
                    users.id
                """).param("id", id).query(new SimplePropertyRowMapper<>(UserRole.class)).list();

        if (userRoles.isEmpty()) {
            return Optional.empty();
        }

        final var firstUserRole = userRoles.getFirst();

        final var
                roles =
                userRoles.stream()
                        .map(ur -> new Role(ur.rId(), ur.roleName(), ur.description(), ur.roleCreatedAt()))
                        .toList();

        final var ledgers = jdbcClient.sql("""
                SELECT
                    id,
                    name,
                    description,
                    currency,
                    created_at
                FROM
                    public.ledgers
                WHERE
                    user_id = :userId
                """).param("userId", id).query(new SimplePropertyRowMapper<>(SimpleLedger.class)).list();

        return Optional.of(new User(
                firstUserRole.uId(),
                firstUserRole.username(),
                firstUserRole.email(),
                firstUserRole.password(),
                firstUserRole.salt(),
                UserStatus.valueOf(firstUserRole.status()),
                firstUserRole.userCreatedAt(),
                ledgers.stream()
                        .map(sl -> new Ledger(
                                sl.id(),
                                sl.name(),
                                sl.description(),
                                sl.currency(),
                                sl.createdAt(),
                                List.of(),
                                List.of()))
                        .toList(),
                roles));
    }

    record UserRole(String uId,
                    String username,
                    String password,
                    String salt,
                    String status,
                    String email,
                    Instant userCreatedAt,
                    String rId,
                    String roleName,
                    String description,
                    Instant roleCreatedAt) {
    }

    record SimpleLedger(String id, String name, String description, String currency, Instant createdAt) {
    }

}

