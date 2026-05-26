/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package casa;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.glu.GLU;

/**
 * Camara en primera persona.
 */
public class Camera {

    public float x, y, z;
    private float yaw = -90f;
    private float pitch = 0f;

    private float fX, fY, fZ; // front
    private float rX, rZ;     // right

    private GLU glu = new GLU();

    public Camera(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        update();
    }

    public void rotate(float dYaw, float dPitch) {
        yaw += dYaw;
        pitch += dPitch;
        if (pitch > 89f) pitch = 89f;
        if (pitch < -89f) pitch = -89f;
        update();
    }

    private void update() {
        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        fX = (float) (Math.cos(pr) * Math.cos(yr));
        fY = (float) Math.sin(pr);
        fZ = (float) (Math.cos(pr) * Math.sin(yr));
        float len = (float) Math.sqrt(fX * fX + fY * fY + fZ * fZ);
        fX /= len; fY /= len; fZ /= len;
        rX = (float) Math.cos(yr + Math.toRadians(90));
        rZ = (float) Math.sin(yr + Math.toRadians(90));
    }

    public void applyView(GL2 gl) {
        glu.gluLookAt(x, y, z, x + fX, y + fY, z + fZ, 0, 1, 0);
    }

    public float frontX() { return fX; }
    public float frontZ() { return fZ; }
    public float rightX() { return rX; }
    public float rightZ() { return rZ; }
}