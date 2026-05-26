# Casa 3D - Proyecto de Visualización 3D

Un proyecto Java que utiliza JMonkeyEngine 3 (JME3) para crear una visualización interactiva de una casa 3D con múltiples modelos, texturas y física.

## Características

- 🏠 Visualización 3D de una casa completa
- 🎨 Múltiples modelos 3D (muebles, electrodomésticos, decoración)
- 🚗 Modelos de vehículos
- 💡 Iluminación realista
- ⚙️ Sistema de física integrado
- 🎮 Controles de cámara interactivos
- 📦 Carga de modelos en múltiples formatos (OBJ, GLB, GLTF, DAE, FBX, STL)

## Requisitos Previos

- **Java 17** o superior
- **Maven 3.6** o superior
- **Windows** (actualmente configurado para Windows x64)

### Instalación de Java y Maven

1. Descargar e instalar [Java 17 JDK](https://www.oracle.com/java/technologies/downloads/#java17)
2. Descargar e instalar [Maven](https://maven.apache.org/download.cgi)
3. Verificar la instalación:
   ```bash
   java -version
   mvn -version
   ```

## Instalación y Configuración

1. **Clonar el repositorio**
   ```bash
   git clone https://github.com/tu-usuario/casa-3d.git
   cd casa-3d
   ```

2. **Compilar el proyecto**
   ```bash
   mvn clean compile
   ```
   
   O usar el script incluido:
   ```bash
   compilar.bat
   ```

3. **Ejecutar la aplicación**
   ```bash
   mvn exec:java -Dexec.mainClass="casa.Main"
   ```
   
   O usar uno de los scripts incluidos:
   ```bash
   ejecutar.bat
   ejecutar_casa3d.bat
   ```

## Estructura del Proyecto

```
casa-3d/
├── src/
│   ├── Main.java                    # Punto de entrada principal
│   └── casa/
│       ├── Main.java                # Aplicación JME3
│       ├── Camera.java              # Sistema de cámara
│       ├── Renderer.java            # Renderizado
│       ├── CollisionManager.java    # Gestión de colisiones
│       └── GLBLoader.java           # Cargador de modelos GLB
├── modelos/                         # Modelos 3D en varios formatos
│   ├── carrros/                     # Modelos de vehículos
│   ├── garage pueta y puertas exterior/
│   └── Models/
│       ├── OBJ format/              # Modelos OBJ
│       ├── GLTF format/             # Modelos GLTF
│       ├── GLB format/
│       ├── FBX format/
│       ├── DAE format/
│       └── STL format/
├── lib/                             # Librerías adicionales
├── natives/                         # Librerías nativas JOGL
├── pom.xml                          # Configuración Maven
└── README.md                        # Este archivo
```

## Controles

| Tecla | Acción |
|-------|--------|
| `W` | Avanzar |
| `A` | Izquierda |
| `S` | Retroceder |
| `D` | Derecha |
| `ESPACIO` | Saltar |
| `Ratón` | Rotar cámara |
| `ESC` | Salir |

## Dependencias Principales

- **JMonkeyEngine 3.6.1** - Motor gráfico 3D
- **JOGL 2.3.2** - Bindings de OpenGL
- **Bullet Physics** - Motor de física 3D
- **JME3 Models** - Carga de modelos 3D

Todas las dependencias se descarga automáticamente mediante Maven.

## Compilación y Empaquetado

### Compilar
```bash
mvn clean compile
```

### Ejecutar
```bash
mvn exec:java -Dexec.mainClass="casa.Main"
```

### Empaquetar JAR
```bash
mvn package
```

## Solución de Problemas

### Error: "No suitable ImageReader found"
- Asegúrate de que las imágenes de textura están en formato PNG o JPG
- Verifica las rutas de recursos en el código

### Error: "JOGL native library not found"
- Ejecuta `mvn clean compile` para descargar las librerías nativas
- Verifica que el directorio `natives/windows-amd64/` contiene los archivos `.dll`

### La aplicación es lenta
- Reduce la cantidad de modelos cargados
- Desactiva la sombra dinámica si no es necesaria
- Verifica los requisitos del sistema

## Desarrollo

### Agregar nuevos modelos
1. Coloca el modelo 3D en la carpeta `modelos/`
2. Actualiza el código en `GLBLoader.java` o `Renderer.java` para cargarlo
3. Recompila con `mvn clean compile`

### Modificar la física
- Edita `CollisionManager.java` para cambiar parámetros de gravedad y colisiones

### Personalizar controles
- Modifica los mapeos de teclado en `casa/Main.java`

## Contribuciones

Las contribuciones son bienvenidas. Para cambios importantes:
1. Fork el proyecto
2. Crea una rama con tu característica (`git checkout -b feature/MiCaracteristica`)
3. Commit tus cambios (`git commit -am 'Agrega nueva característica'`)
4. Push a la rama (`git push origin feature/MiCaracteristica`)
5. Abre un Pull Request

## Licencia

Este proyecto está bajo licencia MIT. Ver archivo [LICENSE](LICENSE) para más detalles.

## Autor

- **Tu Nombre** - Trabajo Inicial

## Agradecimientos

- JMonkeyEngine Team
- Comunidad de desarrollo de juegos en Java
- Modelos 3D de libre uso
