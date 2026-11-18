# NOCHE — oscuro de precisión

Sistema de diseño del producto. Las maquetas de esta carpeta (`style.css`, `menu.html`, `platillo.html` y sus
capturas) son la referencia visual; este documento dice cómo está implementado en la app
(`composeApp/src/commonMain/kotlin/app/ui/theme` y `app/ui/components`). Las firmas exactas están en
[`docs/DESIGN_SYSTEM_API.md`](../../DESIGN_SYSTEM_API.md).

## 1. Reglas

1. **La fotografía es lo único que brilla.** La interfaz es negra, plana y exacta.
2. **La brasa significa "tu pedido".** El acento (`colors.primary`) sólo existe como color sólido y sólo en tres
   lugares: la acción principal, la cantidad agregada y el aviso de una elección obligatoria pendiente.
   Nunca es contorno, texto, ícono ni fondo suave.
3. **La selección es tinta sólida.** Lo elegido (segmento, chip, radio, casilla, interruptor) se pinta con
   `colors.select` y texto `colors.onSelect`. No usa la brasa.
4. **Sin sombras, sin resplandores, sin desenfoques.** La profundidad es un filete de 1 px. No hay degradados,
   salvo el velo bajo el texto que va encima de una fotografía (`Modifier.photoScrim`).
5. **Las cifras van en mono.** Precios, conteos, horas y las etiquetas de 11 px usan Geist Mono; todo lo demás, Geist.
6. **Retícula de 4.** Margen de página 16; objetivo táctil mínimo 44 aunque la cara visible sea menor.
7. **Un solo sistema de radios:** 4 / 8 / 12 / 16, interior = exterior − 4.
8. **Movimiento corto y sin rebote:** 120–220 ms; nada pulsa ni late.

## 2. Marca blanca: cómo entra un restaurante

De un restaurante sólo entran **su color de acento y su fotografía**.

- `AppTheme(brand)` lee únicamente `brand.primary`. Si falta, usa la brasa `#FF6A3D`.
- El texto sobre el acento (`onPrimary`) se calcula por contraste: tinta oscura `#0B0B0C` sobre un acento
  luminoso, tinta clara `#F2F2F3` sobre uno profundo.
- Un acento demasiado oscuro para verse sobre negro se aclara hasta 3 : 1 contra el fondo.
- `fontDisplay`, `fontBody`, `radius` y `mode` del tenant **se ignoran**: tipografía, radios y modo oscuro son del sistema.
- `logoUrl` y `coverUrl` siguen siendo del restaurante (`BrandMark`, `DishImage`). Sin logo, el monograma va en
  tinta sobre superficie 2, no en el acento.

Acentos de la demostración: Brasa `#FF6A3D` · Nori `#7CF5C4` · Miga `#E8B86B` (los tres llevan texto oscuro).

## 3. Tokens

### Color

| Token (`AppTheme.colors`) | CSS | Valor | Uso |
|---|---|---|---|
| `canvas` | `--bg` | `#0B0B0C` | fondo de toda pantalla |
| `surface` | `--surface-1` | `#141416` | controles, mosaicos, tarjetas, barras, diálogos |
| `surfaceRaised` | `--surface-2` | `#1B1B1E` | control sobre superficie 1; marco de foto vacío |
| `line` | `--line` | blanco al 8 % | filete |
| `lineStrong` | `--line-strong` | blanco al 14 % | filete sobre fotografía; filete reforzado |
| `ink` | `--ink` | `#F2F2F3` | texto principal · 17.6 : 1 |
| `inkMuted` | `--muted` | `#A1A1A8` | texto secundario · 7.6 : 1 |
| `inkSubtle` | `--faint` | `#8A8A92` | etiquetas, íconos apagados · 5.6 : 1 |
| `select` / `onSelect` | `--select` / `--on-select` | `#F2F2F3` / `#0B0B0C` | selección |
| `primary` / `onPrimary` | `--accent` / `--on-accent` | del restaurante / por contraste | "tu pedido" |
| `positive` (= `success`) | `--positive` | `#3ECF8E` | abierto, correcto |
| `warning` | — | `#F2B84B` | atención (paneles) |
| `danger` | — | `#F2555A` | error, destructivo (paneles) |
| `info` | — | `#6CB4FF` | informativo (paneles) |
| `scrim` | — | negro al 60 % | detrás de diálogos y hojas |

