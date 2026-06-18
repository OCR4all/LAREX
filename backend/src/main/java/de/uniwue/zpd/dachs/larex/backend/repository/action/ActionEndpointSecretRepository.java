package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionEndpointSecret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActionEndpointSecretRepository extends JpaRepository<ActionEndpointSecret, String> {
    Optional<ActionEndpointSecret> findByRef(String ref);

    boolean existsByRef(String ref);

    List<ActionEndpointSecret> findAllByOrderByRefAsc();
}
