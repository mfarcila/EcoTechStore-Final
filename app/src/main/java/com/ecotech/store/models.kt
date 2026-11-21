package com.ecotech.store

/**
 * Representa un usuario registrado en EcoTech Store (colección "users" en Firestore).
 */
data class UserModel(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "COMPRADOR", // COMPRADOR, VENDEDOR o ADMIN
    val active: Boolean = true
)

/**
 * Representa un producto reacondicionado en Firestore.
 * La imagen se guarda comprimida en Base64 para operar en el plan gratuito.
 */
data class ProductModel(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val warranty: String = "Garantía 12 meses (Ley 1480)",
    val sellerId: String = "",
    val imageBase64: String = "",
    val status: String = "PENDIENTE" // PENDIENTE, APROBADO, RECHAZADO
)

/**
 * Representa una orden de compra registrada en Firestore.
 */
data class OrderModel(
    val id: String = "",
    val buyerId: String = "",
    val buyerEmail: String = "",
    val total: Double = 0.0,
    val address: String = "",
    val paymentRef: String = "",
    val status: String = "PAGADO", // PAGADO o REVERTIDO (Decreto 587)
    val timestamp: Long = System.currentTimeMillis()
)