Heredados que siguen compilando: `surfaceSunken` (= superficie 2), `primarySoft` y `successSoft`/`warningSoft`/
`dangerSoft`/`infoSoft` (tinte plano y opaco al 14 % sobre superficie 1). Los semánticos se usan como **punto
sólido o color de texto**, no como relleno.

**Nivel de superficie.** Un control se pinta un nivel arriba de lo que lo contiene: superficie 1 sobre el fondo,
superficie 2 dentro de una tarjeta, hoja, diálogo o barra. Se resuelve solo con `AppTheme.controlFill`
(lee `LocalSurfaceLevel`, que proveen `AppCard`, `AppDialog`, `AppBottomSheet`, `BottomBar` y `OptionList`).

### Tipografía

Geist 400 / 500 / 600 y Geist Mono 500 / 600 (archivos en `composeResources/font`). Escala
**40 · 32 · 20 · 16 · 15 · 13 · 11**, interlíneas en múltiplos de 4, tracking cerrado en lo grande.

| Estilo (`AppTheme.type`) | CSS | Fuente · tamaño/interlínea · peso · tracking | Para |
|---|---|---|---|
| `display` | `--t-hero` | Geist 40/44 · 600 · −0.04em | nombre del restaurante |
| `h1` | `--t-display` | Geist 32/36 · 600 · −0.035em | nombre del platillo, título de página |
| `h2` | `--t-title` | Geist 20/24 · 600 · −0.02em | título de sección, de diálogo |
| `h3` | (título de grupo, botón) | Geist 16/20 · 600 · −0.011em | título de grupo, texto de botón |
| `title` | `--t-body` | Geist 16/20 · 500 · −0.011em | nombre en fila o mosaico grande |
| `titleSm` | `--t-body-sm` | Geist 15/20 · 500 · −0.015em | nombre en mosaico chico, navegación |
| `body` | (descripción) | Geist 16/24 · 400 · −0.011em | texto corrido |
| `bodySm` | `--t-small` | Geist 13/20 · 400 | descripción de fila, dato secundario |
| `label` | — | Geist 11/16 · 500 | nota mínima en sans |
| `monoLabel` | `--t-label` | Geist Mono 11/16 · 500 · +0.06em, MAYÚSCULAS | etiquetas (`LabelText`) |
| `mono` | `--t-mono` | Geist Mono 13/20 · 500 | precios, conteos, tiempos |
| `monoMd` | `--t-mono-md` | Geist Mono 16/20 · 500 · −0.02em | precio en mosaico, cantidad grande |
| `monoLg` | `--t-mono-lg` | Geist Mono 20/24 · 500 · −0.02em | total, precio del platillo, métrica |

Las etiquetas se escriben con `LabelText`, que pasa el texto a MAYÚSCULAS y cierra el espacio entre palabras
0.25em, igual que la maqueta. Las unidades ("min") van en sans junto a la cifra en mono.

Las fuentes empaquetadas cubren latín y latín extendido (español completo). No traen flechas ni palomitas:
esos signos se dibujan con `AppIcons` (`Forward`, `Check`…), nunca como texto.

### Espacio, medidas y radios

- `Space`: 4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 56 · 72; `Space.gutter` = 16; `Space.tap` = 44.
- `Sizes`: filete 1 · ícono 20 · control de fila 32 · control 44 · control alto 48 · botón grande 56 · barra 72 · miniatura 80.
- `AppTheme.shapes`: `xs` 4 (ficha dentro de un control) · `sm` 8 (segmento activo, control de fila) ·
  `md` 12 (controles, miniaturas) · `lg` 16 (mosaicos, tarjetas, diálogos). `control` = `md`; `pill` = `sm`.

### Filetes (en lugar de sombras)

`Modifier.ring(color, shape)` filete interior · `Modifier.edgeTop(color)` / `edgeBottom(color)` filete en un borde ·
`Modifier.flatSurface(fill, line, shape)` superficie completa · `Modifier.photoScrim(color)` el velo.

### Movimiento

`Motion.FAST` 120 ms (color, presión), `Motion.BASE` 220 ms (entradas), `Motion.SLOW` 360 ms (desplazar el riel).
Curvas `Motion.standard` y `Motion.exit`; resorte `Motion.snappy()`. Sin halos, pulsos ni barridos de brillo:
lo que carga es una superficie plana que respira.

## 4. Componentes y estados

