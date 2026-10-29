package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class RegistroAuditable {
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;
    @Column(name = "modificado_en", nullable = false)
    private Instant modificadoEn;
    @PrePersist
    void crearFechas() {
        if (creadoEn == null) creadoEn = Instant.now();
        modificadoEn = creadoEn;
    }
    @PreUpdate
    void actualizarFecha() { modificadoEn = Instant.now(); }
}

