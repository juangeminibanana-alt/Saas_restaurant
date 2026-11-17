package app.shared.demo

import app.shared.model.*

/**
 * Datos de demostración genéricos (restaurantes ficticios, sin marca real).
 * Fuente única: el backend siembra la base con esto y DemoRepository lo usa en memoria.
 * Imágenes: "res://demo/<archivo>.jpg" = recurso empaquetado en composeResources/files/demo/.
 */
object DemoData {
    const val DEMO_PASSWORD = "demo1234"
    const val OPERATOR_EMAIL = "operador@demo.mx"

    data class DemoUser(val email: String, val name: String, val role: Role, val tenantSlug: String? = null)

    val users: List<DemoUser> = listOf(
        DemoUser(OPERATOR_EMAIL, "Operador Demo", Role.OPERATOR),
        DemoUser("dueno@brasa.demo", "Mariana Ríos", Role.OWNER, "brasa-demo"),
        DemoUser("gerente@brasa.demo", "Diego Luna", Role.MANAGER, "brasa-demo"),
        DemoUser("caja@brasa.demo", "Sofía Paz", Role.CASHIER, "brasa-demo"),
        DemoUser("cocina@brasa.demo", "Chef Tomás", Role.KITCHEN, "brasa-demo"),
        DemoUser("dueno@nori.demo", "Kenji Mora", Role.OWNER, "nori-demo"),
        DemoUser("cocina@nori.demo", "Cocina Nori", Role.KITCHEN, "nori-demo"),
        DemoUser("dueno@miga.demo", "Lucía Vera", Role.OWNER, "miga-demo"),
        DemoUser("cocina@miga.demo", "Cocina Miga", Role.KITCHEN, "miga-demo"),
    )

    val plans: List<Plan> = listOf(
        Plan(
            id = "plan-esencial", name = "Esencial", priceCents = 49_900,
            description = "Para empezar a vender con tu propio menú digital.",
            features = listOf("Menú digital con tu marca", "Link y QR por mesa", "Pagos con Mercado Pago", "Pantalla de cocina", "1 sucursal"),
            feeBps = 250, maxLocations = 1,
        ),
        Plan(
            id = "plan-pro", name = "Pro", priceCents = 99_900,
            description = "Para restaurantes que venden todos los días en varios canales.",
            features = listOf("Todo lo de Esencial", "Dominio propio", "Promociones y clientes", "Reparto con Lalamove", "Uber Eats, Rappi y DiDi Food en una sola pantalla", "Hasta 3 sucursales"),
            feeBps = 150, highlighted = true, maxLocations = 3,
        ),
        Plan(
            id = "plan-cadena", name = "Cadena", priceCents = 249_900,
            description = "Para grupos y cadenas con operación centralizada.",
            features = listOf("Todo lo de Pro", "Sucursales ilimitadas", "Roles y permisos avanzados", "Reportes consolidados", "Soporte prioritario"),
            feeBps = 90, maxLocations = 99,
        ),
    )

    /** slug → planId */
    val tenantPlan: Map<String, String> = mapOf("brasa-demo" to "plan-pro", "nori-demo" to "plan-esencial", "miga-demo" to "plan-pro")

    private val weekHours = (1..7).map { OpeningHours(it, "13:00", "23:00") }
    private val cafeHours = (1..7).map { OpeningHours(it, "07:30", "20:00") }

