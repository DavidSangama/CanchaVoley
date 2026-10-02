package com.canchavoley.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "cliente_token_gestion", schema = "renta_cancha")
public class TokenGestionCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_token_gestion")
    private Long idTokenGestion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    public TokenGestionCliente() {
    }

    public TokenGestionCliente(Cliente cliente, String tokenHash) {
        this.cliente = cliente;
        this.tokenHash = tokenHash;
    }

    public Long getIdTokenGestion() {
        return idTokenGestion;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public String getTokenHash() {
        return tokenHash;
    }
}
