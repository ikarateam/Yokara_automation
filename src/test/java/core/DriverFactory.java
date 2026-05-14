package core;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import io.appium.java_client.ios.options.wda.XcodeCertificate;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Factory khởi tạo AppiumDriver cho Android hoặc iOS.
 * Hỗ trợ cấu hình tường minh (platform=android/ios)
 * và tự động phát hiện (platform=auto).
 */
public class DriverFactory {

    public static AppiumDriver createDriver() {
        return createDriver(null, null, null);
    }

    public static AppiumDriver createDriver(String platformParam, String udidParam) {
        return createDriver(platformParam, udidParam, null);
    }

    /**
     * Khởi tạo driver với platform, UDID, và URL Appium server tùy chọn (phục vụ parallel/multi-device).
     *
     * <p>Thứ tự ưu tiên URL server:
     * <ol>
     *   <li>{@code -DappiumServer} (CLI override, cao nhất)</li>
     *   <li>{@code serverUrlParam} (từ suite param {@code suiteAppiumPort}, mô hình 1 server / device)</li>
     *   <li>{@code appiumServer} trong config.properties (fallback local)</li>
     * </ol>
     */
    public static AppiumDriver createDriver(String platformParam, String udidParam, String serverUrlParam) {
        try {
            String server = firstNonBlank(
                    System.getProperty("appiumServer"),
                    serverUrlParam,
                    ConfigManager.get("appiumServer"),
                    ConfigManager.getRequired("appiumServer")
            );
            String platform = (platformParam != null && !platformParam.isBlank())
                    ? platformParam.trim().toLowerCase()
                    : resolvePlatform();

            System.out.println("[DriverFactory] Đang khởi tạo driver cho nền tảng: " + platform.toUpperCase()
                    + (udidParam != null ? " (UDID: " + udidParam + ")" : ""));

            if ("android".equalsIgnoreCase(platform)) {
                return createAndroidDriver(server, udidParam);
            } else if ("ios".equalsIgnoreCase(platform)) {
                return createIOSDriver(server, udidParam);
            } else {
                throw new RuntimeException(
                        "[DriverFactory] Nền tảng không được hỗ trợ: " + platform
                                + ". Giá trị hợp lệ: android, ios, auto");
            }

        } catch (Exception e) {
            throw new RuntimeException("[DriverFactory] Khởi tạo driver thất bại: " + e.getMessage(), e);
        }
    }

    // ================================================================
    //  ANDROID
    // ================================================================

    private static AppiumDriver createAndroidDriver(String server, String udidParam) throws Exception {
        String udid = firstNonBlank(
                udidParam,
                System.getProperty("android.udid"),
                ConfigManager.get("android.udid")
        );
        if (udid == null || udid.isBlank()) {
            udid = DeviceManager.getAndroidUDID();
        } else {
            System.out.println("[DriverFactory] Sử dụng Android UDID: " + udid);
        }

        String deviceName = ConfigManager.get("android.deviceName");
        if (deviceName == null || deviceName.isBlank()) {
            deviceName = udid;
        }

        System.out.println("[DriverFactory] Android UDID=" + udid + " | DeviceName=" + deviceName);

        UiAutomator2Options options = new UiAutomator2Options();
        options.setAutomationName("UiAutomator2");
        options.setPlatformName("Android");
        options.setDeviceName(deviceName);
        options.setUdid(udid);
        options.setAppPackage(ConfigManager.getRequired("android.appPackage"));
        options.setAppActivity(ConfigManager.getRequired("android.appActivity"));
        options.setNoReset(true);

        // Ổn định trên Android 13/14
        options.setAutoGrantPermissions(true);
        options.setIgnoreHiddenApiPolicyError(true);
        options.setDisableWindowAnimation(true);
        options.setSkipDeviceInitialization(true);

        int systemPort = resolveAndroidSystemPort(udid);
        options.setSystemPort(systemPort);
        System.out.println("[DriverFactory] Android systemPort=" + systemPort + " (tự cấp, tránh conflict)");

        AndroidDriver androidDriver = new AndroidDriver(new URL(server), options);

        // UiAutomator2 server có bug XPath 2.0 với một số biểu thức (vd. following:: + predicate):
        //   "java.util.ArrayList$ListItr cannot be cast to ... NodeType"
        // Appium recommend bật enforceXPath1 để parse theo XPath 1.0 — set runtime sau khi có session.
        try {
            androidDriver.setSetting("enforceXPath1", true);
        } catch (Exception e) {
            System.out.println("[DriverFactory] Bỏ qua setSetting(enforceXPath1): " + e.getMessage());
        }

        return androidDriver;
    }