    val tenants: List<TenantPublic> = listOf(
        TenantPublic(
            id = "t-brasa", slug = "brasa-demo", name = "Brasa Demo",
            tagline = "Parrilla mexicana contemporánea",
            description = "Cortes al carbón, tacos de autor y cocteles de la casa. Restaurante ficticio de demostración.",
            cuisine = "Parrilla · Mexicana",
            theme = BrandTheme(primary = "#B4411F", fontDisplay = FontChoice.FRAUNCES, radius = RadiusStyle.SOFT, mode = ThemeMode.LIGHT, coverUrl = "res://demo/cover_brasa.jpg"),
            address = "Av. de la Demostración 123, Roma Norte, CDMX", phone = "55 0000 0001",
            hours = weekHours, etaMinutes = 25, rating = 4.8,
        ),
        TenantPublic(
            id = "t-nori", slug = "nori-demo", name = "Nori Demo",
            tagline = "Sushi bar y ramen",
            description = "Barra japonesa de temporada. Restaurante ficticio de demostración.",
            cuisine = "Japonesa · Sushi",
            theme = BrandTheme(primary = "#D9363E", fontDisplay = FontChoice.SPACE_GROTESK, radius = RadiusStyle.SHARP, mode = ThemeMode.DARK, coverUrl = "res://demo/cover_nori.jpg"),
            address = "Calle Ejemplo 45, Polanco, CDMX", phone = "55 0000 0002",
            hours = weekHours, etaMinutes = 30, rating = 4.7,
        ),
        TenantPublic(
            id = "t-miga", slug = "miga-demo", name = "Miga Demo",
            tagline = "Panadería y café de especialidad",
            description = "Pan recién horneado, café de especialidad y desayunos todo el día. Restaurante ficticio de demostración.",
            cuisine = "Panadería · Café",
            theme = BrandTheme(primary = "#2F6B4F", fontDisplay = FontChoice.NUNITO, fontBody = FontChoice.NUNITO, radius = RadiusStyle.PILL, mode = ThemeMode.LIGHT, coverUrl = "res://demo/cover_miga.jpg"),
            address = "Plaza Muestra 8, Coyoacán, CDMX", phone = "55 0000 0003",
            hours = cafeHours, etaMinutes = 15, rating = 4.9,
        ),
    )

    fun tenant(slug: String): TenantPublic? = tenants.firstOrNull { it.slug == slug }

    // ── helpers
    private fun img(file: String) = "res://demo/$file.jpg"
    private fun opt(id: String, name: String, pesos: Int = 0) = ModifierOption(id, name, pesos * 100L)
    private fun item(
        id: String, cat: String, name: String, desc: String, pesos: Int, image: String,
        tags: List<String> = emptyList(), groups: List<ModifierGroup> = emptyList(), prep: Int = 15, order: Int = 0,
    ) = MenuItem(id = id, categoryId = cat, name = name, description = desc, priceCents = pesos * 100L, imageUrl = img(image), tags = tags, modifierGroups = groups, prepMinutes = prep, sortOrder = order)

    private val termino = ModifierGroup("g-termino", "Término", minSelect = 1, maxSelect = 1, options = listOf(
        opt("o-term-rojo", "Rojo"), opt("o-term-medio", "Medio"), opt("o-term-tres", "Tres cuartos"), opt("o-term-bien", "Bien cocido"),
    ))
    private val guarnicion = ModifierGroup("g-guarnicion", "Guarnición", minSelect = 1, maxSelect = 1, options = listOf(
        opt("o-gua-papas", "Papas a la francesa"), opt("o-gua-ensalada", "Ensalada verde"), opt("o-gua-esquites", "Esquites", 25),
    ))
    private val extrasTaco = ModifierGroup("g-extras-taco", "Extras", minSelect = 0, maxSelect = 3, options = listOf(
        opt("o-ext-queso", "Costra de queso", 20), opt("o-ext-aguacate", "Aguacate", 18), opt("o-ext-pina", "Piña asada", 10),
    ))
    private val salsa = ModifierGroup("g-salsa", "Salsa", minSelect = 0, maxSelect = 2, options = listOf(
        opt("o-sal-verde", "Verde cruda"), opt("o-sal-roja", "Roja tatemada"), opt("o-sal-habanero", "Habanero"),
    ))
    private val leche = ModifierGroup("g-leche", "Leche", minSelect = 1, maxSelect = 1, options = listOf(
        opt("o-leche-entera", "Entera"), opt("o-leche-desl", "Deslactosada"), opt("o-leche-avena", "Avena", 15), opt("o-leche-almendra", "Almendra", 15),
    ))
    private val tamano = ModifierGroup("g-tamano", "Tamaño", minSelect = 1, maxSelect = 1, options = listOf(
        opt("o-tam-ch", "Chico"), opt("o-tam-gr", "Grande", 18),
    ))

