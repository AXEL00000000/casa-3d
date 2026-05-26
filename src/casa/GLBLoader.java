/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package casa;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;

/**
 * Parser de archivos .glb (glTF 2.0 Binary).
 * Extrae geometría (vértices, normales, UVs, índices),
 * colores de material, y texturas embebidas (JPEG/PNG).
 */
public class GLBLoader {

    private List<MeshData> meshes = new ArrayList<>();
    private List<TextureData> textures = new ArrayList<>();

    /** Datos de una malla individual */
    public static class MeshData {
        public String name;
        public float[] vertices;
        public float[] normals;
        public float[] texCoords;
        public int[] indices;
        public float[] color;       // RGBA
        public int textureIndex;    // -1 = sin textura

        @Override
        public String toString() {
            return name + " [v=" + (vertices != null ? vertices.length / 3 : 0)
                    + " tri=" + (indices != null ? indices.length / 3 : 0)
                    + " tex=" + textureIndex + "]";
        }
    }

    /** Datos de una textura embebida */
    public static class TextureData {
        public String name;
        public String mimeType;
        public byte[] data;

        @Override
        public String toString() {
            return name + " (" + mimeType + ", " + data.length + " bytes)";
        }
    }

    /**
     * Carga un archivo .glb
     */
    public List<MeshData> load(String path) throws IOException {
        System.out.println("=== Cargando GLB: " + path + " ===");

        File file = new File(path);
        if (!file.exists()) throw new FileNotFoundException("Archivo no encontrado: " + path);

        RandomAccessFile raf = new RandomAccessFile(file, "r");
        FileChannel channel = raf.getChannel();
        ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Header (12 bytes)
        int magic = buffer.getInt();
        int version = buffer.getInt();
        int length = buffer.getInt();

        if (magic != 0x46546C67)
            throw new IOException("No es un archivo GLB valido");

        System.out.println("GLB v" + version + ", " + length + " bytes");

        // Chunk 0: JSON
        int jsonLength = buffer.getInt();
        buffer.getInt(); // tipo
        byte[] jsonBytes = new byte[jsonLength];
        buffer.get(jsonBytes);
        String json = new String(jsonBytes, "UTF-8");

        // Chunk 1: BIN
        ByteBuffer binData = null;
        if (buffer.remaining() >= 8) {
            int binLength = buffer.getInt();
            buffer.getInt(); // tipo
            int binStart = buffer.position();
            binData = buffer.slice();
            binData.order(ByteOrder.LITTLE_ENDIAN);
            binData.limit(binLength);
        }

        raf.close();

        // Parsear JSON
        Map<String, Object> root = parseJsonObject(json.trim());

        List<Map<String, Object>> accessors = getList(root, "accessors");
        List<Map<String, Object>> bufferViews = getList(root, "bufferViews");
        List<Map<String, Object>> jsonMeshes = getList(root, "meshes");
        List<Map<String, Object>> materials = getList(root, "materials");
        List<Map<String, Object>> nodes = getList(root, "nodes");
        List<Map<String, Object>> jsonTextures = getList(root, "textures");
        List<Map<String, Object>> images = getList(root, "images");
        List<Map<String, Object>> scenes = getList(root, "scenes");

        // Extraer texturas embebidas
        if (images != null && binData != null) {
            for (Map<String, Object> img : images) {
                TextureData td = new TextureData();
                td.name = getString(img, "name", "texture");
                td.mimeType = getString(img, "mimeType", "image/jpeg");
                int bvIdx = getInt(img, "bufferView", -1);
                if (bvIdx >= 0) {
                    Map<String, Object> bv = bufferViews.get(bvIdx);
                    int offset = getInt(bv, "byteOffset", 0);
                    int len = getInt(bv, "byteLength", 0);
                    td.data = new byte[len];
                    ByteBuffer slice = binData.duplicate();
                    slice.order(ByteOrder.LITTLE_ENDIAN);
                    slice.position(offset);
                    slice.get(td.data);
                }
                textures.add(td);
                System.out.println("  Textura: " + td);
            }
        }

        // Construir mapa textura_index -> image_index
        Map<Integer, Integer> texToImage = new HashMap<>();
        if (jsonTextures != null) {
            for (int i = 0; i < jsonTextures.size(); i++) {
                int src = getInt(jsonTextures.get(i), "source", -1);
                texToImage.put(i, src);
            }
        }

        // Obtener nodos raíz de la escena
        Set<Integer> sceneNodes = new HashSet<>();
        if (scenes != null && !scenes.isEmpty()) {
            List<Number> rootNodes = (List<Number>) scenes.get(0).get("nodes");
            if (rootNodes != null) {
                for (Number n : rootNodes) sceneNodes.add(n.intValue());
            }
        }

        // Recorrer todos los nodos (incluidos hijos)
        if (nodes != null) {
            for (int i = 0; i < nodes.size(); i++) {
                Map<String, Object> node = nodes.get(i);
                int meshIdx = getInt(node, "mesh", -1);
                if (meshIdx < 0 || meshIdx >= jsonMeshes.size()) continue;

                String nodeName = getString(node, "name", "node_" + i);
                Map<String, Object> mesh = jsonMeshes.get(meshIdx);
                List<Map<String, Object>> primitives = getList(mesh, "primitives");
                if (primitives == null) continue;

                for (int pi = 0; pi < primitives.size(); pi++) {
                    Map<String, Object> prim = primitives.get(pi);
                    Map<String, Object> attrs = (Map<String, Object>) prim.get("attributes");
                    if (attrs == null) continue;

                    MeshData md = new MeshData();
                    md.name = nodeName + (primitives.size() > 1 ? "_p" + pi : "");

                    // Vértices
                    int posAcc = getInt(attrs, "POSITION", -1);
                    if (posAcc >= 0) md.vertices = readFloats(accessors.get(posAcc), bufferViews, binData);

                    // Normales
                    int normAcc = getInt(attrs, "NORMAL", -1);
                    if (normAcc >= 0) md.normals = readFloats(accessors.get(normAcc), bufferViews, binData);

                    // UVs
                    int uvAcc = getInt(attrs, "TEXCOORD_0", -1);
                    if (uvAcc >= 0) md.texCoords = readFloats(accessors.get(uvAcc), bufferViews, binData);

                    // Índices
                    int idxAcc = getInt(prim, "indices", -1);
                    if (idxAcc >= 0) md.indices = readIndices(accessors.get(idxAcc), bufferViews, binData);

                    // Material
                    md.color = new float[]{0.8f, 0.8f, 0.8f, 1.0f};
                    md.textureIndex = -1;
                    int matIdx = getInt(prim, "material", -1);
                    if (matIdx >= 0 && materials != null && matIdx < materials.size()) {
                        Map<String, Object> mat = materials.get(matIdx);
                        Map<String, Object> pbr = (Map<String, Object>) mat.get("pbrMetallicRoughness");
                        if (pbr != null) {
                            // Color
                            List<Number> bcf = (List<Number>) pbr.get("baseColorFactor");
                            if (bcf != null && bcf.size() >= 4) {
                                md.color = new float[]{
                                        bcf.get(0).floatValue(), bcf.get(1).floatValue(),
                                        bcf.get(2).floatValue(), bcf.get(3).floatValue()
                                };
                            }
                            // Textura
                            Map<String, Object> bct = (Map<String, Object>) pbr.get("baseColorTexture");
                            if (bct != null) {
                                int texIdx = getInt(bct, "index", -1);
                                if (texIdx >= 0 && texToImage.containsKey(texIdx)) {
                                    md.textureIndex = texToImage.get(texIdx);
                                }
                            }
                        }
                    }

                    if (md.vertices != null) {
                        meshes.add(md);
                    }
                }
            }
        }

        System.out.println("=== Total: " + meshes.size() + " submallas, " + textures.size() + " texturas ===");
        return meshes;
    }