| Componente | Reposo | Estados |
|---|---|---|
| `AppButton` PRIMARY | brasa sólida, texto `onPrimary`, radio 12, alto 40 / 48 / 56 | sobre: velo 8 % · presionado: velo 16 % y escala 0.98 · deshabilitado: superficie + texto gris + filete · cargando: arco de 1.5 px |
| `AppButton` SECONDARY | superficie + filete, texto en tinta | sobre: filete fuerte · presionado: velo |
| `AppButton` GHOST | sólo texto | sobre / presionado: velo de tinta |
| `AppButton` DANGER | superficie + filete, texto rojo | como SECONDARY |
| `AppIconButton` | 44 × 44, radio 12, superficie + filete, ícono 20 | `filled`: fondo + filete fuerte (sobre foto) · `plain`: sin caja · `badge`: ficha de brasa |
| `AppCard` | superficie 1 + filete, radio 16 | clicable: filete fuerte al pasar · `elevated`: filete fuerte · anidada: superficie 2 |
| `AppTextField` | etiqueta mono arriba; superficie + filete, radio 12, alto 48 | foco: filete y etiqueta en tinta · error: filete, etiqueta y mensaje en rojo · deshabilitado: texto gris |
| `AppSwitch` | 44 × 24; apagado: superficie 2 + filete, pulgar gris | encendido: pista en tinta, pulgar oscuro · deshabilitado: 40 % |
| `SegmentedControl` | riel superficie + filete, radio 12, alto 44 (`large`: 48 y cuerpo 16); cada segmento mide su texto y el sobrante se reparte por igual | activo: pastilla en tinta 4 px adentro, radio 8, texto oscuro 600; se desliza al cambiar; el ancho no cambia al activarse |
| `CategoryTabs` | el mismo riel, con desplazamiento; segmentos con 16 de margen | activo: pastilla en tinta; se centra sola; el ancho no cambia al activarse |
| `AppChip` | superficie + filete, radio 8, alto 36 | seleccionado: tinta sólida · sobre: texto en tinta, filete fuerte |
| `StatusPill` | ficha radio 4, alto 24: punto sólido del tono + etiqueta mono | tonos: neutro, brasa, verde, ámbar, rojo, azul (sólo cambia el punto) |
| `TagBadge` | ficha radio 4, alto 20, fondo + filete, etiqueta mono en el color del tono | `PRIMARY`: tinta sólida (destacado) |
| `SelectionMark` | aro de 20 px, 1.5 px en gris | elegido: aro en tinta + punto de 10 · `multiple`: casilla radio 4 rellena con palomita |
| `OptionList` / `OptionRow` | bloque superficie + filete, radio 12; filas de 48 separadas por filetes | fila elegida: marca en tinta · `trailing`: dato en mono ("+$25") |
| `QuantityStepper` compacto | cara de 32, radio 8, superficie + filete; cifra mono 13 | `inOrder` (por defecto cuando hay cantidad; pasa `false` si la cifra no es de un pedido): ficha sólida de brasa 24 × 24 radio 4 · tope: botón gris |
| `QuantityStepper` grande | 120 × 48, radio 12; cifra mono 16 | `inOrder`: ficha de brasa 32 × 32 radio 8 |
| `AddToCartButton` | "+" con cara de 32, radio 8, superficie + filete | con cantidad: stepper compacto con ficha de brasa |
| `CountChip` | ficha sólida de brasa, radio 4, conteo en mono 600 | — |
| `BottomBar` | superficie 1, filete arriba, 12 / 16 de margen; contenido de 48 | `BottomBarSummary`: etiqueta mono + total mono 20/24, ancho 120 |
| `DishImage` | foto sin filtros con filete interior de 1 px | cargando: marco plano · sin imagen: marco plano + ícono · `alignment` + `zoom`: encuadre |
| `Skeleton` | superficie 2 que respira (50–100 %) | — |
| `Avatar` / `BrandMark` | círculo o cuadro radio 8, superficie 2 + filete, iniciales en tinta | con imagen: la foto |
| `AppDialog` | superficie 1 + filete fuerte, radio 16, sobre velo negro 60 % | entra con un fundido corto y 8 px de recorrido; sin sombra ni rebote |
| `AppBottomSheet` | igual, radio 16 arriba, asa de 36 × 4 | se arrastra para cerrar; en EXPANDED se centra |
| `Toasts` | superficie 2 + filete fuerte, radio 12, punto del tono + texto 13 | dura 2.6 s |
| `LoadingState` | tres puntos de tinta de 6 px en cascada; mensaje como etiqueta mono | — |
| `ErrorState` / `EmptyState` | mosaico de ícono 44 radio 8 + título 20/24 + texto 13 | acción en botón SECONDARY |
| `StatCard` | etiqueta mono, cifra mono 20/24 | variación: flecha + cifra en verde / rojo · tendencia: línea gris con el último punto en brasa |
| `BarChart` | barras planas en gris de tinta, radio 4 arriba, ejes en mono | la barra más alta en brasa |
| `LineChart` / `Sparkline` | línea de 1.5 px en tinta sobre filetes, sin área | el último punto en brasa |
| `OrderStatusTimeline` | nodos de 24 unidos por un filete | hecho: tinta sólida con palomita · actual: brasa sólida · pendiente: sólo filete · avisos: superficie + filete con punto del tono |
| `AdminScaffold` | todo sobre el fondo; filetes entre barra lateral, barra superior y contenido | entrada activa: superficie 2, ícono y texto en tinta · título de barra: etiqueta mono · conteos: ficha de brasa · `NavItem.section`: etiqueta mono de grupo |
| `PageHeader` | título 32/36 (20/24 en COMPACT), subtítulo 13 | `label`: antetítulo en mono |
| `SectionHeader` | título 20/24 | `label`: etiqueta mono a la derecha, en la línea base · `subtitle` debajo |
| `QrCode` | negro `#0B0B0C` sobre blanco, radio 8 | siempre así, para que cualquier cámara lo lea |

