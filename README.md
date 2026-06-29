# MakClean

Limpiador de galería para Android. Revisas tus fotos y vídeos **deslizando tarjetas**
—estilo app de citas, pero para liberar espacio— y mandas lo que no quieras a la
papelera del sistema, de donde se puede recuperar.

Hecho 100% en Kotlin con Jetpack Compose. Todo el análisis ocurre en el dispositivo.

## Características

- **Revisión por gestos**: desliza para conservar (derecha), descartar (izquierda) o marcar favorito (arriba).
- **Detección real de duplicados** por hash del contenido del archivo (no por nombre).
- **Detección de fotos borrosas** mediante la varianza del Laplaciano.
- **Filtros**: duplicados, capturas, GIFs, vídeos pesados/ligeros y fotos borrosas.
- **Borrado recuperable**: los archivos se envían a la papelera del sistema (~30 días) en lugar de borrarse para siempre.
- **Análisis de almacenamiento** por categorías.
- **Modo privacidad**, varios temas y preferencias que se guardan entre sesiones.
- Diseño sobrio en modo oscuro, con la media a pantalla casi completa.

## Tecnologías

- Kotlin + Jetpack Compose (Material 3)
- Arquitectura MVVM con `StateFlow`
- `MediaStore` para acceder a la galería
- Coil para imágenes y miniaturas de vídeo

## Compilar

**Requisitos:** [Android Studio](https://developer.android.com/studio) con el SDK de Android.

1. Clona el repositorio.
2. Ábrelo en Android Studio y deja que sincronice Gradle.
3. Ejecútalo en un emulador o dispositivo físico (`minSdk 24`).

No necesita claves ni configuración extra: los builds de depuración se firman automáticamente.

## Permisos y privacidad

La app pide acceso a tus fotos y vídeos solo para escanear la galería y encontrar
archivos que puedes limpiar. **Nada sale de tu dispositivo**: no hay servidores ni
analítica. La única conexión de red es para mostrar contenido de ejemplo en la
demostración.

## Licencia

[MIT](LICENSE) © 2026 schwarmak-dev
