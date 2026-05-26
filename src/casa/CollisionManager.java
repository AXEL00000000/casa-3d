/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package casa;

import java.util.*;

/**
 * Colisiones AABB contra objetos solidos.
 * Detecta automaticamente por nombre del objeto.
 */
public class CollisionManager {

    private List<AABB> colliders = new ArrayList<>();

    private static class AABB {
        String name;
        float minX, minZ, maxX, maxZ;

        boolean intersects(float px, float pz, float radius) {
            float cx = Math.max(minX, Math.min(px, maxX));
            float cz = Math.max(minZ, Math.min(pz, maxZ));
            float dx = px - cx, dz = pz - cz;
            return (dx * dx + dz * dz) < (radius * radius);
        }
    }

   public CollisionManager(List<GLBLoader.MeshData> meshes) {
    if (meshes == null) return;

    // En lugar de calcular AABB de 'paredes' (que es un rectángulo enorme),
    // definimos manualmente los segmentos solidos de la pared norte
    // dejando libre el hueco de la entrada X[-1.5 .. 1.5]
    addManual("pared_norte_izq",  -5.01f, 3.35f, -1.60f, 3.55f);
    addManual("pared_norte_der",   1.60f, 3.35f,  5.51f, 3.55f);
    addManual("pared_sur",        -5.01f,-5.05f,  5.51f,-4.95f);
    addManual("pared_este",        5.41f,-5.00f,  5.55f,  3.55f);
    addManual("pared_oeste",      -5.05f,-5.00f, -4.95f,  3.55f);

    // Columnas si quieres
    for (GLBLoader.MeshData m : meshes) {
        String n = m.name == null ? "" : m.name.toLowerCase();
        if (n.contains("columna") || n.contains("pilar")) {
            AABB box = computeAABB(m);
            if (box != null) colliders.add(box);
        }
    }

    System.out.println("Colisiones: " + colliders.size() + " objetos solidos");
    for (AABB b : colliders) System.out.println("  [COL] " + b.name);
}

private void addManual(String name, float x1, float z1, float x2, float z2) {
    AABB b = new AABB();
    b.name = name;
    b.minX = x1; b.maxX = x2;
    b.minZ = z1; b.maxZ = z2;
    colliders.add(b);
}
    /**
     * Objetos que NO deben bloquear el paso aunque su nombre
     * contenga una palabra solida (puertas, cajones, neveras, duchas
     * son decoracion — el jugador debe poder pasar por ellos).
     */
    private boolean isExcluded(String n) {
        String[] excluded = {
            "door",          // todas las puertas: door.011, doorFridge, doorFreezer, doorLeft, doorRight...
            "puerta",        // variantes en español
            "drawer",        // cajones: bathroomCabinetDrawer, cabinetBedDrawer
            "fridge",        // neveras: kitchenFridge, doorFridge
            "freezer",       // congeladores
            "shower",        // duchas
            "cabinet",       // gabinetes/armarios
            "kitchen"        // muebles de cocina
        };
        for (String ex : excluded) {
            if (n.contains(ex)) return true;
        }
        return false;
    }

    /**
     * Solo paredes, muros, columnas y techo son solidos.
     */
    private boolean isCollidable(String name) {
    if (name == null) return false;
    String n = name.toLowerCase();
    if (isExcluded(n)) return false;

    // Solo columnas estructurales pequeñas
    String[] solid = {
        "columna", "column", "pilar"
    };
    for (String s : solid) {
        if (n.contains(s)) return true;
    }
    return false;
}

    private AABB computeAABB(GLBLoader.MeshData mesh) {
        if (mesh.vertices == null || mesh.vertices.length < 3) return null;
        AABB b = new AABB();
        b.name = mesh.name;
        b.minX = b.maxX = mesh.vertices[0];
        b.minZ = b.maxZ = mesh.vertices[2];
        for (int i = 0; i < mesh.vertices.length; i += 3) {
            float x = mesh.vertices[i], z = mesh.vertices[i + 2];
            if (x < b.minX) b.minX = x;
            if (x > b.maxX) b.maxX = x;
            if (z < b.minZ) b.minZ = z;
            if (z > b.maxZ) b.maxZ = z;
        }
        return b;
    }

    public boolean collidesAt(float x, float z, float radius) {
        for (AABB b : colliders) {
            if (b.intersects(x, z, radius)) return true;
        }
        return false;
    }
}