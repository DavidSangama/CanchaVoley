package com.canchavoley.backend.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "reserva", schema = "renta_cancha")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reserva")
    private Long idReserva;

    @ManyToOne
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "id_cancha", nullable = false)
    private Cancha cancha;

    @ManyToOne
    @JoinColumn(name = "id_horario", nullable = false)
    private Horario horario;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "precio", nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @JsonIgnore
    @Column(name = "token_cancelacion_hash", length = 64, unique = true)
    private String tokenCancelacionHash;

    public Reserva() {
    }

    public Reserva(Long idReserva, Cliente cliente, Cancha cancha, Horario horario, LocalDate fecha) {
        this.idReserva = idReserva;
        this.cliente = cliente;
        this.cancha = cancha;
        this.horario = horario;
        this.fecha = fecha;
    }

    public Long getIdReserva() {
        return idReserva;
    }

    public void setIdReserva(Long idReserva) {
        this.idReserva = idReserva;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public Cancha getCancha() {
        return cancha;
    }

    public void setCancha(Cancha cancha) {
        this.cancha = cancha;
    }

    public Horario getHorario() {
        return horario;
    }

    public void setHorario(Horario horario) {
        this.horario = horario;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    public String getTokenCancelacionHash() {
        return tokenCancelacionHash;
    }

    public void setTokenCancelacionHash(String tokenCancelacionHash) {
        this.tokenCancelacionHash = tokenCancelacionHash;
    }
}