    // ======================== Lectura de buffers ========================

    private float[] readFloats(Map<String, Object> accessor,
                                List<Map<String, Object>> bufferViews,
                                ByteBuffer bin) {
        int bvIdx = getInt(accessor, "bufferView", -1);
        if (bvIdx < 0) return null;
        int count = getInt(accessor, "count", 0);
        String type = getString(accessor, "type", "VEC3");
        int comps = type.equals("SCALAR") ? 1 : type.equals("VEC2") ? 2 :
                type.equals("VEC3") ? 3 : type.equals("VEC4") ? 4 : 3;

        Map<String, Object> bv = bufferViews.get(bvIdx);
        int offset = getInt(bv, "byteOffset", 0) + getInt(accessor, "byteOffset", 0);
        int stride = getInt(bv, "byteStride", 0);

        float[] result = new float[count * comps];

        if (stride == 0 || stride == comps * 4) {
            bin.position(offset);
            for (int i = 0; i < result.length; i++) result[i] = bin.getFloat();
        } else {
            for (int i = 0; i < count; i++) {
                bin.position(offset + i * stride);
                for (int c = 0; c < comps; c++) result[i * comps + c] = bin.getFloat();
            }
        }
        return result;
    }

    private int[] readIndices(Map<String, Object> accessor,
                               List<Map<String, Object>> bufferViews,
                               ByteBuffer bin) {
        int bvIdx = getInt(accessor, "bufferView", -1);
        if (bvIdx < 0) return null;
        int count = getInt(accessor, "count", 0);
        int compType = getInt(accessor, "componentType", 5123);

        Map<String, Object> bv = bufferViews.get(bvIdx);
        int offset = getInt(bv, "byteOffset", 0) + getInt(accessor, "byteOffset", 0);

        int[] result = new int[count];
        bin.position(offset);

        switch (compType) {
            case 5121: for (int i = 0; i < count; i++) result[i] = bin.get() & 0xFF; break;
            case 5123: for (int i = 0; i < count; i++) result[i] = bin.getShort() & 0xFFFF; break;
            case 5125: for (int i = 0; i < count; i++) result[i] = bin.getInt(); break;
        }
        return result;
    }

