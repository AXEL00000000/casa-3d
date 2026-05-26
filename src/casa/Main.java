/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package casa;

import com.jogamp.newt.event.*;
import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.MouseEvent;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.*;
import com.jogamp.opengl.util.FPSAnimator;

/**
 * Clase principal - Ventana NEWT, loop de renderizado y controles.
 *
 * Controles:
 *   W/A/S/D       - Moverse
 *   Mouse          - Girar camara
 *   Shift + W/S    - Subir/bajar
 *   ESC            - Salir
 */
public class Main implements GLEventListener, KeyListener, MouseListener {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    private GLWindow window;
    private Renderer renderer;
    private Camera camera;
    private CollisionManager collision;

    private boolean[] keys = new boolean[65536];
    private int lastMouseX, lastMouseY;
    private boolean firstMouse = true;
    private long lastTime = System.nanoTime();

    public static void main(String[] args) {
        new Main().start();
    }

    public void start() {
        GLProfile profile = GLProfile.get(GLProfile.GL2);
        GLCapabilities caps = new GLCapabilities(profile);
        caps.setDoubleBuffered(true);
        caps.setDepthBits(24);

        window = GLWindow.create(caps);
        window.setTitle("Casa 3D - Recorrido Virtual");
        window.setSize(WIDTH, HEIGHT);
        window.addGLEventListener(this);
        window.addKeyListener(this);
        window.addMouseListener(this);

        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowDestroyNotify(WindowEvent e) { System.exit(0); }
        });

        window.confinePointer(true);
        window.setPointerVisible(false);
        window.setVisible(true);

        // Centrar
        java.awt.Dimension scr = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        window.setPosition((scr.width - WIDTH) / 2, (scr.height - HEIGHT) / 2);

        FPSAnimator animator = new FPSAnimator(window, 60, true);
        animator.start();
    }

    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        gl.glClearColor(0.53f, 0.81f, 0.98f, 1.0f);
        gl.glEnable(GL2.GL_DEPTH_TEST);

        // No usar CULL_FACE para ver ambos lados de las paredes
        // gl.glEnable(GL2.GL_CULL_FACE);

        // Iluminacion
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_LIGHT0);
        gl.glEnable(GL2.GL_LIGHT1);

        // Luz principal (sol)
        float[] sunPos = {10f, 20f, 10f, 0f}; // Direccional
        float[] sunDif = {1.0f, 0.95f, 0.9f, 1f};
        float[] sunAmb = {0.4f, 0.4f, 0.4f, 1f};
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, sunPos, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, sunDif, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_AMBIENT, sunAmb, 0);

        // Luz de relleno (para interiores)
        float[] fillPos = {-5f, 5f, -5f, 0f};
        float[] fillDif = {0.3f, 0.3f, 0.35f, 1f};
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_POSITION, fillPos, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_DIFFUSE, fillDif, 0);

        gl.glEnable(GL2.GL_COLOR_MATERIAL);
        gl.glColorMaterial(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT_AND_DIFFUSE);

        // Camara
        camera = new Camera(0f, 1.0f, 8f);

        // Cargar modelo
        System.out.println("Cargando modelo...");
        renderer = new Renderer(gl);
        collision = new CollisionManager(renderer.getModel());

        System.out.println("Listo! WASD=mover, Mouse=mirar, ESC=salir");
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        long now = System.nanoTime();
        float dt = (now - lastTime) / 1_000_000_000f;
        lastTime = now;
        if (dt > 0.05f) dt = 0.05f;

        processInput(dt);

        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();
        camera.applyView(gl);

        renderer.draw(gl);
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {
        GL2 gl = drawable.getGL().getGL2();
        if (h == 0) h = 1;
        gl.glViewport(0, 0, w, h);
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        float aspect = (float) w / h;
        float fov = 70f, near = 0.1f, far = 500f;
        float top = (float) (near * Math.tan(Math.toRadians(fov / 2.0)));
        gl.glFrustum(-top * aspect, top * aspect, -top, top, near, far);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        if (renderer != null) renderer.cleanup(drawable.getGL().getGL2());
    }

    private void processInput(float dt) {
        float speed = 5.0f * dt;
        float dx = 0, dy = 0, dz = 0;

        if (keys[KeyEvent.VK_W]) { dx += camera.frontX(); dz += camera.frontZ(); }
        if (keys[KeyEvent.VK_S]) { dx -= camera.frontX(); dz -= camera.frontZ(); }
        if (keys[KeyEvent.VK_A]) { dx -= camera.rightX(); dz -= camera.rightZ(); }
        if (keys[KeyEvent.VK_D]) { dx += camera.rightX(); dz += camera.rightZ(); }
        if (keys[KeyEvent.VK_SPACE])      dy += speed;
        if (keys[KeyEvent.VK_SHIFT])      dy -= speed;

        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len > 0) {
            dx = (dx / len) * speed;
            dz = (dz / len) * speed;

            float newX = camera.x + dx;
            float newZ = camera.z + dz;

            if (collision != null) {
                if (!collision.collidesAt(newX, camera.z, 0.3f)) camera.x = newX;
                if (!collision.collidesAt(camera.x, newZ, 0.3f)) camera.z = newZ;
            } else {
                camera.x = newX;
                camera.z = newZ;
            }
        }
        camera.y += dy;
    }

    // Teclado
    @Override
    public void keyPressed(KeyEvent e) {
        short c = e.getKeyCode();
        if (c < keys.length) keys[c] = true;
        if (c == KeyEvent.VK_ESCAPE) System.exit(0);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        short c = e.getKeyCode();
        if (c < keys.length) keys[c] = false;
    }

    // Mouse
    @Override
    public void mouseMoved(MouseEvent e) {
        if (firstMouse) {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            firstMouse = false;
            return;
        }
        float dx = e.getX() - lastMouseX;
        float dy = lastMouseY - e.getY();
        lastMouseX = e.getX();
        lastMouseY = e.getY();
        camera.rotate(dx * 0.15f, dy * 0.15f);
    }

    @Override public void mouseDragged(MouseEvent e) { mouseMoved(e); }
    @Override public void mouseClicked(MouseEvent e) { }
    @Override public void mousePressed(MouseEvent e) { }
    @Override public void mouseReleased(MouseEvent e) { }
    @Override public void mouseEntered(MouseEvent e) { }
    @Override public void mouseExited(MouseEvent e) { }
    @Override public void mouseWheelMoved(MouseEvent e) { }
}