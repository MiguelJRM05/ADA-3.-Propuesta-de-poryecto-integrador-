# TecnoFix
Punto de venta de componentes de PC en Java Swing con MySQL. Los importes se expresan en pesos mexicanos.

## Abrir en VS Code
1. Instala un JDK 17 o superior y la extensión Extension Pack for Java. Inicia MySQL local (si usas XAMPP, inicia MySQL y Apache para abrir phpMyAdmin).
2. Importa únicamente `sql/tecnofix.sql` desde la pestaña **Importar** de phpMyAdmin o ejecútalo con tu cliente SQL. El script crea la base de datos y carga cuatro productos de muestra.
3. Descomprime el ZIP y abre la carpeta **TecnoFix** que contiene `pom.xml`. Permite que VS Code importe las dependencias de Maven. La primera descarga requiere internet.
4. Si usas el servidor local en el puerto 3306 con usuario root sin contraseña, abre `TecnoFix.java` y pulsa **Run** sobre el método main. Si tu servidor usa otras credenciales, configura las variables de entorno indicadas abajo antes de abrir VS Code.

### Ejecución alternativa desde PowerShell
Con Maven instalado, abre una terminal en la carpeta del proyecto y ejecuta:
```powershell
$env:TECNOFIX_URL = "jdbc:mysql://localhost:3306/tecnofix"
$env:TECNOFIX_USER = "tu_usuario"
$env:TECNOFIX_PASSWORD = "tu_contraseña"
mvn compile exec:java
```
El usuario de la aplicación necesita acceso de lectura y escritura a la base tecnofix.

## Probar una venta
Busca RAM16, selecciona la memoria, pulsa **Agregar al carrito** y escribe 2. Con los datos iniciales el total será $1,198.00. Al cobrar, se generará un folio y el stock bajará de 8 a 6. Usa **Consultar ticket** para recuperar el comprobante y **Ventas por fecha** para ver el corte. Selecciona un artículo y pulsa **Recibir mercancía** para registrar una entrada. Los productos nuevos comienzan con cero unidades.
