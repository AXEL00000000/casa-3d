package practica.cuatro.pkg3d;

import java.awt.Cursor;
import java.awt.Font;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.awt.TextRenderer;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;

public class Casa3D_SinEdicion implements GLEventListener,
        MouseMotionListener, MouseListener, MouseWheelListener, KeyListener {

    // =========================================================================
    //  CÁMARA ORBITAL
    // =========================================================================
    private int mx, my;
    private final GLU glu = new GLU();
    private double camAngH = -30, camAngV = 45, camDist = 22;

    // =========================================================================
    //  CÁMARA PRIMERA PERSONA
    // =========================================================================
    private boolean fpsMode      = false;
    private double  fpsPosX      = 9.5, fpsPosY = 1.2, fpsPosZ = 23.0;
    private double  fpsYaw       = 0,   fpsPitch = 0;
    private boolean mouseCaptured  = false;
    private boolean justRecentered = false;
    private final Set<Integer> keysDown = new HashSet<>();

    private GLJPanel panel;
    private Robot    robot;

    // =========================================================================
    //  CONSTANTES DE ESCENA
    // =========================================================================
    static final int    GW   = 19;
    static final int    GH   = 46;
    static final double CELL = 1.0;
    static final double WH   = 2.8;
    static final double WT   = 0.12;

    // =========================================================================
    //  MATERIALES
    // =========================================================================
    static final int C = 0, M = 1, D = 2, K = 3, O = 4, A = 5, P = 6;

    private static final float[][] COLORES = {
        {0.25f, 0.55f, 0.20f},  // C cesped
        {0.85f, 0.82f, 0.78f},  // M marmol
        {0.60f, 0.38f, 0.18f},  // D madera
        {0.62f, 0.62f, 0.62f},  // K concreto
        {0.38f, 0.38f, 0.38f},  // O concreto oscuro
        {0.20f, 0.45f, 0.75f},  // A agua
        {0.88f, 0.84f, 0.76f}   // P pared
    };

    private static final float[] KENNY_GREEN = {0.23921569f, 0.30588236f, 0.3019608f, 1f};
    private static final double PLAYER_HEIGHT = 1.6;

    // =========================================================================
    //  SUBGRILLA Y PAREDES
    // =========================================================================
    static final int SW = GW * 2;
    static final int SH = GH * 2;

    private final int[][]     subgrid  = new int[SH][SW];
    private final boolean[][] paredesH = new boolean[GH + 1][GW + 1];
    private final boolean[][] paredesV = new boolean[GH + 1][GW + 1];

    // =========================================================================
    //  TEXTURAS
    // =========================================================================
    private Texture texMarmol, texMadera, texConcreto, texConcOsc,
                    texCesped, texAgua, texPared;

    // =========================================================================
    //  DISPLAY LISTS
    // =========================================================================
    private int     dlPisos = -1, dlParedesMerged = -1;
    private boolean dlCompiladas = false;
    private TextRenderer textRenderer;

    // =========================================================================
    //  MODELOS OBJ
    // =========================================================================
    private static class ObjModel {
        static class Face  { int[] vIdx, tIdx, nIdx; Face(int[] v,int[] t,int[] n){vIdx=v;tIdx=t;nIdx=n;} }
        static class Group { String mtlName=""; java.util.List<Face> faces=new ArrayList<>(); }

        java.util.List<float[]> verts=new ArrayList<>(), texs=new ArrayList<>(), norms=new ArrayList<>();
        java.util.List<Group>   groups   = new ArrayList<>();
        Map<String,Map<String,String>> materials = new HashMap<>();
        Map<String,Texture>     texCache = new HashMap<>();
        boolean loaded=false; String baseDir="";

        void load(String objPath) {
            if ("pared_plana".equals(objPath)) { crearCajaGeom(); loaded=true; return; }
            File f=resolveFile(objPath); if(f==null)return;
            baseDir=f.getParent()!=null?f.getParent():"";
            try(BufferedReader br=new BufferedReader(new FileReader(f))){
                String line; Group cur=new Group(); groups.add(cur);
                while((line=br.readLine())!=null){
                    line=line.trim();
                    if(line.startsWith("mtllib "))      loadMtl(new File(baseDir,line.substring(7).trim()));
                    else if(line.startsWith("usemtl ")){ cur=new Group(); cur.mtlName=line.substring(7).trim(); groups.add(cur);}
                    else if(line.startsWith("v "))  { String[]p=line.substring(2).trim().split("\\s+"); verts.add(new float[]{Float.parseFloat(p[0]),Float.parseFloat(p[1]),Float.parseFloat(p[2])});}
                    else if(line.startsWith("vt ")) { String[]p=line.substring(3).trim().split("\\s+"); texs.add(new float[]{Float.parseFloat(p[0]),p.length>1?Float.parseFloat(p[1]):0});}
                    else if(line.startsWith("vn ")) { String[]p=line.substring(3).trim().split("\\s+"); norms.add(new float[]{Float.parseFloat(p[0]),Float.parseFloat(p[1]),Float.parseFloat(p[2])});}
                    else if(line.startsWith("f "))  parseFace(line.substring(2).trim(),cur);
                }
                loaded=true;
            }catch(Exception e){System.err.println("OBJ: "+objPath+": "+e.getMessage());}
        }
        void parseFace(String s,Group g){
            String[]tok=s.split("\\s+"); int n=tok.length;
            int[]vi=new int[n],ti=new int[n],ni=new int[n];
            for(int i=0;i<n;i++){
                String[]p=tok[i].split("/");
                int iv=Integer.parseInt(p[0]); vi[i]=iv>0?iv-1:verts.size()+iv;
                if(p.length>1&&!p[1].isEmpty()){int it=Integer.parseInt(p[1]);ti[i]=it>0?it-1:texs.size()+it;}else ti[i]=-1;
                if(p.length>2&&!p[2].isEmpty()){int in_=Integer.parseInt(p[2]);ni[i]=in_>0?in_-1:norms.size()+in_;}else ni[i]=-1;
            }
            for(int i=1;i<n-1;i++) g.faces.add(new Face(new int[]{vi[0],vi[i],vi[i+1]},new int[]{ti[0],ti[i],ti[i+1]},new int[]{ni[0],ni[i],ni[i+1]}));
        }
        void loadMtl(File f){
            if(!f.exists())return;
            try(BufferedReader br=new BufferedReader(new FileReader(f))){
                String line,cur=null;
                while((line=br.readLine())!=null){
                    line=line.trim();
                    if(line.startsWith("newmtl ")){cur=line.substring(7).trim();materials.put(cur,new HashMap<>());} 
                    else if(cur!=null){
                        if(line.startsWith("Kd "))    materials.get(cur).put("Kd",line.substring(3).trim());
                        else if(line.startsWith("map_Kd ")) materials.get(cur).put("map_Kd",new File(line.substring(7).trim()).getName());
                    }
                }
            }catch(Exception e){}
        }
        void initTextures(GL2 gl,String objPath){
            String dir=baseDir.isEmpty()?(resolveFile(objPath)!=null?resolveFile(objPath).getParent():""):baseDir;
            for(Map.Entry<String,Map<String,String>>me:materials.entrySet()){
                String tn=me.getValue().get("map_Kd"); if(tn==null)continue;
                tn=new File(tn).getName(); me.getValue().put("map_Kd",tn);
                if(texCache.containsKey(tn))continue;
                File tf=new File(dir,tn);
                if(!tf.exists()) for(String sub:new String[]{"textures","texture","Textures","tex"}){File t2=new File(dir+"/"+sub,tn);if(t2.exists()){tf=t2;break;}}
                if(tf.exists()) try{
                    Texture t=TextureIO.newTexture(tf,true);
                    t.setTexParameteri(gl,GL2.GL_TEXTURE_WRAP_S,GL2.GL_REPEAT);
                    t.setTexParameteri(gl,GL2.GL_TEXTURE_WRAP_T,GL2.GL_REPEAT);
                    t.setTexParameteri(gl,GL2.GL_TEXTURE_MIN_FILTER,GL2.GL_LINEAR_MIPMAP_LINEAR);
                    t.setTexParameteri(gl,GL2.GL_TEXTURE_MAG_FILTER,GL2.GL_LINEAR);
                    texCache.put(tn,t);
                }catch(Exception e){}
            }
        }
        void render(GL2 gl,float[]colorOverride, boolean allowTexture){
            if(!loaded)return;
            for(Group g:groups){
                Map<String,String>mat=materials.get(g.mtlName);
                Texture tex=null; if(mat!=null){String tn=mat.get("map_Kd");if(tn!=null)tex=texCache.get(tn);} 
                if(colorOverride!=null) gl.glColor3f(colorOverride[0],colorOverride[1],colorOverride[2]);
                else if(mat!=null){String kd=mat.get("Kd");if(kd!=null){String[]p=kd.split("\\s+");if(p.length>=3){float r=Float.parseFloat(p[0]),gv=Float.parseFloat(p[1]),b=Float.parseFloat(p[2]);gl.glColor3f(r,gv,b);}}}
                else gl.glColor3f(0.65f,0.60f,0.55f);
                if(allowTexture && tex!=null){gl.glEnable(GL2.GL_TEXTURE_2D);tex.bind(gl);gl.glTexEnvi(GL2.GL_TEXTURE_ENV,GL2.GL_TEXTURE_ENV_MODE,GL2.GL_MODULATE);} 
                else gl.glDisable(GL2.GL_TEXTURE_2D);
                gl.glBegin(GL2.GL_TRIANGLES);
                for(Face face:g.faces){
                    boolean ok=true;for(int i=0;i<3;i++)if(face.vIdx[i]<0||face.vIdx[i]>=verts.size()){ok=false;break;}
                    if(!ok)continue;
                    for(int i=0;i<3;i++){
                        if(face.nIdx[i]>=0&&face.nIdx[i]<norms.size()){float[]nv=norms.get(face.nIdx[i]);gl.glNormal3f(nv[0],nv[1],nv[2]);}
                        if(tex!=null&&face.tIdx[i]>=0&&face.tIdx[i]<texs.size()){float[]tv=texs.get(face.tIdx[i]);gl.glTexCoord2f(tv[0],tv[1]);}
                        float[]v=verts.get(face.vIdx[i]);gl.glVertex3f(v[0],v[1],v[2]);
                    }
                }
                gl.glEnd(); gl.glDisable(GL2.GL_TEXTURE_2D);
            }
        }
        void crearCajaGeom() {
            verts.add(new float[]{-0.5f,0,-0.5f});
            verts.add(new float[]{ 0.5f,0,-0.5f});
            verts.add(new float[]{ 0.5f,1,-0.5f});
            verts.add(new float[]{-0.5f,1,-0.5f});
            verts.add(new float[]{-0.5f,0, 0.5f});
            verts.add(new float[]{ 0.5f,0, 0.5f});
            verts.add(new float[]{ 0.5f,1, 0.5f});
            verts.add(new float[]{-0.5f,1, 0.5f});
            norms.add(new float[]{ 0, 0,-1});
            norms.add(new float[]{ 0, 0, 1});
            norms.add(new float[]{-1, 0, 0});
            norms.add(new float[]{ 1, 0, 0});
            norms.add(new float[]{ 0, 1, 0});
            texs.add(new float[]{0,0}); texs.add(new float[]{1,0});
            texs.add(new float[]{1,1}); texs.add(new float[]{0,1});
            Group g=new Group();
            g.faces.add(new Face(new int[]{1,0,3},new int[]{1,0,3},new int[]{0,0,0}));
            g.faces.add(new Face(new int[]{1,3,2},new int[]{1,3,2},new int[]{0,0,0}));
            g.faces.add(new Face(new int[]{4,5,6},new int[]{0,1,2},new int[]{1,1,1}));
            g.faces.add(new Face(new int[]{4,6,7},new int[]{0,2,3},new int[]{1,1,1}));
            g.faces.add(new Face(new int[]{0,4,7},new int[]{0,1,2},new int[]{2,2,2}));
            g.faces.add(new Face(new int[]{0,7,3},new int[]{0,2,3},new int[]{2,2,2}));
            g.faces.add(new Face(new int[]{5,1,2},new int[]{0,1,2},new int[]{3,3,3}));
            g.faces.add(new Face(new int[]{5,2,6},new int[]{0,2,3},new int[]{3,3,3}));
            g.faces.add(new Face(new int[]{3,7,6},new int[]{0,1,2},new int[]{4,4,4}));
            g.faces.add(new Face(new int[]{3,6,2},new int[]{0,2,3},new int[]{4,4,4}));
            g.faces.add(new Face(new int[]{0,1,5},new int[]{0,1,2},new int[]{4,4,4}));
            g.faces.add(new Face(new int[]{0,5,4},new int[]{0,2,3},new int[]{4,4,4}));
            groups.add(g);
        }
        static File resolveFile(String path){
            for(String p:new String[]{"","src/","../"}) {File f=new File(p+path);if(f.exists())return f;}
            return null;
        }
    }

    private static class SceneObject {
        String objPath; double posX,posY,posZ,rotX,rotY,rotZ,targetW,targetH,targetD;
        double scaleX=1,scaleY=1,scaleZ=1,baseOffsetX=0,baseOffsetY=0,baseOffsetZ=0;
        float[] colorOverride=null;
        ObjModel model=new ObjModel(); boolean initDone=false; int dlId=-1;
        boolean wallMaterial=false;
        boolean isDoor=false; float doorAngle=0f,doorTarget=0f; int doorCloseDelay=0;
        SceneObject(String o,double px,double py,double pz,double rx,double ry,double rz,double tw,double th,double td){
            objPath=o;posX=px;posY=py;posZ=pz;rotX=rx;rotY=ry;rotZ=rz;targetW=tw;targetH=th;targetD=td;
            wallMaterial = isCustomWall(o);
            isDoor = tieneHojaPuerta(o);
        }
        void computeScale(){
            if(!model.loaded||model.verts.isEmpty())return;
            float minX=Float.MAX_VALUE,maxX=-Float.MAX_VALUE,minY=Float.MAX_VALUE,maxY=-Float.MAX_VALUE,minZ=Float.MAX_VALUE,maxZ=-Float.MAX_VALUE;
            for(float[]v:model.verts){if(v[0]<minX)minX=v[0];if(v[0]>maxX)maxX=v[0];if(v[1]<minY)minY=v[1];if(v[1]>maxY)maxY=v[1];if(v[2]<minZ)minZ=v[2];if(v[2]>maxZ)maxZ=v[2];}
            double bw=maxX-minX,bh=maxY-minY,bd=maxZ-minZ;
            if(bw<1e-6||bh<1e-6||bd<1e-6)return;
            scaleX=targetW/bw;scaleY=targetH/bh;scaleZ=targetD/bd;
            baseOffsetX=-(minX+maxX)/2.0*scaleX;
            baseOffsetY=-minY*scaleY;
            baseOffsetZ=-(minZ+maxZ)/2.0*scaleZ;
        }
    }

    private final java.util.List<SceneObject> sceneObjects = new ArrayList<>();

    private void crearObjetos() {
        add("pared_plana", 9.00, 1.95, 28.00,  0,0,0,  2.10,0.84,0.12);
        add("pared_plana", 10.00, 1.95, 13.00,  0,0,0,  2.10,0.84,0.12);
        add("pared_plana", 14.00, 1.95, 15.50,  0,90,0,  1.30,0.84,0.12);
        add("pared_plana", 14.00, 1.95, 17.50,  0,90,0,  1.10,0.84,0.12);
        add("pared_plana", 17.50, 1.95, 23.00,  0,180,0,  1.10,0.84,0.12);
        add("pared_plana", 12.00, 1.95, 34.50,  0,270,0,  1.20,0.84,0.05);
        add("pared_plana", 12.58, 1.95, 41.00,  0,0,0,  1.20,0.84,0.12);
        add("pared_plana", 13.34, 1.95, 33.96,  0,0,0,  1.20,0.84,0.12);
        add("pared_plana", 15.50, 1.95, 35.04,  0,0,0,  1.20,0.84,0.12);
        add("pared_plana", 9.75, -0.00, 28.00,  0,0,0,  0.60,2.80,0.12);
        add("pared_plana", 8.25, -0.00, 28.00,  0,0,0,  0.60,2.80,0.12);
        add("pared_plana", 9.25, -0.00, 13.00,  0,0,0,  0.60,2.80,0.12);
        add("pared_plana", 10.75, -0.00, 13.00,  0,0,0,  0.60,2.80,0.12);
        add("modelos/carrros/Car-Model/CarBlanco.obj", 15.58, 0.00, 11.36,  0,180,0,  1.89,1.43,4.20);
        add("modelos/carrros/Car-Model/Car.obj", 13.20, 0.00, 11.34,  0,180,0,  1.89,1.43,4.20);
        add("modelos/Models/OBJ format/doorwayFront.obj", 10.44, 0.01, 12.89, 0,0,0,  0.90,1.92,0.12);
        add("modelos/Models/OBJ format/doorwayFront.obj", 9.45, 0.02, 27.92,  0,0,0,  0.90,1.92,0.12);
        add("modelos/Models/OBJ format/doorwayFront.obj", 12.98, 0.02, 40.96, 0,0,0,  0.90,1.92,0.12);
        add("modelos/Models/OBJ format/doorway.obj",      16.01, 0.02, 35.00, 0,0,0,  1.00,1.92,0.12);
        add("modelos/Models/OBJ format/doorway.obj",      12.97, 0.02, 34.09, 0,180,0,1.00,1.92,0.12);
        add("modelos/Models/OBJ format/doorway.obj",      13.97, 0.03, 15.99, 0,270,0,1.00,1.92,0.12);
        add("modelos/Models/OBJ format/doorway.obj",      13.99, 0.03, 17.92, 0,270,0,1.00,1.92,0.12);
        add("modelos/Models/OBJ format/doorway.obj",      17.88, 0.03, 23.04, 0,0,0,  1.00,1.92,0.12);
        add("modelos/Models/OBJ format/stairs.obj", 11.49, 0.00, 18.88, 0,90,0, 3.10,2.40,1.40);
        
        // Objetos decorativos - Sala de estar
        add("modelos/Models/OBJ format/loungeSofaCorner.obj", 4.98, 0.00, 16.39,  0,90,0,  3.60,0.94,1.60);
        add("modelos/Models/OBJ format/loungeChair.obj", 4.58, 0.00, 18.51,  0,0,0,  1.26,0.99,1.12);
        add("modelos/Models/OBJ format/loungeChair.obj", 4.90, 0.00, 15.32,  0,135,0,  1.26,0.99,1.12);
        add("modelos/Models/OBJ format/cabinetTelevisionDoors.obj", 1.89, 0.00, 17.65,  0,270,0,  1.62,1.32,0.64);
        add("modelos/Models/OBJ format/televisionModern.obj", 1.61, 1.32, 16.80,  0,270,0,  2.16,1.26,0.18);
        add("modelos/Models/OBJ format/plantSmall3.obj", 1.50, 0.01, 21.40,  0,270,0,  0.72,1.08,0.72);
        add("modelos/Models/OBJ format/plantSmall3.obj", 1.62, 0.01, 14.14,  0,270,0,  0.72,1.08,0.72);
        add("modelos/Models/OBJ format/plantSmall3.obj", 4.74, 0.01, 21.55,  0,270,0,  0.72,1.08,0.72);
        add("modelos/Models/OBJ format/rugRound.obj", 5.44, -0.03, 17.93,  0,270,0,  3.60,0.04,3.60);
        add("modelos/Models/OBJ format/tableCross.obj", 7.64, -0.03, 24.85,  0,0,0,  3.60,1.09,1.40);
        add("modelos/Models/OBJ format/tableCross.obj", 7.66, -0.03, 24.40,  0,0,0,  3.60,1.01,1.40);
        add("modelos/Models/OBJ format/chair.obj", 6.89, -0.03, 25.81,  0,0,0,  0.84,1.08,0.84);
        add("modelos/Models/OBJ format/chair.obj", 5.70, -0.03, 25.83,  0,0,0,  0.84,1.08,0.84);
        add("modelos/Models/OBJ format/chair.obj", 4.83, -0.03, 24.31,  0,180,0,  0.84,1.08,0.84);
        add("modelos/Models/OBJ format/chair.obj", 6.17, -0.03, 24.30,  0,180,0,  0.84,1.08,0.84);
        add("modelos/Models/OBJ format/chair.obj", 3.95, -0.03, 25.51,  0,270,0,  0.84,1.08,0.84);
        add("modelos/Models/OBJ format/chair.obj", 7.87, -0.03, 24.30,  0,90,0,  0.84,1.08,0.84);
        add("modelos/Models/OBJ format/tableRound.obj", 14.43, -0.03, 25.61,  0,90,0,  1.80,0.94,1.20);
        add("modelos/Models/OBJ format/chair.obj", 15.76, -0.03, 26.02,  0,90,0,  0.84,1.08,0.84);
        add("modelos/Models/OBJ format/chair.obj", 14.77, -0.03, 25.78,  0,180,0,  0.84,1.08,0.84);
        add("modelos/Models/OBJ format/chair.obj", 14.24, -0.03, 26.89,  0,270,0,  0.84,1.08,0.84);
        add("modelos/Models/OBJ format/chair.obj", 15.64, -0.03, 27.14,  0,0,0,  0.84,1.08,0.84);
        
        // Baño
        add("modelos/Models/OBJ format/bathroomSink.obj", 14.26, -0.03, 14.62,  0,180,0,  0.66,0.94,0.55);
        add("modelos/Models/OBJ format/toilet.obj", 15.80, -0.06, 14.09,  0,180,0,  0.55,0.94,0.55);
        add("modelos/Models/OBJ format/washer.obj", 15.99, -0.06, 16.79,  0,180,0,  0.72,1.02,0.72);
        add("modelos/Models/OBJ format/cabinetTelevisionDoors.obj", 14.82, -0.00, 16.53,  0,180,0,  0.72,0.96,0.32);
        
        // Librerías
        add("modelos/Models/OBJ format/bookcaseOpen.obj", 14.30, -0.00, 20.65,  0,180,0,  0.99,2.10,0.44);
        add("modelos/Models/OBJ format/bookcaseOpen.obj", 15.79, -0.00, 20.62,  0,180,0,  0.99,2.10,0.44);
        add("modelos/Models/OBJ format/bookcaseOpen.obj", 14.70, -0.00, 22.39,  0,270,0,  0.99,2.10,0.44);
        add("modelos/Models/OBJ format/cardboardBoxOpen.obj", 17.56, -0.00, 20.82,  0,270,0,  0.44,0.49,0.44);
        
        // Recámara
        add("modelos/Models/OBJ format/bedDouble.obj", 14.90, 0.01, 37.57,  0,90,0,  2.20,1.05,2.85);
        add("modelos/Models/OBJ format/cabinetBed.obj", 16.88, 0.01, 36.08,  0,90,0,  1.20,0.45,0.90);
        add("modelos/Models/OBJ format/cabinetBed.obj", 16.97, 0.01, 39.84,  0,90,0,  1.00,0.45,0.90);
        
        // Estantería
        add("modelos/Models/OBJ format/bookcaseClosedWide.obj", 13.51, 0.01, 30.13,  0,90,0,  0.63,1.80,0.32);
        add("modelos/Models/OBJ format/bookcaseClosedWide.obj", 13.54, 0.01, 30.80,  0,90,0,  0.63,1.80,0.32);
        add("modelos/Models/OBJ format/bookcaseClosedWide.obj", 13.49, 0.01, 31.47,  0,90,0,  0.63,1.80,0.32);
        
        // Sala de ocio
        add("modelos/Models/OBJ format/loungeDesignSofaCorner.obj", 4.11, 0.01, 31.35,  0,180,0,  3.40,1.02,2.10);
        add("modelos/Models/OBJ format/tableRound.obj", 3.38, 0.01, 32.79,  0,180,0,  2.55,0.78,2.10);
        add("modelos/Models/OBJ format/loungeChairRelax.obj", 2.99, 0.01, 32.07,  0,270,0,  1.19,0.90,1.47);
        add("modelos/Models/OBJ format/loungeChairRelax.obj", 3.24, 0.01, 30.75,  0,270,0,  1.19,0.90,1.47);
        
        // Baño adicional 2
        add("modelos/Models/OBJ format/toilet.obj", 14.22, 0.01, 34.09,  0,270,0,  0.60,1.02,0.60);
        add("modelos/Models/OBJ format/bathroomSink.obj", 17.35, 0.01, 33.39,  0,90,0,  0.72,1.02,0.60);
        add("modelos/Models/OBJ format/bathroomMirror.obj", 17.65, 1.08, 33.37,  0,90,0,  0.72,1.02,0.60);
        add("modelos/Models/OBJ format/showerRound.obj", 15.18, -0.00, 31.17,  0,270,0,  1.08,2.40,1.08);
        add("modelos/Models/OBJ format/bathtub.obj", 17.90, -0.00, 32.30,  0,270,0,  2.04,0.72,0.96);
        add("modelos/Models/OBJ format/rugDoormat.obj", 15.16, -0.00, 32.33,  0,270,0,  1.00,0.01,1.00);
        add("modelos/Models/OBJ format/televisionModern.obj", 12.41, 0.98, 38.85,  0,270,0,  1.32,0.84,0.10);
    }
    private void add(String p,double px,double py,double pz,double rx,double ry,double rz,double sx,double sy,double sz){
        sceneObjects.add(new SceneObject(p,px,py,pz,rx,ry,rz,sx,sy,sz));
    }

    private static String resolveObjPath(String rel){
        if ("pared_plana".equals(rel)) return "pared_plana";
        for(String p:new String[]{"","src/","../"}){if(new File(p+rel).exists())return p+rel;}
        return "src/"+rel;
    }

    private static boolean isCustomWall(String path) {
        String n = path.toLowerCase(Locale.ROOT);
        return n.contains("pared_plana");
    }

    // =========================================================================
    //  CONSTRUCTOR
    // =========================================================================
    public Casa3D_SinEdicion() {
        for (int[] row : subgrid) Arrays.fill(row, C);
        cargarSubgrid();
        inicializarParedes();
        crearObjetos();
        Thread t=new Thread(()->{
            for(SceneObject so:sceneObjects){so.model.load(resolveObjPath(so.objPath));so.computeScale();}
        },"ObjLoader");
        t.setDaemon(true); t.start();
    }

    private void fill(int row, int c0, int c1, int mat) {
        for (int c=c0;c<=c1;c++) subgrid[row][c]=mat;
    }

    private void cargarSubgrid() {
        fill(1,15,19,K); fill(1,21,35,K); fill(2,15,19,K); fill(2,21,35,K);
        fill(4,15,19,K); fill(4,21,35,K); fill(5,15,19,K); fill(5,21,35,K);
        fill(7,15,19,K); fill(7,21,35,K); fill(8,15,19,K); fill(8,21,35,K);
        fill(10,15,19,K); fill(10,21,35,K); fill(11,15,19,K); fill(11,21,35,K);
        fill(12,21,35,K); fill(13,15,35,K); fill(14,15,35,K); fill(15,15,35,K);
        fill(16,15,35,K); fill(17,15,35,K); fill(18,15,35,K);
        fill(19,2,34,K); fill(19,35,35,O); fill(20,2,34,K); fill(20,35,35,O);
        fill(21,2,34,K); fill(21,35,35,O);
        fill(22,2,21,K); fill(22,22,22,P); fill(22,23,34,K); fill(22,35,35,O);
        fill(23,2,21,K); fill(23,22,22,P); fill(23,23,35,K);
        fill(24,2,21,K); fill(24,22,22,P); fill(24,23,35,K);
        fill(25,2,2,P); fill(25,3,13,O); fill(25,14,14,P); fill(25,15,21,K); fill(25,22,22,P); fill(25,23,35,K);
        fill(26,2,4,P); fill(26,5,13,M); fill(26,14,14,P); fill(26,15,21,M); fill(26,22,22,P); fill(26,23,35,K);
        fill(27,2,13,M); fill(27,14,14,P); fill(27,15,21,M); fill(27,22,22,P); fill(27,23,35,K);
        fill(28,2,21,M); fill(28,22,22,P); fill(28,23,27,M); fill(28,28,33,O);
        fill(29,2,21,M); fill(29,22,22,P); fill(29,23,27,M); fill(29,28,33,O);
        fill(30,2,21,M); fill(30,22,22,P); fill(30,23,27,M); fill(30,28,33,O);
        fill(31,2,27,M); fill(31,28,33,O); fill(32,2,27,M); fill(32,28,33,O);
        fill(33,2,27,M); fill(33,28,33,O);
        fill(34,2,24,M); fill(34,25,25,P); fill(34,26,27,M); fill(34,28,33,O);
        fill(35,2,24,M); fill(35,25,25,P); fill(35,26,27,M); fill(35,28,33,O);
        fill(36,2,24,M); fill(36,25,25,P); fill(36,26,27,M); fill(36,28,33,O);
        fill(37,2,24,M); fill(37,25,25,P); fill(37,26,27,M); fill(37,28,33,O);
        fill(38,2,21,M); fill(38,22,22,P); fill(38,23,24,M); fill(38,25,25,P); fill(38,26,27,M); fill(38,28,33,O);
        fill(39,2,21,M); fill(39,22,22,P); fill(39,23,24,M); fill(39,25,25,P); fill(39,26,27,M); fill(39,28,33,O);
        fill(40,2,21,M); fill(40,22,22,P); fill(40,23,24,M); fill(40,25,25,P); fill(40,26,35,M);
        fill(41,2,21,M); fill(41,22,22,P); fill(41,23,24,M); fill(41,25,25,P); fill(41,26,35,M);
        fill(42,2,21,M); fill(42,22,22,P); fill(42,23,24,M); fill(42,25,25,P); fill(42,26,35,M);
        fill(43,2,21,M); fill(43,22,22,P); fill(43,23,24,M); fill(43,25,25,P); fill(43,26,35,M);
        fill(44,2,9,P); fill(44,10,21,M); fill(44,22,22,P); fill(44,23,24,M); fill(44,25,25,P); fill(44,26,35,M);
        fill(45,2,9,P); fill(45,10,21,M); fill(45,22,22,P); fill(45,23,24,M); fill(45,25,25,P); fill(45,26,35,M);
        for (int r=46;r<=55;r++) fill(r,2,35,M);
        fill(56,2,19,K); fill(56,20,35,M); fill(57,2,19,K); fill(57,20,35,M);
        fill(58,2,19,K); fill(58,20,35,M); fill(59,2,19,K); fill(59,20,35,M);
        for (int r=60;r<=68;r++) { fill(r,2,19,K); fill(r,20,27,M); fill(r,28,35,O); }
        fill(69,2,2,O); fill(69,3,19,K); fill(69,20,27,M); fill(69,28,35,O);
        fill(70,2,19,K); fill(70,24,35,M); fill(71,2,19,K); fill(71,24,35,M);
        fill(72,2,19,K); fill(72,24,35,M); fill(73,2,19,K); fill(73,24,35,M);
        fill(74,2,17,D); fill(74,18,19,K); fill(74,24,35,M); fill(75,2,17,D); fill(75,18,19,K); fill(75,24,35,M);
        for (int r=76;r<=81;r++) { fill(r,2,3,D); fill(r,4,5,K); fill(r,6,13,A); fill(r,14,15,K); fill(r,16,17,D); fill(r,18,19,K); fill(r,24,35,M); }
        fill(82,2,3,D); fill(82,4,15,K); fill(82,16,17,D); fill(82,18,19,K); fill(82,24,35,K);
        fill(83,2,3,D); fill(83,4,15,K); fill(83,16,17,D); fill(83,18,19,K); fill(83,24,35,K);
        fill(84,24,35,K); fill(85,24,35,K);
    }

    private void inicializarParedes() {
        for (int[] p : new int[][]{{1,13},{2,13},{3,13},{4,13},{5,13},{6,13},{7,13},{8,13},{11,14},{12,14},{13,14},{14,14},{15,14},{16,14},{14,16},{15,16},{16,16},{13,18},{14,20},{15,20},{16,20},{17,20},{14,23},{15,23},{16,23},{1,28},{2,28},{3,28},{4,28},{5,28},{6,28},{7,28},{12,30},{13,30},{14,30},{15,30},{16,30},{17,30},{12,34},{10,35},{11,35},{14,35},{16,35},{17,35},{13,41},{14,41},{15,41},{16,41},{17,41}})
            paredesH[p[1]][p[0]]=true;
        for (int[] p : new int[][]{{11,11},{11,12},{1,13},{11,13},{1,14},{14,14},{17,14},{1,15},{17,15},{1,16},{14,16},{17,16},{1,17},{13,17},{17,17},{1,18},{13,18},{14,18},{17,18},{1,19},{13,19},{14,19},{17,19},{1,20},{13,20},{14,20},{18,20},{1,21},{13,21},{14,21},{18,21},{1,22},{13,22},{14,22},{18,22},{1,23},{18,23},{1,24},{18,24},{1,25},{18,25},{1,26},{18,26},{1,27},{18,27},{1,28},{10,28},{12,28},{18,28},{10,29},{12,29},{18,29},{10,30},{12,30},{14,30},{18,30},{10,31},{12,31},{14,31},{18,31},{10,32},{12,32},{14,32},{18,32},{10,33},{12,33},{14,33},{18,33},{10,34},{14,34},{18,34},{12,35},{18,35},{12,36},{18,36},{12,37},{18,37},{12,38},{18,38},{12,39},{18,39},{12,40},{18,40},{12,41}})
            paredesV[p[1]][p[0]]=true;
    }

    @Override
    public void init(GLAutoDrawable d) {
        GL2 gl = d.getGL().getGL2();
        d.getGL().setSwapInterval(1);
        gl.glClearColor(0.5f, 0.7f, 0.9f, 1f);
        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glEnable(GL2.GL_MULTISAMPLE);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_LIGHT0);
        gl.glEnable(GL2.GL_COLOR_MATERIAL);
        gl.glColorMaterial(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT_AND_DIFFUSE);
        gl.glLightModeli(GL2.GL_LIGHT_MODEL_TWO_SIDE, GL2.GL_TRUE);
        gl.glShadeModel(GL2.GL_SMOOTH);
        gl.glEnable(GL2.GL_NORMALIZE);
        gl.glHint(GL2.GL_PERSPECTIVE_CORRECTION_HINT, GL2.GL_NICEST);

        gl.glLightModelfv(GL2.GL_LIGHT_MODEL_AMBIENT, new float[]{0.55f,0.55f,0.55f,1}, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_AMBIENT,  new float[]{0.35f,0.35f,0.35f,1}, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE,  new float[]{1.0f,1.0f,1.0f,1}, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, new float[]{-0.2f,1.0f,0.1f,0}, 0);
        gl.glDisable(GL2.GL_LIGHT1);

        texMarmol   = cargarTex(gl,"piso_marmol.jpg");
        texMadera   = cargarTex(gl,"piso_madera.jpg");
        texConcreto = cargarTex(gl,"concreto.jpg");
        texConcOsc  = cargarTex(gl,"concreto oscuro.jpg");
        texCesped   = cargarTex(gl,"cesped.jpg");
        texAgua     = cargarTex(gl,"agua.jpg");
        texPared    = cargarTex(gl,"pared.jpg");

        textRenderer = new TextRenderer(new Font("Arial", Font.BOLD, 17));
    }

    private Texture cargarTex(GL2 gl, String nombre) {
        for (String ruta : new String[]{"textura/"+nombre,"src/textura/"+nombre,nombre}) {
            File f = new File(ruta);
            if (!f.exists()) continue;
            try {
                Texture t = TextureIO.newTexture(f, true);
                t.setTexParameteri(gl,GL2.GL_TEXTURE_WRAP_S,    GL2.GL_REPEAT);
                t.setTexParameteri(gl,GL2.GL_TEXTURE_WRAP_T,    GL2.GL_REPEAT);
                t.setTexParameteri(gl,GL2.GL_TEXTURE_MIN_FILTER,GL2.GL_LINEAR);
                t.setTexParameteri(gl,GL2.GL_TEXTURE_MAG_FILTER,GL2.GL_LINEAR);
                return t;
            } catch (Exception e) { System.err.println("Tex "+nombre+": "+e.getMessage()); }
        }
        return null;
    }

    @Override
    public void display(GLAutoDrawable d) {
        GL2 gl = d.getGL().getGL2();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        if (fpsMode) {
            double rad  = Math.toRadians(fpsYaw);
            double pit  = Math.toRadians(Math.max(-85, Math.min(85, fpsPitch)));
            double lx   = Math.cos(pit) * Math.sin(rad);
            double ly   = Math.sin(pit);
            double lz   = Math.cos(pit) * Math.cos(rad);
            glu.gluLookAt(fpsPosX, fpsPosY, fpsPosZ,
                          fpsPosX+lx, fpsPosY+ly, fpsPosZ+lz, 0,1,0);
        } else {
            double aH = Math.toRadians(camAngH);
            double aV = Math.toRadians(Math.max(5, Math.min(85, camAngV)));
            double cx = GW*CELL/2.0, cz = GH*CELL/2.0;
            double ex = cx + camDist*Math.cos(aV)*Math.sin(aH);
            double ey = camDist*Math.sin(aV);
            double ez = cz + camDist*Math.cos(aV)*Math.cos(aH);
            glu.gluLookAt(ex,ey,ez, cx,0,cz, 0,1,0);
        }

        if (!dlCompiladas) compilarDisplayLists(gl);

        if (dlPisos > 0) gl.glCallList(dlPisos); else dibujarPisos(gl);
        if (dlParedesMerged > 0) gl.glCallList(dlParedesMerged); else dibujarParedesMerged(gl);

        dibujarObjetos(gl);

        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        dibujarHUD(gl, w, h);
    }

    private void dibujarHUD(GL2 gl, int w, int h) {
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glDisable(GL2.GL_DEPTH_TEST);
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPushMatrix(); gl.glLoadIdentity();
        glu.gluOrtho2D(0, w, 0, h);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPushMatrix(); gl.glLoadIdentity();

        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);

        if (fpsMode) {
            gl.glColor4f(0f,0f,0f,0.60f);
            gl.glBegin(GL2.GL_QUADS);
            gl.glVertex2i(0,h); gl.glVertex2i(w,h);
            gl.glVertex2i(w,h-40); gl.glVertex2i(0,h-40);
            gl.glEnd();

            gl.glDisable(GL2.GL_BLEND);
            if (textRenderer != null) {
                textRenderer.beginRendering(w, h);
                textRenderer.setColor(1f,1f,0.2f,1f);
                textRenderer.draw(String.format("X=%.1f  Z=%.1f", fpsPosX, fpsPosZ), 10, h-26);
                textRenderer.setColor(0.85f,0.85f,0.85f,1f);
                textRenderer.draw("[WASD] mover   [Tab] camara orbital   [Esc] salir FPS", 10, h-44);
                textRenderer.endRendering();
            }
        } else {
            gl.glColor4f(0f,0f,0f,0.60f);
            gl.glBegin(GL2.GL_QUADS);
            gl.glVertex2i(0,36); gl.glVertex2i(w,36);
            gl.glVertex2i(w,0);  gl.glVertex2i(0,0);
            gl.glEnd();
            gl.glDisable(GL2.GL_BLEND);
            if (textRenderer != null) {
                textRenderer.beginRendering(w, h);
                textRenderer.setColor(1f,1f,0.3f,1f);
                textRenderer.draw("[Tab] primera persona   [Arrastrar] rotar   [Rueda] zoom   [1]Sur [2]Este [3]Norte [4]Oeste [5]Aérea", 10, 10);
                textRenderer.endRendering();
            }
        }

        gl.glMatrixMode(GL2.GL_PROJECTION); gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_MODELVIEW);  gl.glPopMatrix();
        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glEnable(GL2.GL_LIGHTING);
    }

    private void dibujarObjetos(GL2 gl) {
        for (SceneObject so : sceneObjects) {
            if (so.wallMaterial) continue;
            if (so.model.loaded && !so.initDone) {
                so.model.initTextures(gl, resolveObjPath(so.objPath));
                so.initDone = true;
            }
            if (!so.model.loaded) continue;
            if (so.dlId == -1) {
                so.dlId = gl.glGenLists(1);
                gl.glNewList(so.dlId, GL2.GL_COMPILE);
                so.model.render(gl, so.colorOverride, true);
                gl.glEndList();
            }
            gl.glPushMatrix();
            gl.glTranslated(so.posX, so.posY + so.baseOffsetY, so.posZ);
            if (so.rotY != 0) gl.glRotated(so.rotY, 0, 1, 0);
            if (so.rotX != 0) gl.glRotated(so.rotX, 1, 0, 0);
            if (so.rotZ != 0) gl.glRotated(so.rotZ, 0, 0, 1);
            if (so.isDoor && so.doorAngle != 0) gl.glRotated(so.doorAngle, 0, 1, 0);
            gl.glScaled(so.scaleX, so.scaleY, so.scaleZ);
            gl.glCallList(so.dlId);
            gl.glPopMatrix();
        }
        gl.glDisable(GL2.GL_TEXTURE_2D);
    }

    private void compilarDisplayLists(GL2 gl) {
        dlPisos    = gl.glGenLists(1); gl.glNewList(dlPisos,   GL2.GL_COMPILE); dibujarPisos(gl);            gl.glEndList();
        dlParedesMerged = gl.glGenLists(1); gl.glNewList(dlParedesMerged,GL2.GL_COMPILE); dibujarParedesMerged(gl); gl.glEndList();
        dlCompiladas = true;
    }

    private Texture texParaMat(int m) {
        switch(m){ case M:return texMarmol; case D:return texMadera; case K:return texConcreto; case O:return texConcOsc; case A:return texAgua; case C:return texCesped; case P:return texPared; default:return null; }
    }
    private void setMat(GL2 gl, int mat) {
        gl.glDisable(GL2.GL_TEXTURE_2D);
        float[] c=(mat>=0&&mat<COLORES.length)?COLORES[mat]:COLORES[0];
        gl.glColor3f(c[0],c[1],c[2]);
        Texture t=texParaMat(mat);
        if(t!=null){ gl.glEnable(GL2.GL_TEXTURE_2D); t.bind(gl); gl.glTexEnvi(GL2.GL_TEXTURE_ENV,GL2.GL_TEXTURE_ENV_MODE,GL2.GL_MODULATE); }
    }

    private void applyWallMaterial(GL2 gl) {
        gl.glPushAttrib(GL2.GL_ENABLE_BIT | GL2.GL_LIGHTING_BIT | GL2.GL_CURRENT_BIT | GL2.GL_TEXTURE_BIT);
        gl.glDisable(GL2.GL_TEXTURE_2D);
        gl.glDisable(GL2.GL_COLOR_MATERIAL);
        gl.glMaterialfv(GL2.GL_FRONT, GL2.GL_AMBIENT_AND_DIFFUSE, KENNY_GREEN, 0);
        gl.glMaterialfv(GL2.GL_BACK,  GL2.GL_AMBIENT_AND_DIFFUSE, KENNY_GREEN, 0);
    }

    private void restoreColorMaterial(GL2 gl) {
        gl.glPopAttrib();
    }

    private void dibujarPisos(GL2 gl) {
        double s=CELL/2.0; int lastMat=-1;
        for (int sy=0;sy<SH;sy++) for (int sx=0;sx<SW;sx++) {
            int mat=subgrid[sy][sx]; if(mat==P) continue;
            if(mat!=lastMat){ setMat(gl,mat); lastMat=mat; }
            double x=sx*s, z=sy*s, y=(mat==A)?-0.1:0.0;
            gl.glBegin(GL2.GL_QUADS); gl.glNormal3d(0,1,0);
            gl.glTexCoord2d(0,0); gl.glVertex3d(x,  y,z);
            gl.glTexCoord2d(1,0); gl.glVertex3d(x+s,y,z);
            gl.glTexCoord2d(1,1); gl.glVertex3d(x+s,y,z+s);
            gl.glTexCoord2d(0,1); gl.glVertex3d(x,  y,z+s);
            gl.glEnd();
        }
        gl.glDisable(GL2.GL_TEXTURE_2D);
    }

    private void dibujarParedesMerged(GL2 gl) {
        applyWallMaterial(gl);
        for (int y=0;y<=GH;y++) {
            int xS=-1;
            for (int x=0;x<=GW;x++) {
                boolean a=(x<GW)&&paredesH[y][x];
                if(a&&xS<0) xS=x;
                else if(!a&&xS>=0){
                    dibujarSegmentoPared(gl,xS*CELL,0,y*CELL,x*CELL,0,y*CELL,WT,WH);
                    xS=-1;
                }
            }
        }
        for (int x=0;x<=GW;x++) {
            int yS=-1;
            for (int y=0;y<=GH;y++) {
                boolean a=(y<GH)&&paredesV[y][x];
                if(a&&yS<0) yS=y;
                else if(!a&&yS>=0){
                    dibujarSegmentoPared(gl,x*CELL,0,yS*CELL,x*CELL,0,y*CELL,WT,WH);
                    yS=-1;
                }
            }
        }
        boolean[][] vis=new boolean[SH][SW]; double ss=CELL/2.0;
        for (int sy=0;sy<SH;sy++) for (int sx=0;sx<SW;sx++) {
            if(subgrid[sy][sx]!=P||vis[sy][sx]) continue;
            int mx=sx; while(mx+1<SW&&subgrid[sy][mx+1]==P&&!vis[sy][mx+1]) mx++;
            int my=sy; outer: while(my+1<SH){for(int tx=sx;tx<=mx;tx++) if(subgrid[my+1][tx]!=P||vis[my+1][tx]) break outer; my++;}
            for(int ry=sy;ry<=my;ry++) for(int rx=sx;rx<=mx;rx++) vis[ry][rx]=true;
            dibujarCaja(gl,sx*ss,0,sy*ss,(mx+1)*ss,WH,(my+1)*ss);
        }
        for (SceneObject so : sceneObjects) {
            if (!so.wallMaterial) continue;
            dibujarCajaPared(gl, so);
        }
        restoreColorMaterial(gl);
    }

    private void dibujarSegmentoPared(GL2 gl,double x1,double y1,double z1,double x2,double y2,double z2,double grosor,double alto) {
        double dx=x2-x1,dz=z2-z1,len=Math.sqrt(dx*dx+dz*dz); if(len<0.001)return;
        double nx=dz/len*grosor/2, nz=-dx/len*grosor/2;
        quad(gl, dx/len*0,dz/len*1,-dx/len, x1+nx,0,z1+nz, x2+nx,0,z2+nz, x2+nx,alto,z2+nz, x1+nx,alto,z1+nz, len,alto);
        quad(gl,-dz/len,0, dx/len, x2-nx,0,z2-nz, x1-nx,0,z1-nz, x1-nx,alto,z1-nz, x2-nx,alto,z2-nz, len,alto);
        gl.glBegin(GL2.GL_QUADS); gl.glNormal3d(0,1,0);
        gl.glVertex3d(x1+nx,alto,z1+nz); gl.glVertex3d(x2+nx,alto,z2+nz);
        gl.glVertex3d(x2-nx,alto,z2-nz); gl.glVertex3d(x1-nx,alto,z1-nz); gl.glEnd();
        quad(gl,-dx/len,0,-dz/len, x1-nx,0,z1-nz, x1+nx,0,z1+nz, x1+nx,alto,z1+nz, x1-nx,alto,z1-nz, grosor,alto);
        quad(gl, dx/len,0, dz/len, x2+nx,0,z2+nz, x2-nx,0,z2-nz, x2-nx,alto,z2-nz, x2+nx,alto,z2+nz, grosor,alto);
    }

    private void quad(GL2 gl,double nx,double ny,double nz, double x0,double y0,double z0, double x1,double y1,double z1, double x2,double y2,double z2, double x3,double y3,double z3, double u,double v) {
        gl.glBegin(GL2.GL_QUADS); gl.glNormal3d(nx,ny,nz);
        gl.glVertex3d(x0,y0,z0); gl.glVertex3d(x1,y1,z1);
        gl.glVertex3d(x2,y2,z2); gl.glVertex3d(x3,y3,z3); gl.glEnd();
    }

    private void dibujarCaja(GL2 gl,double x0,double y0,double z0,double x1,double y1,double z1) {
        gl.glBegin(GL2.GL_QUADS);
        gl.glNormal3d(0,0,1);  gl.glVertex3d(x0,y0,z1); gl.glVertex3d(x1,y0,z1); gl.glVertex3d(x1,y1,z1); gl.glVertex3d(x0,y1,z1);
        gl.glNormal3d(0,0,-1); gl.glVertex3d(x1,y0,z0); gl.glVertex3d(x0,y0,z0); gl.glVertex3d(x0,y1,z0); gl.glVertex3d(x1,y1,z0);
        gl.glNormal3d(1,0,0);  gl.glVertex3d(x1,y0,z1); gl.glVertex3d(x1,y0,z0); gl.glVertex3d(x1,y1,z0); gl.glVertex3d(x1,y1,z1);
        gl.glNormal3d(-1,0,0); gl.glVertex3d(x0,y0,z0); gl.glVertex3d(x0,y0,z1); gl.glVertex3d(x0,y1,z1); gl.glVertex3d(x0,y1,z0);
        gl.glNormal3d(0,1,0);  gl.glVertex3d(x0,y1,z0); gl.glVertex3d(x1,y1,z0); gl.glVertex3d(x1,y1,z1); gl.glVertex3d(x0,y1,z1);
        gl.glNormal3d(0,-1,0); gl.glVertex3d(x0,y0,z1); gl.glVertex3d(x1,y0,z1); gl.glVertex3d(x1,y0,z0); gl.glVertex3d(x0,y0,z0);
        gl.glEnd();
    }

    private void dibujarCajaPared(GL2 gl, SceneObject so) {
        double w = so.targetW;
        double h = so.targetH;
        double d = so.targetD;
        double x0 = -w/2.0, x1 = w/2.0;
        double y0 = 0.0,   y1 = h;
        double z0 = -d/2.0, z1 = d/2.0;
        gl.glPushMatrix();
        gl.glTranslated(so.posX, so.posY, so.posZ);
        if (so.rotY != 0) gl.glRotated(so.rotY, 0, 1, 0);
        gl.glBegin(GL2.GL_QUADS);
        gl.glNormal3d(0,0,1);  gl.glVertex3d(x0,y0,z1); gl.glVertex3d(x1,y0,z1); gl.glVertex3d(x1,y1,z1); gl.glVertex3d(x0,y1,z1);
        gl.glNormal3d(0,0,-1); gl.glVertex3d(x1,y0,z0); gl.glVertex3d(x0,y0,z0); gl.glVertex3d(x0,y1,z0); gl.glVertex3d(x1,y1,z0);
        gl.glNormal3d(1,0,0);  gl.glVertex3d(x1,y0,z1); gl.glVertex3d(x1,y0,z0); gl.glVertex3d(x1,y1,z0); gl.glVertex3d(x1,y1,z1);
        gl.glNormal3d(-1,0,0); gl.glVertex3d(x0,y0,z0); gl.glVertex3d(x0,y0,z1); gl.glVertex3d(x0,y1,z1); gl.glVertex3d(x0,y1,z0);
        gl.glNormal3d(0,1,0);  gl.glVertex3d(x0,y1,z0); gl.glVertex3d(x1,y1,z0); gl.glVertex3d(x1,y1,z1); gl.glVertex3d(x0,y1,z1);
        gl.glNormal3d(0,-1,0); gl.glVertex3d(x0,y0,z1); gl.glVertex3d(x1,y0,z1); gl.glVertex3d(x1,y0,z0); gl.glVertex3d(x0,y0,z0);
        gl.glEnd();
        gl.glPopMatrix();
    }

    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {
        GL2 gl=d.getGL().getGL2(); if(h==0)h=1;
        gl.glViewport(0,0,w,h); gl.glMatrixMode(GL2.GL_PROJECTION); gl.glLoadIdentity();
        glu.gluPerspective(45,(double)w/h,0.05,300); gl.glMatrixMode(GL2.GL_MODELVIEW);
    }
    @Override public void dispose(GLAutoDrawable d) {}

    void updateMovement() {
        if (!fpsMode) return;
        double speed = keysDown.contains(KeyEvent.VK_SHIFT) ? 0.12 : 0.07;
        double rad   = Math.toRadians(fpsYaw);
        double sinY  = Math.sin(rad), cosY = Math.cos(rad);
        double dx=0, dz=0;
        if (keysDown.contains(KeyEvent.VK_W)) { dx+=sinY; dz+=cosY; }
        if (keysDown.contains(KeyEvent.VK_S)) { dx-=sinY; dz-=cosY; }
        if (keysDown.contains(KeyEvent.VK_A)) { dx-=cosY; dz+=sinY; }
        if (keysDown.contains(KeyEvent.VK_D)) { dx+=cosY; dz-=sinY; }
        double nx = fpsPosX + dx*speed;
        double nz = fpsPosZ + dz*speed;
        if (!hayColision(nx, fpsPosZ)) fpsPosX = nx;
        if (!hayColision(fpsPosX, nz)) fpsPosZ = nz;

        // Animación de puertas
        for (SceneObject so : sceneObjects) {
            if (!so.isDoor) continue;
            double yr = Math.toRadians(so.rotY);
            double dcX = so.posX - (so.targetW / 2.0) * Math.cos(yr);
            double dcZ = so.posZ + (so.targetW / 2.0) * Math.sin(yr);
            double ddx = fpsPosX - dcX, ddz = fpsPosZ - dcZ;
            if (Math.sqrt(ddx*ddx + ddz*ddz) < 1.1) {
                double faceNx = Math.sin(yr), faceNz = Math.cos(yr);
                double lado = ddx * faceNx + ddz * faceNz;
                so.doorTarget = (lado >= 0) ? 85f : -85f;
                so.doorCloseDelay = 90;
            } else if (so.doorCloseDelay > 0) {
                so.doorCloseDelay--;
            } else {
                so.doorTarget = 0f;
            }
            float diff = so.doorTarget - so.doorAngle;
            so.doorAngle += diff * 0.12f;
            if (Math.abs(diff) < 0.2f) so.doorAngle = so.doorTarget;
        }
    }

    private boolean hayColision(double x, double z) {
        double radio = 0.3;
        if (estaEnPuerta(x, z, radio)) return false;
        int sx1=(int)((x-radio)/(CELL/2)), sz1=(int)((z-radio)/(CELL/2));
        int sx2=(int)((x+radio)/(CELL/2)), sz2=(int)((z+radio)/(CELL/2));
        for (int sz=sz1;sz<=sz2;sz++) for (int sx=sx1;sx<=sx2;sx++) {
            if (sz<0||sz>=SH||sx<0||sx>=SW) return true;
            if (subgrid[sz][sx]==P) return true;
        }
        if (colisionParedesDelgadas(x, z, radio)) return true;
        if (colisionParedesColocadas(x, z, radio)) return true;
        return false;
    }

    private boolean colisionParedesDelgadas(double x, double z, double r) {
        double hit = r + WT / 2.0;
        double hit2 = hit * hit;
        for (int y=0;y<=GH;y++) {
            int xS=-1;
            for (int xg=0;xg<=GW;xg++) {
                boolean a=(xg<GW)&&paredesH[y][xg];
                if(a&&xS<0) xS=xg;
                else if(!a&&xS>=0){
                    double x1 = xS * CELL, z1 = y * CELL;
                    double x2 = xg * CELL, z2 = y * CELL;
                    if (distPointToSegmentSq(x, z, x1, z1, x2, z2) <= hit2) return true;
                    xS=-1;
                }
            }
        }
        for (int xg=0;xg<=GW;xg++) {
            int yS=-1;
            for (int y=0;y<=GH;y++) {
                boolean a=(y<GH)&&paredesV[y][xg];
                if(a&&yS<0) yS=y;
                else if(!a&&yS>=0){
                    double x1 = xg * CELL, z1 = yS * CELL;
                    double x2 = xg * CELL, z2 = y * CELL;
                    if (distPointToSegmentSq(x, z, x1, z1, x2, z2) <= hit2) return true;
                    yS=-1;
                }
            }
        }
        return false;
    }

    private boolean colisionParedesColocadas(double x, double z, double r) {
        for (SceneObject so : sceneObjects) {
            if (!so.wallMaterial) continue;
            if (so.posY >= PLAYER_HEIGHT || so.posY + so.targetH <= 0.05) continue;
            double w = so.targetW;
            double d = so.targetD;
            double halfX = isRotated90((int) so.rotY) ? d / 2.0 : w / 2.0;
            double halfZ = isRotated90((int) so.rotY) ? w / 2.0 : d / 2.0;
            if (Math.abs(x - so.posX) <= halfX + r && Math.abs(z - so.posZ) <= halfZ + r) return true;
        }
        return false;
    }

    private static double distPointToSegmentSq(double px, double pz, double x1, double z1, double x2, double z2) {
        double vx = x2 - x1;
        double vz = z2 - z1;
        double wx = px - x1;
        double wz = pz - z1;
        double c1 = wx * vx + wz * vz;
        if (c1 <= 0) return wx * wx + wz * wz;
        double c2 = vx * vx + vz * vz;
        if (c2 <= c1) {
            double dx = px - x2;
            double dz = pz - z2;
            return dx * dx + dz * dz;
        }
        double t = c1 / c2;
        double bx = x1 + t * vx;
        double bz = z1 + t * vz;
        double dx = px - bx;
        double dz = pz - bz;
        return dx * dx + dz * dz;
    }

    private static boolean isRotated90(int rotY) {
        int r = ((rotY % 360) + 360) % 360;
        return r == 90 || r == 270;
    }

    static boolean tieneHojaPuerta(String path) {
        String n = new File(path).getName().toLowerCase(Locale.ROOT);
        return (n.startsWith("doorway") && !n.contains("open")) || n.startsWith("door.");
    }

    private boolean estaEnPuerta(double x, double z, double r) {
        for (SceneObject so : sceneObjects) {
            if (!so.isDoor) continue;
            double halfX = isRotated90((int) so.rotY) ? so.targetD / 2.0 : so.targetW / 2.0;
            double halfZ = isRotated90((int) so.rotY) ? so.targetW / 2.0 : so.targetD / 2.0;
            if (Math.abs(x - so.posX) <= halfX + r && Math.abs(z - so.posZ) <= halfZ + r) return true;
        }
        return false;
    }

    private void entrarFPS() {
        fpsMode=true; mouseCaptured=true;
        if (panel!=null) panel.setCursor(panel.getToolkit().createCustomCursor(
            new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB), new Point(), "invisible"));
        centerMouse();
    }
    private void salirFPS() {
        fpsMode=false; mouseCaptured=false;
        if (panel!=null) panel.setCursor(Cursor.getDefaultCursor());
    }
    private void centerMouse() {
        if (robot==null||panel==null) return;
        Point c=new Point(panel.getWidth()/2, panel.getHeight()/2);
        SwingUtilities.convertPointToScreen(c, panel);
        robot.mouseMove(c.x, c.y);
    }

    @Override public void mousePressed (MouseEvent e) { mx=e.getX(); my=e.getY(); }
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseClicked (MouseEvent e) { if (!fpsMode) entrarFPS(); }
    @Override public void mouseEntered (MouseEvent e) {}
    @Override public void mouseExited  (MouseEvent e) {}

    @Override
    public void mouseDragged(MouseEvent e) {
        if (fpsMode) return;
        camAngH += (e.getX()-mx)*0.5;
        camAngV  = Math.max(5,Math.min(85, camAngV+(e.getY()-my)*0.3));
        mx=e.getX(); my=e.getY();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (!fpsMode||!mouseCaptured||robot==null||panel==null) return;
        if (justRecentered) { justRecentered=false; return; }
        int cx=panel.getWidth()/2, cy=panel.getHeight()/2;
        int dx=e.getX()-cx, dy=e.getY()-cy;
        if (dx==0&&dy==0) return;
        fpsYaw   += dx*0.2;
        fpsPitch  = Math.max(-85,Math.min(85, fpsPitch-dy*0.2));
        justRecentered=true;
        centerMouse();
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (!fpsMode) {
            camDist = Math.max(5, Math.min(80, camDist + e.getWheelRotation() * 1.5));
        }
    }

    @Override public void keyTyped  (KeyEvent e) {}
    @Override public void keyReleased(KeyEvent e) { keysDown.remove(e.getKeyCode()); }

    @Override
    public void keyPressed(KeyEvent e) {
        keysDown.add(e.getKeyCode());
        int k = e.getKeyCode();
        if (k==KeyEvent.VK_TAB)    { if(fpsMode) salirFPS(); else entrarFPS(); }
        if (k==KeyEvent.VK_ESCAPE) { if(fpsMode) salirFPS(); }
        // Vistas predefinidas 1-5 (salen de FPS si hace falta)
        if (k==KeyEvent.VK_1) { if(fpsMode) salirFPS(); camAngH=  0; camAngV=28; camDist=40; }
        if (k==KeyEvent.VK_2) { if(fpsMode) salirFPS(); camAngH= 90; camAngV=28; camDist=40; }
        if (k==KeyEvent.VK_3) { if(fpsMode) salirFPS(); camAngH=180; camAngV=28; camDist=40; }
        if (k==KeyEvent.VK_4) { if(fpsMode) salirFPS(); camAngH=270; camAngV=28; camDist=40; }
        if (k==KeyEvent.VK_5) { if(fpsMode) salirFPS(); camAngH=  0; camAngV=82; camDist=55; }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GLProfile  pr    = GLProfile.get(GLProfile.GL2);
            final GLJPanel panel = createPanel(pr);
            Casa3D_SinEdicion app = new Casa3D_SinEdicion();
            app.panel = panel;
            try { app.robot = new Robot(); } catch (Exception ex) { System.err.println("Robot no disponible: "+ex.getMessage()); }

            panel.addGLEventListener(app);
            panel.addMouseMotionListener(app);
            panel.addMouseListener(app);
            panel.addMouseWheelListener(app);
            panel.addKeyListener(app);
            panel.setFocusable(true);
            panel.setFocusTraversalKeysEnabled(false);

            JFrame f = new JFrame("Casa 3D (Sin Edicion)");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setSize(1280, 720);
            f.add(panel);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            panel.requestFocusInWindow();

            new javax.swing.Timer(16, e -> { app.updateMovement(); panel.display(); }).start();
        });
    }

    private static GLJPanel createPanel(GLProfile pr) {
        try {
            GLCapabilities caps = new GLCapabilities(pr);
            caps.setSampleBuffers(true);
            caps.setNumSamples(4);
            return new GLJPanel(caps);
        } catch (Exception ex) {
            return new GLJPanel(new GLCapabilities(pr));
        }
    }
}
