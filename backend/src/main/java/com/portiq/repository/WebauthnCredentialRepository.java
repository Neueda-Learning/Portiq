package com.portiq.repository;

import com.portiq.model.WebauthnCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebauthnCredentialRepository extends JpaRepository<WebauthnCredential, Long> {
    List<WebauthnCredential> findByUserId(Long userId);
    Optional<WebauthnCredential> findByCredentialId(String credentialId);
}
