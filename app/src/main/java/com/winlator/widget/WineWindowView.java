package com.winlator.widget;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;

import com.winlator.WineSession;
import com.winlator.WinlatorConstants;
import com.winlator.renderer.GLRenderer;
import com.winlator.xserver.Pointer;
import com.winlator.xserver.XServer;

/**
 * Одно окно Wine внутри окна WinArs: показывает заданную область X-экрана (границы окна программы)
 * и превращает касания / перо / мышь в абсолютные события X-сервера (1:1, без чувствительности).
 */
public class WineWindowView extends TextureView implements TextureView.SurfaceTextureListener {
    private final WineSession session;
    private final int xid;
    private final GLRenderer.RegionTarget target = new GLRenderer.RegionTarget();
    private Surface surface;
    private boolean leftDown = false;
    private boolean penRight = false;
    private boolean twoFingerHandled = false;

    public WineWindowView(Context context, WineSession session, int xid) {
        super(context);
        this.session = session;
        this.xid = xid;
        setOpaque(true);
        setFocusable(true);
        setSurfaceTextureListener(this);
    }

    public int getXid() {
        return xid;
    }

    /** Область X-экрана (координаты корневого окна), которую показывает это окно. */
    public void setRegion(int x, int y, int width, int height) {
        target.regionX = x;
        target.regionY = y;
        target.regionWidth = Math.max(1, width);
        target.regionHeight = Math.max(1, height);
        GLRenderer renderer = renderer();
        if (renderer != null) renderer.requestRegionRender();
    }

    private GLRenderer renderer() {
        XServerView view = session.getXServerView();
        return view != null ? view.getRenderer() : null;
    }

    // ---------------- поверхность ----------------

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
        surface = new Surface(st);
        target.surface = surface;
        target.width = Math.max(1, width);
        target.height = Math.max(1, height);
        GLRenderer renderer = renderer();
        if (renderer != null) renderer.addRegionTarget(target);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
        target.width = Math.max(1, width);
        target.height = Math.max(1, height);
        GLRenderer renderer = renderer();
        if (renderer != null) renderer.requestRegionRender();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        GLRenderer renderer = renderer();
        if (renderer != null) renderer.removeRegionTarget(target);
        target.surface = null;
        if (surface != null) { surface.release(); surface = null; }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {}

    // ---------------- ввод: абсолютные координаты ----------------

    private int mapX(float x) {
        return target.regionX + Math.round(x / Math.max(1, getWidth()) * target.regionWidth);
    }

    private int mapY(float y) {
        return target.regionY + Math.round(y / Math.max(1, getHeight()) * target.regionHeight);
    }

    private boolean isPen(MotionEvent e) {
        return e.getToolType(e.getActionIndex()) == MotionEvent.TOOL_TYPE_STYLUS
            || e.getDeviceId() == WinlatorConstants.PEN_REMAPPED_DEVICE_ID;
    }

    private void releaseLeft(XServer xs) {
        if (leftDown) {
            xs.injectPointerButtonRelease(penRight ? Pointer.Button.BUTTON_RIGHT : Pointer.Button.BUTTON_LEFT);
            leftDown = false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        XServer xs = session.getXServer();
        if (xs == null) return false;

        int x = mapX(e.getX());
        int y = mapY(e.getY());
        int action = e.getActionMasked();

        // реальная мышь: кнопки приходят через onGenericMotionEvent, здесь только движение
        if (e.isFromSource(InputDevice.SOURCE_MOUSE) && e.getDeviceId() != WinlatorConstants.PEN_REMAPPED_DEVICE_ID) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) xs.injectPointerMove(x, y);
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                twoFingerHandled = false;
                session.focusXWindow(xid);
                xs.injectPointerMove(x, y);
                penRight = isPen(e) && ((e.getButtonState() & (MotionEvent.BUTTON_STYLUS_PRIMARY | MotionEvent.BUTTON_SECONDARY)) != 0
                    || e.getDeviceId() == WinlatorConstants.PEN_REMAPPED_DEVICE_ID);
                xs.injectPointerButtonPress(penRight ? Pointer.Button.BUTTON_RIGHT : Pointer.Button.BUTTON_LEFT);
                leftDown = true;
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                // второй палец = правый клик в текущей точке
                if (!twoFingerHandled && !isPen(e)) {
                    twoFingerHandled = true;
                    releaseLeft(xs);
                    xs.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT);
                    xs.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!twoFingerHandled) xs.injectPointerMove(x, y);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!twoFingerHandled) xs.injectPointerMove(x, y);
                releaseLeft(xs);
                twoFingerHandled = false;
                return true;
        }
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        XServer xs = session.getXServer();
        if (xs == null) return false;

        int x = mapX(e.getX());
        int y = mapY(e.getY());
        int action = e.getActionMasked();

        if (action == MotionEvent.ACTION_HOVER_MOVE) {         // мышь или перо над экраном
            xs.injectPointerMove(x, y);
            return true;
        }

        if (e.isFromSource(InputDevice.SOURCE_MOUSE)) {
            switch (action) {
                case MotionEvent.ACTION_BUTTON_PRESS:
                    xs.injectPointerMove(x, y);
                    session.focusXWindow(xid);
                    xs.injectPointerButtonPress(toButton(e.getActionButton()));
                    return true;
                case MotionEvent.ACTION_BUTTON_RELEASE:
                    xs.injectPointerButtonRelease(toButton(e.getActionButton()));
                    return true;
                case MotionEvent.ACTION_SCROLL:
                    float v = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
                    if (v != 0) {
                        Pointer.Button b = v > 0 ? Pointer.Button.BUTTON_SCROLL_UP : Pointer.Button.BUTTON_SCROLL_DOWN;
                        xs.injectPointerButtonPress(b);
                        xs.injectPointerButtonRelease(b);
                    }
                    return true;
            }
        }
        return super.onGenericMotionEvent(e);
    }

    private Pointer.Button toButton(int androidButton) {
        switch (androidButton) {
            case MotionEvent.BUTTON_SECONDARY: return Pointer.Button.BUTTON_RIGHT;
            case MotionEvent.BUTTON_TERTIARY: return Pointer.Button.BUTTON_MIDDLE;
            default: return Pointer.Button.BUTTON_LEFT;
        }
    }
}
