package pe.edu.utp.escuela.app.curso;

/** Solo GRATUITO o PAGADO: la columna `curso.tipo_venta` de la base de datos definitiva documenta
 * exactamente esas dos condiciones. La mención de "In-house" en HU-010 es una etiqueta puramente
 * comercial sin columna propia en el esquema actual, así que no se modela aquí como un tercer
 * valor; si se necesita más adelante habrá que ampliar el esquema primero. */
public enum TipoVentaCurso {
    GRATUITO,
    PAGADO
}
