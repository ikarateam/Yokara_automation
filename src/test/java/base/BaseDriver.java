package base;

import core.AppiumServerProbe;
import core.ConfigManager;
import core.DeviceManager;
import core.DriverFactory;
import flows.AuthFlow;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.InteractsWithApps;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import utils.AllureDeviceLabels;
import utils.StepContext;

import java.net.URI;
import java.time.Duration;
import java.util.List;

public class BaseDriver {

    private static final ThreadLocal<AppiumDriver> DRIVER = new ThreadLocal<>();

    protected AppiumDriver driver;
    protected WebDriverWait wait;
    protected AuthFlow auth;

    @BeforeMethod(alwaysRun = true)
    @Parameters({"suitePlatform", "suiteUdid", "suiteDeviceLabel", "suiteDeviceFolder", "suiteAppiumPort"})
    public void setup(@Optional String suitePlatform,
                      @Optional String suiteUdid,
                      @Optional String suiteDeviceLabel,
                      @Optional String suiteDeviceFolder,
                      @Optional String suiteAppiumPort) {

        String runPlatform = normalizePlatform(System.getProperty("platform"));
        String runAndroidUdid = normalizeRaw(System.getProperty("android.udid"));
        String runIosUdid = normalizeRaw(System.getProperty("ios.udid"));

        String requestedPlatform = normalizePlatform(suitePlatform);
        String requestedUdid = normalizeRaw(suiteUdid);

        if (requestedPlatform == null) {
            requestedPlatform = runPlatform;
        }

        if (runPlatform != null && requestedPlatform != null && !runPlatform.equals(requestedPlatform)) {
            throw new SkipException(String.format(
                    "[Skip] Invocation platform=%s không thuộc branch hiện tại platform=%s",
                    requestedPlatform, runPlatform
            ));
        }

        if (requestedUdid == null) {
            if ("android".equals(requestedPlatform)) {
                requestedUdid = runAndroidUdid;
            } else if ("ios".equals(requestedPlatform)) {
                requestedUdid = runIosUdid;
            }
        }

        if (requestedPlatform == null) {
            throw new SkipException("[Skip] Không xác định được platform cho test invocation hiện tại.");
        }

        if ("android".equals(requestedPlatform)) {
            List<String> online = DeviceManager.getAndroidPhysicalDevices();
            if (online.isEmpty()) {
                throw new SkipException("[Skip] Không có thiết bị Android nào kết nối – bỏ qua test này.");
            }
            if (requestedUdid != null && !containsIgnoreCase(online, requestedUdid)) {
                throw new SkipException("[Skip] Android UDID không còn online: " + requestedUdid);
            }
        } else if ("ios".equals(requestedPlatform)) {
            List<String> online = DeviceManager.getIOSDevices();
            if (online.isEmpty()) {
                throw new SkipException("[Skip] Không có thiết bị iOS nào kết nối – bỏ qua test này.");
            }
            if (requestedUdid != null && !containsIgnoreCase(online, requestedUdid)) {
                throw new SkipException("[Skip] iOS UDID không còn online: " + requestedUdid);
            }
        } else {
            throw new SkipException("[Skip] Platform không hợp lệ: " + requestedPlatform);
        }

        String suiteServerUrl = buildSuiteAppiumUrl(suiteAppiumPort);

        try {
            driver = DriverFactory.createDriver(requestedPlatform, requestedUdid, suiteServerUrl);
            DRIVER.set(driver);
            attachAllureDeviceLabels(requestedPlatform, requestedUdid, suiteAppiumPort);
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("Không tìm thấy thiết bị") || msg.contains("unknown")) {
                throw new SkipException("[Skip] Không thể khởi tạo driver: " + msg);
            }
            throw e;
        }

        if (driver == null) {
            throw new RuntimeException("Driver initialization failed!");
        }

        activateAppForCurrentPlatform();

        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        auth = new AuthFlow(driver);

