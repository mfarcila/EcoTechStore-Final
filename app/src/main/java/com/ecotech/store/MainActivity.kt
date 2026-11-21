package com.ecotech.store

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import java.text.NumberFormat
import java.util.Locale

data class ProductItem(val id: String, val name: String, val price: Int, val specs: String, var qty: Int = 1)

/**
 * Repositorio en memoria para gestionar el carrito de compras activo,
 * la imagen del producto seleccionado y el formato de moneda colombiana (COP).
 */
object StoreRepository {
    val cart = mutableListOf<ProductItem>()
    var selectedProductImage: String = ""

    var deliveryAddress: String = ""

    fun addToCart(item: ProductItem) {
        val existing = cart.find { it.id == item.id }
        if (existing != null) {
            existing.qty += 1
        } else {
            cart.add(item.copy(qty = 1))
        }
    }

    fun formatCop(amount: Int): String {
        val format = NumberFormat.getNumberInstance(Locale("es", "CO"))
        return "$ " + format.format(amount) + " COP"
    }

    fun getTotal(): Int = cart.sumOf { it.price * it.qty }
}

/**
 * Pantalla de bienvenida (Splash Screen) con transición automática al Login.
 */
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }, 1500)
    }
}

/**
 * Pantalla principal de autenticación.
 * Permite inicio de sesión, registro por rol en Firebase Auth/Firestore y acceso biométrico.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        authManager = AuthManager(this)

        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val cbHabeas = findViewById<CheckBox>(R.id.cbHabeasData)
        val cbTerms = findViewById<CheckBox>(R.id.cbTerms)

        findViewById<Button>(R.id.btnLogin).setOnClickListener { btn ->
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Ingresa correo y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btn.isEnabled = false
            authManager.login(
                email = email,
                password = pass,
                onSuccess = { user ->
                    btn.isEnabled = true
                    Toast.makeText(this, "Bienvenido ${user.email}", Toast.LENGTH_SHORT).show()
                    openRole(user.role)
                },
                onError = { msg ->
                    btn.isEnabled = true
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            )
        }

        findViewById<Button>(R.id.btnBiometric).setOnClickListener {
            authManager.authenticateWithBiometrics(
                activity = this,
                onSuccess = { savedRole ->
                    Toast.makeText(this, "Huella verificada", Toast.LENGTH_SHORT).show()
                    openRole(savedRole)
                },
                onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
            )
        }

        findViewById<Button>(R.id.btnGoogleOAuth).setOnClickListener {
            Toast.makeText(this, "Usa tu correo y contraseña o huella para ingresar", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            startActivity(Intent(this, RecoverPasswordActivity::class.java))
        }

        val registerWithRole: (String) -> Unit = { role ->
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            when {
                email.isEmpty() || pass.length < 6 -> {
                    Toast.makeText(this, "Ingresa un correo válido y contraseña de mínimo 6 caracteres", Toast.LENGTH_LONG).show()
                }
                !cbHabeas.isChecked || !cbTerms.isChecked -> {
                    Toast.makeText(this, "Debes aceptar Habeas Data y Términos y Condiciones", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    val defaultName = email.substringBefore("@")
                    authManager.register(
                        name = defaultName,
                        email = email,
                        password = pass,
                        role = role,
                        onSuccess = { user ->
                            Toast.makeText(this, "Cuenta creada como $role", Toast.LENGTH_SHORT).show()
                            openRole(user.role)
                        },
                        onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
                    )
                }
            }
        }

        findViewById<Button>(R.id.btnEnterBuyer).setOnClickListener { registerWithRole("BUYER") }
        findViewById<Button>(R.id.btnEnterSeller).setOnClickListener { registerWithRole("SELLER") }
        findViewById<Button>(R.id.btnEnterAdmin).setOnClickListener { registerWithRole("ADMIN") }
    }

    private fun openRole(role: String) {
        val intent = Intent(this, RoleHostActivity::class.java)
        intent.putExtra("ROLE", role)
        startActivity(intent)
    }
}

/**
 * Pantalla de recuperación de contraseña conectada a Firebase Auth.
 */
class RecoverPasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recover_password)

        val authManager = AuthManager(this)
        val etEmail = findViewById<TextInputEditText>(R.id.etRecoverEmail)

        findViewById<Button>(R.id.btnSendRecover).setOnClickListener {
            val email = etEmail.text.toString().trim()
            authManager.resetPassword(
                email = email,
                onSuccess = {
                    Toast.makeText(this, "Correo de recuperación enviado. Revisa tu bandeja.", Toast.LENGTH_LONG).show()
                    finish()
                },
                onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
            )
        }
        findViewById<Button>(R.id.btnBackLogin).setOnClickListener { finish() }
    }
}

/**
 * Contenedor principal por roles (BUYER, SELLER, ADMIN).
 * Conecta los CRUDs de Usuarios, Productos (con Cámara/Galería) y Órdenes a Cloud Firestore.
 */
class RoleHostActivity : AppCompatActivity() {
    private lateinit var container: FrameLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNav: BottomNavigationView
    private val firestoreManager = FirestoreManager()

    private var currentRole: String = "BUYER"
    private var capturedImageBase64: String = ""
    private var editingProductId: String? = null
    private var editingUserUid: String? = null

    private var gpsStatusView: TextView? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchCurrentLocation()
        } else {
            Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestAndCaptureGps() {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            fetchCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    @Suppress("MissingPermission")
    private fun fetchCurrentLocation() {
        gpsStatusView?.text = "Obteniendo coordenadas GPS..."
        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                if (location != null) {
                    val lat = String.format(Locale.US, "%.5f", location.latitude)
                    val lng = String.format(Locale.US, "%.5f", location.longitude)
                    var readableAddress = "Lat: $lat, Lng: $lng"
                    try {
                        val geocoder = Geocoder(this, Locale("es", "CO"))
                        @Suppress("DEPRECATION")
                        val results = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!results.isNullOrEmpty()) {
                            readableAddress = "${results[0].getAddressLine(0)} (Lat: $lat, Lng: $lng)"
                        }
                    } catch (_: Exception) { }

                    StoreRepository.deliveryAddress = readableAddress
                    gpsStatusView?.text = "Ubicación capturada:\n$readableAddress"
                    Toast.makeText(this, "Ubicación GPS guardada para el envío", Toast.LENGTH_SHORT).show()
                } else {
                    gpsStatusView?.text = "No se pudo obtener señal GPS. Activa la ubicación del teléfono."
                }
            }
            .addOnFailureListener { e ->
                gpsStatusView?.text = "Error GPS: ${e.message}"
            }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            capturedImageBase64 = firestoreManager.bitmapToBase64(bitmap)
            Toast.makeText(this, "Foto capturada y comprimida lista para guardar", Toast.LENGTH_SHORT).show()
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        capturedImageBase64 = firestoreManager.bitmapToBase64(bitmap)
                        Toast.makeText(this, "Imagen seleccionada y comprimida lista para guardar", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error leyendo imagen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        currentRole = intent.getStringExtra("ROLE") ?: "BUYER"
        when (currentRole) {
            "BUYER" -> setTheme(R.style.Theme_EcoTech_Buyer)
            "SELLER" -> setTheme(R.style.Theme_EcoTech_Seller)
            "ADMIN" -> setTheme(R.style.Theme_EcoTech_Admin)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_host)

        container = findViewById(R.id.fragmentContainer)
        toolbar = findViewById(R.id.topAppBar)
        bottomNav = findViewById(R.id.bottomNavigation)

        toolbar.setNavigationOnClickListener {
            AuthManager(this).logout()
            Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()
            finish()
        }

        when (currentRole) {
            "BUYER" -> setupBuyer()
            "SELLER" -> setupSeller()
            "ADMIN" -> setupAdmin()
        }
    }

    private fun setupBuyer() {
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.buyer_primary))
        toolbar.title = getString(R.string.buyer_home_title)
        bottomNav.inflateMenu(R.menu.menu_buyer_bottom)
        loadView(R.layout.view_buyer_home)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_buyer_home -> { toolbar.title = getString(R.string.buyer_home_title); loadView(R.layout.view_buyer_home) }
                R.id.nav_buyer_catalog -> { toolbar.title = getString(R.string.buyer_catalog_title); loadView(R.layout.view_buyer_catalog) }
                R.id.nav_buyer_cart -> { toolbar.title = getString(R.string.buyer_cart_title); loadView(R.layout.view_buyer_cart) }
                R.id.nav_buyer_profile -> { toolbar.title = getString(R.string.buyer_profile_title); loadView(R.layout.view_buyer_profile) }
            }
            true
        }
    }

    private fun setupSeller() {
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.seller_primary))
        toolbar.title = getString(R.string.seller_panel_title)
        bottomNav.inflateMenu(R.menu.menu_seller_bottom)
        loadView(R.layout.view_seller_products)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_seller_products -> { toolbar.title = getString(R.string.seller_panel_title); loadView(R.layout.view_seller_products) }
                R.id.nav_seller_orders -> { toolbar.title = getString(R.string.seller_orders_title); loadView(R.layout.view_seller_orders) }
                R.id.nav_seller_profile -> { toolbar.title = getString(R.string.seller_profile_title); loadView(R.layout.view_seller_profile) }
            }
            true
        }
    }

    private fun setupAdmin() {
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.admin_primary))
        toolbar.title = getString(R.string.admin_dash_title)
        bottomNav.inflateMenu(R.menu.menu_admin_bottom)
        loadView(R.layout.view_admin_dashboard)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_admin_dash -> { toolbar.title = getString(R.string.admin_dash_title); loadView(R.layout.view_admin_dashboard) }
                R.id.nav_admin_users -> { toolbar.title = getString(R.string.admin_users_title); loadView(R.layout.view_admin_users) }
                R.id.nav_admin_products -> { toolbar.title = getString(R.string.admin_products_title); loadView(R.layout.view_admin_products) }
                R.id.nav_admin_sales -> { toolbar.title = getString(R.string.admin_sales_title); loadView(R.layout.view_admin_sales) }
            }
            true
        }
    }

    private fun openProductDetail(product: ProductItem, imageBase64: String = "") {
        StoreRepository.selectedProductImage = imageBase64
        val intent = Intent(this, ProductDetailActivity::class.java).apply {
            putExtra("ID", product.id)
            putExtra("NAME", product.name)
            putExtra("PRICE", product.price)
            putExtra("SPECS", product.specs)
        }
        startActivity(intent)
    }

    private fun loadView(layoutRes: Int) {
        container.removeAllViews()
        val view = LayoutInflater.from(this).inflate(layoutRes, container, false)
        container.addView(view)

        view.findViewById<Button>(R.id.btnLogout)?.setOnClickListener {
            AuthManager(this).logout()
            Toast.makeText(this, "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()
            finish()
        }

        // 1. Home Comprador
        view.findViewById<Button>(R.id.btnOpenDetailFromHome)?.setOnClickListener {
            bottomNav.selectedItemId = R.id.nav_buyer_catalog
        }

        // 2. Catálogo Comprador
        val catalogContainer = view.findViewById<LinearLayout>(R.id.catalogItemsContainer)
        if (catalogContainer != null) {
            renderCatalogFromFirestore(catalogContainer)
        }

        // 3. Carrito Comprador
        val cartContainer = view.findViewById<LinearLayout>(R.id.cartItemsContainer)
        val tvTotal = view.findViewById<TextView>(R.id.tvCartTotal)
        if (cartContainer != null && tvTotal != null) {
            renderCartItems(cartContainer, tvTotal)
        }
        gpsStatusView = view.findViewById(R.id.tvGpsStatus)
        if (StoreRepository.deliveryAddress.isNotBlank()) {
            gpsStatusView?.text = "Ubicación capturada:\n${StoreRepository.deliveryAddress}"
        }
        view.findViewById<Button>(R.id.btnCaptureGps)?.setOnClickListener {
            requestAndCaptureGps()
        }

        view.findViewById<Button>(R.id.btnProceedToPayment)?.setOnClickListener {
            if (StoreRepository.cart.isEmpty()) {
                Toast.makeText(this, "El carrito está vacío", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, PaymentGatewayActivity::class.java))
            }
        }

        // 4. Vendedor CRUD Productos
        val sellerListContainer = view.findViewById<LinearLayout>(R.id.sellerProductsContainer)
        val btnSave = view.findViewById<Button>(R.id.btnSaveProduct)
        val btnCamera = view.findViewById<Button>(R.id.btnOpenCamera)
        val etName = view.findViewById<TextInputEditText>(R.id.etSellerName)
        val etPrice = view.findViewById<TextInputEditText>(R.id.etSellerPrice)
        val etSpecs = view.findViewById<TextInputEditText>(R.id.etSellerSpecs)

        if (sellerListContainer != null) {
            renderSellerProductsFromFirestore(sellerListContainer, etName, etPrice, etSpecs, btnSave)
        }

        btnCamera?.setOnClickListener {
            val options = arrayOf("Tomar foto con Cámara", "Elegir desde Galería")
            AlertDialog.Builder(this)
                .setTitle("Imagen del Producto")
                .setItems(options) { _, which ->
                    if (which == 0) cameraLauncher.launch(null)
                    else galleryLauncher.launch("image/*")
                }
                .show()
        }

        btnSave?.setOnClickListener {
            val nameTxt = etName?.text.toString().trim()
            val priceDouble = etPrice?.text.toString().trim().toDoubleOrNull()
            val specsTxt = etSpecs?.text.toString().trim().ifEmpty { "Reacondicionado Certificado (Ley 1480)" }

            if (nameTxt.isEmpty() || priceDouble == null) {
                Toast.makeText(this, "Ingresa nombre y precio válido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            if (editingProductId == null) {
                firestoreManager.createProduct(
                    name = nameTxt,
                    price = priceDouble,
                    specs = specsTxt,
                    imageBase64 = capturedImageBase64,
                    onSuccess = {
                        if (isFinishing || isDestroyed) return@createProduct
                        btnSave.isEnabled = true
                        Toast.makeText(this, "¡Guardado en Firestore! Pendiente de aprobación.", Toast.LENGTH_LONG).show()
                        etName?.setText("")
                        etPrice?.setText("")
                        etSpecs?.setText("")
                        capturedImageBase64 = ""
                        if (sellerListContainer != null) {
                            renderSellerProductsFromFirestore(sellerListContainer, etName, etPrice, etSpecs, btnSave)
                        }
                    },
                    onError = { msg ->
                        btnSave.isEnabled = true
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                )
            } else {
                firestoreManager.updateProduct(
                    productId = editingProductId!!,
                    name = nameTxt,
                    price = priceDouble,
                    specs = specsTxt,
                    onSuccess = {
                        if (isFinishing || isDestroyed) return@updateProduct
                        btnSave.isEnabled = true
                        Toast.makeText(this, "Producto actualizado en Firestore", Toast.LENGTH_SHORT).show()
                        editingProductId = null
                        btnSave.text = "Guardar Producto"
                        etName?.setText("")
                        etPrice?.setText("")
                        etSpecs?.setText("")
                        if (sellerListContainer != null) {
                            renderSellerProductsFromFirestore(sellerListContainer, etName, etPrice, etSpecs, btnSave)
                        }
                    },
                    onError = { msg ->
                        btnSave.isEnabled = true
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }

        // 5. Admin - CRUD de Usuarios
        val adminUsersContainer = view.findViewById<LinearLayout>(R.id.adminUsersListContainer)
        val etUserEmail = view.findViewById<TextInputEditText>(R.id.etAdminUserEmail)
        val etUserRole = view.findViewById<TextInputEditText>(R.id.etAdminUserRole)
        val btnSaveUser = view.findViewById<Button>(R.id.btnAdminSaveUser)

        if (adminUsersContainer != null) {
            renderAdminUsersFromFirestore(adminUsersContainer, etUserEmail, etUserRole)
        }

        btnSaveUser?.setOnClickListener {
            val uid = editingUserUid
            val newRole = etUserRole?.text.toString().trim().uppercase()
            if (uid == null) {
                Toast.makeText(this, "Primero toca 'Editar Rol' en un usuario de la lista", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newRole !in listOf("BUYER", "SELLER", "ADMIN")) {
                Toast.makeText(this, "El rol debe ser BUYER, SELLER o ADMIN", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            firestoreManager.updateUser(
                uid = uid,
                newRole = newRole,
                isActive = true,
                onSuccess = {
                    if (isFinishing || isDestroyed) return@updateUser
                    Toast.makeText(this, "Rol actualizado a $newRole en Firestore", Toast.LENGTH_SHORT).show()
                    editingUserUid = null
                    etUserEmail?.setText("")
                    etUserRole?.setText("")
                    if (adminUsersContainer != null) {
                        renderAdminUsersFromFirestore(adminUsersContainer, etUserEmail, etUserRole)
                    }
                },
                onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
            )
        }

        // 6. Admin - Moderación de Productos
        val adminProductsContainer = view.findViewById<LinearLayout>(R.id.adminProductsListContainer)
        if (adminProductsContainer != null) {
            renderAdminProductsFromFirestore(adminProductsContainer)
        }

        // 7. Admin - Reporte de Ventas
        val salesContainer = view.findViewById<LinearLayout>(R.id.salesListContainer)
        if (salesContainer != null) {
            renderAdminSalesFromFirestore(salesContainer)
        }
    }

    private fun renderCatalogFromFirestore(container: LinearLayout) {
        container.removeAllViews()
        container.addView(TextView(this).apply { text = "Cargando catálogo desde Firestore..." })

        firestoreManager.getProductsByStatus(
            status = "APROBADO",
            onSuccess = { products ->
                if (isFinishing || isDestroyed) return@getProductsByStatus
                container.removeAllViews()
                if (products.isEmpty()) {
                    container.addView(TextView(this).apply { text = "No hay productos aprobados todavía." })
                    return@getProductsByStatus
                }
                products.forEach { prod ->
                    val card = MaterialCardView(this).apply {
                        radius = 20f
                        cardElevation = 4f
                        setContentPadding(24, 24, 24, 24)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                    }
                    val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

                    val bmp = firestoreManager.base64ToBitmap(prod.imageBase64)
                    if (bmp != null) {
                        inner.addView(ImageView(this).apply {
                            setImageBitmap(bmp)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 380).apply { setMargins(0, 0, 0, 12) }
                        })
                    }

                    inner.addView(TextView(this).apply { text = prod.name; textSize = 17f; setTextColor(ContextCompat.getColor(context, R.color.eco_text_primary)); setTypeface(null, android.graphics.Typeface.BOLD) })
                    inner.addView(TextView(this).apply { text = "${StoreRepository.formatCop(prod.price.toInt())} | ${prod.warranty}"; setTextColor(ContextCompat.getColor(context, R.color.buyer_primary)); setPadding(0, 6, 0, 12) })

                    val btnDetail = Button(this).apply {
                        text = "Ver Detalle y Garantía"
                        setBackgroundColor(ContextCompat.getColor(context, R.color.buyer_primary))
                        setTextColor(ContextCompat.getColor(context, R.color.eco_white))
                        setOnClickListener {
                            openProductDetail(
                                ProductItem(prod.id, prod.name, prod.price.toInt(), "${prod.description}\n• ${prod.warranty}"),
                                prod.imageBase64
                            )
                        }
                    }
                    inner.addView(btnDetail)
                    card.addView(inner)
                    container.addView(card)
                }
            },
            onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        )
    }

    private fun renderCartItems(cartContainer: LinearLayout, tvTotal: TextView) {
        cartContainer.removeAllViews()
        if (StoreRepository.cart.isEmpty()) {
            cartContainer.addView(TextView(this).apply { text = "Tu carrito está vacío."; setPadding(16, 16, 16, 16) })
        } else {
            StoreRepository.cart.forEachIndexed { index, item ->
                val card = MaterialCardView(this).apply {
                    radius = 20f
                    cardElevation = 4f
                    setContentPadding(24, 24, 24, 24)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                }
                val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                inner.addView(TextView(this).apply { text = "${item.qty}x ${item.name}"; textSize = 16f; setTextColor(ContextCompat.getColor(context, R.color.eco_text_primary)) })
                inner.addView(TextView(this).apply { text = "Subtotal: ${StoreRepository.formatCop(item.price * item.qty)}"; setTextColor(ContextCompat.getColor(context, R.color.buyer_primary)) })

                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 12, 0, 0) } }
                actions.addView(Button(this).apply { text = "+1 Cantidad"; setOnClickListener { item.qty += 1; renderCartItems(cartContainer, tvTotal) } })
                actions.addView(Button(this).apply { text = "Eliminar"; setOnClickListener { StoreRepository.cart.removeAt(index); renderCartItems(cartContainer, tvTotal) } })
                inner.addView(actions)
                card.addView(inner)
                cartContainer.addView(card)
            }
        }
        tvTotal.text = "Total a Pagar: ${StoreRepository.formatCop(StoreRepository.getTotal())}"
    }

    private fun renderSellerProductsFromFirestore(
        container: LinearLayout,
        etName: TextInputEditText?,
        etPrice: TextInputEditText?,
        etSpecs: TextInputEditText?,
        btnSave: Button?
    ) {
        container.removeAllViews()
        container.addView(TextView(this).apply { text = "Cargando inventario..." })

        firestoreManager.getAllProducts(
            onSuccess = { products ->
                if (isFinishing || isDestroyed) return@getAllProducts
                container.removeAllViews()
                if (products.isEmpty()) {
                    container.addView(TextView(this).apply { text = "No hay productos registrados en Firestore." })
                    return@getAllProducts
                }
                products.forEach { prod ->
                    val card = MaterialCardView(this).apply {
                        radius = 20f
                        cardElevation = 4f
                        setContentPadding(24, 24, 24, 24)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                    }
                    val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    inner.addView(TextView(this).apply { text = "${prod.name} [${prod.status}]"; textSize = 16f; setTextColor(ContextCompat.getColor(context, R.color.eco_text_primary)); setTypeface(null, android.graphics.Typeface.BOLD) })
                    inner.addView(TextView(this).apply { text = StoreRepository.formatCop(prod.price.toInt()); setTextColor(ContextCompat.getColor(context, R.color.seller_primary)) })

                    val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 12, 0, 0) } }
                    actions.addView(Button(this).apply {
                        text = "Editar"
                        setOnClickListener {
                            editingProductId = prod.id
                            etName?.setText(prod.name)
                            etPrice?.setText(prod.price.toInt().toString())
                            etSpecs?.setText(prod.description)
                            btnSave?.text = "Actualizar Producto"
                            Toast.makeText(context, "Editando: ${prod.name}", Toast.LENGTH_SHORT).show()
                        }
                    })
                    actions.addView(Button(this).apply {
                        text = "Eliminar"
                        setTextColor(android.graphics.Color.parseColor("#D32F2F"))
                        setOnClickListener {
                            firestoreManager.deleteProduct(
                                productId = prod.id,
                                onSuccess = {
                                    if (isFinishing || isDestroyed) return@deleteProduct
                                    Toast.makeText(context, "Producto eliminado de Firestore", Toast.LENGTH_SHORT).show()
                                    renderSellerProductsFromFirestore(container, etName, etPrice, etSpecs, btnSave)
                                },
                                onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                            )
                        }
                    })
                    inner.addView(actions)
                    card.addView(inner)
                    container.addView(card)
                }
            },
            onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        )
    }

    private fun renderAdminUsersFromFirestore(
        container: LinearLayout,
        etEmail: TextInputEditText?,
        etRole: TextInputEditText?
    ) {
        container.removeAllViews()
        container.addView(TextView(this).apply { text = "Cargando usuarios desde Firestore..." })

        firestoreManager.getAllUsers(
            onSuccess = { users ->
                if (isFinishing || isDestroyed) return@getAllUsers
                container.removeAllViews()
                if (users.isEmpty()) {
                    container.addView(TextView(this).apply { text = "No hay usuarios registrados." })
                    return@getAllUsers
                }
                users.forEach { user ->
                    val card = MaterialCardView(this).apply {
                        radius = 20f
                        cardElevation = 4f
                        setContentPadding(24, 24, 24, 24)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                    }
                    val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    inner.addView(TextView(this).apply { text = "${user.email} (${user.role})"; textSize = 16f; setTextColor(ContextCompat.getColor(context, R.color.admin_primary_dark)); setTypeface(null, android.graphics.Typeface.BOLD) })
                    inner.addView(TextView(this).apply { text = "Nombre: ${user.name}"; setTextColor(ContextCompat.getColor(context, R.color.eco_text_primary)) })

                    val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 12, 0, 0) } }
                    actions.addView(Button(this).apply {
                        text = "Editar Rol"
                        setOnClickListener {
                            editingUserUid = user.uid
                            etEmail?.setText(user.email)
                            etRole?.setText(user.role)
                            Toast.makeText(context, "Modifica el rol arriba y guarda", Toast.LENGTH_SHORT).show()
                        }
                    })
                    actions.addView(Button(this).apply {
                        text = "Eliminar"
                        setTextColor(ContextCompat.getColor(context, R.color.eco_error))
                        setOnClickListener {
                            firestoreManager.deleteUser(
                                uid = user.uid,
                                onSuccess = {
                                    if (isFinishing || isDestroyed) return@deleteUser
                                    Toast.makeText(context, "Usuario eliminado de Firestore", Toast.LENGTH_SHORT).show()
                                    renderAdminUsersFromFirestore(container, etEmail, etRole)
                                },
                                onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                            )
                        }
                    })
                    inner.addView(actions)
                    card.addView(inner)
                    container.addView(card)
                }
            },
            onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        )
    }

    private fun renderAdminProductsFromFirestore(container: LinearLayout) {
        container.removeAllViews()
        container.addView(TextView(this).apply { text = "Cargando productos pendientes..." })

        firestoreManager.getProductsByStatus(
            status = "PENDIENTE",
            onSuccess = { pendingList ->
                if (isFinishing || isDestroyed) return@getProductsByStatus
                container.removeAllViews()
                if (pendingList.isEmpty()) {
                    container.addView(TextView(this).apply { text = "No hay productos pendientes de moderación."; setPadding(16, 16, 16, 16) })
                    return@getProductsByStatus
                }
                pendingList.forEach { prod ->
                    val card = MaterialCardView(this).apply {
                        radius = 20f
                        cardElevation = 4f
                        setContentPadding(24, 24, 24, 24)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                    }
                    val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    inner.addView(TextView(this).apply { text = "${prod.name} - Pendiente"; textSize = 17f; setTextColor(ContextCompat.getColor(context, R.color.admin_primary_dark)); setTypeface(null, android.graphics.Typeface.BOLD) })
                    inner.addView(TextView(this).apply { text = "${StoreRepository.formatCop(prod.price.toInt())}\n${prod.description}"; setTextColor(ContextCompat.getColor(context, R.color.eco_text_primary)); setPadding(0, 6, 0, 12) })

                    val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
                    actions.addView(Button(this).apply {
                        text = "Aprobar"
                        setBackgroundColor(ContextCompat.getColor(context, R.color.seller_primary))
                        setTextColor(ContextCompat.getColor(context, R.color.eco_white))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 8, 0) }
                        setOnClickListener {
                            firestoreManager.approveProduct(
                                productId = prod.id,
                                onSuccess = {
                                    if (isFinishing || isDestroyed) return@approveProduct
                                    Toast.makeText(context, "¡Producto aprobado y publicado!", Toast.LENGTH_LONG).show()
                                    renderAdminProductsFromFirestore(container)
                                },
                                onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                            )
                        }
                    })
                    actions.addView(Button(this).apply {
                        text = "Rechazar"
                        setTextColor(ContextCompat.getColor(context, R.color.eco_error))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener {
                            firestoreManager.deleteProduct(
                                productId = prod.id,
                                onSuccess = {
                                    if (isFinishing || isDestroyed) return@deleteProduct
                                    Toast.makeText(context, "Producto rechazado y eliminado", Toast.LENGTH_SHORT).show()
                                    renderAdminProductsFromFirestore(container)
                                },
                                onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                            )
                        }
                    })
                    inner.addView(actions)
                    card.addView(inner)
                    container.addView(card)
                }
            },
            onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        )
    }

    private fun renderAdminSalesFromFirestore(container: LinearLayout) {
        container.removeAllViews()
        container.addView(TextView(this).apply { text = "Cargando historial de órdenes..." })

        firestoreManager.getAllOrders(
            onSuccess = { orders ->
                if (isFinishing || isDestroyed) return@getAllOrders
                container.removeAllViews()
                if (orders.isEmpty()) {
                    container.addView(TextView(this).apply { text = "No hay órdenes registradas en Firestore."; setPadding(16, 16, 16, 16) })
                    return@getAllOrders
                }
                orders.forEach { order ->
                    val card = MaterialCardView(this).apply {
                        radius = 20f
                        cardElevation = 4f
                        setContentPadding(24, 24, 24, 24)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                    }
                    val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    inner.addView(TextView(this).apply { text = "Orden #${order.paymentRef} (${StoreRepository.formatCop(order.total.toInt())})"; textSize = 17f; setTextColor(ContextCompat.getColor(context, R.color.admin_primary_dark)); setTypeface(null, android.graphics.Typeface.BOLD) })
                    inner.addView(TextView(this).apply { text = "Cliente: ${order.buyerEmail}\nDirección: ${order.address}\nEstado: ${order.status}"; setTextColor(ContextCompat.getColor(context, R.color.eco_text_primary)); setPadding(0, 6, 0, 12) })

                    val btnReversal = Button(this).apply {
                        text = "Ejecutar Reversión de Pago (Decreto 587)"
                        setBackgroundColor(ContextCompat.getColor(context, R.color.admin_primary))
                        setTextColor(ContextCompat.getColor(context, R.color.eco_white))
                        setOnClickListener {
                            firestoreManager.reverseOrder(
                                orderId = order.id,
                                onSuccess = {
                                    if (isFinishing || isDestroyed) return@reverseOrder
                                    Toast.makeText(context, "¡Reversión aplicada en Firestore!", Toast.LENGTH_LONG).show()
                                    renderAdminSalesFromFirestore(container)
                                },
                                onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                            )
                        }
                    }
                    inner.addView(btnReversal)
                    card.addView(inner)
                    container.addView(card)
                }
            },
            onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        )
    }
}

