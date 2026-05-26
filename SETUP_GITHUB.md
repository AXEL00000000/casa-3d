# 📚 Instrucciones para Subir a GitHub

Tu proyecto está listo para ser subido a GitHub. Sigue estos pasos:

## Paso 1: Crear un Repositorio en GitHub

1. Ve a [github.com](https://github.com) e inicia sesión con tu cuenta
2. Haz clic en el "+" en la esquina superior derecha y selecciona "New repository"
3. Llena los datos:
   - **Repository name:** `casa-3d` (o el nombre que prefieras)
   - **Description:** "Casa 3D - Visualización interactiva con JMonkeyEngine 3"
   - **Visibility:** Public (o Private si lo prefieres)
   - **NO** inicialices con README, .gitignore o LICENSE (ya los tenemos)
4. Haz clic en "Create repository"

## Paso 2: Agregar el Remote y Hacer Push

Después de crear el repositorio, GitHub te mostrará comandos. Ejecuta estos en PowerShell:

```powershell
cd "c:\Users\yonot\Downloads\matadamas casas"

# Agregar el remoto (reemplaza TU_USUARIO con tu usuario de GitHub)
git remote add origin https://github.com/TU_USUARIO/casa-3d.git

# Renombrar la rama a main (opcional pero recomendado)
git branch -M main

# Hacer push al repositorio
git push -u origin main
```

## Paso 3: Verificar en GitHub

1. Abre tu repositorio en GitHub
2. Verifica que todos los archivos están presentes
3. El README.md debería aparecer automáticamente en la página principal

## Archivos Incluidos en el Repositorio

✅ **Documentación:**
- `README.md` - Documentación completa del proyecto
- `LICENSE` - Licencia MIT
- `SETUP_GITHUB.md` - Este archivo

✅ **Código Fuente:**
- `src/` - Todos los archivos Java
- `pom.xml` - Configuración Maven

✅ **Recursos:**
- `modelos/` - Modelos 3D en múltiples formatos
- `lib/` - Librerías adicionales
- Scripts batch para compilar y ejecutar

✅ **Configuración:**
- `.gitignore` - Excluye archivos no necesarios
- `compilar.bat`, `ejecutar.bat` - Scripts de utilidad

## Lo Que NO Incluye el Repositorio

❌ Los siguientes directorios se excluyen automáticamente:
- `target/` - Archivos compilados
- `out/` - Archivos de salida
- `.vscode/` - Configuración local de VS Code
- `.venv/` - Entorno virtual de Python
- `natives/` - Se descargan automáticamente con Maven

## Clonar el Repositorio en Otra Computadora

Una vez subido, cualquiera puede clonarlo:

```powershell
git clone https://github.com/TU_USUARIO/casa-3d.git
cd casa-3d

# Compilar
mvn clean compile

# Ejecutar
mvn exec:java -Dexec.mainClass="casa.Main"
```

## Actualizaciones Futuras

Para hacer push de cambios después de las modificaciones:

```powershell
cd "c:\Users\yonot\Downloads\matadamas casas"

# Ver cambios
git status

# Agregar cambios
git add .

# Hacer commit
git commit -m "Descripción de los cambios"

# Hacer push
git push
```

## Comandos Útiles de Git

```powershell
# Ver el estado del repositorio
git status

# Ver historial de commits
git log

# Ver cambios no commiteados
git diff

# Deshacer cambios en un archivo
git checkout -- archivo.txt

# Eliminar un archivo del repositorio
git rm archivo.txt
```

## Recomendaciones

1. **Credenciales de GitHub:** GitHub ahora requiere token en lugar de contraseña
   - [Crear un Personal Access Token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token)
   - Usa el token en lugar de contraseña cuando git te lo pida

2. **Cambios Frecuentes:** Haz commits pequeños y frecuentes con mensajes descriptivos

3. **Branches:** Para cambios importantes, crea una rama:
   ```powershell
   git checkout -b feature/nuevaFuncionalidad
   # ... haz cambios ...
   git push -u origin feature/nuevaFuncionalidad
   # Abre un Pull Request en GitHub
   ```

4. **Sincronizar Cambios:** Si trabajas desde varias máquinas:
   ```powershell
   git pull  # Trae cambios remotos
   ```

## Estructura Final en GitHub

```
casa-3d/
├── README.md                    # Documentación principal
├── LICENSE                      # Licencia MIT
├── SETUP_GITHUB.md             # Este archivo
├── pom.xml                      # Maven configuration
├── compilar.bat                 # Script de compilación
├── ejecutar.bat                 # Script de ejecución
├── ejecutar_casa3d.bat          # Script alternativo
├── .gitignore                   # Configuración de Git
└── src/
    ├── Main.java
    └── casa/
        └── [código fuente]
└── modelos/
    └── [modelos 3D]
```

---

¡Tu proyecto está listo para compartir con el mundo! 🚀

Si tienes problemas, consulta la [documentación de GitHub](https://docs.github.com/en).