        new BaseScr(driver).handleStartupPopups();
        handleLaunchBannerIfPresent();
        ensureMainTabBarVisible();
    }

    /**
     * Đồng bộ iOS/Android: sau khi tạo session, đưa AUT lên foreground bằng bundleId (iOS) hoặc package (Android).
     */
    private void activateAppForCurrentPlatform() {
        String appId = primaryAppIdForPlatform();
        if (appId == null) {
            return;
        }
        try {
            ((InteractsWithApps) driver).activateApp(appId);
        } catch (Exception e) {
            System.out.println("[BaseDriver] activateApp (" + platformLabel() + "): " + e.getMessage());
        }
    }

    private String platformLabel() {
        if (driver instanceof IOSDriver) {
            return "ios";
        }
        if (driver instanceof AndroidDriver) {
            return "android";
        }
        return "unknown";
    }

    /** iOS: bundleId AUT. Android: applicationId (package). */
    private String primaryAppIdForPlatform() {
        if (driver instanceof IOSDriver) {
            return ConfigManager.getRequired("ios.bundleId");
        }
        if (driver instanceof AndroidDriver) {
            return ConfigManager.getRequired("android.appPackage");
        }
        return null;
    }

    private String normalizePlatform(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    private String normalizeRaw(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        if (target == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Đồng bộ iOS/Android: đảm bảo có bottom tab (Trang chủ / Tôi); nếu không thấy thì terminate + activate AUT.
     */
    private void ensureMainTabBarVisible() {
        String appId = primaryAppIdForPlatform();
        if (appId == null) {
            return;
        }

        By trangChu = AppiumBy.accessibilityId("Trang chủ");
        By toi = AppiumBy.accessibilityId("Tôi");
        WebDriverWait tabWait = new WebDriverWait(driver, Duration.ofSeconds(22));

        try {
            tabWait.until(d -> !d.findElements(trangChu).isEmpty() || !d.findElements(toi).isEmpty());
            waitMainTabBarSettled();
            return;
        } catch (TimeoutException e) {
            System.out.println("[BaseDriver] " + platformLabel()
                    + ": chưa thấy thanh tab sau activateApp — thử terminate + activate");
        }

        try {
            InteractsWithApps app = (InteractsWithApps) driver;
            app.terminateApp(appId);
            app.activateApp(appId);

            new BaseScr(driver).handleStartupPopups();
            handleLaunchBannerIfPresent();

            WebDriverWait afterRelaunch = new WebDriverWait(driver, Duration.ofSeconds(35));
            afterRelaunch.until(d -> !d.findElements(trangChu).isEmpty() || !d.findElements(toi).isEmpty());
            waitMainTabBarSettled();
        } catch (Exception ex) {
            throw new RuntimeException(
                    "[BaseDriver] " + platformLabel()
                            + ": không đưa app về màn có bottom bar (Trang chủ / Tôi): " + ex.getMessage(), ex);
        }
    }

    /** Banner khởi động (nút Bỏ qua) — Flutter semantics thường giống trên cả hai nền tảng. */
    private void handleLaunchBannerIfPresent() {
        By btnBoQua = AppiumBy.accessibilityId("Bỏ qua");

        try {
            List<org.openqa.selenium.WebElement> skips = driver.findElements(btnBoQua);
            if (skips.isEmpty()) {
                return;
            }

            org.openqa.selenium.WebElement skip = skips.get(0);
            try {
                skip.click();
            } catch (Exception clickEx) {
                driver.executeScript(
                        "mobile: clickGesture",
                        java.util.Map.of("elementId", ((org.openqa.selenium.remote.RemoteWebElement) skip).getId())
                );
            }

            System.out.println("[BaseDriver] " + platformLabel() + ": đã dismiss banner bằng nút 'Bỏ qua'");

            new WebDriverWait(driver, Duration.ofSeconds(8)).until(d ->
                    d.findElements(btnBoQua).isEmpty()
                            || !d.findElements(AppiumBy.accessibilityId("Bài hát")).isEmpty()
                            || !d.findElements(AppiumBy.accessibilityId("Song ca")).isEmpty());
        } catch (Exception e) {
            System.out.println("[BaseDriver] " + platformLabel() + ": xử lý banner 'Bỏ qua' lỗi: " + e.getMessage());
        }
    }

    private void waitMainTabBarSettled() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(15)).until(d ->
                    !d.findElements(AppiumBy.accessibilityId("Trực tuyến")).isEmpty()
                            || !d.findElements(AppiumBy.xpath("//*[contains(@name,'Trực tuyến')]")).isEmpty()
                            || !d.findElements(AppiumBy.xpath("//*[contains(@content-desc,'Trực tuyến')]")).isEmpty());
        } catch (TimeoutException ignored) {
        }
    }

    /**
     * Resolve URL Appium server bằng cách probe lần lượt các nguồn:
     * suite param → {@code -DappiumServer} → config → PM2 fallback (4723/4725).
     *
     * <p>Trả URL ready hoặc throw kèm hint — không trả null. Gọi tới DriverFactory
     * với URL không ready chỉ dẫn đến lỗi mơ hồ ("Could not start a new session"),
     * nên dồn xử lý 1 chỗ ở đây.
     *
     * <p>Vì sao có hardcoded 4723/4725? Đây là default Appium standalone + port
     * PM2 dev đã từng dùng. Cho phép {@code mvn test} thuần local hoạt động mà
     * không cần truyền cờ. Jenkins flow vẫn match #1 (suite param trỏ Appium đã
     * spawn) nên không ảnh hưởng performance.
     */
    private String buildSuiteAppiumUrl(String suiteAppiumPort) {
        java.util.List<AppiumServerProbe.Candidate> candidates =
                AppiumServerProbe.buildDefaultCandidates(
                        normalizeRaw(suiteAppiumPort),
                        normalizeRaw(System.getProperty("appiumServer")),
                        normalizeRaw(ConfigManager.get("appiumServer")));

        String readyUrl = AppiumServerProbe.firstReadyOrNull(candidates, 800, 1200);
        if (readyUrl != null) {
            System.out.println("[BaseDriver] Appium ready: " + readyUrl);
            return readyUrl;
        }

        throw new RuntimeException(
                "[BaseDriver] Không tìm thấy Appium server ready ở các URL sau:\n"
                        + AppiumServerProbe.formatCandidatesForLog(candidates)
                        + "Hint:\n"
                        + "  • Local: kiểm tra `pm2 ls` hoặc khởi động Appium: `appium --address 127.0.0.1 --port 4723`.\n"
                        + "  • Jenkins: kiểm tra stage 'SPAWN APPIUM' trong log có dòng `✓ Appium :<port> ready` không.\n"
                        + "  • Override thủ công: `mvn test -DappiumServer=http://127.0.0.1:<port>`.");
    }

    private void attachAllureDeviceLabels(String requestedPlatform, String requestedUdid, String suiteAppiumPort) {
        try {
            String platform = normalizePlatform(firstNonBlank(requestedPlatform, System.getProperty("platform")));
            if (platform == null) {
                platform = platformLabel();
            }

            String udid = normalizeRaw(requestedUdid);
            if (udid == null) {
                if ("ios".equals(platform)) {
                    udid = normalizeRaw(System.getProperty("ios.udid"));
                } else {
                    udid = normalizeRaw(System.getProperty("android.udid"));
                }
            }

            String shortUdid = udid == null ? "unknown" : (udid.length() > 8 ? udid.substring(udid.length() - 8) : udid);
            String branchName = firstNonBlank(System.getProperty("jenkins.branchName"), platform + "-" + shortUdid);
            String appiumPort = firstNonBlank(
                    normalizeRaw(suiteAppiumPort),
                    normalizeRaw(System.getProperty("jenkins.appiumPort")),
                    parsePortFromUrl(System.getProperty("appiumServer")),
                    "unknown"
            );
            // Đọc tên thiết bị từ capabilities sau khi session khởi tạo — iOS dùng
            // `appium:deviceName` (XCUITest set từ IosDeviceInfo.queryDeviceName),
            // Android dùng `appium:deviceModel` (UiAutomator2 tự populate khi probe).
            String deviceName = readDeviceCap(platform);

            if ("ios".equals(platform)) {
                String wdaLocalPort = firstNonBlank(
                        normalizeRaw(System.getProperty("jenkins.wdaLocalPort")),
                        normalizeRaw(System.getProperty("ios.wdaLocalPort")),
                        ""
                );
                String mjpegServerPort = firstNonBlank(
                        normalizeRaw(System.getProperty("jenkins.mjpegServerPort")),
                        normalizeRaw(System.getProperty("ios.mjpegServerPort")),
                        ""
                );
                AllureDeviceLabels.attach(branchName, "ios", deviceName, safe(udid), appiumPort, wdaLocalPort, mjpegServerPort);
            } else {
                String systemPort = firstNonBlank(
                        normalizeRaw(System.getProperty("jenkins.systemPort")),
                        normalizeRaw(System.getProperty("android.systemPort")),
                        ""
                );
                AllureDeviceLabels.attach(branchName, "android", deviceName, safe(udid), appiumPort, systemPort, "");
            }
        } catch (Exception e) {
            System.out.println("[BaseDriver] attachAllureDeviceLabels failed: " + e.getMessage());
        }
    }

    /**
     * Đọc tên thiết bị từ capability driver (iOS: deviceName, Android: deviceModel).
     * Trả null nếu driver chưa init hoặc capability không có.
     */
    private String readDeviceCap(String platform) {
        if (driver == null) {
            return null;
        }
        try {
            String[] keys = "ios".equals(platform)
                    ? new String[]{"appium:deviceName", "appium:deviceModel"}
                    : new String[]{"appium:deviceModel", "appium:deviceName"};
            for (String k : keys) {
                Object v = driver.getCapabilities().getCapability(k);
                if (v != null && !v.toString().isBlank()) {
                    return v.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String parsePortFromUrl(String appiumServer) {
        if (appiumServer == null || appiumServer.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(appiumServer.trim());
            return uri.getPort() > 0 ? String.valueOf(uri.getPort()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
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

    private String safe(String value) {
        return value == null ? "unknown" : value;
    }

    public static AppiumDriver getDriver() {
        AppiumDriver d = DRIVER.get();
        if (d == null) {
            throw new IllegalStateException("Không có AppiumDriver trong luồng hiện tại.");
        }
        return d;
    }

    @AfterMethod(alwaysRun = true)
    public void teardown() {
        try {
            if (driver != null) {
                terminateAppQuietly();
                driver.quit();
            }
        } catch (Exception e) {
            System.out.println("Driver quit failed: " + e.getMessage());
        } finally {
            DRIVER.remove();
            driver = null;
            StepContext.clear();
        }
    }

    /**
     * Kill AUT trên thiết bị trước khi đóng session để mỗi test kết thúc về trạng thái sạch.
     * Vì noReset=true, terminateApp không xóa data — chỉ đóng tiến trình app.
     */
    private void terminateAppQuietly() {
        try {
            String appId = primaryAppIdForPlatform();
            if (appId == null) {
                return;
            }
            ((InteractsWithApps) driver).terminateApp(appId);
        } catch (Exception e) {
            System.out.println("[BaseDriver] terminateApp khi teardown bỏ qua: " + e.getMessage());
        }
    }
}