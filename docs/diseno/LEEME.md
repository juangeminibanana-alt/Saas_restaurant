# Direcciones de diseño (en curso)

Tres direcciones para la misma pantalla (menú del comensal y detalle de platillo de "Brasa Demo"), hechas en
HTML + CSS y **revisadas sobre el render real**. Cada carpeta tiene `style.css` (tokens arriba), `menu.html`,
`platillo.html` y sus capturas.

| Carpeta | Dirección | Nota del crítico (ronda 1) |
| --- | --- | --- |
| `claro/` | Claro — precisión luminosa | 7.2 |
| `atelier/` | Atelier — lujo silencioso | 7.5 |
| `noche/` | Noche — oscuro de precisión | 7.5 |

Comparativas: `comparativa-menu.jpg` y `comparativa-platillo.jpg` (Claro, Atelier, Noche de izquierda a derecha).

Para volver a renderizar (requiere Node y Playwright): `node tools/render.js claro/menu.html salida.png 390 844 0 2`.

Estado: instantánea de la primera versión de cada dirección; la ronda de pulido sigue en curso y falta que
el cliente elija. Ninguna está aplicada todavía a las apps.