    // ======================== Helpers ========================

    private int getInt(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return def;
    }

    private String getString(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        if (v instanceof String) return (String) v;
        return def;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List) return (List<Map<String, Object>>) v;
        return null;
    }

    public List<MeshData> getMeshes() { return meshes; }
    public List<TextureData> getTextures() { return textures; }

    // ======================== JSON Parser ========================
    // Parser JSON completo para no depender de librerías externas.

    private int pos;
    private String src;

    private Map<String, Object> parseJsonObject(String json) {
        this.src = json;
        this.pos = 0;
        return readObject();
    }

    private void skipWhitespace() {
        while (pos < src.length() && " \t\n\r".indexOf(src.charAt(pos)) >= 0) pos++;
    }

    private Map<String, Object> readObject() {
        skipWhitespace();
        if (src.charAt(pos) != '{') throw new RuntimeException("Expected '{' at " + pos);
        pos++;
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == '}') { pos++; return map; }

        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            if (src.charAt(pos) != ':') throw new RuntimeException("Expected ':' at " + pos);
            pos++;
            skipWhitespace();
            Object val = readValue();
            map.put(key, val);
            skipWhitespace();
            if (pos >= src.length()) break;
            if (src.charAt(pos) == ',') { pos++; continue; }
            if (src.charAt(pos) == '}') { pos++; break; }
        }
        return map;
    }

    private List<Object> readArray() {
        pos++; // skip '['
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == ']') { pos++; return list; }

        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            if (pos >= src.length()) break;
            if (src.charAt(pos) == ',') { pos++; continue; }
            if (src.charAt(pos) == ']') { pos++; break; }
        }
        return list;
    }

    private Object readValue() {
        skipWhitespace();
        char c = src.charAt(pos);
        if (c == '"') return readString();
        if (c == '{') return readObject();
        if (c == '[') return readArray();
        if (c == 't') { pos += 4; return Boolean.TRUE; }
        if (c == 'f') { pos += 5; return Boolean.FALSE; }
        if (c == 'n') { pos += 4; return null; }
        return readNumber();
    }

    private String readString() {
        pos++; // skip opening "
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '"') { pos++; return sb.toString(); }
            if (c == '\\') {
                pos++;
                char esc = src.charAt(pos);
                switch (esc) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        String hex = src.substring(pos + 1, pos + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                        break;
                    default: sb.append(esc);
                }
            } else {
                sb.append(c);
            }
            pos++;
        }
        return sb.toString();
    }

    private Number readNumber() {
        int start = pos;
        boolean isFloat = false;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '.' || c == 'e' || c == 'E') isFloat = true;
            if ("0123456789.eE+-".indexOf(c) < 0) break;
            pos++;
        }
        String numStr = src.substring(start, pos);
        if (isFloat) return Double.parseDouble(numStr);
        long val = Long.parseLong(numStr);
        if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) return (int) val;
        return val;
    }
}