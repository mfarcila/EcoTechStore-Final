package com.ecotech.store

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream

/**
 * Administra todas las operaciones CRUD en Cloud Firestore para:
 * 1. Usuarios (Gestión del Administrador)
 * 2. Productos (Publicación del Vendedor, Moderación del Admin y Catálogo del Comprador)
 * 3. Órdenes de compra (Historial de ventas y Reversión Decreto 587)
 */
class FirestoreManager {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // =========================================================================
    // UTILIDADES DE IMAGEN (BASE64 PARA PLAN GRATUITO)
    // =========================================================================

    /**
     * Convierte un Bitmap (cámara o galería) a texto Base64 comprimido (~50 KB)
     * para almacenarlo en Firestore sin requerir Firebase Storage de pago.
     */
    fun bitmapToBase64(bitmap: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(bitmap, 400, 400, true)
        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    /**
     * Decodifica una cadena Base64 almacenada en Firestore a un Bitmap visible.
     */
    fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            if (base64Str.isBlank()) return null
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    // =========================================================================
    // 1. CRUD DE USUARIOS (ROL ADMINISTRADOR)
    // =========================================================================

    /**
     * Obtiene la lista completa de usuarios registrados en la colección "users".
     */
    fun getAllUsers(onSuccess: (List<UserModel>) -> Unit, onError: (String) -> Unit) {
        db.collection("users").get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull { it.toObject(UserModel::class.java) }
                onSuccess(list)
            }
            .addOnFailureListener { e -> onError("Error cargando usuarios: ${e.message}") }
    }

    /**
     * Actualiza el rol (BUYER, SELLER, ADMIN) y estado (activo/suspendido) de un usuario.
     */
    fun updateUser(
        uid: String,
        newRole: String,
        isActive: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val updates = mapOf("role" to newRole, "active" to isActive)
        db.collection("users").document(uid).update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError("Error actualizando usuario: ${e.message}") }
    }

    /**
     * Elimina el documento de un usuario en Firestore.
     */
    fun deleteUser(uid: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection("users").document(uid).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError("Error eliminando usuario: ${e.message}") }
    }

    // =========================================================================
    // 2. CRUD DE PRODUCTOS (VENDEDOR, ADMIN Y COMPRADOR)
    // =========================================================================

    /**
     * Crea un producto nuevo con estado PENDIENTE para revisión del Administrador.
     */
    fun createProduct(
        name: String,
        price: Double,
        specs: String,
        imageBase64: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val docRef = db.collection("products").document()
        val sellerId = auth.currentUser?.uid ?: "vendedor_local"

        val product = ProductModel(
            id = docRef.id,
            name = name,
            description = specs,
            price = price,
            warranty = "Garantía 12 meses (Ley 1480)",
            sellerId = sellerId,
            imageBase64 = imageBase64,
            status = "PENDIENTE"
        )

        docRef.set(product)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError("Error guardando producto: ${e.message}") }
    }

    /**
     * Actualiza el nombre, precio y especificaciones de un producto existente.
     */
    fun updateProduct(
        productId: String,
        name: String,
        price: Double,
        specs: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val updates = mapOf("name" to name, "price" to price, "description" to specs)
        db.collection("products").document(productId).update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError("Error actualizando producto: ${e.message}") }
    }

    /**
     * Obtiene productos filtrados por estado ("APROBADO" o "PENDIENTE").
     */
    fun getProductsByStatus(
        status: String,
        onSuccess: (List<ProductModel>) -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection("products")
            .whereEqualTo("status", status)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull { it.toObject(ProductModel::class.java) }
                onSuccess(list)
            }
            .addOnFailureListener { e -> onError("Error cargando productos: ${e.message}") }
    }

    /**
     * Obtiene todos los productos (usado por el Vendedor para ver y gestionar su inventario).
     */
    fun getAllProducts(onSuccess: (List<ProductModel>) -> Unit, onError: (String) -> Unit) {
        db.collection("products").get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull { it.toObject(ProductModel::class.java) }
                onSuccess(list)
            }
            .addOnFailureListener { e -> onError("Error cargando inventario: ${e.message}") }
    }

    /**
     * Aprueba un producto pendiente para publicarlo en el catálogo del Comprador.
     */
    fun approveProduct(productId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection("products").document(productId)
            .update("status", "APROBADO")
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError("Error aprobando: ${e.message}") }
    }

    /**
     * Elimina un producto de Firestore (Vendedor o Admin al rechazar).
     */
    fun deleteProduct(productId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection("products").document(productId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError("Error eliminando: ${e.message}") }
    }

    // =========================================================================
    // 3. CRUD DE ÓRDENES DE COMPRA (CARRITO, VENDEDOR Y ADMIN)
    // =========================================================================

    /**
     * Registra una orden de compra en la colección "orders".
     */
    fun createOrder(
        total: Double,
        address: String,
        paymentRef: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val docRef = db.collection("orders").document()
        val order = OrderModel(
            id = docRef.id,
            buyerId = auth.currentUser?.uid ?: "comprador",
            buyerEmail = auth.currentUser?.email ?: "cliente@ecotech.com",
            total = total,
            address = address,
            paymentRef = paymentRef,
            status = "PAGADO",
            timestamp = System.currentTimeMillis()
        )

        docRef.set(order)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError("Error registrando orden: ${e.message}") }
    }

    /**
     * Obtiene todas las órdenes registradas en Firestore.
     */
    fun getAllOrders(onSuccess: (List<OrderModel>) -> Unit, onError: (String) -> Unit) {
        db.collection("orders").get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull { it.toObject(OrderModel::class.java) }
                onSuccess(list)
            }
            .addOnFailureListener { e -> onError("Error cargando órdenes: ${e.message}") }
    }

    /**
     * Aplica la reversión de pago (Decreto 587) en Firestore.
     */
    fun reverseOrder(orderId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection("orders").document(orderId)
            .update("status", "REVERTIDO (Decreto 587)")
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError("Error aplicando reversión: ${e.message}") }
    }
}