    // ================================================================
    //  iOS
    // ================================================================

    private static AppiumDriver createIOSDriver(String server, String udidParam) throws Exception {
        XcodeProbe.logSummaryIfEnabled();
        String udid = firstNonBlank(
                udidParam,
                System.getProperty("ios.udid"),
                ConfigManager.get("ios.udid")
        );
        if (udid == null || udid.isBlank()) {
            String detected = DeviceManager.resolveIosUdid(false);
            if (detected == null) {
                throw new RuntimeException("[DriverFactory] Không tìm thấy thiết bị iOS (USB/Simulator).");
            }
            udid = detected;
        } else {
            System.out.println("[DriverFactory] Sử dụng iOS UDID: " + udid);
        }

        boolean sim = isLikelySimulatorUdid(udid);
        String deviceName = ConfigManager.get("ios.deviceName");
        if (deviceName == null || deviceName.isBlank()) {
            if (!sim) {
                deviceName = IosDeviceInfo.queryDeviceName(udid);
            }
            if (deviceName == null || deviceName.isBlank()) {
                deviceName = sim ? "iPhone Simulator" : "iPhone";
            }
        }

        String platformVersion = ConfigManager.get("ios.platformVersion");
        if (!sim) {
            String fromDevice = IosDeviceInfo.queryProductVersionNormalized(udid);
            if (fromDevice != null) {
                platformVersion = fromDevice;
            }
        }

        System.out.println("[DriverFactory] iOS UDID=" + udid + " | DeviceName=" + deviceName
                + (sim ? " | loại=Simulator" : " | loại=Thiết bị thật")
                + (platformVersion != null && !platformVersion.isBlank()
                        ? " | iOS=" + platformVersion : ""));

        XCUITestOptions options = new XCUITestOptions();
        options.setAutomationName("XCUITest");
        options.setPlatformName("iOS");
        options.setUdid(udid);
        options.setDeviceName(deviceName);
        if (platformVersion != null && !platformVersion.isBlank()) {
            options.setPlatformVersion(platformVersion);
        }
        applyIosAppOrBundle(options);
        options.setNoReset(parseBool(ConfigManager.get("ios.noReset"), false));
        if (!parseBool(ConfigManager.get("ios.forceAppLaunch"), true)) {
            options.setForceAppLaunch(false);
        }

        if (!sim) {
            options.setAllowProvisioningDeviceRegistration(true);
        }

        options.setWdaLaunchTimeout(Duration.ofSeconds(180));
        options.setWdaConnectionTimeout(Duration.ofSeconds(180));
        if ("true".equalsIgnoreCase(ConfigManager.get("ios.showXcodeLog"))) {
            options.setShowXcodeLog(true);
        }

        String team = ConfigManager.get("ios.xcodeOrgId");
        if (team == null || team.isBlank()) {
            team = IosEnvHelper.detectFirstAppleTeamId();
        }
        if (team != null && !team.isBlank()) {
            String signingId = ConfigManager.get("ios.xcodeSigningId");
            if (signingId == null || signingId.isBlank()) {
                signingId = "iPhone Developer";
            }
            options.setXcodeCertificate(new XcodeCertificate(team, signingId));
            String wdaBundle = ConfigManager.get("ios.wda.bundleId");
            if (wdaBundle == null || wdaBundle.isBlank()) {
                wdaBundle = "com.yokara.WebDriverAgentRunner";
            }
            options.setUpdatedWdaBundleId(wdaBundle);
        }

        int wdaPort = resolveIosWdaLocalPort(udid);
        int mjpegPort = resolveIosMjpegServerPort(udid);
        options.setWdaLocalPort(wdaPort);
        options.setMjpegServerPort(mjpegPort);
        System.out.println("[DriverFactory] iOS wdaLocalPort=" + wdaPort + " mjpegServerPort=" + mjpegPort
                + " (tự cấp, tránh conflict)");

        return new IOSDriver(new URL(server), options);
    }

