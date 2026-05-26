import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.collision.CollisionResults;
import com.jme3.input.ChaseCamera;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.system.AppSettings;

public class Main extends SimpleApplication implements ActionListener {

    private BulletAppState fisica;
    private CharacterControl jugador;
    private boolean izq, der, adelante, atras;

    // Camara orbital
    private ChaseCamera camaraOrbital;
    private Node objetivoOrbita;   // punto central que orbita la camara
    private boolean modoOrbita = false;

    public static void main(String[] args) {
        Main app = new Main();
        AppSettings cfg = new AppSettings(true);
        cfg.setTitle("Casa 3D");
        cfg.setWidth(1280);
        cfg.setHeight(720);
        cfg.setFrameRate(60);
        app.setSettings(cfg);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        fisica = new BulletAppState();
        stateManager.attach(fisica);

        flyCam.setMoveSpeed(0f);
        inputManager.setCursorVisible(false);

        configurarTeclado();
        cargarCasa();
        crearJugador();
        agregarLuces();
        configurarCamaraOrbital();
    }

    // -------------------------------------------------------------------------
    // CAMARA ORBITAL
    // -------------------------------------------------------------------------

    private void configurarCamaraOrbital() {
        // Nodo en el centro de la casa — la camara orbital gira alrededor de el
        objetivoOrbita = new Node("objetivoOrbita");
        objetivoOrbita.setLocalTranslation(0, 1.5f, 0);
        rootNode.attachChild(objetivoOrbita);

        camaraOrbital = new ChaseCamera(cam, objetivoOrbita, inputManager);
        camaraOrbital.setDefaultDistance(22f);
        camaraOrbital.setMinDistance(6f);
        camaraOrbital.setMaxDistance(50f);
        camaraOrbital.setDefaultVerticalRotation(FastMath.DEG_TO_RAD * 30f);
        camaraOrbital.setRotationSpeed(3f);
        camaraOrbital.setZoomSensitivity(3f);
        camaraOrbital.setDragToRotate(false); // rotar sin mantener clic
        camaraOrbital.setEnabled(false);      // empieza en primera persona
    }

    private void cambiarCamara() {
        modoOrbita = !modoOrbita;
        if (modoOrbita) {
            flyCam.setEnabled(false);
            camaraOrbital.setEnabled(true);
            inputManager.setCursorVisible(false);
        } else {
            camaraOrbital.setEnabled(false);
            flyCam.setEnabled(true);
            flyCam.setMoveSpeed(0f);
            inputManager.setCursorVisible(false);
        }
    }

    // -------------------------------------------------------------------------
    // ESCENA — modelo de Blender
    // -------------------------------------------------------------------------

    private void cargarCasa() {
        // === AGREGA MAS CASAS AQUI con cargarModelo("modelos/X.glb", x, y, z) ===
        cargarModelo("modelos/casa.glb", 0, 0, 0);
        // cargarModelo("modelos/casa2.glb", 30, 0, 0);
        // cargarModelo("modelos/casa3.glb", -30, 0, 0);

        // Suelo de respaldo para toda la escena
        Node sueloFisico = new Node("suelo_respaldo");
        sueloFisico.setLocalTranslation(0, -1f, 0);
        RigidBodyControl rbSuelo = new RigidBodyControl(
            new BoxCollisionShape(new Vector3f(200f, 0.5f, 200f)), 0f);
        sueloFisico.addControl(rbSuelo);
        rootNode.attachChild(sueloFisico);
        fisica.getPhysicsSpace().add(rbSuelo);
    }

    private void cargarModelo(String ruta, float x, float y, float z) {
        Spatial modelo = assetManager.loadModel(ruta);
        modelo.setLocalTranslation(x, y, z);
        rootNode.attachChild(modelo);

        RigidBodyControl rb = new RigidBodyControl(
            CollisionShapeFactory.createMeshShape(modelo), 0f);
        modelo.addControl(rb);
        fisica.getPhysicsSpace().add(rb);
    }

    // -------------------------------------------------------------------------
    // JUGADOR (primera persona con colisiones)
    // -------------------------------------------------------------------------

