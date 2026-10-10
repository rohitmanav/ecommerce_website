package org.example.userauthservice.repos;

import org.example.userauthservice.models.Session;
import org.example.userauthservice.models.State;
import org.example.userauthservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepo extends JpaRepository<Session,Long> {
    Optional<Session> findByToken(String token);

    Optional<Session> findByTokenAndState(String token, State state);

    Optional<Session> findByRefreshTokenAndState(String refreshToken, State state);

    List<Session> findAllByUserAndState(User user, State state);
}
