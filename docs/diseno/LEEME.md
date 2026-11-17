# Direcciones de diseño

Tres direcciones para la misma pantalla (menú del comensal y detalle de platillo de "Brasa Demo"), hechas en
HTML + CSS y **revisadas sobre el render real**: cada una pasó por diseño, dos críticas independientes y dos
pulidos. Cada carpeta tiene `style.css` (tokens arriba), `menu.html`, `platillo.html`, sus capturas y, si las
usa, fotos en mayor resolución.

| Carpeta | Dirección | 1.ª crítica | 2.ª crítica |
| --- | --- | --- | --- |
| `claro/` | Claro — precisión luminosa | 7.2 | 7.8 |
| `atelier/` | Atelier — lujo silencioso | 7.5 | 8.0 |
| `noche/` | Noche — oscuro de precisión | 7.5 | 7.8 |

Después de la segunda crítica hubo un último pulido que ya no se volvió a calificar.

Comparativas (Claro, Atelier, Noche de izquierda a derecha): `final-menu.jpg`, `final-platillo.jpg` y
`final-pagina-completa.jpg` (página completa del menú, al 50 %).

Para volver a renderizar (requiere Node y Playwright): `node tools/render.js claro/menu.html salida.png 390 844 0 2`.

Estado: falta que el cliente elija dirección. Ninguna está aplicada todavía a las apps.