Iconos (`AppIcons`): trazo único, sin relleno, 1.5 px al tamaño estándar de 20.

## 5. Recetas de la maqueta

| Pieza de la maqueta | En la app |
|---|---|
| Portada con identidad encima | `DishImage(cover, shape = RectangleShape, ring = false)` + `Modifier.photoScrim(colors.canvas)`; antetítulo `LabelText(color = inkMuted)`; nombre en `type.display` |
| Ficha de datos (abierto · tiempo · calificación) | `Modifier.edgeTop(line)`; `StatusDot(colors.positive)`; cifras en `type.mono`, unidades en `type.bodySm` |
| Buscar + categorías pegadas | `AppIconButton(AppIcons.Search)` + `CategoryTabs`; al pegarse, `Modifier.edgeBottom(line)` |
| Encabezado de sección | `SectionHeader(title, label = "SELECCIÓN DE LA CASA")` |
| Mosaico con foto | `Modifier.flatSurface(colors.surface, colors.lineStrong, shapes.lg)`; foto con `ring = false`; pie con `edgeTop(line)` y margen 12; nombre `titleSm` / `title`; precio `monoMd` |
| Fila de platillo | miniatura `DishImage(Modifier.size(Sizes.thumb))` en macro (`zoom`); nombre `title`; descripción `bodySm` en `inkMuted`; precio `mono`; `AddToCartButton` |
| Barra del menú | `BottomBar { BottomBarSummary("3 platillos", "$413"); AppButton("Ver pedido", …, trailingIcon = AppIcons.Forward, modifier = Modifier.weight(1f)) }` |
| Barra del platillo | `BottomBar { QuantityStepper(qty, …, min = 1); AppButton("Agregar", …, mono = "$345", modifier = Modifier.weight(1f)) }` |
| Cerrar sobre la foto | `AppIconButton(AppIcons.Close, …, filled = true)` |
| Grupo obligatorio pendiente | título en `type.h3` + `LabelText("Elige 1", color = colors.ink, dot = colors.primary)`; resuelto: `LabelText("Elige 1")` |
| Elección corta (término) | `SegmentedControl(…, large = true)` |
| Elección en lista (guarnición) | `OptionList { OptionRow(name, selected, onClick, trailing = "+$25", first = i == 0) }` |

## 6. Lo que no se hace

- Brasa como contorno, texto, ícono o fondo suave; brasa para marcar selección.
- Sombras, brillos, halos que pulsan, desenfoques, degradados decorativos.
- Cápsulas: el radio máximo es 16.
- Otra tipografía, otro juego de radios o un modo claro por restaurante.
- Filtros sobre la fotografía: se cura con encuadre (`alignment`, `zoom`), no con CSS ni capas.
- Colores o medidas sueltos: todo sale de `AppTheme.colors`, `AppTheme.type`, `AppTheme.shapes`, `Space` y `Sizes`.