    private val brasaMenu = Menu(
        categories = listOf(
            MenuCategory("c-entradas", "Entradas", "Para abrir boca", 0),
            MenuCategory("c-tacos", "Tacos", "Tortilla hecha a mano", 1),
            MenuCategory("c-parrilla", "De la parrilla", "Al carbón de mezquite", 2),
            MenuCategory("c-postres", "Postres", "El cierre perfecto", 3),
            MenuCategory("c-bebidas", "Bebidas", "Con y sin alcohol", 4),
        ),
        items = listOf(
            item("i-ensalada", "c-entradas", "Ensalada de la huerta", "Aguacate en láminas, garbanzo, jitomate cherry, camote rostizado y hojas tiernas con vinagreta de limón.", 135, "ensalada_aguacate_garbanzo", listOf("popular", "vegano"), prep = 8),
            item("i-albondigas", "c-entradas", "Albóndigas al carbón", "Albóndigas de res doradas a la parrilla sobre una cama de arúgula fresca.", 155, "albondigas_arugula", emptyList(), prep = 12, order = 1),
            item("i-camarones", "c-entradas", "Caldillo de camarón", "Camarones enteros en caldillo especiado de jitomate y chiles, con arroz blanco y cilantro fresco.", 195, "camarones_caldillo_arroz", listOf("picante", "nuevo"), prep = 14, order = 2),
            item("i-tacos-barbacoa", "c-tacos", "Tacos de barbacoa", "Tacos de res deshebrada en tortilla de maíz con cebolla picada y mucho cilantro.", 139, "tacos_barbacoa", listOf("popular"), listOf(extrasTaco, salsa), prep = 10),
            item("i-tacos-carnitas", "c-tacos", "Tacos de carnitas", "Tres tacos de carnitas con pico de gallo, aguacate y cilantro fresco.", 129, "tacos_carnitas", emptyList(), listOf(extrasTaco, salsa), prep = 10, order = 1),
            item("i-tacos-garbanzo", "c-tacos", "Tacos de garbanzo", "Tacos vegetarianos de garbanzo especiado con aguacate, cebolla morada y chile fresco, terminados con limón recién exprimido.", 119, "tacos_garbanzo_aguacate", listOf("vegetariano", "picante"), listOf(extrasTaco, salsa), prep = 10, order = 2),
            item("i-arrachera", "c-parrilla", "Arrachera al carbón", "Arrachera marinada y rebanada al momento, terminada con hierbas frescas y servida con papas a la francesa o la guarnición que elijas.", 345, "arrachera_papas", listOf("popular"), listOf(termino, guarnicion), prep = 20),
            item("i-costillas", "c-parrilla", "Costillas BBQ", "Costillar de cerdo cocido lentamente y glaseado con BBQ de chipotle, con jitomate fresco y pepinillos.", 365, "costillas_bbq", emptyList(), listOf(guarnicion), prep = 20, order = 1),
            item("i-chuleta", "c-parrilla", "Chuleta de cerdo a la parrilla", "Chuleta gruesa marcada al carbón con fruta asada en cubos, papas doradas y brocolini.", 289, "chuleta_cerdo_parrilla", listOf("nuevo"), prep = 18, order = 2),
            item("i-brochetas", "c-parrilla", "Parrillada de brochetas", "Brochetas de carnes al carbón con chiles y verduras asadas, papas y salsas de la casa para compartir.", 395, "parrillada_brochetas", emptyList(), prep = 22, order = 3),
            item("i-hamburguesa", "c-parrilla", "Hamburguesa Brasa", "Doble carne de res a la parrilla, queso cheddar, lechuga, jitomate, cebolla morada y aderezo de la casa en pan brioche.", 229, "hamburguesa", listOf("popular"), listOf(termino, guarnicion), prep = 15, order = 4),
            item("i-pastel", "c-postres", "Pastel de frambuesa", "Capas de bizcocho suave con crema de frambuesa, coronado con frambuesas frescas.", 115, "pastel_frambuesa", listOf("popular"), prep = 5),
            item("i-pannacotta", "c-postres", "Panna cotta con fresas", "Panna cotta de vainilla en vasito con fresas frescas en su jugo.", 95, "panna_cotta_fresas", emptyList(), prep = 4, order = 1),
            item("i-coctel", "c-bebidas", "Coctel de la casa", "Mezcal, cítricos y jarabe de agave, servido en copa fría con una rodaja de limón.", 165, "margaritas", listOf("nuevo"), prep = 5),
            item("i-limonada", "c-bebidas", "Limonada de fresa", "Limón recién exprimido con fresa natural, hierbabuena y mucho hielo.", 69, "agua_fresa_limon", emptyList(), listOf(tamano), prep = 3, order = 1),
            item("i-te-helado", "c-bebidas", "Té helado con limón", "Té negro frío servido con mucho hielo y rodajas de limón.", 55, "te_helado_limon", emptyList(), prep = 3, order = 2),
        ),
    )

