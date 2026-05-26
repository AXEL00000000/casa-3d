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

public class Casa3D implements GLEventListener,
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

    // Panel y robot para captura de mouse
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
    private static final double WALL_SNAP = 0.5;
    private static final double WALL_EDGE_SNAP = 0.2;
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
    private int     dlPisos = -1, dlParedesD = -1, dlParedesG = -1, dlParedesMerged = -1;
    private boolean dlCompiladas = false;
    private boolean wallMeshDirty = true;
    private TextRenderer textRenderer;

    // ── Menú de muebles ──────────────────────────────────────────────────────
    private boolean menuMuebles  = false;
    private int     modeloIdx    = 0;
    private int     colocarRotY  = 0;
    private volatile SceneObject pendingDelete = null;

    // ── Previsualización (ghost) ─────────────────────────────────────────────
    private SceneObject previewSO      = null;
    private int         previewDl      = -1;
    private int         lastPreviewIdx = -2;
    private double      psX = 1.0, psY = 1.0, psZ = 1.0;  // escala por eje
    private int         scaleAxis      = 3;  // 0=X 1=Y 2=Z 3=XYZ (uniforme)
    private double      colocarOffsetY = 0.0;
    private int         placedCounter  = 0;
    private static final String[] MODELOS = {
        // ── Pared personalizada (geometría interna) ──
        "pared_plana",
        // Sofás y sillones
        "loungeSofa","loungeSofaLong","loungeSofaCorner","loungeSofaOttoman",
        "loungeChair","loungeChairRelax","loungeDesignChair","loungeDesignSofa","loungeDesignSofaCorner",
        // Mesas
        "table","tableRound","tableGlass","tableCross","tableCrossCloth","tableCloth",
        "tableCoffee","tableCoffeeGlass","tableCoffeeGlassSquare","tableCoffeeSquare",
        // Sillas
        "chair","chairCushion","chairModernCushion","chairModernFrameCushion","chairRounded","chairDesk",
        "stoolBar","stoolBarSquare","bench","benchCushion","benchCushionLow",
        // Camas y dormitorio
        "bedDouble","bedSingle","bedBunk",
        "cabinetBed","cabinetBedDrawer","cabinetBedDrawerTable",
        "pillow","pillowBlue","pillowLong","pillowBlueLong",
        // Televisión y gabinetes
        "cabinetTelevision","cabinetTelevisionDoors",
        "televisionModern","televisionAntenna","televisionVintage",
        // Librerías y almacenamiento
        "bookcaseClosed","bookcaseClosedDoors","bookcaseClosedWide","bookcaseOpen","bookcaseOpenLow",
        "books","cardboardBoxClosed","cardboardBoxOpen","trashcan",
        // Escritorios y tecnología
        "desk","deskCorner","computerScreen","computerKeyboard","computerMouse","laptop",
        // Cocina
        "kitchenCabinet","kitchenCabinetDrawer","kitchenCabinetCornerInner","kitchenCabinetCornerRound",
        "kitchenCabinetUpper","kitchenCabinetUpperCorner","kitchenCabinetUpperDouble","kitchenCabinetUpperLow",
        "kitchenBar","kitchenBarEnd",
        "kitchenStove","kitchenStoveElectric","kitchenSink","kitchenFridge",
        "kitchenFridgeBuiltIn","kitchenFridgeLarge","kitchenFridgeSmall",
        "kitchenMicrowave","kitchenBlender","kitchenCoffeeMachine",
        "hoodLarge","hoodModern",
        // Baño
        "bathtub","bathroomSink","bathroomSinkSquare","bathroomCabinet","bathroomCabinetDrawer",
        "bathroomMirror","toilet","toiletSquare","shower","showerRound",
        // Lavandería
        "washer","dryer","washerDryerStacked",
        // Lámparas
        "lampRoundFloor","lampSquareFloor","lampRoundTable","lampSquareTable","lampWall","lampSquareCeiling",
        "ceilingFan",
        // Plantas y decoración
        "plantSmall1","plantSmall2","plantSmall3","pottedPlant",
        "rugRectangle","rugRound","rugRounded","rugSquare","rugDoormat",
        "bear","radio","speaker","speakerSmall","toaster",
        // Auxiliares y varios
        "sideTable","sideTableDrawers","coatRack","coatRackStanding",
        // Escaleras y estructura
        "stairs","stairsCorner","stairsOpen","stairsOpenSingle",
        // Paredes y pisos (para decorar)
        "wall","wallCorner","wallCornerRond","wallDoorway","wallDoorwayWide",
        "wallHalf","wallWindow","wallWindowSlide","doorway","doorwayFront","doorwayOpen",
        "floorFull","floorHalf","floorCorner","floorCornerRound","paneling",
        // Vehículos
        "carro","carroBlanco"
    };

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
        void render(GL2 gl,float[]colorOverride){
            render(gl, colorOverride, true);
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
            // caja unitaria centrada en X/Z, base en Y=0
            verts.add(new float[]{-0.5f,0,-0.5f}); // 0
            verts.add(new float[]{ 0.5f,0,-0.5f}); // 1
            verts.add(new float[]{ 0.5f,1,-0.5f}); // 2
            verts.add(new float[]{-0.5f,1,-0.5f}); // 3
            verts.add(new float[]{-0.5f,0, 0.5f}); // 4
            verts.add(new float[]{ 0.5f,0, 0.5f}); // 5
            verts.add(new float[]{ 0.5f,1, 0.5f}); // 6
            verts.add(new float[]{-0.5f,1, 0.5f}); // 7
            norms.add(new float[]{ 0, 0,-1}); // 0 frente
            norms.add(new float[]{ 0, 0, 1}); // 1 atrás
            norms.add(new float[]{-1, 0, 0}); // 2 izq
            norms.add(new float[]{ 1, 0, 0}); // 3 der
            norms.add(new float[]{ 0, 1, 0}); // 4 arriba
            texs.add(new float[]{0,0}); texs.add(new float[]{1,0});
            texs.add(new float[]{1,1}); texs.add(new float[]{0,1});
            Group g=new Group();
            // frente  (z=-0.5): 1,0,3,2
            g.faces.add(new Face(new int[]{1,0,3},new int[]{1,0,3},new int[]{0,0,0}));
            g.faces.add(new Face(new int[]{1,3,2},new int[]{1,3,2},new int[]{0,0,0}));
            // atrás   (z=+0.5): 4,5,6,7
            g.faces.add(new Face(new int[]{4,5,6},new int[]{0,1,2},new int[]{1,1,1}));
            g.faces.add(new Face(new int[]{4,6,7},new int[]{0,2,3},new int[]{1,1,1}));
            // izq     (x=-0.5): 0,4,7,3
            g.faces.add(new Face(new int[]{0,4,7},new int[]{0,1,2},new int[]{2,2,2}));
            g.faces.add(new Face(new int[]{0,7,3},new int[]{0,2,3},new int[]{2,2,2}));
            // der     (x=+0.5): 5,1,2,6
            g.faces.add(new Face(new int[]{5,1,2},new int[]{0,1,2},new int[]{3,3,3}));
            g.faces.add(new Face(new int[]{5,2,6},new int[]{0,2,3},new int[]{3,3,3}));
            // arriba  (y=1)   : 3,7,6,2
            g.faces.add(new Face(new int[]{3,7,6},new int[]{0,1,2},new int[]{4,4,4}));
            g.faces.add(new Face(new int[]{3,6,2},new int[]{0,2,3},new int[]{4,4,4}));
            // abajo   (y=0)   : 0,1,5,4
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
        int placedId = -1;  // -1 = objeto de código; ≥0 = colocado en runtime
        boolean isDoor = false;
        float doorAngle = 0f, doorTarget = 0f;
        int doorCloseDelay = 0;
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
        add(objPathParaNombre("carroBlanco"), 15.58, 0.00, 11.36,  0,180,0,  1.89,1.43,4.20);
        add(objPathParaNombre("carro"), 13.20, 0.00, 11.34,  0,180,0,  1.89,1.43,4.20);
        addDoor("modelos/Models/OBJ format/doorwayFront.obj", 10.44, 0.01, 12.89, 0,0,0, 0.90,1.92,0.12);
        add("modelos/Models/OBJ format/doorwayFront.obj", 9.45, 0.02, 27.92,  0,0,0,   0.90,1.92,0.12);
        add("modelos/Models/OBJ format/doorwayFront.obj", 12.98, 0.02, 40.96, 0,0,0,   0.90,1.92,0.12);
        add("modelos/Models/OBJ format/doorway.obj",      16.01, 0.02, 35.00, 0,0,0,   1.00,1.92,0.12);
        add("modelos/Models/OBJ format/doorway.obj",      12.97, 0.02, 34.09, 0,180,0, 1.00,1.92,0.12);
        add("modelos/Models/OBJ format/doorway.obj",      13.97, 0.03, 15.99, 0,270,0, 1.00,1.92,0.12);
        add("modelos/Models/OBJ format/doorway.obj",      13.99, 0.03, 17.92, 0,270,0, 1.00,1.92,0.12);
        add("modelos/Models/OBJ format/doorway.obj",      17.88, 0.03, 23.04, 0,0,0,   1.00,1.92,0.12);
        add("modelos/Models/OBJ format/stairs.obj", 11.49, 0.00, 18.88, 0,90,0, 3.10,2.40,1.60);
    }
    private void add(String p,double px,double py,double pz,double rx,double ry,double rz,double sx,double sy,double sz){
        sceneObjects.add(new SceneObject(p,px,py,pz,rx,ry,rz,sx,sy,sz));
    }
    private void addDoor(String p,double px,double py,double pz,double rx,double ry,double rz,double sx,double sy,double sz){
        SceneObject so=new SceneObject(p,px,py,pz,rx,ry,rz,sx,sy,sz);
        so.isDoor=true; sceneObjects.add(so);
    }
    private void addColor(String p,double px,double py,double pz,double rx,double ry,double rz,double sx,double sy,double sz,float[]c){
        SceneObject so=new SceneObject(p,px,py,pz,rx,ry,rz,sx,sy,sz);so.colorOverride=c;sceneObjects.add(so);
    }

    private static String resolveObjPath(String rel){
        if ("pared_plana".equals(rel)) return "pared_plana";
        for(String p:new String[]{"","src/","../"}){if(new File(p+rel).exists())return p+rel;}
        return "src/"+rel;
    }

    private static String objPathParaNombre(String nom) {
        if ("pared_plana".equals(nom))   return "pared_plana";
        if ("carro".equals(nom))         return "modelos/carrros/Car-Model/Car.obj";
        if ("carroBlanco".equals(nom))   return "modelos/carrros/Car-Model/CarBlanco.obj";
        return "modelos/Models/OBJ format/" + nom + ".obj";
    }

    private static boolean isCustomWall(String path) {
        String n = path.toLowerCase(Locale.ROOT);
        return n.contains("pared_plana");
    }

    private static double snapValue(double v, double step) {
        if (step <= 0.0) return v;
        return Math.round(v / step) * step;
    }

    private static boolean isRotated90(int rotY) {
        int r = ((rotY % 360) + 360) % 360;
        return r == 90 || r == 270;
    }

    private double[] snapToWallEdges(double x, double z, int rotY, double w, double d) {
        double halfX = isRotated90(rotY) ? d / 2.0 : w / 2.0;
        double halfZ = isRotated90(rotY) ? w / 2.0 : d / 2.0;
        double bestX = x, bestZ = z;
        double bestDx = WALL_EDGE_SNAP, bestDz = WALL_EDGE_SNAP;
        boolean snappedX = false, snappedZ = false;
        for (SceneObject so : sceneObjects) {
            if (!so.wallMaterial) continue;
            double soHalfX = isRotated90((int) so.rotY) ? so.targetD / 2.0 : so.targetW / 2.0;
            double soHalfZ = isRotated90((int) so.rotY) ? so.targetW / 2.0 : so.targetD / 2.0;
            double leftX = so.posX - soHalfX;
            double rightX = so.posX + soHalfX;
            double backZ = so.posZ - soHalfZ;
            double frontZ = so.posZ + soHalfZ;
            for (double edgeX : new double[]{leftX, rightX}) {
                double sign = (x >= edgeX) ? 1.0 : -1.0;
                double cand = edgeX + sign * halfX;
                double dist = Math.abs(x - cand);
                if (dist < bestDx) { bestDx = dist; bestX = cand; snappedX = true; }
            }
            for (double edgeZ : new double[]{backZ, frontZ}) {
                double sign = (z >= edgeZ) ? 1.0 : -1.0;
                double cand = edgeZ + sign * halfZ;
                double dist = Math.abs(z - cand);
                if (dist < bestDz) { bestDz = dist; bestZ = cand; snappedZ = true; }
            }
        }
        return new double[]{bestX, bestZ, snappedX ? 1.0 : 0.0, snappedZ ? 1.0 : 0.0};
    }

    // =========================================================================
    //  CONSTRUCTOR
    // =========================================================================
    public Casa3D() {
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

    // =========================================================================
    //  PISOS Y PAREDES — igual que antes
    // =========================================================================
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

    // =========================================================================
    //  OPENGL – INIT
    // =========================================================================
    @Override
    public void init(GLAutoDrawable d) {
        GL2 gl = d.getGL().getGL2();
        d.getGL().setSwapInterval(1); // VSync — limita a la frecuencia del monitor
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

    // =========================================================================
    //  OPENGL – DISPLAY
    // =========================================================================
    @Override
    public void display(GLAutoDrawable d) {
        GL2 gl = d.getGL().getGL2();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        if (fpsMode) {
            // Cámara primera persona
            double rad  = Math.toRadians(fpsYaw);
            double pit  = Math.toRadians(Math.max(-85, Math.min(85, fpsPitch)));
            double lx   = Math.cos(pit) * Math.sin(rad);
            double ly   = Math.sin(pit);
            double lz   = Math.cos(pit) * Math.cos(rad);
            glu.gluLookAt(fpsPosX, fpsPosY, fpsPosZ,
                          fpsPosX+lx, fpsPosY+ly, fpsPosZ+lz, 0,1,0);
        } else {
            // Cámara orbital
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
        if (wallMeshDirty) rebuildWallMesh(gl);
        if (dlParedesMerged > 0) gl.glCallList(dlParedesMerged); else dibujarParedesMerged(gl);

        dibujarObjetos(gl);

        if (fpsMode) dibujarGrid(gl);

        if (fpsMode && menuMuebles) {
            ensurePreview(gl);
            dibujarGhost(gl);
        }

        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        dibujarHUD(gl, w, h);
    }

    // Cuadrícula en el suelo cada 1 unidad para saber la escala
    private void dibujarGrid(GL2 gl) {
        gl.glDisable(GL2.GL_TEXTURE_2D);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glLineWidth(1f);
        gl.glBegin(GL2.GL_LINES);
        for (int x = 0; x <= GW; x++) {
            // línea cada 5 unidades más brillante
            if (x % 5 == 0) gl.glColor3f(0.6f, 0.6f, 0.9f);
            else             gl.glColor3f(0.3f, 0.3f, 0.5f);
            gl.glVertex3d(x * CELL, 0.01, 0);
            gl.glVertex3d(x * CELL, 0.01, GH * CELL);
        }
        for (int z = 0; z <= GH; z++) {
            if (z % 5 == 0) gl.glColor3f(0.6f, 0.6f, 0.9f);
            else             gl.glColor3f(0.3f, 0.3f, 0.5f);
            gl.glVertex3d(0,          0.01, z * CELL);
            gl.glVertex3d(GW * CELL,  0.01, z * CELL);
        }
        gl.glEnd();
        gl.glEnable(GL2.GL_LIGHTING);
    }

    private static double[] tamanoDefault(String n) {
        if (n.equals("pared_plana"))                         return new double[]{1.0, 2.8, 0.12};
        if (n.contains("Sofa")||n.contains("sofa"))   return new double[]{2.0,0.85,1.0};
        if (n.contains("Chair")||n.contains("chair")) return new double[]{0.7,0.9,0.7};
        if (n.contains("Coffee")||n.contains("coffee")) return new double[]{1.2,0.45,0.9};
        if (n.contains("table")||n.contains("Table")) return new double[]{1.5,0.78,1.0};
        if (n.contains("bed")||n.contains("Bed"))     return new double[]{2.0,0.75,1.5};
        if (n.contains("Fridge")||n.contains("fridge")) return new double[]{0.7,1.8,0.7};
        if (n.contains("kitchen")||n.contains("Kitchen")) return new double[]{0.6,0.9,0.6};
        if (n.contains("lamp")||n.contains("Lamp"))   return new double[]{0.3,1.5,0.3};
        if (n.contains("plant")||n.contains("Plant")) return new double[]{0.4,0.6,0.4};
        if (n.contains("bathroom")||n.contains("Bathroom")) return new double[]{0.6,0.85,0.5};
        if (n.contains("cabinet")||n.contains("Cabinet")) return new double[]{0.9,1.2,0.4};
        if (n.contains("bookcase")||n.contains("Bookcase")) return new double[]{0.9,1.5,0.4};
        if (n.contains("stool")||n.contains("Stool")) return new double[]{0.4,0.7,0.4};
        if (n.contains("television")||n.contains("Television")) return new double[]{1.2,0.7,0.1};
        if (n.contains("toilet")||n.contains("Toilet")) return new double[]{0.5,0.85,0.5};
        if (n.contains("washer")||n.contains("dryer")) return new double[]{0.6,0.85,0.6};
        if (n.contains("desk")||n.contains("Desk"))   return new double[]{1.2,0.75,0.6};
        if (n.contains("bathtub"))                           return new double[]{1.7,0.6,0.8};
        if (n.contains("shower")||n.contains("Shower"))      return new double[]{0.9,2.0,0.9};
        if (n.contains("pillow")||n.contains("Pillow"))      return new double[]{0.6,0.15,0.4};
        if (n.contains("rug")||n.contains("Rug"))            return new double[]{2.0,0.02,2.0};
        if (n.contains("stairs")||n.contains("Stairs"))      return new double[]{1.0,2.4,1.0};
        if (n.contains("wall")||n.contains("Wall"))          return new double[]{1.0,2.8,0.12};
        if (n.contains("floor")||n.contains("Floor"))        return new double[]{1.0,0.1,1.0};
        if (n.contains("door")||n.contains("Door"))          return new double[]{1.0,2.4,0.12};
        if (n.contains("panel")||n.contains("Panel"))        return new double[]{1.0,2.8,0.05};
        if (n.contains("ceiling")||n.contains("Ceiling"))    return new double[]{0.8,0.2,0.8};
        if (n.contains("hood")||n.contains("Hood"))          return new double[]{0.6,0.4,0.6};
        if (n.contains("bar")||n.contains("Bar"))            return new double[]{1.0,0.9,0.6};
        if (n.contains("trashcan")||n.contains("Trashcan"))  return new double[]{0.35,0.5,0.35};
        if (n.contains("toaster")||n.contains("Toaster"))    return new double[]{0.3,0.2,0.2};
        if (n.contains("bear")||n.contains("Bear"))          return new double[]{0.3,0.35,0.2};
        if (n.contains("books")||n.contains("Books"))        return new double[]{0.4,0.25,0.15};
        if (n.contains("cardboard")||n.contains("Cardboard"))return new double[]{0.4,0.35,0.4};
        if (n.contains("blender")||n.contains("Blender"))    return new double[]{0.2,0.38,0.2};
        if (n.contains("coffee")||n.contains("Coffee"))      return new double[]{0.25,0.35,0.2};
        if (n.contains("mouse")||n.contains("Mouse"))        return new double[]{0.12,0.04,0.07};
        if (n.contains("fan")||n.contains("Fan"))            return new double[]{0.6,0.15,0.6};
        if (n.equals("carro")||n.equals("carroBlanco"))      return new double[]{2.1,1.59,4.67};
        return new double[]{1.0,1.0,1.0};
    }

    // Posición del ghost: 2 u enfrente del jugador, en el suelo
    private double[] ghostPos() {
        double rad = Math.toRadians(fpsYaw);
        return new double[]{ fpsPosX + Math.sin(rad) * 2.0, fpsPosZ + Math.cos(rad) * 2.0 };
    }

    private void ensurePreview(GL2 gl) {
        if (lastPreviewIdx == modeloIdx && previewSO != null) return;
        if (previewDl != -1) { gl.glDeleteLists(previewDl, 1); previewDl = -1; }
        lastPreviewIdx = modeloIdx;
        String nom = MODELOS[modeloIdx];
        double[] sz = tamanoDefault(nom);
        String rutaPreview = objPathParaNombre(nom);
        previewSO = new SceneObject(rutaPreview, 0, 0, 0,  0, colocarRotY, 0,  sz[0], sz[1], sz[2]);
        if (nom.equals("pared_plana")) previewSO.colorOverride = COLORES[P].clone();
        Thread t = new Thread(() -> {
            previewSO.model.load(resolveObjPath(previewSO.objPath));
            previewSO.computeScale();
        }, "Preview");
        t.setDaemon(true); t.start();
    }

    private void dibujarGhost(GL2 gl) {
        if (previewSO == null || !previewSO.model.loaded) return;
        // Compilar DL la primera vez que el modelo esté cargado
        if (previewDl == -1) {
            previewDl = gl.glGenLists(1);
            gl.glNewList(previewDl, GL2.GL_COMPILE);
            boolean wallPreview = previewSO.wallMaterial;
            if (wallPreview) applyWallMaterial(gl);
            previewSO.model.render(gl, new float[]{0.2f, 0.8f, 1.0f}, !wallPreview);
            if (wallPreview) restoreColorMaterial(gl);
            gl.glEndList();
        }
        double[] gp = ghostPos();
        gl.glPushMatrix();
        gl.glTranslated(gp[0] + previewSO.baseOffsetX * psX,
                        previewSO.baseOffsetY * psY + colocarOffsetY,
                        gp[1] + previewSO.baseOffsetZ * psZ);
        if (colocarRotY != 0) gl.glRotated(colocarRotY, 0, 1, 0);
        gl.glScaled(previewSO.scaleX * psX,
                    previewSO.scaleY * psY,
                    previewSO.scaleZ * psZ);
        gl.glDisable(GL2.GL_TEXTURE_2D);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glColor3f(0.2f, 0.85f, 1.0f);
        gl.glLineWidth(1.5f);
        gl.glPolygonMode(GL2.GL_FRONT_AND_BACK, GL2.GL_LINE);
        gl.glCallList(previewDl);
        gl.glPolygonMode(GL2.GL_FRONT_AND_BACK, GL2.GL_FILL);
        gl.glLineWidth(1f);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glPopMatrix();
    }

    // HUD: retícula, posición y ayuda
    private void dibujarHUD(GL2 gl, int w, int h) {
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glDisable(GL2.GL_DEPTH_TEST);
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPushMatrix(); gl.glLoadIdentity();
        glu.gluOrtho2D(0, w, 0, h);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPushMatrix(); gl.glLoadIdentity();

        // helper inline: rect semitransparente
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);

        if (fpsMode) {
            // Retícula central
            int cx = w/2, cy = h/2, s = 14;
            gl.glColor4f(1f,1f,1f,1f);
            gl.glLineWidth(2f);
            gl.glBegin(GL2.GL_LINES);
            gl.glVertex2i(cx-s,cy); gl.glVertex2i(cx+s,cy);
            gl.glVertex2i(cx,cy-s); gl.glVertex2i(cx,cy+s);
            gl.glEnd(); gl.glLineWidth(1f);

            // Panel superior — barra de estado
            gl.glColor4f(0f,0f,0f,0.60f);
            gl.glBegin(GL2.GL_QUADS);
            gl.glVertex2i(0,h); gl.glVertex2i(w,h);
            gl.glVertex2i(w,h-58); gl.glVertex2i(0,h-58);
            gl.glEnd();

            if (menuMuebles) {
                int panX   = w - 290;
                int visibles = 14;
                int headerH  = 6 * 22 + 14;   // 6 líneas de instrucciones
                int listH    = visibles * 22 + 26;
                int panH     = headerH + listH;

                // Panel izquierdo — instrucciones
                gl.glColor4f(0f,0f,0f,0.72f);
                gl.glBegin(GL2.GL_QUADS);
                gl.glVertex2i(0,    h); gl.glVertex2i(panX-10, h);
                gl.glVertex2i(panX-10, h-58); gl.glVertex2i(0, h-58);
                gl.glEnd();
                // Panel derecho — lista de modelos
                gl.glBegin(GL2.GL_QUADS);
                gl.glVertex2i(panX-10, h); gl.glVertex2i(w, h);
                gl.glVertex2i(w, h-panH); gl.glVertex2i(panX-10, h-panH);
                gl.glEnd();

                gl.glDisable(GL2.GL_BLEND);
                if (textRenderer != null) {
                    int inicio = Math.max(0, modeloIdx - visibles/2);
                    int fin    = Math.min(MODELOS.length, inicio + visibles);
                    String flechaRot = colocarRotY==0?"→":colocarRotY==45?"↗":colocarRotY==90?"↑":
                                       colocarRotY==135?"↖":colocarRotY==180?"←":colocarRotY==225?"↙":
                                       colocarRotY==270?"↓":"↘";
                    String ejeActivo = new String[]{"X","Y","Z","XYZ"}[scaleAxis];

                    textRenderer.beginRendering(w, h);

                    // Barra superior izquierda — posición + controles base
                    textRenderer.setColor(1f,1f,0.2f,1f);
                    textRenderer.draw(String.format("X=%.1f  Z=%.1f", fpsPosX, fpsPosZ), 10, h-22);
                    textRenderer.setColor(0.85f,0.85f,0.85f,1f);
                    textRenderer.draw("[WASD] mover   [Tab] orbital   [Esc] salir FPS", 10, h-44);

                    // Panel derecho — título
                    textRenderer.setColor(0.3f,1f,1f,1f);
                    textRenderer.draw("=== MUEBLES ===", panX, h-22);

                    // Instrucciones línea a línea
                    textRenderer.setColor(0.9f,0.9f,0.9f,1f);
                    textRenderer.draw("[↑↓]  navegar lista", panX, h-46);
                    textRenderer.draw("[F]   cerrar menu", panX, h-68);
                    textRenderer.setColor(1f,0.85f,0.2f,1f);
                    textRenderer.draw("[P]   colocar objeto", panX, h-90);
                    textRenderer.draw("[X]   eliminar cercano", panX, h-112);
                    textRenderer.setColor(0.6f,1f,0.6f,1f);
                    textRenderer.draw(String.format("[R]   girar: %s %d°", flechaRot, colocarRotY), panX, h-134);
                    textRenderer.setColor(0.6f,0.8f,1f,1f);
                    textRenderer.draw(String.format("[G]   eje escala: %s", ejeActivo), panX, h-156);
                    textRenderer.draw(String.format("[Rueda/[]] X:%.1f Y:%.1f Z:%.1f", psX,psY,psZ), panX, h-178);
                    textRenderer.setColor(1f,0.6f,0.6f,1f);
                    textRenderer.draw(String.format("[Q/Z] altura: %.2f", colocarOffsetY), panX, h-200);

                    // Separador lista
                    textRenderer.setColor(0.5f,0.5f,0.5f,1f);
                    textRenderer.draw("─────────────────", panX, h-218);

                    // Lista de modelos
                    for (int i=inicio; i<fin; i++) {
                        int lineY = h - 240 - (i-inicio)*22;
                        if (i==modeloIdx) { textRenderer.setColor(0.1f,1f,0.1f,1f); textRenderer.draw("► "+MODELOS[i], panX, lineY); }
                        else              { textRenderer.setColor(0.8f,0.8f,0.8f,1f); textRenderer.draw("  "+MODELOS[i], panX, lineY); }
                    }
                    textRenderer.setColor(0.5f,0.5f,0.5f,1f);
                    textRenderer.draw(String.format("%d / %d", modeloIdx+1, MODELOS.length), panX, h-240-visibles*22);
                    textRenderer.endRendering();
                }
            } else {
                gl.glDisable(GL2.GL_BLEND);
                if (textRenderer != null) {
                    textRenderer.beginRendering(w, h);
                    textRenderer.setColor(1f,1f,0.2f,1f);
                    textRenderer.draw(String.format("X=%.1f  Z=%.1f  |  [F] muebles  [P] colocar  [X] eliminar", fpsPosX, fpsPosZ), 10, h-24);
                    textRenderer.setColor(0.85f,0.85f,0.85f,1f);
                    textRenderer.draw("[WASD] mover   [Tab] camara orbital   [Esc] salir FPS", 10, h-46);
                    textRenderer.endRendering();
                }
            }
        } else {
            // Panel inferior — modo orbital
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
        if (pendingDelete != null) {
            SceneObject del = pendingDelete; pendingDelete = null;
            if (del.dlId > 0) gl.glDeleteLists(del.dlId, 1);
            sceneObjects.remove(del);
            if (del.wallMaterial) wallMeshDirty = true;
        }
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
                if (so.wallMaterial) applyWallMaterial(gl);
                so.model.render(gl, so.colorOverride, !so.wallMaterial);
                if (so.wallMaterial) restoreColorMaterial(gl);
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
        wallMeshDirty = false;
    }


    // =========================================================================
    //  TEXTURA / COLOR
    // =========================================================================
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


    // =========================================================================
    //  DIBUJO DE PISOS
    // =========================================================================
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

    // =========================================================================
    //  DIBUJO DE PAREDES DELGADAS
    // =========================================================================
    private void dibujarParedesDelgadas(GL2 gl) {
        applyWallMaterial(gl);
        for (int y=0;y<=GH;y++) { int xS=-1; for (int x=0;x<=GW;x++) { boolean a=(x<GW)&&paredesH[y][x]; if(a&&xS<0)xS=x; else if(!a&&xS>=0){ dibujarSegmentoPared(gl,xS*CELL,0,y*CELL,x*CELL,0,y*CELL,WT,WH); xS=-1; } } }
        for (int x=0;x<=GW;x++) { int yS=-1; for (int y=0;y<=GH;y++) { boolean a=(y<GH)&&paredesV[y][x]; if(a&&yS<0)yS=y; else if(!a&&yS>=0){ dibujarSegmentoPared(gl,x*CELL,0,yS*CELL,x*CELL,0,y*CELL,WT,WH); yS=-1; } } }
        restoreColorMaterial(gl);
    }

    private void dibujarSegmentoPared(GL2 gl,double x1,double y1,double z1,double x2,double y2,double z2,double grosor,double alto) {
        double dx=x2-x1,dz=z2-z1,len=Math.sqrt(dx*dx+dz*dz); if(len<0.001)return;
        double nx=dz/len*grosor/2, nz=-dx/len*grosor/2;
        quad(gl, dx/len*0,dz/len*1,-dx/len, x1+nx,0,z1+nz, x2+nx,0,z2+nz, x2+nx,alto,z2+nz, x1+nx,alto,z1+nz, len,alto);
        quad(gl,-dz/len,0, dx/len, x2-nx,0,z2-nz, x1-nx,0,z1-nz, x1-nx,alto,z1-nz, x2-nx,alto,z2-nz, len,alto);
        gl.glBegin(GL2.GL_QUADS); gl.glNormal3d(0,1,0);
        gl.glTexCoord2d(0,0);gl.glVertex3d(x1+nx,alto,z1+nz); gl.glTexCoord2d(len,0);gl.glVertex3d(x2+nx,alto,z2+nz);
        gl.glTexCoord2d(len,grosor);gl.glVertex3d(x2-nx,alto,z2-nz); gl.glTexCoord2d(0,grosor);gl.glVertex3d(x1-nx,alto,z1-nz); gl.glEnd();
        quad(gl,-dx/len,0,-dz/len, x1-nx,0,z1-nz, x1+nx,0,z1+nz, x1+nx,alto,z1+nz, x1-nx,alto,z1-nz, grosor,alto);
        quad(gl, dx/len,0, dz/len, x2+nx,0,z2+nz, x2-nx,0,z2-nz, x2-nx,alto,z2-nz, x2+nx,alto,z2+nz, grosor,alto);
    }

    private void quad(GL2 gl,double nx,double ny,double nz, double x0,double y0,double z0, double x1,double y1,double z1, double x2,double y2,double z2, double x3,double y3,double z3, double u,double v) {
        gl.glBegin(GL2.GL_QUADS); gl.glNormal3d(nx,ny,nz);
        gl.glTexCoord2d(0,0);gl.glVertex3d(x0,y0,z0); gl.glTexCoord2d(u,0);gl.glVertex3d(x1,y1,z1);
        gl.glTexCoord2d(u,v);gl.glVertex3d(x2,y2,z2); gl.glTexCoord2d(0,v);gl.glVertex3d(x3,y3,z3); gl.glEnd();
    }

    // =========================================================================
    //  DIBUJO DE PAREDES GRUESAS
    // =========================================================================
    private void dibujarParedesGruesas(GL2 gl) {
        applyWallMaterial(gl);
        boolean[][] vis=new boolean[SH][SW]; double ss=CELL/2.0;
        for (int sy=0;sy<SH;sy++) for (int sx=0;sx<SW;sx++) {
            if(subgrid[sy][sx]!=P||vis[sy][sx]) continue;
            int mx=sx; while(mx+1<SW&&subgrid[sy][mx+1]==P&&!vis[sy][mx+1]) mx++;
            int my=sy; outer: while(my+1<SH){for(int tx=sx;tx<=mx;tx++) if(subgrid[my+1][tx]!=P||vis[my+1][tx]) break outer; my++;}
            for(int ry=sy;ry<=my;ry++) for(int rx=sx;rx<=mx;rx++) vis[ry][rx]=true;
            dibujarCaja(gl,sx*ss,0,sy*ss,(mx+1)*ss,WH,(my+1)*ss);
        }
        restoreColorMaterial(gl);
    }

    private void dibujarParedesMerged(GL2 gl) {
        applyWallMaterial(gl);
        // Paredes delgadas
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
        // Paredes gruesas del subgrid
        boolean[][] vis=new boolean[SH][SW]; double ss=CELL/2.0;
        for (int sy=0;sy<SH;sy++) for (int sx=0;sx<SW;sx++) {
            if(subgrid[sy][sx]!=P||vis[sy][sx]) continue;
            int mx=sx; while(mx+1<SW&&subgrid[sy][mx+1]==P&&!vis[sy][mx+1]) mx++;
            int my=sy; outer: while(my+1<SH){for(int tx=sx;tx<=mx;tx++) if(subgrid[my+1][tx]!=P||vis[my+1][tx]) break outer; my++;}
            for(int ry=sy;ry<=my;ry++) for(int rx=sx;rx<=mx;rx++) vis[ry][rx]=true;
            dibujarCaja(gl,sx*ss,0,sy*ss,(mx+1)*ss,WH,(my+1)*ss);
        }
        // Paredes colocadas (pared_plana)
        for (SceneObject so : sceneObjects) {
            if (!so.wallMaterial) continue;
            dibujarCajaPared(gl, so);
        }
        restoreColorMaterial(gl);
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

    private void rebuildWallMesh(GL2 gl) {
        if (dlParedesMerged > 0) gl.glDeleteLists(dlParedesMerged, 1);
        dlParedesMerged = gl.glGenLists(1);
        gl.glNewList(dlParedesMerged, GL2.GL_COMPILE);
        dibujarParedesMerged(gl);
        gl.glEndList();
        wallMeshDirty = false;
    }

    private void dibujarCaja(GL2 gl,double x0,double y0,double z0,double x1,double y1,double z1) {
        double dx=x1-x0,dy=y1-y0,dz=z1-z0;
        gl.glBegin(GL2.GL_QUADS);
        gl.glNormal3d(0,0,1);  gl.glTexCoord2d(0,0);gl.glVertex3d(x0,y0,z1); gl.glTexCoord2d(dx,0);gl.glVertex3d(x1,y0,z1); gl.glTexCoord2d(dx,dy);gl.glVertex3d(x1,y1,z1); gl.glTexCoord2d(0,dy);gl.glVertex3d(x0,y1,z1);
        gl.glNormal3d(0,0,-1); gl.glTexCoord2d(0,0);gl.glVertex3d(x1,y0,z0); gl.glTexCoord2d(dx,0);gl.glVertex3d(x0,y0,z0); gl.glTexCoord2d(dx,dy);gl.glVertex3d(x0,y1,z0); gl.glTexCoord2d(0,dy);gl.glVertex3d(x1,y1,z0);
        gl.glNormal3d(1,0,0);  gl.glTexCoord2d(0,0);gl.glVertex3d(x1,y0,z1); gl.glTexCoord2d(dz,0);gl.glVertex3d(x1,y0,z0); gl.glTexCoord2d(dz,dy);gl.glVertex3d(x1,y1,z0); gl.glTexCoord2d(0,dy);gl.glVertex3d(x1,y1,z1);
        gl.glNormal3d(-1,0,0); gl.glTexCoord2d(0,0);gl.glVertex3d(x0,y0,z0); gl.glTexCoord2d(dz,0);gl.glVertex3d(x0,y0,z1); gl.glTexCoord2d(dz,dy);gl.glVertex3d(x0,y1,z1); gl.glTexCoord2d(0,dy);gl.glVertex3d(x0,y1,z0);
        gl.glNormal3d(0,1,0);  gl.glTexCoord2d(0,0);gl.glVertex3d(x0,y1,z0); gl.glTexCoord2d(dx,0);gl.glVertex3d(x1,y1,z0); gl.glTexCoord2d(dx,dz);gl.glVertex3d(x1,y1,z1); gl.glTexCoord2d(0,dz);gl.glVertex3d(x0,y1,z1);
        gl.glNormal3d(0,-1,0); gl.glTexCoord2d(0,0);gl.glVertex3d(x0,y0,z1); gl.glTexCoord2d(dx,0);gl.glVertex3d(x1,y0,z1); gl.glTexCoord2d(dx,dz);gl.glVertex3d(x1,y0,z0); gl.glTexCoord2d(0,dz);gl.glVertex3d(x0,y0,z0);
        gl.glEnd();
    }

    // =========================================================================
    //  RESHAPE / DISPOSE
    // =========================================================================
    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {
        GL2 gl=d.getGL().getGL2(); if(h==0)h=1;
        gl.glViewport(0,0,w,h); gl.glMatrixMode(GL2.GL_PROJECTION); gl.glLoadIdentity();
        glu.gluPerspective(45,(double)w/h,0.05,300); gl.glMatrixMode(GL2.GL_MODELVIEW);
    }
    @Override public void dispose(GLAutoDrawable d) {}

    // =========================================================================
    //  PRIMERA PERSONA — movimiento y colisión
    // =========================================================================
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

        // Altura: escaleras y gravedad simple
        double targetY = alturaEnPosicion(fpsPosX, fpsPosZ);
        fpsPosY += (targetY - fpsPosY) * 0.25; // suaviza subida/bajada

        // Animación de puertas
        for (SceneObject so : sceneObjects) {
            if (!so.isDoor) continue;
            // Centro de la hoja en mundo (bisagra + mitad del ancho en la dirección de la puerta)
            double yr = Math.toRadians(so.rotY);
            double dcX = so.posX - (so.targetW / 2.0) * Math.cos(yr);
            double dcZ = so.posZ + (so.targetW / 2.0) * Math.sin(yr);
            double ddx = fpsPosX - dcX, ddz = fpsPosZ - dcZ;
            if (Math.sqrt(ddx*ddx + ddz*ddz) < 1.1) {
                // Abrir hacia el lado contrario al jugador respecto a la cara de la puerta
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

    private boolean estaEnPuerta(double x, double z, double r) {
        for (SceneObject so : sceneObjects) {
            if (!esModeloPuerta(so.objPath)) continue;
            double halfX = isRotated90((int) so.rotY) ? so.targetD / 2.0 : so.targetW / 2.0;
            double halfZ = isRotated90((int) so.rotY) ? so.targetW / 2.0 : so.targetD / 2.0;
            if (Math.abs(x - so.posX) <= halfX + r && Math.abs(z - so.posZ) <= halfZ + r) return true;
        }
        return false;
    }

    private static boolean esModeloPuerta(String path) {
        String n = path.toLowerCase(Locale.ROOT);
        return n.contains("doorway");
    }

    // Solo modelos que tienen hoja giratoria real (no frames/open)
    static boolean tieneHojaPuerta(String path) {
        String n = new File(path).getName().toLowerCase(Locale.ROOT);
        return (n.startsWith("doorway") && !n.contains("open"))
            || n.startsWith("door.");
    }

    private double alturaEnPosicion(double x, double z) {
        for (SceneObject so : sceneObjects) {
            if (!new File(so.objPath).getName().toLowerCase(Locale.ROOT).startsWith("stair")) continue;
            // Zona de la escalera en mundo (rotY=90: W→Z, D→X)
            double yr  = Math.toRadians(so.rotY);
            double hw  = so.targetW / 2.0;   // mitad largo escalera
            double hd  = so.targetD / 2.0;   // mitad ancho escalera
            // Ejes locales de la escalera en mundo
            double axX =  Math.cos(yr), axZ = Math.sin(yr); // eje largo
            double azX = -Math.sin(yr), azZ = Math.cos(yr); // eje ancho
            double relX = x - so.posX, relZ = z - so.posZ;
            double along = relX * axX + relZ * axZ; // posición a lo largo
            double perp  = relX * azX + relZ * azZ; // posición a lo ancho
            if (Math.abs(along) <= hw && Math.abs(perp) <= hd) {
                // t=0 fondo, t=1 arriba; along va de -hw a +hw
                double t = (along + hw) / (2.0 * hw);
                return PLAYER_HEIGHT + so.targetH * Math.max(0, Math.min(1, t));
            }
        }
        return PLAYER_HEIGHT; // suelo normal
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

    // =========================================================================
    //  CAPTURA DE MOUSE PARA FPS
    // =========================================================================
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

    // =========================================================================
    //  EVENTOS DE MOUSE
    // =========================================================================
    @Override public void mousePressed (MouseEvent e) { mx=e.getX(); my=e.getY(); }
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseClicked (MouseEvent e) { if (!fpsMode) entrarFPS(); }
    @Override public void mouseEntered (MouseEvent e) {}
    @Override public void mouseExited  (MouseEvent e) {}

    @Override
    public void mouseDragged(MouseEvent e) {
        if (fpsMode) return; // en FPS el look lo hace mouseMoved
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
        if (fpsMode && menuMuebles) {
            double delta = -e.getWheelRotation() * 0.1;
            switch (scaleAxis) {
                case 0: psX = Math.max(0.1, Math.min(5.0, psX + delta)); break;
                case 1: psY = Math.max(0.1, Math.min(5.0, psY + delta)); break;
                case 2: psZ = Math.max(0.1, Math.min(5.0, psZ + delta)); break;
                default: psX = psY = psZ = Math.max(0.1, Math.min(5.0, psX + delta)); break;
            }
        } else if (!fpsMode) {
            camDist = Math.max(5, Math.min(80, camDist + e.getWheelRotation() * 1.5));
        }
    }

    // =========================================================================
    //  EVENTOS DE TECLADO
    // =========================================================================
    @Override public void keyTyped  (KeyEvent e) {}
    @Override public void keyReleased(KeyEvent e) { keysDown.remove(e.getKeyCode()); }

    @Override
    public void keyPressed(KeyEvent e) {
        keysDown.add(e.getKeyCode());
        int k = e.getKeyCode();
        if (k==KeyEvent.VK_TAB)    { if(fpsMode) salirFPS(); else entrarFPS(); }
        if (k==KeyEvent.VK_ESCAPE) { if(menuMuebles) menuMuebles=false; else if(fpsMode) salirFPS(); }
        // Vistas predefinidas 1-5 (salen de FPS si hace falta)
        if (k==KeyEvent.VK_1) { if(fpsMode) salirFPS(); camAngH=  0; camAngV=28; camDist=40; }
        if (k==KeyEvent.VK_2) { if(fpsMode) salirFPS(); camAngH= 90; camAngV=28; camDist=40; }
        if (k==KeyEvent.VK_3) { if(fpsMode) salirFPS(); camAngH=180; camAngV=28; camDist=40; }
        if (k==KeyEvent.VK_4) { if(fpsMode) salirFPS(); camAngH=270; camAngV=28; camDist=40; }
        if (k==KeyEvent.VK_5) { if(fpsMode) salirFPS(); camAngH=  0; camAngV=82; camDist=55; }
        if (k==KeyEvent.VK_F && fpsMode)  menuMuebles = !menuMuebles;
        if (k==KeyEvent.VK_UP   && menuMuebles) modeloIdx = (modeloIdx-1+MODELOS.length) % MODELOS.length;
        if (k==KeyEvent.VK_DOWN && menuMuebles) modeloIdx = (modeloIdx+1) % MODELOS.length;
        if (k==KeyEvent.VK_R   && fpsMode)     colocarRotY = (colocarRotY + 45) % 360;
        if (k==KeyEvent.VK_G && fpsMode && menuMuebles) scaleAxis = (scaleAxis + 1) % 4;
        if (k==KeyEvent.VK_OPEN_BRACKET  && fpsMode && menuMuebles) {
            switch(scaleAxis){case 0:psX=Math.max(0.1,psX-0.1);break;case 1:psY=Math.max(0.1,psY-0.1);break;case 2:psZ=Math.max(0.1,psZ-0.1);break;default:psX=psY=psZ=Math.max(0.1,psX-0.1);}
        }
        if (k==KeyEvent.VK_CLOSE_BRACKET && fpsMode && menuMuebles) {
            switch(scaleAxis){case 0:psX=Math.min(5.0,psX+0.1);break;case 1:psY=Math.min(5.0,psY+0.1);break;case 2:psZ=Math.min(5.0,psZ+0.1);break;default:psX=psY=psZ=Math.min(5.0,psX+0.1);}
        }
        if (k==KeyEvent.VK_Q && fpsMode && menuMuebles) colocarOffsetY += 0.01;
        if (k==KeyEvent.VK_Z && fpsMode && menuMuebles) colocarOffsetY -= 0.01;
        if (k==KeyEvent.VK_X   && fpsMode) {
            double[] gp = ghostPos();
            SceneObject nearest = null; double minDist = 2.5;
            for (SceneObject so : sceneObjects) {
                double d = Math.hypot(so.posX - gp[0], so.posZ - gp[1]);
                if (d < minDist) { minDist = d; nearest = so; }
            }
            if (nearest != null) {
                if (nearest.placedId >= 0)
                    System.out.printf("/* ELIMINADO #%d */%n", nearest.placedId);
                pendingDelete = nearest;
            }
        }
        if (k==KeyEvent.VK_P && fpsMode) {
            double[] gp = ghostPos();
            String nom = MODELOS[modeloIdx];
            double[] sz = tamanoDefault(nom);
            String ruta = objPathParaNombre(nom);
            // posición real = ghostPos + offsets de centrado (igual que dibujarGhost)
            double ox = (previewSO != null) ? previewSO.baseOffsetX * psX : 0;
            double oz = (previewSO != null) ? previewSO.baseOffsetZ * psZ : 0;
            double placeX = gp[0] + ox, placeZ = gp[1] + oz;
            boolean esPared = nom.equals("pared_plana");
            if (esPared) {
                double[] snap = snapToWallEdges(placeX, placeZ, colocarRotY, sz[0] * psX, sz[2] * psZ);
                placeX = snap[0];
                placeZ = snap[1];
                if (snap[2] == 0.0) placeX = snapValue(placeX, WALL_SNAP);
                if (snap[3] == 0.0) placeZ = snapValue(placeZ, WALL_SNAP);
            }
            String rutaPlace = esPared ? "pared_plana" : ruta;
            int id = ++placedCounter;
            System.out.printf("/* #%d */ add(M+\"%s.obj\", %.2f, %.2f, %.2f,  0,%d,0,  %.2f,%.2f,%.2f);%n",
                              id, nom, placeX, colocarOffsetY, placeZ, colocarRotY,
                              sz[0]*psX, sz[1]*psY, sz[2]*psZ);
            SceneObject so = new SceneObject(rutaPlace, placeX, colocarOffsetY, placeZ, 0,colocarRotY,0,
                              sz[0]*psX, sz[1]*psY, sz[2]*psZ);
            if (esPared) so.colorOverride = COLORES[P].clone();
            so.placedId = id;
            sceneObjects.add(so);
            if (esPared) wallMeshDirty = true;
            Thread t = new Thread(() -> { so.model.load(resolveObjPath(so.objPath)); so.computeScale(); }, "Placer");
            t.setDaemon(true); t.start();
        }
    }

    // =========================================================================
    //  MAIN
    // =========================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GLProfile  pr    = GLProfile.get(GLProfile.GL2);
            final GLJPanel panel = createPanel(pr);
            Casa3D     app   = new Casa3D();
            app.panel = panel;
            try { app.robot = new Robot(); } catch (Exception ex) { System.err.println("Robot no disponible: "+ex.getMessage()); }

            panel.addGLEventListener(app);
            panel.addMouseMotionListener(app);
            panel.addMouseListener(app);
            panel.addMouseWheelListener(app);
            panel.addKeyListener(app);
            panel.setFocusable(true);
            panel.setFocusTraversalKeysEnabled(false); // permite usar Tab como tecla de juego

            JFrame f = new JFrame("Casa 3D  |  Tab=FPS/Orbital  WASD=mover  ESC=salir FPS");
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
