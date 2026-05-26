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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

public class CasaFinal implements GLEventListener,
        MouseMotionListener, MouseListener, MouseWheelListener, KeyListener {

    // =========================================================================
    //  MODELO PRINCIPAL
    // =========================================================================
    private static final String HOUSE_OBJ = "modelos/modelo casa final/PROYECTO FINAL ACOSADAMAS.obj";

    private final ObjModel houseModel    = new ObjModel();
    private boolean        houseInitDone = false;
    private int            houseDlId     = -1;

    // triangulos de suelo/rampa extraídos del mesh para la altura del jugador
    private final List<float[][]> floorTris = new ArrayList<>();
    private volatile boolean      floorReady = false;

    // =========================================================================
    //  CÁMARA ORBITAL
    // =========================================================================
    private int    mx, my;
    private final  GLU glu = new GLU();
    private double camAngH = -30, camAngV = 35, camDist = 35;

    // =========================================================================
    //  CÁMARA PRIMERA PERSONA
    // =========================================================================
    private boolean fpsMode       = false;
    private double  fpsPosX       = 0, fpsPosY = 1.6, fpsPosZ = 8;
    private double  fpsYaw        = 180, fpsPitch = 0;
    private boolean mouseCaptured  = false;
    private boolean justRecentered = false;
    private final Set<Integer> keysDown = new HashSet<>();

    private GLJPanel panel;
    private Robot    robot;

    private static final double PLAYER_HEIGHT = 1.6;
    private static final double MAX_STEP      = 0.8; // cuánto puede subir de un paso

    // =========================================================================
    //  OBJ MODEL
    // =========================================================================
    private static class ObjModel {
        static class Face  { int[] vIdx, tIdx, nIdx; Face(int[]v,int[]t,int[]n){vIdx=v;tIdx=t;nIdx=n;} }
        static class Group { String mtlName=""; List<Face> faces=new ArrayList<>(); }

        List<float[]> verts=new ArrayList<>(), texs=new ArrayList<>(), norms=new ArrayList<>();
        List<Group>   groups   = new ArrayList<>();
        Map<String,Map<String,String>> materials = new HashMap<>();
        Map<String,Texture>     texCache = new HashMap<>();
        boolean loaded=false; String baseDir="";

        void load(String objPath) {
            File f = resolveFile(objPath); if (f == null) { System.err.println("No se encontró: "+objPath); return; }
            baseDir = f.getParent() != null ? f.getParent() : "";
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line; Group cur = new Group(); groups.add(cur);
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if      (line.startsWith("mtllib "))  loadMtl(new File(baseDir, line.substring(7).trim()));
                    else if (line.startsWith("usemtl "))  { cur = new Group(); cur.mtlName = line.substring(7).trim(); groups.add(cur); }
                    else if (line.startsWith("v "))       { String[]p=line.substring(2).trim().split("\\s+"); verts.add(new float[]{Float.parseFloat(p[0]),Float.parseFloat(p[1]),Float.parseFloat(p[2])}); }
                    else if (line.startsWith("vt "))      { String[]p=line.substring(3).trim().split("\\s+"); texs.add(new float[]{Float.parseFloat(p[0]),p.length>1?Float.parseFloat(p[1]):0}); }
                    else if (line.startsWith("vn "))      { String[]p=line.substring(3).trim().split("\\s+"); norms.add(new float[]{Float.parseFloat(p[0]),Float.parseFloat(p[1]),Float.parseFloat(p[2])}); }
                    else if (line.startsWith("f "))       parseFace(line.substring(2).trim(), cur);
                }
                loaded = true;
            } catch (Exception e) { System.err.println("OBJ error: "+e.getMessage()); }
        }

        void parseFace(String s, Group g) {
            String[] tok=s.split("\\s+"); int n=tok.length;
            int[]vi=new int[n],ti=new int[n],ni=new int[n];
            for (int i=0;i<n;i++) {
                String[]p=tok[i].split("/");
                int iv=Integer.parseInt(p[0]); vi[i]=iv>0?iv-1:verts.size()+iv;
                if(p.length>1&&!p[1].isEmpty()){int it=Integer.parseInt(p[1]);ti[i]=it>0?it-1:texs.size()+it;}else ti[i]=-1;
                if(p.length>2&&!p[2].isEmpty()){int in_=Integer.parseInt(p[2]);ni[i]=in_>0?in_-1:norms.size()+in_;}else ni[i]=-1;
            }
            for (int i=1;i<n-1;i++) g.faces.add(new Face(new int[]{vi[0],vi[i],vi[i+1]},new int[]{ti[0],ti[i],ti[i+1]},new int[]{ni[0],ni[i],ni[i+1]}));
        }

        void loadMtl(File f) {
            if (!f.exists()) return;
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line, cur=null;
                while ((line=br.readLine())!=null) {
                    line=line.trim();
                    if      (line.startsWith("newmtl "))  { cur=line.substring(7).trim(); materials.put(cur,new HashMap<>()); }
                    else if (cur!=null) {
                        if      (line.startsWith("Kd "))     materials.get(cur).put("Kd",line.substring(3).trim());
                        else if (line.startsWith("map_Kd ")) {
                            String full=line.substring(7).trim();
                            materials.get(cur).put("map_Kd",      new File(full).getName()); // solo nombre
                            materials.get(cur).put("map_Kd_full", full);                     // ruta completa
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        void initTextures(GL2 gl) {
            for (Map.Entry<String,Map<String,String>> me : materials.entrySet()) {
                String tn   = me.getValue().get("map_Kd");   if (tn==null) continue;
                String full = me.getValue().get("map_Kd_full");
                if (texCache.containsKey(tn)) continue;

                // prueba en orden: ruta absoluta del MTL, junto al modelo, subcarpetas, texturas del usuario
                File tf = null;
                for (File cand : new File[]{
                        full!=null ? new File(full) : null,
                        new File(baseDir, tn),
                        new File(baseDir+"/textures",  tn),
                        new File(baseDir+"/texture",   tn),
                        new File(baseDir+"/Textures",  tn),
                        new File(System.getProperty("user.home")+"/Downloads/texturas", tn),
                        new File("textura", tn),
                        new File("src/textura", tn)
                }) {
                    if (cand!=null && cand.exists()) { tf=cand; break; }
                }

                if (tf==null) { System.err.println("Textura no encontrada: "+tn); continue; }
                try {
                    Texture t = TextureIO.newTexture(tf, true);
                    t.setTexParameteri(gl,GL2.GL_TEXTURE_WRAP_S,GL2.GL_REPEAT);
                    t.setTexParameteri(gl,GL2.GL_TEXTURE_WRAP_T,GL2.GL_REPEAT);
                    t.setTexParameteri(gl,GL2.GL_TEXTURE_MIN_FILTER,GL2.GL_LINEAR_MIPMAP_LINEAR);
                    t.setTexParameteri(gl,GL2.GL_TEXTURE_MAG_FILTER,GL2.GL_LINEAR);
                    texCache.put(tn, t);
                    System.out.println("Textura OK: "+tn);
                } catch (Exception e) { System.err.println("Textura error "+tn+": "+e.getMessage()); }
            }
        }

        void render(GL2 gl, float[] colorOverride, boolean allowTexture) {
            if (!loaded) return;
            for (Group g : groups) {
                Map<String,String> mat = materials.get(g.mtlName);
                Texture tex = null;
                if (mat!=null) { String tn=mat.get("map_Kd"); if(tn!=null) tex=texCache.get(tn); }
                if      (colorOverride!=null) gl.glColor3f(colorOverride[0],colorOverride[1],colorOverride[2]);
                else if (mat!=null) { String kd=mat.get("Kd"); if(kd!=null){ String[]p=kd.split("\\s+"); if(p.length>=3) gl.glColor3f(Float.parseFloat(p[0]),Float.parseFloat(p[1]),Float.parseFloat(p[2])); } }
                else gl.glColor3f(0.85f,0.82f,0.78f);
                if (allowTexture && tex!=null) { gl.glEnable(GL2.GL_TEXTURE_2D); tex.bind(gl); gl.glTexEnvi(GL2.GL_TEXTURE_ENV,GL2.GL_TEXTURE_ENV_MODE,GL2.GL_MODULATE); }
                else gl.glDisable(GL2.GL_TEXTURE_2D);
                gl.glBegin(GL2.GL_TRIANGLES);
                for (Face face : g.faces) {
                    boolean ok=true; for(int i=0;i<3;i++) if(face.vIdx[i]<0||face.vIdx[i]>=verts.size()){ok=false;break;} if(!ok)continue;
                    for (int i=0;i<3;i++) {
                        if (face.nIdx[i]>=0&&face.nIdx[i]<norms.size()) { float[]nv=norms.get(face.nIdx[i]); gl.glNormal3f(nv[0],nv[1],nv[2]); }
                        if (tex!=null&&face.tIdx[i]>=0&&face.tIdx[i]<texs.size()) { float[]tv=texs.get(face.tIdx[i]); gl.glTexCoord2f(tv[0],tv[1]); }
                        float[]v=verts.get(face.vIdx[i]); gl.glVertex3f(v[0],v[1],v[2]);
                    }
                }
                gl.glEnd(); gl.glDisable(GL2.GL_TEXTURE_2D);
            }
        }

        static File resolveFile(String path) {
            for (String p : new String[]{"","src/","../"}) { File f=new File(p+path); if(f.exists())return f; }
            return null;
        }
    }

    // =========================================================================
    //  DETECCIÓN DE ALTURA DESDE EL MESH (rampas / pisos)
    // =========================================================================
    private void buildFloorGeometry() {
        floorTris.clear();
        for (ObjModel.Group g : houseModel.groups) {
            for (ObjModel.Face f : g.faces) {
                if (f.vIdx[0]>=houseModel.verts.size()||f.vIdx[1]>=houseModel.verts.size()||f.vIdx[2]>=houseModel.verts.size()) continue;
                float[] v0=houseModel.verts.get(f.vIdx[0]), v1=houseModel.verts.get(f.vIdx[1]), v2=houseModel.verts.get(f.vIdx[2]);
                // normal del triángulo
                float ex=v1[0]-v0[0], ey=v1[1]-v0[1], ez=v1[2]-v0[2];
                float fx=v2[0]-v0[0], fy=v2[1]-v0[1], fz=v2[2]-v0[2];
                float nx=ey*fz-ez*fy, ny=ez*fx-ex*fz, nz=ex*fy-ey*fx;
                float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz); if(len<1e-8f) continue;
                ny/=len;
                // solo triángulos que miran hacia arriba (piso/rampa). ny>0.3 = hasta ~72° de inclinación
                if (ny > 0.3f) floorTris.add(new float[][]{v0.clone(),v1.clone(),v2.clone()});
            }
        }
        System.out.println("Triángulos de suelo/rampa: " + floorTris.size());
        floorReady = true;
    }

    // Devuelve la Y del suelo bajo (x,z), o NaN si no hay triángulo
    private double meshFloorAt(double x, double z) {
        double best = Double.NEGATIVE_INFINITY;
        double standY = fpsPosY - PLAYER_HEIGHT; // altura actual de los pies

        for (float[][] tri : floorTris) {
            float[] v0=tri[0], v1=tri[1], v2=tri[2];
            // test XZ: está (x,z) dentro del triángulo proyectado en XZ?
            double d1=(v1[0]-v0[0])*(z-v0[2])-(v1[2]-v0[2])*(x-v0[0]);
            double d2=(v2[0]-v1[0])*(z-v1[2])-(v2[2]-v1[2])*(x-v1[0]);
            double d3=(v0[0]-v2[0])*(z-v2[2])-(v0[2]-v2[2])*(x-v2[0]);
            boolean neg=(d1<0)||(d2<0)||(d3<0);
            boolean pos=(d1>0)||(d2>0)||(d3>0);
            if (neg&&pos) continue; // fuera del triángulo

            // altura en el plano del triángulo
            float ex=v1[0]-v0[0], ey=v1[1]-v0[1], ez=v1[2]-v0[2];
            float fx=v2[0]-v0[0], fy=v2[1]-v0[1], fz=v2[2]-v0[2];
            float bny=ez*fx-ex*fz; // componente Y de la normal (sin normalizar)
            if (Math.abs(bny) < 1e-8f) continue;
            // n·(P-v0)=0  => ny*(y-v0[1]) = -(nx*(x-v0[0]) + nz*(z-v0[2]))
            float bnx=ey*fz-ez*fy, bnz=ex*fy-ey*fx;
            double y = v0[1] - (bnx*(x-v0[0]) + bnz*(z-v0[2])) / bny;

            // solo subir si el suelo no está más de MAX_STEP por encima de donde estamos parados
            if (y > standY + MAX_STEP) continue;
            if (y > best) best = y;
        }
        return best == Double.NEGATIVE_INFINITY ? Double.NaN : best;
    }

    // =========================================================================
    //  OBJETOS EXTRA (puertas, escaleras, coches…)
    // =========================================================================
    private static class SceneObject {
        String objPath; double posX,posY,posZ,rotX,rotY,rotZ,targetW,targetH,targetD;
        double scaleX=1,scaleY=1,scaleZ=1,baseOffsetY=0;
        float[] colorOverride=null;
        ObjModel model=new ObjModel(); boolean initDone=false; int dlId=-1;
        boolean isDoor=false; float doorAngle=0f,doorTarget=0f; int doorCloseDelay=0;

        SceneObject(String o,double px,double py,double pz,double rx,double ry,double rz,double tw,double th,double td) {
            objPath=o; posX=px; posY=py; posZ=pz; rotX=rx; rotY=ry; rotZ=rz; targetW=tw; targetH=th; targetD=td;
            isDoor = tieneHojaPuerta(o);
        }
        void computeScale() {
            if (!model.loaded||model.verts.isEmpty()) return;
            float mnX=Float.MAX_VALUE,mxX=-Float.MAX_VALUE,mnY=Float.MAX_VALUE,mxY=-Float.MAX_VALUE,mnZ=Float.MAX_VALUE,mxZ=-Float.MAX_VALUE;
            for (float[]v:model.verts){if(v[0]<mnX)mnX=v[0];if(v[0]>mxX)mxX=v[0];if(v[1]<mnY)mnY=v[1];if(v[1]>mxY)mxY=v[1];if(v[2]<mnZ)mnZ=v[2];if(v[2]>mxZ)mxZ=v[2];}
            double bw=mxX-mnX,bh=mxY-mnY,bd=mxZ-mnZ; if(bw<1e-6||bh<1e-6||bd<1e-6) return;
            scaleX=targetW/bw; scaleY=targetH/bh; scaleZ=targetD/bd; baseOffsetY=-mnY*scaleY;
        }
    }

    static boolean tieneHojaPuerta(String path) {
        String n=new File(path).getName().toLowerCase(Locale.ROOT);
        return (n.startsWith("doorway")&&!n.contains("open"))||n.startsWith("door.");
    }

    private final List<SceneObject> sceneObjects = new ArrayList<>();

    private void add(String p,double px,double py,double pz,double rx,double ry,double rz,double sx,double sy,double sz) {
        sceneObjects.add(new SceneObject(p,px,py,pz,rx,ry,rz,sx,sy,sz));
    }

    private static String resolveObjPath(String rel) {
        for (String p:new String[]{"","src/","../"}) if(new File(p+rel).exists()) return p+rel;
        return "src/"+rel;
    }

    private void crearObjetos() {
        // Añade puertas o escaleras aquí con add():
        // add("modelos/Models/OBJ format/doorwayFront.obj", x, y, z, 0,0,0, 0.90,1.92,0.12);
    }

    // =========================================================================
    //  CONSTRUCTOR
    // =========================================================================
    public CasaFinal() {
        crearObjetos();
        Thread t = new Thread(() -> {
            houseModel.load(HOUSE_OBJ);
            System.out.println("Casa: " + houseModel.verts.size() + " verts, " + houseModel.groups.size() + " grupos");
            buildFloorGeometry();
            for (SceneObject so : sceneObjects) { so.model.load(resolveObjPath(so.objPath)); so.computeScale(); }
        }, "ObjLoader");
        t.setDaemon(true);
        t.start();
    }

    // =========================================================================
    //  INIT
    // =========================================================================
    private TextRenderer textRenderer;

    @Override
    public void init(GLAutoDrawable d) {
        GL2 gl=d.getGL().getGL2();
        d.getGL().setSwapInterval(1);
        gl.glClearColor(0.5f,0.7f,0.9f,1f);
        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glEnable(GL2.GL_MULTISAMPLE);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_LIGHT0);
        gl.glEnable(GL2.GL_COLOR_MATERIAL);
        gl.glColorMaterial(GL2.GL_FRONT_AND_BACK,GL2.GL_AMBIENT_AND_DIFFUSE);
        gl.glLightModeli(GL2.GL_LIGHT_MODEL_TWO_SIDE,GL2.GL_TRUE);
        gl.glShadeModel(GL2.GL_SMOOTH);
        gl.glEnable(GL2.GL_NORMALIZE);
        gl.glHint(GL2.GL_PERSPECTIVE_CORRECTION_HINT,GL2.GL_NICEST);
        gl.glLightModelfv(GL2.GL_LIGHT_MODEL_AMBIENT,new float[]{0.55f,0.55f,0.55f,1},0);
        gl.glLightfv(GL2.GL_LIGHT0,GL2.GL_AMBIENT, new float[]{0.35f,0.35f,0.35f,1},0);
        gl.glLightfv(GL2.GL_LIGHT0,GL2.GL_DIFFUSE, new float[]{1.0f,1.0f,1.0f,1},0);
        gl.glLightfv(GL2.GL_LIGHT0,GL2.GL_POSITION,new float[]{-0.4f,1.0f,0.3f,0},0);
        gl.glDisable(GL2.GL_LIGHT1);
        textRenderer = new TextRenderer(new Font("Arial",Font.BOLD,17));
    }

    // =========================================================================
    //  DISPLAY
    // =========================================================================
    @Override
    public void display(GLAutoDrawable d) {
        GL2 gl=d.getGL().getGL2();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT|GL2.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        if (fpsMode) {
            double rad=Math.toRadians(fpsYaw);
            double pit=Math.toRadians(Math.max(-85,Math.min(85,fpsPitch)));
            double lx=Math.cos(pit)*Math.sin(rad), ly=Math.sin(pit), lz=Math.cos(pit)*Math.cos(rad);
            glu.gluLookAt(fpsPosX,fpsPosY,fpsPosZ, fpsPosX+lx,fpsPosY+ly,fpsPosZ+lz, 0,1,0);
        } else {
            double aH=Math.toRadians(camAngH), aV=Math.toRadians(Math.max(5,Math.min(85,camAngV)));
            glu.gluLookAt(camDist*Math.cos(aV)*Math.sin(aH), camDist*Math.sin(aV), camDist*Math.cos(aV)*Math.cos(aH), 0,0,0, 0,1,0);
        }

        dibujarCasa(gl);
        dibujarObjetos(gl);
        dibujarHUD(gl, d.getSurfaceWidth(), d.getSurfaceHeight());
    }

    private void dibujarCasa(GL2 gl) {
        if (!houseModel.loaded) return;
        if (!houseInitDone) { houseModel.initTextures(gl); houseInitDone=true; }
        if (houseDlId==-1) {
            houseDlId=gl.glGenLists(1);
            gl.glNewList(houseDlId,GL2.GL_COMPILE);
            houseModel.render(gl,null,true);
            gl.glEndList();
        }
        gl.glPushMatrix();
        gl.glCallList(houseDlId);
        gl.glPopMatrix();
    }

    private void dibujarObjetos(GL2 gl) {
        for (SceneObject so : sceneObjects) {
            if (so.model.loaded&&!so.initDone) { so.model.initTextures(gl); so.initDone=true; }
            if (!so.model.loaded) continue;
            if (so.dlId==-1) {
                so.dlId=gl.glGenLists(1); gl.glNewList(so.dlId,GL2.GL_COMPILE);
                so.model.render(gl,so.colorOverride,true); gl.glEndList();
            }
            gl.glPushMatrix();
            gl.glTranslated(so.posX,so.posY+so.baseOffsetY,so.posZ);
            if(so.rotY!=0) gl.glRotated(so.rotY,0,1,0);
            if(so.rotX!=0) gl.glRotated(so.rotX,1,0,0);
            if(so.rotZ!=0) gl.glRotated(so.rotZ,0,0,1);
            if(so.isDoor&&so.doorAngle!=0) gl.glRotated(so.doorAngle,0,1,0);
            gl.glScaled(so.scaleX,so.scaleY,so.scaleZ);
            gl.glCallList(so.dlId);
            gl.glPopMatrix();
        }
        gl.glDisable(GL2.GL_TEXTURE_2D);
    }

    private void dibujarHUD(GL2 gl, int w, int h) {
        gl.glDisable(GL2.GL_LIGHTING); gl.glDisable(GL2.GL_DEPTH_TEST);
        gl.glMatrixMode(GL2.GL_PROJECTION); gl.glPushMatrix(); gl.glLoadIdentity();
        glu.gluOrtho2D(0,w,0,h);
        gl.glMatrixMode(GL2.GL_MODELVIEW); gl.glPushMatrix(); gl.glLoadIdentity();
        gl.glEnable(GL2.GL_BLEND); gl.glBlendFunc(GL2.GL_SRC_ALPHA,GL2.GL_ONE_MINUS_SRC_ALPHA);

        if (fpsMode) {
            gl.glColor4f(0f,0f,0f,0.6f); gl.glBegin(GL2.GL_QUADS);
            gl.glVertex2i(0,h); gl.glVertex2i(w,h); gl.glVertex2i(w,h-40); gl.glVertex2i(0,h-40); gl.glEnd();
            gl.glDisable(GL2.GL_BLEND);
            if (textRenderer!=null) {
                textRenderer.beginRendering(w,h);
                textRenderer.setColor(1f,1f,0.2f,1f);
                textRenderer.draw(String.format("X=%.2f  Y=%.2f  Z=%.2f", fpsPosX, fpsPosY, fpsPosZ), 10, h-26);
                textRenderer.setColor(0.85f,0.85f,0.85f,1f);
                textRenderer.draw("[WASD] mover   [Shift] correr   [Tab] orbital   [Esc] salir FPS", 10, h-44);
                textRenderer.endRendering();
            }
        } else {
            gl.glColor4f(0f,0f,0f,0.6f); gl.glBegin(GL2.GL_QUADS);
            gl.glVertex2i(0,36); gl.glVertex2i(w,36); gl.glVertex2i(w,0); gl.glVertex2i(0,0); gl.glEnd();
            gl.glDisable(GL2.GL_BLEND);
            if (textRenderer!=null) {
                textRenderer.beginRendering(w,h);
                textRenderer.setColor(1f,1f,0.3f,1f);
                textRenderer.draw("[Tab/Clic] primera persona   [Arrastrar] rotar   [Rueda] zoom   [1]Sur [2]Este [3]Norte [4]Oeste [5]Aérea", 10, 10);
                textRenderer.endRendering();
            }
        }

        gl.glMatrixMode(GL2.GL_PROJECTION); gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_MODELVIEW);  gl.glPopMatrix();
        gl.glEnable(GL2.GL_DEPTH_TEST); gl.glEnable(GL2.GL_LIGHTING);
    }

    // =========================================================================
    //  RESHAPE / DISPOSE
    // =========================================================================
    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {
        GL2 gl=d.getGL().getGL2(); if(h==0)h=1;
        gl.glViewport(0,0,w,h); gl.glMatrixMode(GL2.GL_PROJECTION); gl.glLoadIdentity();
        glu.gluPerspective(45,(double)w/h,0.05,500);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }
    @Override public void dispose(GLAutoDrawable d) {}

    // =========================================================================
    //  MOVIMIENTO
    // =========================================================================
    void updateMovement() {
        if (!fpsMode) return;
        double speed = keysDown.contains(KeyEvent.VK_SHIFT) ? 0.12 : 0.07;
        double rad = Math.toRadians(fpsYaw);
        double sinY=Math.sin(rad), cosY=Math.cos(rad);
        double dx=0, dz=0;
        if (keysDown.contains(KeyEvent.VK_W)) { dx+=sinY; dz+=cosY; }
        if (keysDown.contains(KeyEvent.VK_S)) { dx-=sinY; dz-=cosY; }
        if (keysDown.contains(KeyEvent.VK_A)) { dx-=cosY; dz+=sinY; }
        if (keysDown.contains(KeyEvent.VK_D)) { dx+=cosY; dz-=sinY; }
        fpsPosX += dx*speed;
        fpsPosZ += dz*speed;

        // Altura: mesh del modelo (rampas/pisos) + objetos escalera
        if (floorReady) {
            double meshY = meshFloorAt(fpsPosX, fpsPosZ);
            double targetY;
            if (!Double.isNaN(meshY)) {
                targetY = meshY + PLAYER_HEIGHT;
            } else {
                targetY = PLAYER_HEIGHT; // suelo por defecto
            }
            // escaleras de objetos extra (si las hay)
            double stairY = alturaEnPosicion(fpsPosX, fpsPosZ);
            if (stairY > targetY) targetY = stairY;
            fpsPosY += (targetY - fpsPosY) * 0.2;
        }

        // Animación de puertas extra
        for (SceneObject so : sceneObjects) {
            if (!so.isDoor) continue;
            double yr=Math.toRadians(so.rotY);
            double dcX=so.posX-(so.targetW/2.0)*Math.cos(yr);
            double dcZ=so.posZ+(so.targetW/2.0)*Math.sin(yr);
            double ddx=fpsPosX-dcX, ddz=fpsPosZ-dcZ;
            if (Math.sqrt(ddx*ddx+ddz*ddz)<1.1) {
                double faceNx=Math.sin(yr), faceNz=Math.cos(yr);
                double lado=ddx*faceNx+ddz*faceNz;
                so.doorTarget=(lado>=0)?85f:-85f; so.doorCloseDelay=90;
            } else if (so.doorCloseDelay>0) { so.doorCloseDelay--;
            } else { so.doorTarget=0f; }
            float diff=so.doorTarget-so.doorAngle;
            so.doorAngle+=diff*0.12f;
            if (Math.abs(diff)<0.2f) so.doorAngle=so.doorTarget;
        }
    }

    private double alturaEnPosicion(double x, double z) {
        for (SceneObject so : sceneObjects) {
            if (!new File(so.objPath).getName().toLowerCase(Locale.ROOT).startsWith("stair")) continue;
            double yr=Math.toRadians(so.rotY);
            double hw=so.targetW/2.0, hd=so.targetD/2.0;
            double axX=Math.cos(yr), axZ=Math.sin(yr), azX=-Math.sin(yr), azZ=Math.cos(yr);
            double relX=x-so.posX, relZ=z-so.posZ;
            double along=relX*axX+relZ*axZ, perp=relX*azX+relZ*azZ;
            if (Math.abs(along)<=hw&&Math.abs(perp)<=hd) {
                double t=(along+hw)/(2.0*hw);
                return PLAYER_HEIGHT+so.targetH*Math.max(0,Math.min(1,t));
            }
        }
        return PLAYER_HEIGHT;
    }

    // =========================================================================
    //  MOUSE
    // =========================================================================
    @Override public void mousePressed (MouseEvent e) { mx=e.getX(); my=e.getY(); }
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseClicked (MouseEvent e) { if (!fpsMode) entrarFPS(); }
    @Override public void mouseEntered (MouseEvent e) {}
    @Override public void mouseExited  (MouseEvent e) {}

    @Override
    public void mouseDragged(MouseEvent e) {
        if (fpsMode) return;
        camAngH+=(e.getX()-mx)*0.5;
        camAngV=Math.max(5,Math.min(85,camAngV+(e.getY()-my)*0.3));
        mx=e.getX(); my=e.getY();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (!fpsMode||!mouseCaptured||robot==null||panel==null) return;
        if (justRecentered) { justRecentered=false; return; }
        int cx=panel.getWidth()/2, cy=panel.getHeight()/2;
        int dx=e.getX()-cx, dy=e.getY()-cy;
        if (dx==0&&dy==0) return;
        fpsYaw+=dx*0.2;
        fpsPitch=Math.max(-85,Math.min(85,fpsPitch-dy*0.2));
        justRecentered=true; centerMouse();
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (!fpsMode) camDist=Math.max(3,Math.min(200,camDist+e.getWheelRotation()*2.0));
    }

    // =========================================================================
    //  TECLADO
    // =========================================================================
    @Override public void keyTyped  (KeyEvent e) {}
    @Override public void keyReleased(KeyEvent e) { keysDown.remove(e.getKeyCode()); }

    @Override
    public void keyPressed(KeyEvent e) {
        keysDown.add(e.getKeyCode());
        int k=e.getKeyCode();
        if (k==KeyEvent.VK_TAB)    { if(fpsMode) salirFPS(); else entrarFPS(); }
        if (k==KeyEvent.VK_ESCAPE) { if(fpsMode) salirFPS(); }
        if (k==KeyEvent.VK_1) { if(fpsMode) salirFPS(); camAngH=  0; camAngV=28; camDist=35; }
        if (k==KeyEvent.VK_2) { if(fpsMode) salirFPS(); camAngH= 90; camAngV=28; camDist=35; }
        if (k==KeyEvent.VK_3) { if(fpsMode) salirFPS(); camAngH=180; camAngV=28; camDist=35; }
        if (k==KeyEvent.VK_4) { if(fpsMode) salirFPS(); camAngH=270; camAngV=28; camDist=35; }
        if (k==KeyEvent.VK_5) { if(fpsMode) salirFPS(); camAngH=  0; camAngV=82; camDist=50; }
    }

    // =========================================================================
    //  FPS HELPERS
    // =========================================================================
    private void entrarFPS() {
        fpsMode=true; mouseCaptured=true;
        if (panel!=null) panel.setCursor(panel.getToolkit().createCustomCursor(new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB),new Point(),"invisible"));
        centerMouse();
    }
    private void salirFPS() {
        fpsMode=false; mouseCaptured=false;
        if (panel!=null) panel.setCursor(Cursor.getDefaultCursor());
    }
    private void centerMouse() {
        if (robot==null||panel==null) return;
        Point c=new Point(panel.getWidth()/2,panel.getHeight()/2);
        SwingUtilities.convertPointToScreen(c,panel); robot.mouseMove(c.x,c.y);
    }

    // =========================================================================
    //  MAIN
    // =========================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GLProfile pr=GLProfile.get(GLProfile.GL2);
            GLJPanel panel=createPanel(pr);
            CasaFinal app=new CasaFinal();
            app.panel=panel;
            try { app.robot=new Robot(); } catch (Exception ex) { System.err.println("Robot: "+ex.getMessage()); }

            panel.addGLEventListener(app);
            panel.addMouseMotionListener(app);
            panel.addMouseListener(app);
            panel.addMouseWheelListener(app);
            panel.addKeyListener(app);
            panel.setFocusable(true);
            panel.setFocusTraversalKeysEnabled(false);

            JFrame f=new JFrame("Proyecto Final ACOSADAMAS");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setSize(1280,720);
            f.add(panel);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            panel.requestFocusInWindow();

            new javax.swing.Timer(16, e -> { app.updateMovement(); panel.display(); }).start();
        });
    }

    private static GLJPanel createPanel(GLProfile pr) {
        try {
            GLCapabilities caps=new GLCapabilities(pr);
            caps.setSampleBuffers(true); caps.setNumSamples(4);
            return new GLJPanel(caps);
        } catch (Exception ex) { return new GLJPanel(new GLCapabilities(pr)); }
    }
}
