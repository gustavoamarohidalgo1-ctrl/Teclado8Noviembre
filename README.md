# Teclado Libre

Teclado Android sencillo para escribir en español, sin conexión a internet, registro de lo escrito, autocorrección ni diccionario. No incluye dictado, selector de emojis ni historial del portapapeles. Requiere Android 8.0 o posterior (API 26).

**[Descargar Teclado Libre 1.4](https://github.com/gustavoamarohidalgo1-ctrl/Teclado8Noviembre/releases/download/v1.4/TecladoLibre.apk)** · [SHA-256](entregables/SHA256.txt) · [Verificación](entregables/VERIFICACION.txt)

La versión 1.4 contiene únicamente el servicio de teclado, sin pantalla de prueba ni icono en el lanzador. Usa negro y gris, con una altura vertical de 238 dp. No emite sonidos ni vibraciones. En Android 16 solicita ocultar la barra inferior propia del teclado; se comprobó en un Xiaomi que desaparecen la flecha y su espacio, que el gesto Atrás sigue cerrando el teclado y que el globo permite abrir el selector. Si Android mantiene la barra, reserva su espacio real para evitar que tape la última fila. El APK firmado ocupa **25 335 bytes**.

## Instalación y uso

1. Instala el APK. No aparece un icono para abrirlo en la lista de aplicaciones.
2. Para una instalación nueva, abre Ajustes de Android, busca «teclado» y habilita **Teclado Libre** en la administración de teclados. El sistema muestra su aviso habitual al activar un método de entrada.
3. Selecciona **Teclado Libre** como teclado actual desde los ajustes de entrada o el selector de teclados de Android.

Si actualizas una instalación ya habilitada y seleccionada, se conserva el mismo servicio. La versión 1.4 se verificó así en el Xiaomi. Puedes administrarlo o desinstalarlo desde Ajustes de Android.

Mantén Gboard instalado y habilitado hasta comprobar que Teclado Libre funciona para ti. Puedes cambiar de teclado con la tecla del globo.

Mantén pulsada una vocal para elegir una tilde; desliza sobre las alternativas para elegir otra. Usa `?123` y `#+=` para acceder a números y símbolos. Toca la tecla de mayúsculas dos veces para fijarlas y vuelve a tocarla para desactivarlas.

## Compilación y pruebas

Se necesitan JDK 17, Android SDK 35 y Build Tools 35. El proyecto usa Android Gradle Plugin 8.9.2 y el wrapper de Gradle 8.11.1. No tiene bibliotecas de producción externas.

Con el SDK y el JDK instalados, `./tools/build.sh` prepara un APK de versión firmado. La clave de firma se guarda fuera del repositorio, en `~/.local/share/tecladolibre/signing`. No la compartas y conserva una copia segura: necesitarás la misma clave para instalar futuras actualizaciones sobre la aplicación existente.

La clave original no se publica. Si compilas en otro equipo sin esa clave, el script genera una nueva: el APK resultante tendrá una firma distinta y no podrá actualizar la instalación del APK publicado.

Para ejecutar las comprobaciones:

```sh
./gradlew testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

La segunda orden necesita un dispositivo o emulador Android conectado; úsala con un emulador de pruebas, ya que instala la variante de depuración. En la versión 1.4 pasaron las dos pruebas unitarias y lint sin errores; también se comprobó en un Xiaomi con Android 16 que el teclado conserva su selección tras actualizar y escribe en otras aplicaciones sin una pantalla propia. Las siete pruebas instrumentadas y la revisión vertical/horizontal en emulador API 35 se ejecutaron en la versión 1.1. Las versiones API 26–34 son compatibles por configuración, pero aún no se han probado.

## Limitaciones conocidas

- El teclado está dibujado en un único lienzo y TalkBack no puede acceder a cada tecla individualmente.
- No hay escritura por gestos, diccionario ni selector de emojis.
- Borrar elimina un punto de código Unicode cada vez; algunos caracteres visibles, como ciertos emojis compuestos, contienen varios puntos de código y pueden requerir varias pulsaciones.
