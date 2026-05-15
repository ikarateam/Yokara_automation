package core;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Cấu hình automation: file {@code config/config.properties} được ghi đè bởi
 * {@link System#getProperty(String)} và biến môi trường (phục vụ Jenkins/CI).
 *
 * Thứ tự ưu tiên:
 * system property → biến môi trường → file config.
 */
public final class ConfigManager {

    private static final String CONFIG_PATH = "config/config.properties";
    private static final String ENV_PREFIX = "YOKARA_";
    private static final Properties PROPERTIES = loadProperties();

    /** Alias env → key trong properties (không cần prefix YOKARA_) */
    private static final Map<String, String> ENV_ALIAS_TO_KEY = new HashMap<>();

    static {
        ENV_ALIAS_TO_KEY.put("APPIUM_SERVER", "appiumServer");
        ENV_ALIAS_TO_KEY.put("PLATFORM", "platform");
        ENV_ALIAS_TO_KEY.put("APP_ENV", "app.env");

        // Android
        ENV_ALIAS_TO_KEY.put("ANDROID_UDID", "android.udid");
        ENV_ALIAS_TO_KEY.put("ANDROID_DEVICE_NAME", "android.deviceName");
        ENV_ALIAS_TO_KEY.put("ANDROID_APP_PACKAGE", "android.appPackage");
        ENV_ALIAS_TO_KEY.put("ANDROID_APP_ACTIVITY", "android.appActivity");
        ENV_ALIAS_TO_KEY.put("ANDROID_REAL_DEVICE_ONLY", "android.realDeviceOnly");
        ENV_ALIAS_TO_KEY.put("ANDROID_SYSTEM_PORT_BASE", "android.systemPort.base");

        // iOS
        ENV_ALIAS_TO_KEY.put("IOS_UDID", "ios.udid");
        ENV_ALIAS_TO_KEY.put("IOS_BUNDLE_ID", "ios.bundleId");
        ENV_ALIAS_TO_KEY.put("IOS_TARGET", "ios.target");
        ENV_ALIAS_TO_KEY.put("IOS_TRY_SIMULATOR_FIRST", "ios.trySimulatorFirst");
        ENV_ALIAS_TO_KEY.put("IOS_SIMULATOR_DEVICE_NAME", "ios.simulatorDeviceName");
        ENV_ALIAS_TO_KEY.put("IOS_XCODE_ORG_ID", "ios.xcodeOrgId");
        ENV_ALIAS_TO_KEY.put("IOS_XCODE_SIGNING_ID", "ios.xcodeSigningId");
        ENV_ALIAS_TO_KEY.put("IOS_WDA_LOCAL_PORT_BASE", "ios.wdaLocalPort.base");
        ENV_ALIAS_TO_KEY.put("IOS_MJPEG_SERVER_PORT_BASE", "ios.mjpegServerPort.base");
        ENV_ALIAS_TO_KEY.put("IOS_DERIVED_DATA_PATH", "ios.derivedDataPath");
        ENV_ALIAS_TO_KEY.put("IOS_WDA_BUNDLE_ID", "ios.wda.bundleId");
        ENV_ALIAS_TO_KEY.put("IOS_NO_RESET", "ios.noReset");
        ENV_ALIAS_TO_KEY.put("IOS_APP", "ios.app");
        ENV_ALIAS_TO_KEY.put("IOS_LOG_XCODE_ENVIRONMENT", "ios.logXcodeEnvironment");
        ENV_ALIAS_TO_KEY.put("IOS_FORCE_APP_LAUNCH", "ios.forceAppLaunch");
    }

    private ConfigManager() {
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();

        try (FileInputStream fis = new FileInputStream(CONFIG_PATH)) {
            properties.load(fis);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot load configuration from " + CONFIG_PATH,
                    e
            );
        }
    }

    private static String envKeyForPropertyKey(String key) {
        return ENV_PREFIX + key.replace('.', '_').toUpperCase();
    }

    private static String resolveFromEnvironment(String key) {
        String prefixed = System.getenv(envKeyForPropertyKey(key));
        if (prefixed != null && !prefixed.isBlank()) {
            return prefixed.trim();
        }

        for (Map.Entry<String, String> e : ENV_ALIAS_TO_KEY.entrySet()) {
            if (e.getValue().equals(key)) {
                String v = System.getenv(e.getKey());
                if (v != null && !v.isBlank()) {
                    return v.trim();
                }
            }
        }
        return null;
    }

    public static String get(String key) {
        String fromSys = System.getProperty(key);
        if (fromSys != null && !fromSys.isBlank()) {
            return fromSys.trim();
        }

        String fromEnv = resolveFromEnvironment(key);
        if (fromEnv != null) {
            return fromEnv;
        }

        String fromFile = PROPERTIES.getProperty(key);
        return fromFile == null ? null : fromFile.trim();
    }

    public static String get(String key, String defaultValue) {
        String value = get(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    public static int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer config for key: " + key + ", value: " + value);
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    public static String getRequired(String key) {
        String value = get(key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required config key: " + key);
        }

        return value;
    }

    /**
     * Resolve key theo môi trường {@code app.env} (mặc định {@code prod}).
     *
     * <p>Thử {@code <key>.<env>} trước (vd {@code ios.bundleId.dev}); fallback
     * về key gốc {@code <key>} cho backward compat. Trả null nếu cả 2 đều thiếu.
     *
     * <p>Override env qua: {@code -Dapp.env=dev} (CLI), env var
     * {@code APP_ENV=dev} hoặc {@code YOKARA_APP_ENV=dev}, hoặc
     * {@code app.env=dev} trong config.properties.
     */
    public static String resolveByEnv(String baseKey) {
        String env = get("app.env", "prod").trim().toLowerCase();
        String envSpecific = get(baseKey + "." + env);
        if (envSpecific != null && !envSpecific.isBlank()) {
            return envSpecific;
        }
        return get(baseKey);
    }

    /**
     * Như {@link #resolveByEnv} nhưng throw nếu cả env-specific và base đều
     * không có giá trị.
     */
    public static String resolveByEnvRequired(String baseKey) {
        String value = resolveByEnv(baseKey);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required config key: " + baseKey
                            + " (env=" + get("app.env", "prod") + ")");
        }
        return value;
    }
}