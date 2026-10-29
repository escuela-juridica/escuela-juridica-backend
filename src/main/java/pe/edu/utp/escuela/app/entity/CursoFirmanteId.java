package pe.edu.utp.escuela.app.entity;

import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CursoFirmanteId implements Serializable {

    private Long curso;
    private Long firmante;

    public CursoFirmanteId(Long curso, Long firmante) {
        this.curso = curso;
        this.firmante = firmante;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CursoFirmanteId otro)) {
            return false;
        }
        return Objects.equals(curso, otro.curso) && Objects.equals(firmante, otro.firmante);
    }

    @Override
    public int hashCode() {
        return Objects.hash(curso, firmante);
    }
}
