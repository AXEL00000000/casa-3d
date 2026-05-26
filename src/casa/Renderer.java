/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package casa;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;

import java.io.*;
import java.nio.*;
import java.util.*;

/**
 * Renderer - Carga mallas en VBOs y texturas embebidas, y dibuja todo.
 */
public class Renderer {

    private List<GLBLoader.MeshData> meshDataList;
    private List<GLMesh> glMeshes = new ArrayList<>();
    private Map<Integer, Texture> textureMap = new HashMap<>(); // imageIndex -> Texture

    private static class GLMesh {
        String name;
        int vboVertices, vboNormals, vboTexCoords, vboIndices;
        int indexCount;
        int vertexCount;
        float[] color;
        int textureIndex; // -1 = sin textura
        boolean hasNormals, hasUVs;
        boolean useIndices;
    }

    public Renderer(GL2 gl) {
        GLBLoader loader = new GLBLoader();
        try {
            String[] paths = {
                    "casa_acosadamas.glb",
                    "src/casa/casa_acosadamas.glb",
                    "resources/casa_acosadamas.glb",
                    System.getProperty("user.dir") + "/casa_acosadamas.glb"
            };

            String foundPath = null;
            for (String p : paths) {
                if (new File(p).exists()) { foundPath = p; break; }
            }

            if (foundPath == null) {
                System.err.println("ERROR: No se encontro casa_acosadamas.glb");
                System.err.println("Directorio actual: " + System.getProperty("user.dir"));
                System.err.println("Coloca el .glb en la raiz del proyecto.");
                meshDataList = new ArrayList<>();
                return;
            }

            meshDataList = loader.load(foundPath);

            // Cargar texturas embebidas
            List<GLBLoader.TextureData> texData = loader.getTextures();
            for (int i = 0; i < texData.size(); i++) {
                GLBLoader.TextureData td = texData.get(i);
                try {
                    String suffix = td.mimeType.contains("png") ? ".png" : ".jpg";
                    // Escribir a archivo temporal
                    File tmp = File.createTempFile("tex_" + td.name + "_", suffix);
                    tmp.deleteOnExit();
                    FileOutputStream fos = new FileOutputStream(tmp);
                    fos.write(td.data);
                    fos.close();

                    Texture tex = TextureIO.newTexture(tmp, true);
                    tex.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_S, GL2.GL_REPEAT);
                    tex.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_T, GL2.GL_REPEAT);
                    tex.setTexParameteri(gl, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR_MIPMAP_LINEAR);
                    tex.setTexParameteri(gl, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
                    textureMap.put(i, tex);
                    System.out.println("  Textura cargada en GPU: " + td.name + " [" + i + "]");
                } catch (Exception e) {
                    System.err.println("  Error cargando textura " + td.name + ": " + e.getMessage());
                }
            }

            // Subir mallas a GPU
            for (GLBLoader.MeshData md : meshDataList) {
                uploadMesh(gl, md);
            }

        } catch (Exception e) {
            System.err.println("Error al cargar modelo: " + e.getMessage());
            e.printStackTrace();
            meshDataList = new ArrayList<>();
        }
    }

    private void uploadMesh(GL2 gl, GLBLoader.MeshData mesh) {
        GLMesh glm = new GLMesh();
        glm.name = mesh.name;
        glm.color = mesh.color;
        glm.textureIndex = mesh.textureIndex;
        glm.hasNormals = mesh.normals != null && mesh.normals.length > 0;
        glm.hasUVs = mesh.texCoords != null && mesh.texCoords.length > 0;

        int[] ids = new int[4];
        gl.glGenBuffers(4, ids, 0);

        // Vértices
        FloatBuffer vb = FloatBuffer.wrap(mesh.vertices);
        gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, ids[0]);
        gl.glBufferData(GL2.GL_ARRAY_BUFFER, (long) mesh.vertices.length * 4, vb, GL2.GL_STATIC_DRAW);
        glm.vboVertices = ids[0];

        // Normales
        if (glm.hasNormals) {
            FloatBuffer nb = FloatBuffer.wrap(mesh.normals);
            gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, ids[1]);
            gl.glBufferData(GL2.GL_ARRAY_BUFFER, (long) mesh.normals.length * 4, nb, GL2.GL_STATIC_DRAW);
            glm.vboNormals = ids[1];
        }

        // UVs
        if (glm.hasUVs) {
            // Invertir V para OpenGL (Blender usa origen abajo-izquierda distinto)
            float[] uvFixed = new float[mesh.texCoords.length];
            for (int i = 0; i < mesh.texCoords.length; i += 2) {
                uvFixed[i] = mesh.texCoords[i];
                uvFixed[i + 1] = 1.0f - mesh.texCoords[i + 1];
            }
            FloatBuffer tb = FloatBuffer.wrap(uvFixed);
            gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, ids[2]);
            gl.glBufferData(GL2.GL_ARRAY_BUFFER, (long) uvFixed.length * 4, tb, GL2.GL_STATIC_DRAW);
            glm.vboTexCoords = ids[2];
        }

        // Índices
        if (mesh.indices != null && mesh.indices.length > 0) {
            IntBuffer ib = IntBuffer.wrap(mesh.indices);
            gl.glBindBuffer(GL2.GL_ELEMENT_ARRAY_BUFFER, ids[3]);
            gl.glBufferData(GL2.GL_ELEMENT_ARRAY_BUFFER, (long) mesh.indices.length * 4, ib, GL2.GL_STATIC_DRAW);
            glm.vboIndices = ids[3];
            glm.indexCount = mesh.indices.length;
            glm.useIndices = true;
        } else {
            glm.vertexCount = mesh.vertices.length / 3;
            glm.useIndices = false;
        }

        gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
        gl.glBindBuffer(GL2.GL_ELEMENT_ARRAY_BUFFER, 0);

        glMeshes.add(glm);
    }

    public void draw(GL2 gl) {
        gl.glEnableClientState(GL2.GL_VERTEX_ARRAY);

        for (GLMesh glm : glMeshes) {
            boolean hasTexture = glm.textureIndex >= 0 && textureMap.containsKey(glm.textureIndex);

            if (hasTexture) {
                gl.glEnable(GL2.GL_TEXTURE_2D);
                textureMap.get(glm.textureIndex).bind(gl);
                gl.glColor4f(1f, 1f, 1f, glm.color[3]); // Blanco para no teñir la textura
            } else {
                gl.glDisable(GL2.GL_TEXTURE_2D);
                gl.glColor4f(glm.color[0], glm.color[1], glm.color[2], glm.color[3]);
            }

            // Transparencia para vidrios
            if (glm.color[3] < 1.0f) {
                gl.glEnable(GL2.GL_BLEND);
                gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
                gl.glDepthMask(false);
            }

            // Vértices
            gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, glm.vboVertices);
            gl.glVertexPointer(3, GL2.GL_FLOAT, 0, 0);

            // Normales
            if (glm.hasNormals) {
                gl.glEnableClientState(GL2.GL_NORMAL_ARRAY);
                gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, glm.vboNormals);
                gl.glNormalPointer(GL2.GL_FLOAT, 0, 0);
            }

            // UVs
            if (glm.hasUVs && hasTexture) {
                gl.glEnableClientState(GL2.GL_TEXTURE_COORD_ARRAY);
                gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, glm.vboTexCoords);
                gl.glTexCoordPointer(2, GL2.GL_FLOAT, 0, 0);
            }

            // Dibujar
            if (glm.useIndices) {
                gl.glBindBuffer(GL2.GL_ELEMENT_ARRAY_BUFFER, glm.vboIndices);
                gl.glDrawElements(GL2.GL_TRIANGLES, glm.indexCount, GL2.GL_UNSIGNED_INT, 0);
            } else {
                gl.glDrawArrays(GL2.GL_TRIANGLES, 0, glm.vertexCount);
            }

            // Limpiar estado
            if (glm.hasNormals) gl.glDisableClientState(GL2.GL_NORMAL_ARRAY);
            if (glm.hasUVs && hasTexture) gl.glDisableClientState(GL2.GL_TEXTURE_COORD_ARRAY);

            if (glm.color[3] < 1.0f) {
                gl.glDepthMask(true);
                gl.glDisable(GL2.GL_BLEND);
            }
        }

        gl.glDisable(GL2.GL_TEXTURE_2D);
        gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
        gl.glBindBuffer(GL2.GL_ELEMENT_ARRAY_BUFFER, 0);
        gl.glDisableClientState(GL2.GL_VERTEX_ARRAY);
    }

    public List<GLBLoader.MeshData> getModel() {
        return meshDataList;
    }

    public void cleanup(GL2 gl) {
        for (GLMesh glm : glMeshes) {
            int[] ids = {glm.vboVertices, glm.vboNormals, glm.vboTexCoords, glm.vboIndices};
            gl.glDeleteBuffers(4, ids, 0);
        }
        for (Texture tex : textureMap.values()) {
            tex.destroy(gl);
        }
        glMeshes.clear();
        textureMap.clear();
    }
}