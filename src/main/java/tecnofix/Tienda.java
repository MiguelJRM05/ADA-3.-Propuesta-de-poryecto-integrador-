package tecnofix;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

final class Tienda {
    record Producto(String clave, String nombre, BigDecimal precio, int stock) {
        public String toString() { return clave + " · " + nombre; }
    }
    record Linea(Producto producto, int cantidad) {
        BigDecimal subtotal() { return producto.precio().multiply(BigDecimal.valueOf(cantidad)); }
    }

    private Connection abrir() throws SQLException {
        var env = System.getenv();
        return DriverManager.getConnection(env.getOrDefault("TECNOFIX_URL", "jdbc:mysql://localhost:3306/tecnofix"),
                env.getOrDefault("TECNOFIX_USER", "root"), env.getOrDefault("TECNOFIX_PASSWORD", ""));
    }
    private static PreparedStatement consulta(Connection c, String sql, Object... args) throws SQLException {
        var s = c.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
        return s;
    }
    private static int ejecutar(Connection c, String sql, Object... args) throws SQLException {
        try (var s = consulta(c, sql, args)) { return s.executeUpdate(); }
    }
    private interface Operacion<T> { T ejecutar(Connection c) throws SQLException; }
    private <T> T transaccion(Operacion<T> op) throws SQLException {
        try (var c = abrir()) {
            c.setAutoCommit(false);
            try {
                T resultado = op.ejecutar(c);
                c.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }
    List<Producto> buscar(String texto) throws SQLException {
        List<Producto> productos = new ArrayList<>();
        try (var c = abrir(); var s = consulta(c, "SELECT * FROM producto WHERE clave=? OR LOCATE(?,nombre)>0 ORDER BY nombre", texto, texto);
                var r = s.executeQuery()) {
            while (r.next()) productos.add(new Producto(r.getString("clave"),r.getString("nombre"),r.getBigDecimal("precio"),r.getInt("stock")));
        }
        return productos;
    }
    void guardar(String clave, String nombre, BigDecimal precio, boolean editar) throws SQLException {
        if (clave.isBlank() || clave.length()>32 || nombre.isBlank() || nombre.length()>120)
            throw new IllegalArgumentException("Revisa la clave (1–32 caracteres) y el nombre (1–120).");
        if (precio.signum()<=0 || precio.scale()>2 || precio.compareTo(new BigDecimal("9999999999.99"))>0)
            throw new IllegalArgumentException("Ingresa un precio positivo con máximo dos decimales.");
        try (var c = abrir()) {
            if (editar) ejecutar(c,"UPDATE producto SET nombre=?,precio=? WHERE clave=?",nombre,precio,clave);
            else ejecutar(c,"INSERT INTO producto(nombre,precio,clave) VALUES(?,?,?)",nombre,precio,clave);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new IllegalArgumentException("La clave ya está registrada.",e);
        }
    }
    void recibir(String clave, int cantidad) throws SQLException {
        if (cantidad<=0) throw new IllegalArgumentException("La cantidad debe ser positiva.");
        transaccion(c -> {
            if (ejecutar(c,"UPDATE producto SET stock=stock+? WHERE clave=?",cantidad,clave)!=1)
                throw new IllegalArgumentException("Producto desconocido.");
            ejecutar(c,"INSERT INTO entrada(clave,cantidad) VALUES(?,?)",clave,cantidad);
            return null;
        });
    }
    long vender(Collection<Linea> carrito) throws SQLException {
        if (carrito.isEmpty()) throw new IllegalArgumentException("El carrito está vacío.");
        return transaccion(c -> {
            long folio;
            try (var s = c.prepareStatement("INSERT INTO venta(fecha) VALUES(CURRENT_TIMESTAMP)",Statement.RETURN_GENERATED_KEYS)) {
                s.executeUpdate();
                try (var r=s.getGeneratedKeys()) { if (!r.next()) throw new SQLException("Folio no disponible."); folio=r.getLong(1); }
            }
            for (Linea l : carrito) {
                Producto p=l.producto();
                if (l.cantidad()<=0) throw new IllegalArgumentException("Cantidad inválida.");
                // La condición se comprueba bajo el bloqueo de la fila para evitar una sobreventa.
                int filas=ejecutar(c,"UPDATE producto SET stock=stock-? WHERE clave=? AND stock>=? AND precio=? AND nombre=?",
                        l.cantidad(),p.clave(),l.cantidad(),p.precio(),p.nombre());
                if (filas!=1) throw new IllegalArgumentException("Revisa el stock o el precio de " + p.clave() + ". Actualiza el carrito.");
                ejecutar(c,"INSERT INTO detalle(folio,clave,nombre,precio,cantidad) VALUES(?,?,?,?,?)",
                        folio,p.clave(),p.nombre(),p.precio(),l.cantidad());
            }
            return folio;
        });
    }
    String ticket(long folio) throws SQLException {
        if (folio <= 0) {
            throw new IllegalArgumentException("El folio debe ser un entero positivo.");
        }
        try (var c=abrir();var s=consulta(c,"SELECT v.fecha,d.* FROM venta v JOIN detalle d USING(folio) WHERE folio=? ORDER BY clave",folio);
                var r=s.executeQuery()) {
            if (!r.next()) throw new IllegalArgumentException("No existe ese folio.");
            var texto=new StringBuilder("TecnoFix · Ticket #"+folio+"\n"+r.getTimestamp("fecha")+"\n\n");
            BigDecimal total=BigDecimal.ZERO;
            do {
                int cantidad=r.getInt("cantidad");
                BigDecimal precio=r.getBigDecimal("precio"), importe=precio.multiply(BigDecimal.valueOf(cantidad));
                texto.append(String.format("%s · %d × $%s = $%s%n",r.getString("nombre"),cantidad,precio,importe));
                total=total.add(importe);
            } while (r.next());
            return texto+"\nTotal MXN: $"+total;
        }
    }
    String reporte(LocalDate dia) throws SQLException {
        try (var c=abrir();var s=consulta(c,"SELECT folio,SUM(precio*cantidad) importe FROM venta JOIN detalle USING(folio) WHERE fecha>=? AND fecha<? GROUP BY folio ORDER BY folio",dia,dia.plusDays(1));var r=s.executeQuery()) {
            var texto=new StringBuilder("Ventas del "+dia+"\n\n");
            BigDecimal total=new BigDecimal("0.00");
            while(r.next()) {
                BigDecimal importe=r.getBigDecimal("importe");
                texto.append("Folio #").append(r.getLong("folio")).append("    $").append(importe).append("\n");
                total=total.add(importe);
            }
            return texto+"\nTotal del día: $"+total;
        }
    }
}