    private void crearJugador() {
        // Capsule mas baja: cilindro 0.6m + 2 semiesferas de 0.22m = ~1.04m total
        CapsuleCollisionShape capsula = new CapsuleCollisionShape(0.22f, 0.6f, 1);
        jugador = new CharacterControl(capsula, 0.05f);
        jugador.setJumpSpeed(8f);
        jugador.setFallSpeed(25f);
        jugador.setGravity(25f);
        // Enfrente de la casa donde estan los pilares — ajusta Z si no es correcto
        jugador.setPhysicsLocation(new Vector3f(0, 6f, 10f));

        Node nJugador = new Node("jugador");
        nJugador.addControl(jugador);
        rootNode.attachChild(nJugador);
        fisica.getPhysicsSpace().add(jugador);
    }

    // -------------------------------------------------------------------------
    // ILUMINACION
    // -------------------------------------------------------------------------

    private void agregarLuces() {
        // Luz ambiente alta para iluminar el interior
        AmbientLight ambiente = new AmbientLight();
        ambiente.setColor(ColorRGBA.White.mult(0.7f));
        rootNode.addLight(ambiente);

        // Luz solar principal
        DirectionalLight sol = new DirectionalLight();
        sol.setDirection(new Vector3f(-0.6f, -1f, -0.4f).normalizeLocal());
        sol.setColor(ColorRGBA.White.mult(1.0f));
        rootNode.addLight(sol);

        // Luz de relleno desde el lado opuesto para eliminar sombras negras
        DirectionalLight relleno = new DirectionalLight();
        relleno.setDirection(new Vector3f(0.6f, -0.3f, 0.4f).normalizeLocal());
        relleno.setColor(ColorRGBA.White.mult(0.5f));
        rootNode.addLight(relleno);
    }

    // -------------------------------------------------------------------------
    // CONTROLES
    // -------------------------------------------------------------------------

    private void configurarTeclado() {
        inputManager.addMapping("Adelante",     new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("Atras",        new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping("Izq",          new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("Der",          new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("Saltar",       new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping("CambiarVista", new KeyTrigger(KeyInput.KEY_TAB));
        inputManager.addListener(this, "Adelante", "Atras", "Izq", "Der", "Saltar", "CambiarVista");
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case "Adelante"     -> adelante = isPressed;
            case "Atras"        -> atras    = isPressed;
            case "Izq"          -> izq      = isPressed;
            case "Der"          -> der      = isPressed;
            case "Saltar"       -> { if (isPressed) jugador.jump(); }
            case "CambiarVista" -> { if (isPressed) cambiarCamara(); }
        }
    }

    @Override
    public void simpleUpdate(float tpf) {
        if (modoOrbita) {
            // Evita que la camara orbital atraviese paredes con un raycast
            Vector3f origen = objetivoOrbita.getWorldTranslation();
            Vector3f haciaCamera = cam.getLocation().subtract(origen);
            float distancia = haciaCamera.length();
            if (distancia > 0.1f) {
                Ray rayo = new Ray(origen, haciaCamera.normalizeLocal());
                CollisionResults resultados = new CollisionResults();
                rootNode.collideWith(rayo, resultados);
                if (resultados.size() > 0) {
                    float impacto = resultados.getClosestCollision().getDistance();
                    if (impacto < distancia) {
                        cam.setLocation(origen.add(haciaCamera.mult(impacto - 0.3f)));
                    }
                }
            }
            return;
        }

        Vector3f camDir  = cam.getDirection().clone();
        Vector3f camLeft = cam.getLeft().clone();
        camDir.y  = 0;
        camLeft.y = 0;
        camDir.normalizeLocal();
        camLeft.normalizeLocal();

        Vector3f dir = new Vector3f();
        if (adelante) dir.addLocal(camDir);
        if (atras)    dir.addLocal(camDir.negate());
        if (izq)      dir.addLocal(camLeft);
        if (der)      dir.addLocal(camLeft.negate());

        jugador.setWalkDirection(dir.mult(4f * tpf));
        cam.setLocation(jugador.getPhysicsLocation().add(0, 0.65f, 0));
    }
}
