package com.canchavoley.backend.repository;

import com.canchavoley.backend.model.TokenGestionCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenGestionClienteRepository extends JpaRepository<TokenGestionCliente, Long> {

    Optional<TokenGestionCliente> findByTokenHash(String tokenHash);

    void deleteAllByClienteIdCliente(Long idCliente);

}
