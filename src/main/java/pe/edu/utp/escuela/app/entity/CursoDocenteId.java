package pe.edu.utp.escuela.app.entity;

import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CursoDocenteId implements Serializable {

    private Long curso;
    private Long persona;

    public CursoDocenteId(Long curso, Long persona) {
        this.curso = curso;
        this.persona = persona;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CursoDocenteId otro)) {
            return false;
        }
        return Objects.equals(curso, otro.curso) && Objects.equals(persona, otro.persona);
    }

    @Override
    public int hashCode() {
        return Objects.hash(curso, persona);
    }
}
