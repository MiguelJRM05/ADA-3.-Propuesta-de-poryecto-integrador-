package tecnofix;

import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import tecnofix.Tienda.Producto;
import tecnofix.Tienda.Linea;

public final class TecnoFix extends JFrame {
    private final Tienda tienda = new Tienda();
    private final Map<String,Linea> carrito = new TreeMap<>();
    private final JTextField filtro = new JTextField(18);
    private final JLabel total = new JLabel("Total MXN: $0.00");
    private final DefaultTableModel catalogo = modelo("Clave","Producto","Precio MXN","Stock");
    private final DefaultTableModel detalle = modelo("Clave","Producto","Cantidad","Subtotal MXN");
    private final JTable productos = new JTable(catalogo), cesta = new JTable(detalle);
    private java.util.List<Producto> resultado = java.util.List.of();

    private static DefaultTableModel modelo(String... columnas) {
        return new DefaultTableModel(columnas,0) {
            public boolean isCellEditable(int fila,int columna) { return false; }
        };
    }
    private interface Accion { void ejecutar() throws Exception; }
    private void intentar(Accion accion) {
        try { accion.ejecutar(); }
        catch (NumberFormatException e) { aviso("Ingresa un número válido."); }
        catch (java.time.format.DateTimeParseException e) { aviso("Usa el formato AAAA-MM-DD."); }
        catch (Exception e) { aviso(e.getMessage()); }
    }
    private void aviso(String mensaje) { JOptionPane.showMessageDialog(this,mensaje,"TecnoFix",JOptionPane.INFORMATION_MESSAGE); }
    private JButton boton(String nombre,Accion accion) {
        JButton b = new JButton(nombre);
        b.addActionListener(e -> intentar(accion));
        return b;
    }
    private JPanel fila(Component... elementos) {
        var panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (Component elemento: elementos) panel.add(elemento);
        return panel;
    }
    private void buscar() throws Exception {
        resultado = tienda.buscar(filtro.getText().trim());
        catalogo.setRowCount(0);
        for (Producto p:resultado) catalogo.addRow(new Object[]{p.clave(),p.nombre(),p.precio(),p.stock()});
    }
    private Producto seleccionado() {
        int i=productos.getSelectedRow();
        if (i<0) throw new IllegalArgumentException("Selecciona un producto del catálogo.");
        return resultado.get(i);
    }
    private Integer cantidad() {
        String texto=JOptionPane.showInputDialog(this,"Cantidad de unidades","1");
        if (texto==null) return null;
        int n=Integer.parseInt(texto.trim());
        if (n<=0) throw new IllegalArgumentException("La cantidad debe ser positiva.");
        return n;
    }
    private void agregar() {
        Producto p=seleccionado();
        Integer n=cantidad();
        if (n==null) return;
        Linea anterior=carrito.get(p.clave());
        long unidades=(long)n+(anterior==null?0:anterior.cantidad());
        if (unidades>p.stock()) throw new IllegalArgumentException("Solo hay "+p.stock()+" unidades de "+p.nombre()+".");
        carrito.put(p.clave(),new Linea(p,(int)unidades));
        mostrarCarrito();
    }
    private void mostrarCarrito() {
        detalle.setRowCount(0);
        BigDecimal suma=new BigDecimal("0.00");
        for (Linea l:carrito.values()) {
            detalle.addRow(new Object[]{l.producto().clave(),l.producto().nombre(),l.cantidad(),l.subtotal()});
            suma=suma.add(l.subtotal());
        }
        total.setText("Total MXN: $"+suma);
    }
    private void cobrar() throws Exception {
        if (carrito.isEmpty()) throw new IllegalArgumentException("Agrega un producto al carrito.");
        if (JOptionPane.showConfirmDialog(this,"¿Registrar la venta? "+total.getText(),"Cobrar",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION) return;
        long folio=tienda.vender(carrito.values());
        carrito.clear();
        mostrarCarrito();
        aviso("Venta registrada. Folio #"+folio);
        buscar();
        mostrarTexto(tienda.ticket(folio));
    }
    private void editar(boolean existente) throws Exception {
        Producto p=existente?seleccionado():null;
        var clave=new JTextField(existente?p.clave():"");
        clave.setEditable(!existente);
        var nombre=new JTextField(existente?p.nombre():"");
        var precio=new JTextField(existente?p.precio().toPlainString():"");
        Object[] campos={"Clave",clave,"Nombre",nombre,"Precio en MXN",precio};
        if (JOptionPane.showConfirmDialog(this,campos,"Producto",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) return;
        tienda.guardar(clave.getText().trim(),nombre.getText().trim(),new BigDecimal(precio.getText().trim()),existente);
        buscar();
    }
    private void mostrarTexto(String texto) {
        var area=new JTextArea(texto,15,48);
        area.setEditable(false);
        area.setCaretPosition(0);
        JOptionPane.showMessageDialog(this,new JScrollPane(area),"Consulta",JOptionPane.PLAIN_MESSAGE);
    }
    private TecnoFix() {
        super("TecnoFix · Punto de venta");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        var panel=new JPanel(new BorderLayout(6,6));
        panel.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        var superior=new JPanel(new BorderLayout());
        superior.add(fila(new JLabel("Clave o nombre"),filtro,boton("Buscar",this::buscar)),BorderLayout.NORTH);
        superior.add(new JScrollPane(productos));
        superior.add(fila(boton("Agregar al carrito",this::agregar),boton("Nuevo producto",()->editar(false)),
                boton("Editar producto",()->editar(true)),boton("Recibir mercancía",()->{
                    Producto p=seleccionado(); Integer n=cantidad();
                    if(n!=null) { tienda.recibir(p.clave(),n); buscar(); }
                })),BorderLayout.SOUTH);
        var inferior=new JPanel(new BorderLayout());
        inferior.setBorder(BorderFactory.createTitledBorder("Carrito"));
        inferior.add(new JScrollPane(cesta));
        inferior.add(fila(total,boton("Quitar renglón",()->{
            int i=cesta.getSelectedRow();
            if(i<0) throw new IllegalArgumentException("Selecciona un renglón del carrito.");
            carrito.remove(detalle.getValueAt(i,0)); mostrarCarrito();
        }),boton("Cobrar",this::cobrar)),BorderLayout.SOUTH);
        var division=new JSplitPane(JSplitPane.VERTICAL_SPLIT,superior,inferior);
        division.setResizeWeight(.57);
        panel.add(division);
        panel.add(fila(boton("Consultar ticket",()->{
            String folio=JOptionPane.showInputDialog(this,"Folio de venta");
            if(folio!=null) mostrarTexto(tienda.ticket(Long.parseLong(folio.trim())));
        }),boton("Ventas por fecha",()->{
            String fecha=JOptionPane.showInputDialog(this,"Fecha (AAAA-MM-DD)",LocalDate.now().toString());
            if(fecha!=null) mostrarTexto(tienda.reporte(LocalDate.parse(fecha.trim())));
        })),BorderLayout.SOUTH);
        filtro.addActionListener(e->intentar(this::buscar));
        setContentPane(panel);
        setSize(900,640); setLocationRelativeTo(null);
        setVisible(true);
        intentar(this::buscar);
    }
    public static void main(String[] args) { SwingUtilities.invokeLater(TecnoFix::new); }
}
