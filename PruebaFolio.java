package tecnofix;
public class PruebaFolio {
    public static void main(String[] args) throws Exception {
        for (long folio : new long[]{0, -3}) {
            try { new Tienda().ticket(folio); throw new AssertionError("Folio aceptado"); }
            catch (IllegalArgumentException error) {
                if (!"El folio debe ser un entero positivo.".equals(error.getMessage())) throw error;
                System.out.println("OK | folio " + folio + " | " + error.getMessage());
            }
        }
    }
}
