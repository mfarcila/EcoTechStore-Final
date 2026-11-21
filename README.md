# 🌱 EcoTech Store

Aplicación móvil nativa para Android diseñada como un *marketplace* de tecnología reacondicionada y certificada. Desarrollada bajo estándares de calidad, arquitectura orientada a roles y cumplimiento de la normativa colombiana de comercio electrónico.
Maria Fernanda Arcila Icasa Y David Felipe Santafe Sanchez
---

## Características Principales

* **Autenticación y Seguridad:** Inicio de sesión, registro por rol, recuperación de contraseña con **Firebase Authentication** y acceso rápido mediante **Biometría nativa (huella dactilar)**.
* **Base de Datos en Tiempo Real:** Sincronización completa de usuarios, productos y órdenes con **Cloud Firestore**.
* **Gestión de Imágenes sin Storage de pago:** Captura desde cámara nativa o selección desde galería con compresión y almacenamiento en formato **Base64**.
* **Geolocalización (GPS):** Captura de coordenadas reales y dirección física de entrega mediante `FusedLocationProviderClient` y `Geocoder`.
* **Cumplimiento Legal (Colombia):**
  * **Ley 1480 de 2011:** Garantía legal y derecho de retracto visibles en el catálogo y detalle del producto.
  * **Ley 1581 de 2012 (Habeas Data):** Aceptación obligatoria en el registro de usuarios.
  * **Decreto 587 de 2016:** Funcionalidad de reversión de pagos desde el panel de administración.

---

##  Roles y Funcionalidades

### 🔵 Comprador
* Exploración del catálogo sincronizado en vivo (solo productos aprobados).
* Vista detallada del producto con imagen, precio en COP (IVA incluido) y garantía.
* Carrito de compras dinámico (aumentar cantidad, eliminar y cálculo automático del total).
* Captura de ubicación GPS para envío y confirmación de pago con generación de referencia única (`ECO-XXXX`).

### 🟢 Vendedor
* **CRUD completo de productos** (Crear, Consultar, Editar y Eliminar) conectado a Firestore.
* Carga de fotografías mediante **Cámara** o **Galería**.
* Control de estado de publicaciones (`PENDIENTE` hasta aprobación administrativa / `APROBADO`).

### 🟣 Administrador
* **CRUD de usuarios:** Consulta de cuentas registradas, actualización de roles (`BUYER`, `SELLER`, `ADMIN`) y eliminación.
* **Moderación de productos:** Revisión de publicaciones pendientes para aprobarlas o rechazarlas.
* **Reporte de ventas:** Visualización de órdenes pagadas (incluyendo dirección GPS) y ejecución de **Reversión de Pago (Decreto 587)**.

---

## 🛠️ Tecnologías Utilizadas

* **Lenguaje:** Kotlin
* **Interfaz de Usuario:** Android XML + Material Design 3 (temas dinámicos por rol)
* **Backend / BaaS:** Firebase Authentication & Cloud Firestore
* **Servicios Nativos:** AndroidX Biometric, Google Play Services Location (GPS), Geocoder, Activity Result API (Cámara y Galería)
* **IDE:** Android Studio

---

## ⚙️ Instalación y Ejecución

1. Clona este repositorio:
   ```bash
   git clone [https://github.com/TU_USUARIO/TU_REPOSITORIO.git](https://github.com/TU_USUARIO/TU_REPOSITORIO.git)
