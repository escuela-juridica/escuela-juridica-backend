package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "curso")
public class Curso extends RegistroAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "curso_id")
    private Long id;

    @Column(name = "curso_origen_id")
    private Long cursoOrigenId;

    @Column(name = "url_amigable", nullable = false, unique = true, length = 180)
    private String urlAmigable;

    @Column(nullable = false, length = 220)
    private String titulo;

    @Column(columnDefinition = "text")
    private String descripcion;

    @Column(name = "imagen_portada_url", columnDefinition = "text")
    private String imagenPortadaUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_curso_id")
    private TipoCurso tipoCurso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_tematica_id")
    private CategoriaTematica categoriaTematica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entidad_certificadora_id")
    private EntidadCertificadora entidadCertificadora;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estado_curso_id", nullable = false)
    private EstadoCurso estadoCurso;

    @Column(length = 15)
    private String modalidad;

    @Column(name = "tipo_venta", length = 10)
    private String tipoVenta;

    @Column(nullable = false)
    private boolean destacado;

    @Column(name = "precio_regular", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioRegular;

    @Column(name = "precio_promocional", precision = 10, scale = 2)
    private BigDecimal precioPromocional;

    @Column(name = "promocion_inicio_en")
    private Instant promocionInicioEn;

    @Column(name = "promocion_fin_en")
    private Instant promocionFinEn;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "fecha_cierre_matricula")
    private LocalDate fechaCierreMatricula;

    @Column(name = "cupo_maximo")
    private Integer cupoMaximo;

    @Column(name = "horas_academicas", precision = 8, scale = 2)
    private BigDecimal horasAcademicas;

    @Column(name = "vigencia_acceso_dias")
    private Integer vigenciaAccesoDias;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "beneficios", nullable = false)
    private String[] beneficios = new String[0];

    @Column(name = "creado_por_usuario_id")
    private Long creadoPorUsuarioId;

    @Column(name = "publicado_en")
    private Instant publicadoEn;

    @Column(name = "cerrado_en")
    private Instant cerradoEn;
}
