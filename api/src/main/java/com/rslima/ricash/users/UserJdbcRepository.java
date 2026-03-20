package com.rslima.ricash.users;

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

        return Optional.of(new User(
                firstUserRole.uId(),
                firstUserRole.username(),
                firstUserRole.email(),
                firstUserRole.password(),
                firstUserRole.salt(),
                UserStatus.valueOf(firstUserRole.status()),
                firstUserRole.userCreatedAt(),
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

    @Override
    public User create(CreateUserRequest request) {
        final var id = java.util.UUID.randomUUID().toString();
        final var salt = java.util.UUID.randomUUID().toString();
        final var now = Instant.now();

        jdbcClient.sql("""
                INSERT INTO public.users (id, username, email, password, salt, status, created_at)
                VALUES (:id, :username, :email, :password, :salt, :status, :createdAt)
                """)
                .param("id", id)
                .param("username", request.name())
                .param("email", request.email())
                .param("password", request.password())
                .param("salt", salt)
                .param("status", UserStatus.ENABLED.name())
                .param("createdAt", now)
                .update();

        return new User(id, request.name(), request.email(), request.password(), salt, UserStatus.ENABLED, now,
                List.of());
    }

    @Override
    public Optional<User> update(String id, UpdateUserRequest request) {
        final var existingUser = findById(id);
        if (existingUser.isEmpty()) {
            return Optional.empty();
        }

        final var user = existingUser.get();
        final var newName = request.name() != null ? request.name() : user.name();
        final var newEmail = request.email() != null ? request.email() : user.email();
        final var newStatus = request.status() != null ? request.status() : user.status();

        jdbcClient.sql("""
                UPDATE public.users
                SET username = :username, email = :email, status = :status
                WHERE id = :id
                """)
                .param("id", id)
                .param("username", newName)
                .param("email", newEmail)
                .param("status", newStatus.name())
                .update();

        return Optional.of(new User(id, newName, newEmail, user.password(), user.salt(), newStatus, user.createdAt(), user.roles()));
    }

    @Override
    public boolean delete(String id) {
        final var rowsAffected = jdbcClient.sql("""
                DELETE FROM public.users WHERE id = :id
                """)
                .param("id", id)
                .update();

        return rowsAffected > 0;
    }
}