    private val noriMenu = Menu(
        categories = listOf(
            MenuCategory("c-rollos", "Rollos", "", 0),
            MenuCategory("c-calientes", "Cocina", "", 1),
            MenuCategory("c-bebidas-n", "Bebidas", "", 2),
        ),
        items = listOf(
            item("n-rollos", "c-rollos", "Rollo de salmón", "Rollo cubierto con salmón fresco y centro cremoso, decorado con brotes verdes.", 185, "sushi_rolls_salmon", listOf("popular"), prep = 12),
            item("n-barco", "c-rollos", "Barco Nori para compartir", "Surtido de nigiris de salmón y camarón con rollos variados, servido en barco de madera.", 465, "sushi_barco_surtido", listOf("nuevo"), prep = 20, order = 1),
            item("n-bowl", "c-calientes", "Bowl de salmón sellado", "Cubos de salmón sellado con edamame, huevo cocido, elote, pepino, jitomate y col morada sobre lechuga fresca.", 195, "bowl_salmon_edamame", emptyList(), prep = 10),
            item("n-ramen", "c-calientes", "Ramen de camarón", "Fideos en caldo ligero con camarones, huevo tierno y verduras verdes.", 215, "ramen_camaron", listOf("popular"), prep = 15, order = 1),
            item("n-tataki", "c-calientes", "Tataki de res", "Láminas de res sellada término rojo con cebolla morada, chile fresco, hojas verdes y salsa de la casa.", 225, "res_sellada_ensalada", listOf("picante"), prep = 12, order = 2),
            item("n-te", "c-bebidas-n", "Té helado con limón", "Té negro frío servido con mucho hielo y rodajas de limón.", 60, "te_helado_limon", emptyList(), prep = 2),
        ),
    )

    private val migaMenu = Menu(
        categories = listOf(
            MenuCategory("c-cafe", "Café", "", 0),
            MenuCategory("c-pan", "Pan y repostería", "", 1),
            MenuCategory("c-desayunos", "Desayunos", "", 2),
        ),
        items = listOf(
            item("m-latte", "c-cafe", "Latte", "Espresso doble con leche vaporizada y arte latte.", 68, "cafe_latte", listOf("popular"), listOf(leche, tamano), prep = 4),
            item("m-flat-white", "c-cafe", "Flat white", "Espresso intenso con leche cremada vertida al momento y arte latte.", 62, "latte_art", emptyList(), listOf(leche, tamano), prep = 3, order = 1),
            item("m-croissant", "c-pan", "Croissant de mantequilla", "Hojaldrado a mano, horneado cada mañana y espolvoreado con azúcar glass.", 52, "croissants", listOf("popular"), prep = 2),
            item("m-pan", "c-pan", "Hogaza rústica de semillas", "Pan rústico de grano entero con costra crujiente cubierta de semillas.", 95, "pan_rustico_semillas", emptyList(), prep = 2, order = 1),
            item("m-cupcake", "c-pan", "Cupcake de fresa", "Panquecito de vainilla con betún de fresa y trocitos de fruta.", 58, "cupcakes_fresa", emptyList(), prep = 2, order = 2),
            item("m-galleta", "c-pan", "Galleta de chocolate", "Galleta gruesa con trozos de chocolate oscuro, crujiente por fuera y suave por dentro.", 42, "galletas_chocolate", listOf("nuevo"), prep = 2, order = 3),
            item("m-toast", "c-desayunos", "Toast de huevo y espinaca", "Pan tostado con huevo cocido en mitades y espinaca tierna.", 125, "tostada_huevo_espinaca", listOf("vegetariano"), prep = 10),
            item("m-pan-frances", "c-desayunos", "Pan francés con plátano", "Rebanadas doradas de pan francés con plátano, moras azules y azúcar glass.", 129, "pan_frances_platano", listOf("popular"), prep = 12, order = 1),
        ),
    )