    // ================================================================
    //  HELPER: xác định platform
    // ================================================================

    /**
     * Xác định nền tảng từ cấu hình hoặc tự động phát hiện.
     * - "android" → dùng Android
     * - "ios"     → dùng iOS
     * - "auto" hoặc không cấu hình → tự động phát hiện qua DeviceManager
     */
    private static String resolvePlatform() {
        String platform = firstNonBlank(
                System.getProperty("platform"),
                ConfigManager.get("platform")
        );

        if (platform == null || platform.isBlank() || "auto".equalsIgnoreCase(platform)) {
            System.out.println("[DriverFactory] platform=auto → Đang tự động phát hiện thiết bị...");
            return DeviceManager.detectPlatform();
        }

        return platform.trim().toLowerCase();
    }

    private static boolean isLikelySimulatorUdid(String udid) {
        if (udid == null) {
            return false;
        }
        return udid.matches("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}");
    }

    private static boolean parseBool(String v, boolean def) {
        if (v == null || v.isBlank()) {
            return def;
        }
        return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
    }

    private static void applyIosAppOrBundle(XCUITestOptions options) {
        String raw = ConfigManager.get("ios.app");
        if (raw != null && !raw.isBlank()) {
            Path app = Paths.get(expandUserHome(raw.trim()));
            if (Files.isDirectory(app) && app.getFileName().toString().endsWith(".app")) {
                options.setApp(app.toAbsolutePath().toString());
                System.out.println("[DriverFactory] Dùng ios.app: " + app.toAbsolutePath());
                return;
            }
            System.out.println("[DriverFactory] ios.app không hợp lệ hoặc chưa build: " + app);
        }
        options.setBundleId(ConfigManager.getRequired("ios.bundleId"));
    }

    private static String expandUserHome(String path) {
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    private static int resolveAndroidSystemPort(String udid) {
        String raw = firstNonBlank(
                System.getProperty("android.systemPort"),
                ConfigManager.get("android.systemPort")
        );
        if (raw != null) {
            return parseInt(raw, 8200);
        }

        int base = parseInt(firstNonBlank(
                System.getProperty("android.systemPort.base"),
                ConfigManager.get("android.systemPort.base")
        ), 8200);

        return base + stablePortOffset(udid, 500);
    }

    private static int resolveIosWdaLocalPort(String udid) {
        String raw = firstNonBlank(
                System.getProperty("ios.wdaLocalPort"),
                ConfigManager.get("ios.wdaLocalPort")
        );
        if (raw != null) {
            return parseInt(raw, 9100);
        }

        int base = parseInt(firstNonBlank(
                System.getProperty("ios.wdaLocalPort.base"),
                ConfigManager.get("ios.wdaLocalPort.base")
        ), 9100);

        return base + stablePortOffset(udid, 500);
    }

    private static int resolveIosMjpegServerPort(String udid) {
        String raw = firstNonBlank(
                System.getProperty("ios.mjpegServerPort"),
                ConfigManager.get("ios.mjpegServerPort")
        );
        if (raw != null) {
            return parseInt(raw, 10100);
        }

        int base = parseInt(firstNonBlank(
                System.getProperty("ios.mjpegServerPort.base"),
                ConfigManager.get("ios.mjpegServerPort.base")
        ), 10100);

        return base + stablePortOffset(udid, 500);
    }

    private static int stablePortOffset(String key, int modulo) {
        if (key == null || key.isBlank()) {
            return 0;
        }
        return Math.abs(key.hashCode()) % modulo;
    }

    private static int parseInt(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