/**
 * Pantalla de detalle de producto con especificaciones de garantía (Ley 1480).
 */
/**
 * Pantalla de detalle de producto con especificaciones de garantía (Ley 1480).
 */
class ProductDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_EcoTech_Buyer)
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_product_detail)

            val id = intent.getStringExtra("ID") ?: "PROD"
            val name = intent.getStringExtra("NAME") ?: "Producto"
            val price = intent.getIntExtra("PRICE", 0)
            val specs = intent.getStringExtra("SPECS") ?: "Grado A"

            // Carga segura de imagen
            val bmp = FirestoreManager().base64ToBitmap(StoreRepository.selectedProductImage)
            val targetView = findViewById<View?>(R.id.ivDetailImage)
            if (bmp != null && targetView != null) {
                if (targetView is ImageView) {
                    targetView.setImageBitmap(bmp)
                    targetView.scaleType = ImageView.ScaleType.FIT_CENTER
                } else if (targetView is ViewGroup) {
                    targetView.removeAllViews()
                    targetView.addView(ImageView(this).apply {
                        setImageBitmap(bmp)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    })
                }
            }

            findViewById<TextView?>(R.id.tvDetailName)?.text = name
            findViewById<TextView?>(R.id.tvDetailPrice)?.text = "${StoreRepository.formatCop(price)} (IVA Incluido)"
            findViewById<TextView?>(R.id.tvDetailSpecs)?.text = specs

            findViewById<Button?>(R.id.btnAddToCart)?.setOnClickListener {
                StoreRepository.addToCart(ProductItem(id, name, price, specs))
                Toast.makeText(this, "$name agregado al carrito", Toast.LENGTH_SHORT).show()
                finish()
            }
            findViewById<Button?>(R.id.btnBackFromDetail)?.setOnClickListener { finish() }

        } catch (e: Throwable) {
            Toast.makeText(this, "Error en Detalle: ${e.javaClass.simpleName} - ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}

/**
 * Pantalla de pago que registra la orden real en Cloud Firestore.
 */
class PaymentGatewayActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_EcoTech_Buyer)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_gateway)

        val firestoreManager = FirestoreManager()
        val btnConfirm = findViewById<Button>(R.id.btnConfirmPayment)
        btnConfirm.text = "Confirmar y Pagar ${StoreRepository.formatCop(StoreRepository.getTotal())}"

        btnConfirm.setOnClickListener {
            btnConfirm.isEnabled = false
            val total = StoreRepository.getTotal().toDouble()
            val ref = "ECO-${(1000..9999).random()}"

            firestoreManager.createOrder(
                total = total,
                address = StoreRepository.deliveryAddress.ifBlank { "Bogotá D.C., Colombia (Sin GPS)" },
                paymentRef = ref,
                onSuccess = {
                    if (isFinishing || isDestroyed) return@createOrder
                    StoreRepository.cart.clear()
                    Toast.makeText(this, "¡Pago exitoso! Orden $ref guardada en Firestore.", Toast.LENGTH_LONG).show()
                    finish()
                },
                onError = { msg ->
                    btnConfirm.isEnabled = true
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}