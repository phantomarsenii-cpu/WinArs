package com.winlator;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import com.winlator.core.Bitmask;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;
import com.google.android.material.navigation.NavigationView;
import com.winlator.alsaserver.ALSAClient;
import com.winlator.container.AudioDrivers;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.DXWrappers;
import com.winlator.container.GraphicsDrivers;
import com.winlator.container.Shortcut;
import com.winlator.contentdialog.AudioDriverConfigDialog;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.contentdialog.DXVKConfigDialog;
import com.winlator.contentdialog.DebugDialog;
import com.winlator.contentdialog.TurnipConfigDialog;
import com.winlator.contentdialog.VKD3DConfigDialog;
import com.winlator.contentdialog.VirGLConfigDialog;
import com.winlator.contentdialog.WineD3DConfigDialog;
import com.winlator.core.AppUtils;
import com.winlator.core.DefaultVersion;
import com.winlator.core.EnvVars;
import com.winlator.core.FileUtils;
import com.winlator.core.GeneralComponents;
import com.winlator.core.KeyValueSet;
import com.winlator.core.LocaleHelper;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.ProcessHelper;
import com.winlator.core.StringUtils;
import com.winlator.core.TarCompressorUtils;
import com.winlator.core.Win32AppWorkarounds;
import com.winlator.core.WineInfo;
import com.winlator.core.WineInstaller;
import com.winlator.core.WineRegistryEditor;
import com.winlator.core.WineStartMenuCreator;
import com.winlator.core.WineThemeManager;
import com.winlator.core.WineUtils;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.ExternalController;
import com.winlator.inputcontrols.InputControlsManager;
import com.winlator.math.Mathf;
import com.winlator.renderer.GLRenderer;
import com.winlator.services.ForegroundService;
import com.winlator.widget.FrameRating;
import com.winlator.widget.InputControlsView;
import com.winlator.widget.MagnifierView;
import com.winlator.widget.TouchpadView;
import com.winlator.widget.XServerView;
import com.winlator.winhandler.WinHandler;
import com.winlator.xconnector.UnixSocketConfig;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.XEnvironment;
import com.winlator.xenvironment.components.ALSAServerComponent;
import com.winlator.xenvironment.components.GuestProgramLauncherComponent;
import com.winlator.xenvironment.components.NetworkInfoUpdateComponent;
import com.winlator.xenvironment.components.PulseAudioComponent;
import com.winlator.xenvironment.components.SysVSharedMemoryComponent;
import com.winlator.xenvironment.components.VirGLRendererComponent;
import com.winlator.xenvironment.components.VortekRendererComponent;
import com.winlator.xenvironment.components.XServerComponent;
import com.winlator.xserver.Atom;
import com.winlator.xserver.Property;
import com.winlator.xserver.ScreenInfo;
import com.winlator.xserver.Window;
import com.winlator.xserver.WindowManager;
import com.winlator.xserver.XKeycode;
import com.winlator.xserver.XLock;
import com.winlator.xserver.XServer;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Невидимое ядро: Wine + Box64 + XServer без какого-либо интерфейса Winlator.
 * Заменяет XServerDisplayActivity. Рисует X-экран в обычный View ({@link #getView()}), который оболочка WinArs
 * встраивает в свои окна. Context-обёртка: все хелперы Winlator, которым раньше передавали Activity, работают как раньше.
 */
public class WineSession extends ContextWrapper {

    public interface Listener {
        /** Появилось первое окно Wine — можно показывать экран. */
        void onReady();
        /** Изменился список окон Wine (для панели задач). */
        void onWindowsChanged();
        /** Закрылась последняя программа Wine. X-сервер и сессия остаются живыми. */
        void onGuestExit();
        /** Сессия остановлена целиком (X-сервер/ядро). */
        void onExit();
        void onError(String message);
    }

    public static final class WindowInfo {
        public int id;
        public String title;
        public String className;
        public long handle;
        public boolean iconic;
        /** положение и размер в координатах X-экрана (корневого окна) */
        public int x, y, width, height;
        public boolean dialog;
    }

    private XServerView xServerView;
    private FrameLayout viewRoot;
    private InputControlsView inputControlsView;
    private TouchpadView touchpadView;
    private XEnvironment environment;
    private Container container;
    private XServer xServer;
    private RootFS rootFS;
    private Shortcut shortcut;
    private String[] graphicsDriver = {GraphicsDrivers.DEFAULT_VULKAN_DRIVER, GraphicsDrivers.DEFAULT_OPENGL_DRIVER};
    private String audioDriver = Container.DEFAULT_AUDIO_DRIVER;
    private String dxwrapper = Container.DEFAULT_DXWRAPPER;
    private ScreenInfo screenInfo = new ScreenInfo(Container.DEFAULT_SCREEN_SIZE);
    private KeyValueSet[] dxwrapperConfig;
    private KeyValueSet[] graphicsDriverConfig = {new KeyValueSet(), new KeyValueSet()};
    private KeyValueSet audioDriverConfig;
    private String wincomponents;
    private WineInfo wineInfo;
    private final EnvVars envVars = new EnvVars();
    private EnvVars overrideEnvVars;
    private SharedPreferences preferences;
    private final WinHandler winHandler = new WinHandler(this);
    private Win32AppWorkarounds win32AppWorkarounds;
    private String screenEffectProfile;
    private String unixExecPath;
    private Listener listener;
    private boolean ready = false;
    private volatile boolean stopped = false;
    private GuestProgramLauncherComponent guestLauncher;
    private volatile boolean guestRunning = false;
    private boolean maximizedOnce = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    public WineSession(Context base) {
        super(base.getApplicationContext());
    }

    /** Запуск. Вызывать в UI-потоке. execPath — путь к exe в файловой системе Android (null = рабочий стол Wine). */
    public void start(Container container, String execPath, Listener listener) {
        this.container = container;
        this.unixExecPath = execPath;
        this.listener = listener;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.rootFS = RootFS.find(this);

        ProcessHelper.removeAllDebugCallbacks();
        new ContainerManager(this).activateContainer(container);
        ForegroundService.startSession(this);

        if (container.getExtra("wineprefixNeedsUpdate").equals("t")) {
            WineUtils.updateWineprefix(this, (status) -> {
                if (status == 0) {
                    container.putExtra("wineprefixNeedsUpdate", null);
                    container.putExtra("wincomponents", null);
                    container.saveData();
                    uiHandler.post(this::continueStart);
                }
                else fail("Не удалось обновить системные файлы Wine");
            });
            return;
        }
        continueStart();
    }

    private void fail(String message) {
        uiHandler.post(() -> { if (listener != null) listener.onError(message); });
        stop();
    }

    private void continueStart() {
        win32AppWorkarounds = new Win32AppWorkarounds(this);

        String wineVersion = container.getWineVersion();
        wineInfo = WineInfo.fromIdentifier(this, wineVersion);
        if (wineInfo != WineInfo.MAIN_WINE_INFO) rootFS.setWinePath(wineInfo.path);

        String graphicsDriver = container.getGraphicsDriver();
        audioDriver = container.getAudioDriver();
        String dxwrapper = container.getDXWrapper();
        wincomponents = container.getWinComponents();
        String dxwrapperConfig = container.getDXWrapperConfig();
        String graphicsDriverConfig = container.getGraphicsDriverConfig();
        audioDriverConfig = new KeyValueSet(container.getAudioDriverConfig());
        screenInfo = new ScreenInfo(container.getScreenSize());

        if (unixExecPath != null) win32AppWorkarounds.applyStartupWorkarounds(FileUtils.getName(unixExecPath));

        this.graphicsDriver = GraphicsDrivers.parseIdentifiers(graphicsDriver);
        this.graphicsDriverConfig = GraphicsDrivers.parseConfigs(graphicsDriver, graphicsDriverConfig);
        this.dxwrapper = DXWrappers.parseIdentifier(dxwrapper);
        this.dxwrapperConfig = DXWrappers.parseConfigs(dxwrapper, dxwrapperConfig);

        xServer = new XServer(this, screenInfo);
        xServer.setWinHandler(winHandler);
        final boolean hideDesktop = shortcut != null || unixExecPath != null;
        xServer.windowManager.addOnWindowModificationListener(new WindowManager.OnWindowModificationListener() {
            @Override
            public void onMapWindow(Window window) {
                if (!ready && window.isRenderable() && !window.getClassName().isEmpty()) {
                    ready = true;
                    uiHandler.post(() -> {
                        if (xServerView != null) xServerView.getRenderer().setCursorVisible(true);
                        if (listener != null) listener.onReady();
                    });
                }

                // первое окно программы разворачиваем на весь X-экран: он совпадает с моим окном, чёрных полей нет
                if (!maximizedOnce && ready && preferences.getBoolean("wa_auto_maximize", false)
                        && window.isRenderable() && !window.isDesktopWindow() && !window.getName().isEmpty()) {
                    maximizedOnce = true;
                    final long handle = window.getHandle();
                    uiHandler.postDelayed(() -> { if (!stopped) winHandler.showWindow(handle, 3); }, 600);
                }

                if (hideDesktop && window.attributes.isViewable() && window.isDesktopWindow()) {
                    window.attributes.setViewable(false);
                    if (window.attributes.isEnabled()) window.disableAllDescendants();
                }

                if (win32AppWorkarounds != null) win32AppWorkarounds.applyWindowWorkarounds(window);
                notifyWindows();
            }

            @Override
            public void onUnmapWindow(Window window) {
                notifyWindows();
            }

            @Override
            public void onUpdateWindowAttributes(Window window, Bitmask mask) {
                notifyWindows();
            }
        });

        setupUI();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                setupWineSystemFiles();
                extractGraphicsDriverFiles();
                changeWineAudioDriver();
                setupXEnvironment();
            }
            catch (Throwable t) {
                fail("Ошибка запуска Wine: " + t);
            }
        });
    }

    private void notifyWindows() {
        uiHandler.post(() -> { if (listener != null && !stopped) listener.onWindowsChanged(); });
    }

    /** X-экран: XServerView + сенсорный ввод. Его встраивает оболочка в своё окно. */
    private void setupUI() {
        xServerView = new XServerView(this, xServer);
        final GLRenderer renderer = xServerView.getRenderer();
        renderer.setCursorVisible(false);
        renderer.setCursorColor(preferences.getInt("cursor_color", 0xffffff));
        renderer.setCursorScale(preferences.getFloat("cursor_scale", 1.0f));
        xServer.setRenderer(renderer);


        touchpadView = new TouchpadView(this, xServer, true);
        // прямое соответствие 1:1: касание/перо = точка на X-экране (без относительного режима и множителя)
        touchpadView.setSensitivity(1.0f);
        touchpadView.setMoveCursorToTouchpoint(true);

        // нужен GamepadHandler; на экран не добавляется
        inputControlsView = new InputControlsView(this);
        inputControlsView.setTouchpadView(touchpadView);
        inputControlsView.setXServer(xServer);
    }

    public View getView() {
        return viewRoot;
    }

    public ScreenInfo getScreenSizeInfo() {
        return screenInfo;
    }

    /** Остановка Wine и всего окружения. */
    public void stop() {
        if (stopped) return;
        stopped = true;
        try { winHandler.stop(); } catch (Throwable ignored) {}
        try { if (environment != null) environment.stopEnvironmentComponents(); } catch (Throwable ignored) {}
        ForegroundService.stopSession(this);
        uiHandler.post(() -> { if (listener != null) listener.onExit(); });
    }

    private void exit() {
        stop();
    }

    // ---------- окна Wine для панели задач ----------

    private volatile List<WindowInfo> lastWindows = new ArrayList<>();
    private int focusedXid = -1;

    public List<WindowInfo> getActiveWindows() {
        ArrayList<WindowInfo> result = new ArrayList<>();
        if (xServer == null || stopped) return result;
        try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.DRAWABLE_MANAGER)) {
            collectTopLevel(xServer.windowManager.rootWindow, 0, 0, result);
        }
        catch (Throwable ignored) {}
        lastWindows = result;
        return result;
    }

    /** Окно программы: показано, не рабочий стол, не крошечное, с заголовком (или достаточно большое). */
    private boolean isAppWindow(Window window) {
        if (window.isDesktopWindow() || !window.attributes.isMapped()) return false;
        if (window.getWidth() < 32 || window.getHeight() < 24) return false;
        boolean named = !window.getName().isEmpty();
        return named || (window.getWidth() >= 320 && window.getHeight() >= 200);
    }

    private void collectTopLevel(Window parent, int parentX, int parentY, ArrayList<WindowInfo> result) {
        for (Window child : parent.getChildren()) {
            if (!child.attributes.isMapped()) continue;
            int cx = parentX + child.getX();
            int cy = parentY + child.getY();
            if (isAppWindow(child)) {
                WindowInfo info = new WindowInfo();
                info.id = child.id;
                info.title = child.getName();
                info.className = child.getClassName();
                info.handle = child.getHandle();
                info.iconic = child.isIconic();
                info.x = cx;
                info.y = cy;
                info.width = child.getWidth();
                info.height = child.getHeight();
                info.dialog = child.isDialogBox() || child.getTransientFor() != 0;
                result.add(info);
            }
            else collectTopLevel(child, cx, cy, result);
        }
    }

    private WindowInfo findWindow(int xid) {
        for (WindowInfo info : lastWindows) if (info.id == xid) return info;
        return null;
    }

    /** Окно получило касание: передаём ему фокус Windows и выносим наверх. */
    public void focusXWindow(int xid) {
        if (focusedXid == xid) return;
        WindowInfo info = findWindow(xid);
        if (info == null) return;
        focusedXid = xid;
        winHandler.bringToFront(info.className, info.handle);
    }

    /** Закрыть программу так же, как Alt+F4 на ПК: приложение само решит, закрываться ли. */
    public void closeXWindow(int xid) {
        WindowInfo info = findWindow(xid);
        if (info == null || xServer == null) return;
        winHandler.bringToFront(info.className, info.handle);
        uiHandler.postDelayed(() -> {
            if (xServer == null || stopped) return;
            xServer.injectKeyPress(XKeycode.KEY_ALT_L);
            xServer.injectKeyPress(XKeycode.KEY_F4);
            xServer.injectKeyRelease(XKeycode.KEY_F4);
            xServer.injectKeyRelease(XKeycode.KEY_ALT_L);
        }, 150);
    }

    /** Клавиатура телефона/USB в активное окно Wine. */
    public boolean dispatchKey(android.view.KeyEvent event) {
        return xServer != null && !stopped && xServer.keyboard.onKeyEvent(event);
    }

    public void bringToFront(WindowInfo info) {
        winHandler.bringToFront(info.className, info.handle);
    }

    public void showDesktop() {
        winHandler.showDesktop();
    }

    public boolean isGuestRunning() {
        return guestRunning;
    }

    /** Запустить программу: внутри работающего Wine, а если Wine уже закрылся — заново поднять его в этой же сессии. */
    public void runProgram(String unixPath) {
        if (guestRunning) {
            winHandler.exec(WineUtils.unixToDOSPath(unixPath, container));
            return;
        }
        launchProgram(unixPath);
    }

    private void launchProgram(String unixPath) {
        if (guestLauncher == null || stopped) return;
        unixExecPath = unixPath;
        ready = false;
        maximizedOnce = false;
        guestRunning = true;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String guestExecutable = "wine explorer /desktop=nogui,"+xServer.screenInfo+" "+getWineStartCommand();
                guestLauncher.setGuestExecutable(guestExecutable);
                guestLauncher.start();
            }
            catch (Throwable t) {
                fail("Не удалось запустить программу: " + t);
            }
        });
    }

    /** Закрыть все программы Wine, не останавливая X-сервер. */
    public void stopGuest() {
        if (guestLauncher != null) guestLauncher.stop();
        onGuestExit();
    }

    private void onGuestExit() {
        guestRunning = false;
        uiHandler.post(() -> { if (listener != null && !stopped) listener.onGuestExit(); });
    }

    /**
     * Убираем рамки Wine: заголовок и толщина границ окон = 0, у программы остаётся только моя шапка.
     * Wine рисует рамку сам (оконного менеджера нет), поэтому гасим её через метрики окон в реестре.
     */
    private void applyUndecoratedMetrics() {
        File userRegFile = new File(rootFS.getRootDir(), RootFS.WINEPREFIX+"/user.reg");
        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            String key = "Control Panel\\Desktop\\WindowMetrics";
            registryEditor.setStringValue(key, "CaptionHeight", "0");
            registryEditor.setStringValue(key, "CaptionWidth", "0");
            registryEditor.setStringValue(key, "SmCaptionHeight", "0");
            registryEditor.setStringValue(key, "SmCaptionWidth", "0");
            registryEditor.setStringValue(key, "BorderWidth", "0");
            registryEditor.setStringValue(key, "PaddedBorderWidth", "0");
        }
    }

    // ---------- перенесено из XServerDisplayActivity ----------

    private void setupWineSystemFiles() {
        String appVersion = String.valueOf(AppUtils.getVersionCode(this));
        String rfsVersion = String.valueOf(rootFS.getVersion());
        boolean containerDataChanged = false;

        boolean wineprefixWasUpdated = WineUtils.isWineprefixWasUpdated(container);
        if (!container.getExtra("appVersion").equals(appVersion) || !container.getExtra("rfsVersion").equals(rfsVersion) || wineprefixWasUpdated) {
            applyGeneralPatches(container);
            container.putExtra("appVersion", appVersion);
            container.putExtra("rfsVersion", rfsVersion);
            containerDataChanged = true;
        }

        if (verifyUserRegistry()) containerDataChanged = true;
        if (extractDXWrapperFiles()) containerDataChanged = true;

        if (!wincomponents.equals(container.getExtra("wincomponents"))) {
            extractWinComponentFiles();
            container.putExtra("wincomponents", wincomponents);
            containerDataChanged = true;
        }

        String desktopTheme = container.getDesktopTheme();
        if (!(desktopTheme+","+xServer.screenInfo).equals(container.getExtra("desktopTheme"))) {
            WineThemeManager.apply(this, new WineThemeManager.ThemeInfo(desktopTheme), xServer.screenInfo);
            applyUndecoratedMetrics();
            container.putExtra("desktopTheme", desktopTheme+","+xServer.screenInfo);
            containerDataChanged = true;
        }

        WineStartMenuCreator.create(this, container);
        WineUtils.createDosdevicesSymlinks(container, true);

        String startupSelection = String.valueOf(container.getStartupSelection());
        if (!startupSelection.equals(container.getExtra("startupSelection")) || wineprefixWasUpdated) {
            WineUtils.changeServicesStatus(container, container.getStartupSelection());
            container.putExtra("startupSelection", startupSelection);
            containerDataChanged = true;
        }

        boolean openAndroidBrowserFromWine = preferences.getBoolean("open_android_browser_from_wine", true);
        String openAndroidBrowserFromWineStr = openAndroidBrowserFromWine ? "t" : "f";
        if (!openAndroidBrowserFromWineStr.equals(container.getExtra("openAndroidBrowserFromWine")) || wineprefixWasUpdated) {
            WineUtils.changeBrowsersRegistryKey(container, openAndroidBrowserFromWine);
            container.putExtra("openAndroidBrowserFromWine", openAndroidBrowserFromWineStr);
            containerDataChanged = true;
        }

        if (containerDataChanged) container.saveData();
    }

    private void setupXEnvironment() {
        String rootPath = rootFS.getRootDir().getPath();
        envVars.put("MESA_DEBUG", "silent");
        envVars.put("MESA_NO_ERROR", "1");
        envVars.put("WINEPREFIX", rootPath+RootFS.WINEPREFIX);
        envVars.put("WINE_DO_NOT_CREATE_DXGI_DEVICE_MANAGER", "1");

        boolean enableWineDebug = preferences.getBoolean("enable_wine_debug", false);
        String wineDebugChannels = preferences.getString("wine_debug_channels", WinlatorConstants.DEFAULT_WINE_DEBUG_CHANNELS);
        envVars.put("WINEDEBUG", enableWineDebug && !wineDebugChannels.isEmpty() ? "+"+wineDebugChannels.replace(",", ",+") : "-all");

        FileUtils.clear(rootFS.getTmpDir());

        GuestProgramLauncherComponent guestProgramLauncherComponent = new GuestProgramLauncherComponent();

        if (container != null) {
            if (container.getHUDMode() == FrameRating.Mode.FULL.ordinal()) envVars.put("X11_WND_GPU_INFO", "1");

            String desktopName = shortcut != null || unixExecPath != null ? "nogui" : "shell";
            String guestExecutable = "wine explorer /desktop="+desktopName+","+xServer.screenInfo+" "+getWineStartCommand();
            guestProgramLauncherComponent.setGuestExecutable(guestExecutable);

            envVars.putAll(container.getEnvVars());
            if (shortcut != null) envVars.putAll(shortcut.getExtra("envVars"));
            if (!envVars.has("WINEESYNC")) envVars.put("WINEESYNC", "1");

            guestProgramLauncherComponent.setBox64Preset(shortcut != null ? shortcut.getExtra("box64Preset", container.getBox64Preset()) : container.getBox64Preset());
        }

        environment = new XEnvironment(this, rootFS);
        environment.addComponent(new SysVSharedMemoryComponent(xServer, UnixSocketConfig.create(rootPath, UnixSocketConfig.SYSVSHM_SERVER_PATH)));
        environment.addComponent(new XServerComponent(xServer, UnixSocketConfig.create(rootPath, UnixSocketConfig.XSERVER_PATH)));
        environment.addComponent(new NetworkInfoUpdateComponent());

        if (audioDriver.equals(AudioDrivers.ALSA)) {
            envVars.put("ANDROID_ALSA_SERVER", rootPath+UnixSocketConfig.ALSA_SERVER_PATH);
            envVars.put("ANDROID_ASERVER_USE_SHM", ALSAClient.USE_SHARED_MEMORY ? "true" : "false");

            ALSAClient.Options options = ALSAClient.Options.fromKeyValueSet(audioDriverConfig);
            environment.addComponent(new ALSAServerComponent(UnixSocketConfig.create(rootPath, UnixSocketConfig.ALSA_SERVER_PATH), options));
        }
        else if (audioDriver.equals(AudioDrivers.PULSEAUDIO)) {
            PulseAudioComponent pulseAudioComponent = new PulseAudioComponent(UnixSocketConfig.create(rootPath, UnixSocketConfig.PULSE_SERVER_PATH));
            envVars.put("PULSE_SERVER", rootPath+UnixSocketConfig.PULSE_SERVER_PATH);

            if (!audioDriverConfig.isEmpty()) {
                envVars.put("PULSE_LATENCY_MSEC", audioDriverConfig.getInt("latencyMillis", AudioDriverConfigDialog.DEFAULT_LATENCY_MILLIS));
                pulseAudioComponent.setVolume(audioDriverConfig.getFloat("volume", AudioDriverConfigDialog.DEFAULT_VOLUME));
                pulseAudioComponent.setPerformanceMode(audioDriverConfig.getInt("performanceMode", AudioDriverConfigDialog.DEFAULT_PERFORMANCE_MODE));
            }
            else envVars.put("PULSE_LATENCY_MSEC", AudioDriverConfigDialog.DEFAULT_LATENCY_MILLIS);
            environment.addComponent(pulseAudioComponent);
        }

        if (graphicsDriver[0].equals(GraphicsDrivers.VORTEK)) {
            VortekRendererComponent.Options options = VortekRendererComponent.Options.fromKeyValueSet(this, graphicsDriverConfig[0]);
            VortekRendererComponent vortekRendererComponent = new VortekRendererComponent(xServer, UnixSocketConfig.create(rootPath, UnixSocketConfig.VORTEK_SERVER_PATH), options);
            environment.addComponent(vortekRendererComponent);
        }
        if (graphicsDriver[1].equals(GraphicsDrivers.VIRGL)) {
            environment.addComponent(new VirGLRendererComponent(xServer, UnixSocketConfig.create(rootPath, UnixSocketConfig.VIRGL_SERVER_PATH)));
        }

        guestProgramLauncherComponent.setEnvVars(envVars);
        guestLauncher = guestProgramLauncherComponent;
        guestRunning = true;
        // закрылась программа — это НЕ конец системы: сессия остаётся, X-сервер живёт
        guestProgramLauncherComponent.setTerminationCallback((status) -> onGuestExit());
        environment.addComponent(guestProgramLauncherComponent);

        if (overrideEnvVars != null) {
            envVars.putAll(overrideEnvVars);
            overrideEnvVars = null;
        }
        environment.startEnvironmentComponents();

        winHandler.start();
        envVars.clear();
        graphicsDriver = null;
        dxwrapperConfig = null;
        graphicsDriverConfig = null;
        audioDriver = null;
        audioDriverConfig = null;
        wincomponents = null;
    }

    private void extractGraphicsDriverFiles() {
        envVars.put("vblank_mode", "0");

        String cacheId = "";
        if (graphicsDriver[0].equals(GraphicsDrivers.TURNIP)) {
            cacheId += graphicsDriver[0]+"-"+graphicsDriverConfig[0].get("version", DefaultVersion.TURNIP);
        }
        else cacheId += graphicsDriver[0]+"-"+DefaultVersion.valueOf(graphicsDriver[0]);
        cacheId += "-"+graphicsDriver[1]+"-"+DefaultVersion.valueOf(graphicsDriver[1]);

        String currentGraphicsDriver = preferences.getString("current_graphics_driver", "");
        boolean changed = !cacheId.equals(currentGraphicsDriver);
        File rootDir = rootFS.getRootDir();
        File libDir = rootFS.getLibDir();

        if (changed) {
            FileUtils.delete(new File(libDir, "libvulkan_freedreno.so"));
            FileUtils.delete(new File(libDir, "libvulkan_vortek.so"));
            FileUtils.delete(new File(libDir, "libGL.so.1.7.0"));

            File vulkanICDDir = new File(rootDir, "/usr/share/vulkan/icd.d");
            FileUtils.delete(vulkanICDDir);
            vulkanICDDir.mkdirs();

            preferences.edit().putString("current_graphics_driver", cacheId).apply();
        }

        if (graphicsDriver[0].equals(GraphicsDrivers.TURNIP)) {
            TurnipConfigDialog.setEnvVars(this, graphicsDriverConfig[0], envVars);

            if (changed) {
                String version = graphicsDriverConfig[0].get("version", DefaultVersion.TURNIP);
                GeneralComponents.extractFile(GeneralComponents.Type.TURNIP, this, version, DefaultVersion.TURNIP);
            }
        }
        else if (graphicsDriver[0].equals(GraphicsDrivers.VORTEK) && (changed || WinlatorConstants.DEBUG_MODE)) {
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/vortek-" + DefaultVersion.VORTEK + ".tzst", rootDir);
        }

        switch (graphicsDriver[1]) {
            case GraphicsDrivers.ZINK:
                envVars.put("GALLIUM_DRIVER", "zink");
                envVars.put("ZINK_CONTEXT_THREADED", "1");
                if (graphicsDriver[0].equals(GraphicsDrivers.VORTEK)) envVars.put("MESA_GL_VERSION_OVERRIDE", "3.3");

                if (changed) TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/zink-"+DefaultVersion.ZINK+".tzst", rootDir);
                break;
            case GraphicsDrivers.VIRGL:
                envVars.put("GALLIUM_DRIVER", "virpipe");
                envVars.put("VIRGL_NO_READBACK", "true");
                envVars.put("VIRGL_SERVER_PATH", rootDir+UnixSocketConfig.VIRGL_SERVER_PATH);
                VirGLConfigDialog.setEnvVars(graphicsDriverConfig[1], envVars);

                if (changed) TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/virgl-"+DefaultVersion.VIRGL+".tzst", rootDir);
                break;
            case GraphicsDrivers.GLADIO:
                envVars.put("GLADIO_NO_ERROR", "1");

                if (changed || WinlatorConstants.DEBUG_MODE) TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/gladio-"+DefaultVersion.GLADIO+".tzst", rootDir);
                break;
        }
    }

    private boolean extractDXWrapperFiles() {
        String cacheId = "";
        if (dxwrapper.equals(DXWrappers.DXVK)) {
            DXVKConfigDialog.setEnvVars(this, dxwrapperConfig[0], envVars);
            cacheId += dxwrapper+"-"+dxwrapperConfig[0].get("version", DefaultVersion.DXVK(graphicsDriver[0]));
        }
        else if (dxwrapper.equals(DXWrappers.WINED3D)) {
            WineD3DConfigDialog.setEnvVars(dxwrapperConfig[0], envVars);
            cacheId += dxwrapper+"-"+dxwrapperConfig[0].get("version", DefaultVersion.WINED3D);
        }

        String ddrawWrapper = dxwrapperConfig[0].get("ddrawWrapper", DXWrappers.WINED3D);
        cacheId += "-"+DXWrappers.VKD3D+"-"+dxwrapperConfig[1].get("version", DefaultVersion.VKD3D)+"-"+ddrawWrapper;
        boolean changed = !cacheId.equals(container.getExtra("dxwrapper"));
        VKD3DConfigDialog.setEnvVars(dxwrapperConfig[1], envVars);

        if (ddrawWrapper.equals(DXWrappers.CNC_DDRAW)) envVars.put("CNC_DDRAW_CONFIG_FILE", "C:\\ProgramData\\cnc-ddraw\\ddraw.ini");

        if (!changed) return false;
        container.putExtra("dxwrapper", cacheId);

        File rootDir = rootFS.getRootDir();
        File windowsDir = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/windows");

        if (dxwrapper.equals(DXWrappers.WINED3D)) {
            String version = dxwrapperConfig[0].get("version", DefaultVersion.WINED3D);
            if (version.equals(WineInfo.MAIN_WINE_VERSION)) {
                final String[] dlls = {"d3d8.dll", "d3d9.dll", "d3d10.dll", "d3d10_1.dll", "d3d10core.dll", "d3d11.dll", "d3d12.dll", "d3d12core.dll", "dxgi.dll", "ddraw.dll", "wined3d.dll"};
                restoreBuiltinDllFiles(dlls);
            }
            else GeneralComponents.extractFile(GeneralComponents.Type.WINED3D, this, version, DefaultVersion.WINED3D);
        }
        else if (dxwrapper.equals(DXWrappers.DXVK)) {
            final boolean[] hasD3D8DllFile = {false};
            final boolean[] hasD3D10DllFile = {false};

            GeneralComponents.extractFile(GeneralComponents.Type.DXVK, this, dxwrapperConfig[0].get("version"), DefaultVersion.DXVK(graphicsDriver[0]), (destination, size) -> {
                String name = destination.getName();
                if (name.equals("d3d10.dll")) {
                    hasD3D10DllFile[0] = true;
                }
                else if (name.equals("d3d8.dll")) {
                    hasD3D8DllFile[0] = true;
                }
                return destination;
            });

            if (!hasD3D8DllFile[0]) {
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/d8vk-"+DefaultVersion.D8VK+".tzst", windowsDir);
            }
            if (!hasD3D10DllFile[0]) restoreBuiltinDllFiles("d3d10.dll", "d3d10_1.dll");
        }

        GeneralComponents.extractFile(GeneralComponents.Type.VKD3D, this, dxwrapperConfig[1].get("version"), DefaultVersion.VKD3D);

        File containerSysWoW64Dir = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/windows/syswow64");
        FileUtils.delete(new File(containerSysWoW64Dir, "ddraw_.dll"));

        switch (ddrawWrapper) {
            case DXWrappers.CNC_DDRAW:
                final String assetDir = "dxwrapper/cnc-ddraw-"+DefaultVersion.CNC_DDRAW;
                File configFile = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/ProgramData/cnc-ddraw/ddraw.ini");
                if (!configFile.isFile()) FileUtils.copy(this, assetDir+"/ddraw.ini", configFile);
                File shadersDir = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/ProgramData/cnc-ddraw/Shaders");
                FileUtils.delete(shadersDir);
                FileUtils.copy(this, assetDir+"/Shaders", shadersDir);
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, assetDir+"/ddraw.tzst", windowsDir);
                break;
            case DXWrappers.D7VK:
                restoreBuiltinDllFiles("ddraw.dll");
                (new File(containerSysWoW64Dir, "ddraw.dll")).renameTo(new File(containerSysWoW64Dir, "ddraw_.dll"));
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/d7vk-"+DefaultVersion.D7VK+".tzst", windowsDir);
                break;
            default:
                restoreBuiltinDllFiles("ddraw.dll");
                break;
        }
        return true;
    }

    private void extractWinComponentFiles() {
        File rootDir = rootFS.getRootDir();
        File windowsDir = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/windows");
        File systemRegFile = new File(rootDir, RootFS.WINEPREFIX+"/system.reg");

        try {
            JSONObject wincomponentsJSONObject = new JSONObject(FileUtils.readString(this, "wincomponents/wincomponents.json"));
            Iterator<String[]> oldWinComponentsIter = new KeyValueSet(container.getExtra("wincomponents", Container.FALLBACK_WINCOMPONENTS)).iterator();
            ArrayList<String> builtinDlls = new ArrayList<>();

            for (String[] wincomponent : new KeyValueSet(wincomponents)) {
                if (wincomponent[1].equals(oldWinComponentsIter.next()[1])) continue;
                String identifier = wincomponent[0];
                boolean useNative = wincomponent[1].equals("1");

                if (useNative) {
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "wincomponents/"+identifier+".tzst", windowsDir);
                }
                else {
                    JSONObject wincomponentJSONObject = wincomponentsJSONObject.getJSONObject(identifier);
                    if (wincomponentJSONObject.getBoolean("restoreBuiltinDlls")) {
                        JSONArray dlnames = wincomponentJSONObject.getJSONArray("dlnames");
                        for (int i = 0; i < dlnames.length(); i++) {
                            String dlname = dlnames.getString(i);
                            builtinDlls.add(!dlname.endsWith(".exe") ? dlname+".dll" : dlname);
                        }
                    }
                    else {
                        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "wincomponents/"+identifier+".tzst", windowsDir, (destination, size) -> {
                            String name = destination.getName();
                            if (name.endsWith(".dll") || name.endsWith(".manifest") || name.endsWith("_deadbeef")) FileUtils.delete(destination);
                            return null;
                        });
                    }
                }

                WineUtils.setWinComponentRegistryKeys(systemRegFile, identifier, useNative);
            }

            if (!builtinDlls.isEmpty()) restoreBuiltinDllFiles(builtinDlls.toArray(new String[0]));
            WineUtils.overrideWinComponentDlls(this, container, wincomponents);
        }
        catch (JSONException e) {}
    }

    private void restoreBuiltinDllFiles(final String... dlls) {
        File rootDir = rootFS.getRootDir();
        File wineDir = new File(rootDir, rootFS.getWinePath());
        File wineSystem32Dir = new File(wineDir, "/lib/wine/x86_64-windows");
        File wineSysWoW64Dir = new File(wineDir, "/lib/wine/i386-windows");
        File containerSystem32Dir = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/windows/system32");
        File containerSysWoW64Dir = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/windows/syswow64");;

        for (String dll : dlls) {
            FileUtils.copy(new File(wineSysWoW64Dir, dll), new File(containerSysWoW64Dir, dll));
            FileUtils.copy(new File(wineSystem32Dir, dll), new File(containerSystem32Dir, dll));
        }
    }

    private String getWineStartCommand() {
        String cmdArgs = "";
        String execPath = null;
        String execArgs = "";

        if (shortcut != null) {
            execArgs = shortcut.getExtra("execArgs");
            execArgs = !execArgs.isEmpty() ? " "+execArgs : "";

            if (shortcut.isLinkPath()) {
                cmdArgs = "\""+shortcut.path+"\""+execArgs;
            }
            else execPath = shortcut.path;
        }
        else {
            if (unixExecPath != null) {
                execPath = WineUtils.unixToDOSPath(unixExecPath, container);

                if (execPath.endsWith(".lnk")) {
                    cmdArgs = "\""+execPath+"\"";
                    execPath = null;
                }
            }
        }

        if (execPath != null) {
            String execDir = FileUtils.getDirname(execPath);
            String filename = FileUtils.getName(execPath);
            int dotIndex, spaceIndex;
            if ((dotIndex = filename.lastIndexOf(".")) != -1 && (spaceIndex = filename.indexOf(" ", dotIndex)) != -1) {
                execArgs = filename.substring(spaceIndex+1)+execArgs;
                filename = filename.substring(0, spaceIndex);
            }
            cmdArgs = "/dir "+StringUtils.escapeDOSPath(execDir)+" \""+filename+"\""+execArgs;
        }

        if (cmdArgs.isEmpty()) cmdArgs = "/dir C:\\windows \"wfm.exe\"";

        if (overrideEnvVars != null && overrideEnvVars.has("EXTRA_EXEC_ARGS")) {
            cmdArgs += " "+overrideEnvVars.get("EXTRA_EXEC_ARGS");
            overrideEnvVars.remove("EXTRA_EXEC_ARGS");
        }
        return "C:\\windows\\winhandler.exe "+cmdArgs;
    }

    private void changeWineAudioDriver() {
        if (!audioDriver.equals(container.getExtra("audioDriver"))) {
            File rootDir = rootFS.getRootDir();
            File userRegFile = new File(rootDir, RootFS.WINEPREFIX+"/user.reg");
            try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
                if (audioDriver.equals(AudioDrivers.ALSA)) {
                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "alsa");
                }
                else if (audioDriver.equals(AudioDrivers.PULSEAUDIO)) {
                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "pulse");
                }
            }
            container.putExtra("audioDriver", audioDriver);
            container.saveData();
        }
    }

    private void applyGeneralPatches(Container container) {
        File rootDir = rootFS.getRootDir();
        FileUtils.delete(new File(rootDir, "/opt/apps"));
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "rootfs_patches.tzst", rootDir);
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "pulseaudio.tzst", new File(getFilesDir(), "pulseaudio"));
        WineUtils.applySystemTweaks(this, wineInfo);
        container.putExtra("dxwrapper", null);
        container.putExtra("desktopTheme", null);
        WinlatorConstants.resetPreferenceVersions(this);
    }

    public boolean verifyUserRegistry() {
        File userRegFile = new File(rootFS.getRootDir(), RootFS.WINEPREFIX+"/user.reg");
        String lastModified = String.valueOf(userRegFile.lastModified());

        if (!lastModified.equals(container.getExtra("userRegLastModified"))) {
            try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
                registryEditor.removeKey("Software\\Wow6432Node\\Wine", true);
            }

            container.putExtra("userRegLastModified", lastModified);
            return true;
        }
        else return false;
    }

    public int frameRatingWindowId = -1;
    public void changeFrameRatingVisibility(Window window, boolean visible) {}
    public SharedPreferences getPreferences() { return preferences; }
    public XServer getXServer() { return xServer; }
    public WinHandler getWinHandler() { return winHandler; }
    public XServerView getXServerView() { return xServerView; }
    public InputControlsView getInputControlsView() { return inputControlsView; }
    public Container getContainer() { return container; }
    public RootFS getRootFs() { return rootFS; }
    public EnvVars getOverrideEnvVars() {
        if (overrideEnvVars == null) overrideEnvVars = new EnvVars();
        return overrideEnvVars;
    }
    public String getDXWrapper() { return dxwrapper; }
    public void setDXWrapper(String dxwrapper) { this.dxwrapper = dxwrapper; }
    public ScreenInfo getScreenInfo() { return screenInfo; }
    public void setScreenInfo(ScreenInfo screenInfo) { this.screenInfo = screenInfo; }
    public String getWinComponents() { return wincomponents; }
    public void setWinComponents(String wincomponents) { this.wincomponents = wincomponents; }
    public DebugDialog getDebugDialog() { return null; }
    public String getScreenEffectProfile() { return screenEffectProfile; }
    public void setScreenEffectProfile(String screenEffectProfile) { this.screenEffectProfile = screenEffectProfile; }
}
