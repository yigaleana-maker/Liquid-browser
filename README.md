# Liquid Browser

Un navegador para Android con estilo de vidrio líquido, con el motor de Firefox (GeckoView).
Busca con DuckDuckGo.

## Qué tiene

- **Barra de direcciones abajo**, al alcance del pulgar: escribe una dirección o una búsqueda
  (DuckDuckGo) y verás sugerencias, tus favoritos y tu historial.
- **Gestos en la barra:** desliza la píldora a los lados para pasar a la pestaña de al lado
  (al final abre una nueva) y hacia arriba para ver todas las pestañas.
- **Pestañas en cuadrícula** de dos columnas con vista previa, botones de vidrio flotantes,
  buscar entre pestañas, seleccionar varias o todas para cerrarlas, y deslizar una para cerrarla.
- **Motor de Firefox** con su protección contra rastreo (rastreadores, criptomineros, huellas
  digitales y Protección total de cookies).
- **Bloqueador de anuncios uBlock Origin**, que se instala solo la primera vez (se prende y apaga desde ⋯).
- **Pestañas privadas:** se ven en oscuro, no guardan historial y Firefox borra sus cookies y datos
  al cerrarlas.
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

Para actualizar el motor, cambia la versión de `geckoview-arm64-v8a` en `app/build.gradle`
(las versiones están en maven.mozilla.org). El APK es solo para teléfonos ARM de 64 bits.

Las fuentes Nunito usan la licencia SIL Open Font License (ver `FONTS-OFL.txt`).
