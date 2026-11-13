# Liquid Browser

Un navegador para Android con estilo de vidrio líquido. Busca con DuckDuckGo.

## Qué tiene

- **Barra de direcciones abajo**, al alcance del pulgar: escribe una dirección o una búsqueda
  (DuckDuckGo) y verás sugerencias, tus favoritos y tu historial.
- **Gestos en la barra:** desliza la píldora a los lados para pasar a la pestaña de al lado
  (al final abre una nueva) y hacia arriba para ver todas las pestañas.
- **Pestañas en cuadrícula** de dos columnas con vista previa, botones de vidrio flotantes,
  buscar entre pestañas, seleccionar varias o todas para cerrarlas, y deslizar una para cerrarla.
- **Pestañas privadas:** se ven en oscuro, no guardan historial y usan su propio perfil de
  WebView, así que sus cookies y datos se borran al cerrarlas.
- **Inicio** con tus favoritos y los sitios que más visitas.
- **Favoritos, historial y descargas** (los archivos van a la carpeta Descargas).
- Buscar en la página, versión de escritorio, compartir, video a pantalla completa y subir archivos.
- Colores de **Material You** (de tu fondo de pantalla). El desenfoque del vidrio necesita Android 12 o más.
- Se puede elegir como **navegador predeterminado** en Ajustes de Android.

## Compilar

El APK se compila solo en GitHub Actions con cada push (Actions → Build Liquid Browser APK → artefacto
`liquid-browser-apk`). Para que las actualizaciones se instalen encima, el APK debe firmarse siempre
con la misma llave: crea los secretos `LIQUID_KEYSTORE_BASE64`, `LIQUID_STORE_PASSWORD`,
`LIQUID_KEY_ALIAS` y `LIQUID_KEY_PASSWORD`, o instálalo desde tu computadora con:

```
gradle installDebug --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx1g -Pkotlin.daemon.jvmargs=-Xmx512m
```

Las fuentes Nunito usan la licencia SIL Open Font License (ver `FONTS-OFL.txt`).