    fun menuFor(slug: String): Menu = when (slug) {
        "brasa-demo" -> brasaMenu
        "nori-demo" -> noriMenu
        "miga-demo" -> migaMenu
        else -> Menu()
    }

    /** Pedidos de ejemplo ya pagados para que la cocina y el panel no arranquen vacíos. */
    fun sampleOrders(slug: String, nowMs: Long): List<Order> {
        val t = tenant(slug) ?: return emptyList()
        val items = menuFor(slug).items
        if (items.size < 4) return emptyList()
        fun line(i: Int, qty: Int, notes: String = ""): OrderLine {
            val it = items[i % items.size]
            val mods = it.modifierGroups.filter { g -> g.minSelect > 0 }.mapNotNull { g -> g.options.firstOrNull()?.let { o -> OrderLineModifier(g.name, o.name, o.priceDeltaCents) } }
            return OrderLine(it.id, it.name, qty, it.priceCents, mods, notes, it.imageUrl)
        }
        fun order(n: Int, status: OrderStatus, type: OrderType, channel: OrderChannel, minutesAgo: Int, name: String, table: String, lines: List<OrderLine>): Order {
            val subtotal = lines.sumOf { it.lineTotalCents }
            val created = nowMs - minutesAgo * 60_000L
            return Order(
                id = "demo-$slug-$n", tenantId = t.id, tenantSlug = t.slug, number = "A-" + (40 + n).toString().padStart(3, '0'),
                status = status, type = type, channel = channel, lines = lines, subtotalCents = subtotal, totalCents = subtotal,
                customerName = name, tableLabel = table, createdAt = created, paidAt = created + 20_000,
                preparingAt = if (status == OrderStatus.PREPARING || status == OrderStatus.READY) created + 90_000 else null,
                readyAt = if (status == OrderStatus.READY) nowMs - 60_000 else null,
                etaMinutes = t.etaMinutes,
                payment = PaymentInfo(PaymentMethod.CARD, PaymentStatus.APPROVED, providerPaymentId = "demo-pay-$n"),
            )
        }
        return listOf(
            order(1, OrderStatus.PAID, OrderType.DINE_IN, OrderChannel.DIRECT, 2, "Andrea", "Mesa 4", listOf(line(0, 1), line(3, 2, "Sin cebolla"))),
            order(2, OrderStatus.PAID, OrderType.DELIVERY, OrderChannel.UBER_EATS, 4, "Carlos M.", "", listOf(line(5, 1), line(1, 1))),
            order(3, OrderStatus.PREPARING, OrderType.PICKUP, OrderChannel.DIRECT, 9, "Fernanda", "", listOf(line(2, 1), line(4, 1), line(6, 2))),
            order(4, OrderStatus.PREPARING, OrderType.DELIVERY, OrderChannel.RAPPI, 14, "Luis R.", "", listOf(line(3, 3))),
            order(5, OrderStatus.READY, OrderType.DINE_IN, OrderChannel.DIRECT, 21, "Paola", "Mesa 9", listOf(line(1, 2), line(0, 1))),
        )
    }
}
