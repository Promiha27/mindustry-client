package eui.util;

import arc.Core;

/** Ported from utils/camera.js. */
public class CameraUtil{
    public static boolean isIn(float x, float y){
        var camera = Core.camera;
        return Math.abs(camera.position.x - x) < camera.width * 0.5f && Math.abs(camera.position.y - y) < camera.height * 0.5f;
    }
}
