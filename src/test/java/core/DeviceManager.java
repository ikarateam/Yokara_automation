package core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quản lý việc nhận dạng thiết bị Android và iOS tự động (USB + Simulator).
 */
public final class DeviceManager {

    private static final Pattern SIMCTL_BOOTED =
            Pattern.compile("\\(([0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})\\)\\s*\\(Booted\\)");
    /** Dòng kiểu: {@code iPhone 14 (8999F5AF-…-ADDF) (Shutdown)} */
    private static final Pattern SIMCTL_DEVICE_LINE =
            Pattern.compile("^\\s*(.+?)\\s+\\(([0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})\\)\\s*\\((Booted|Shutdown)\\)\\s*$");

    private DeviceManager() {
    }

    // ================================================================
    //  ANDROID
    // ================================================================

    public static boolean hasConnectedAndroidDevice() {
        return !getAndroidPhysicalDevices().isEmpty();
    }

    public static String getAndroidUDID() {
        List<String> online = getAndroidPhysicalDevices();
        if (online.isEmpty()) {
            throw new RuntimeException("[DeviceManager] Không tìm thấy thiết bị Android nào đang kết nối. "
                    + "Vui lòng kiểm tra lại kết nối hoặc chạy: adb devices");
        }

        String fromConfig = ConfigManager.get("android.udid");
        if (fromConfig != null && !fromConfig.isBlank()) {
            String configured = fromConfig.trim();
            if (online.contains(configured)) {
                return configured;
            }
        }

        String udid = online.get(0);
        System.out.println("[DeviceManager] Đã phát hiện thiết bị Android: " + udid
                + (online.size() > 1 ? " (có " + online.size() + " thiết bị, chọn thiết bị đầu tiên)" : ""));
        return udid;
    }

    public static List<String> getAndroidDevices() {
        List<String> devices = new ArrayList<>();
        try {
            Process process = new ProcessBuilder("adb", "devices")
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String normalized = line.trim();
                    if (normalized.isEmpty() || normalized.startsWith("List")) {
                        continue;
                    }
                    String[] parts = normalized.split("\\s+");
                    if (parts.length >= 2 && "device".equals(parts[1])) {
                        devices.add(parts[0]);
                    }
                }
            }
            process.waitFor();
        } catch (IOException e) {
            return devices;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("[DeviceManager] Lỗi khi chạy 'adb devices': " + e.getMessage(), e);
        }
        return devices;
    }

    /** Chỉ máy thật: loại emulator (trừ khi android.realDeviceOnly=false). */
    public static List<String> getAndroidPhysicalDevices() {
        List<String> raw = getAndroidDevices();
        if (!androidRealDeviceOnly()) {
            return raw;
        }
        List<String> out = new ArrayList<>();
        for (String id : raw) {
            if (id.startsWith("emulator-")) {
                continue;
            }
            out.add(id);
        }
        return out;
    }

    private static boolean androidRealDeviceOnly() {
        return !"false".equalsIgnoreCase(ConfigManager.get("android.realDeviceOnly"));
    }

    /**
     * Danh sách thiết bị thật để chạy song song (HC BOX): mỗi Android + mỗi iOS USB là một slot.
     *
     * <p>Mỗi slot được gán một port Appium riêng theo {@code base + index * step}, để
     * Jenkinsfile spawn 1 Appium server per device → cô lập flaky / log riêng.
     * Base/step có thể override qua {@code -Dappium.basePort} / {@code -Dappium.portStep}
     * (mặc định 4700 / 10, khớp env Jenkinsfile).</p>
     */
    public static List<AutomationDeviceSlot> listAutomationSlots() {
        int basePort = parsePositiveInt(System.getProperty("appium.basePort"), 4700);
        int portStep = parsePositiveInt(System.getProperty("appium.portStep"), 10);

        List<AutomationDeviceSlot> out = new ArrayList<>();
        int idx = 0;
        for (String udid : getAndroidPhysicalDevices()) {
            String model = queryAndroidProductModel(udid);
            String folder = sanitizeReportFolder("android_" + model + "_" + shortUdid(udid));
            out.add(new AutomationDeviceSlot("android", udid, model, folder, basePort + idx * portStep));
            idx++;
        }
        for (String udid : getPhysicalIosUdids()) {
            String name = IosDeviceInfo.queryDeviceName(udid);
            if (name == null || name.isBlank()) {
                name = "iOS";
            } else {
                name = name.trim();
            }
            String folder = sanitizeReportFolder("ios_" + name + "_" + shortUdid(udid));
            out.add(new AutomationDeviceSlot("ios", udid, name, folder, basePort + idx * portStep));
            idx++;
        }
        return out;
    }

    private static int parsePositiveInt(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String queryAndroidProductModel(String udid) {
        try {
            Process p = new ProcessBuilder("adb", "-s", udid, "shell", "getprop", "ro.product.model")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                p.waitFor();
                if (line != null && !line.isBlank()) {
                    return line.trim();
                }
            }
        } catch (IOException e) {
            return "Android";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Android";
    }

    private static String shortUdid(String udid) {
        if (udid == null || udid.length() <= 12) {
            return udid != null ? udid : "unknown";
        }
        return udid.substring(udid.length() - 12);
    }

    /** Tên thư mục an toàn: chữ, số, gạch dưới — tối đa 80 ký tự. */
    public static String sanitizeReportFolder(String raw) {
        String s = raw.replaceAll("[^a-zA-Z0-9._-]+", "_");
        s = s.replaceAll("_+", "_");
        if (s.length() > 80) {
            s = s.substring(0, 80);
        }
        if (s.isEmpty()) {
            return "device_" + System.currentTimeMillis();
        }
        return s;
    }

    // ================================================================
    //  iOS — USB
    // ================================================================

    public static String getIOSUDID() {
        return resolveIosUdid(false);
    }

    /**
     * @param forPlatformDetect true khi chỉ kiểm tra có thể chạy iOS (tránh boot simulator sớm)
     */
    public static String resolveIosUdid(boolean forPlatformDetect) {
        String target = ConfigManager.get("ios.target");
        if (target == null || target.isBlank()) {
            target = "auto";
        }
        target = target.trim().toLowerCase();

        if ("simulator".equals(target)) {
            if (forPlatformDetect) {
                return IosEnvHelper.isMacOs() && peekSimulatorUdidOrNull() != null ? "sim" : null;
            }
            return getOrBootSimulatorUdid();
        }
        if ("device".equals(target)) {
            if (forPlatformDetect) {
                return getPhysicalIosUdids().isEmpty() ? null : "device";
            }
            return firstPhysicalIosUdidOrNull();
        }

        // auto
        boolean simFirst = !"false".equalsIgnoreCase(ConfigManager.get("ios.trySimulatorFirst"));
        if (forPlatformDetect) {
            if (!getPhysicalIosUdids().isEmpty()) {
                return "physical";
            }
            if (simFirst && IosEnvHelper.isMacOs()) {
                return peekSimulatorUdidOrNull() != null ? "simulator" : null;
            }
            return null;
        }

        if (simFirst && IosEnvHelper.isMacOs()) {
            String sim = getOrBootSimulatorUdid();
            if (sim != null) {
                return sim;
            }
        }
        String usb = firstPhysicalIosUdidOrNull();
        if (usb != null) {
            return usb;
        }
        if (IosEnvHelper.isMacOs()) {
            String sim = getOrBootSimulatorUdid();
            if (sim != null) {
                return sim;
            }
        }
        return null;
    }

    private static String firstPhysicalIosUdidOrNull() {
        List<String> physical = getPhysicalIosUdids();
        if (physical.isEmpty()) {
            return null;
        }
        String udid = physical.get(0);
        System.out.println("[DeviceManager] iOS thật (USB): " + udid);
        return udid;
    }

    public static List<String> getIOSDevices() {
        return new ArrayList<>(getPhysicalIosUdids());
    }

    static List<String> getPhysicalIosUdids() {
        Set<String> udids = new LinkedHashSet<>();
        List<String> raw = runIOSCommand("idevice_id", "-l");
        for (String line : raw) {
            for (String part : line.split("\\s+")) {
                if (isLikelyPhysicalIosUdid(part)) {
                    udids.add(part);
                }
            }
        }
        if (!udids.isEmpty()) {
            return new ArrayList<>(udids);
        }
        List<String> tidevice = runIOSCommand("tidevice", "list", "--json");
        return parseTideviceOutput(tidevice);
    }

    private static boolean isLikelyPhysicalIosUdid(String part) {
        if (part == null || part.isBlank()) {
            return false;
        }
        // iPhone mới: 00008030-0019290826... (8-16)
        // iPhone cũ:  40 ký tự hex
        return part.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{16}") || part.matches("[0-9a-fA-F]{40}");
    }

    // ================================================================
    //  iOS — Simulator
    // ================================================================

    private static String peekSimulatorUdidOrNull() {
        try {
            Process process = new ProcessBuilder("xcrun", "simctl", "list", "devices", "booted")
                    .redirectErrorStream(true)
                    .start();
            String udid = parseBootedSimulator(process);
            if (udid != null) {
                return udid;
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return findShutdownSimulatorUdid();
    }

    private static String getOrBootSimulatorUdid() {
        if (!IosEnvHelper.isMacOs()) {
            return null;
        }
        try {
            Process process = new ProcessBuilder("xcrun", "simctl", "list", "devices", "booted")
                    .redirectErrorStream(true)
                    .start();
            String booted = parseBootedSimulator(process);
            if (booted != null) {
                System.out.println("[DeviceManager] Simulator đang Booted: " + booted);
                return booted;
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        String toBoot = findShutdownSimulatorUdid();
        if (toBoot == null) {
            System.out.println("[DeviceManager] Không tìm thấy Simulator iPhone khả dụng (cài Xcode + runtime).");
            return null;
        }
        try {
            Process boot = new ProcessBuilder("xcrun", "simctl", "boot", toBoot)
                    .redirectErrorStream(true)
                    .start();
            boot.waitFor();
            Process open = new ProcessBuilder("open", "-a", "Simulator")
                    .redirectErrorStream(true)
                    .start();
            open.waitFor();
            System.out.println("[DeviceManager] Đã boot Simulator: " + toBoot);
            return toBoot;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private static String parseBootedSimulator(Process process) throws InterruptedException, IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = SIMCTL_BOOTED.matcher(line);
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        process.waitFor();
        return null;
    }

    private static String findShutdownSimulatorUdid() {
        String want = ConfigManager.get("ios.simulatorDeviceName");
        if (want == null || want.isBlank()) {
            want = "iPhone 14";
        }
        want = want.trim();

        try {
            Process process = new ProcessBuilder("xcrun", "simctl", "list", "devices", "available")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                String fallback = null;
                while ((line = reader.readLine()) != null) {
                    Matcher m = SIMCTL_DEVICE_LINE.matcher(line);
                    if (!m.find()) {
                        continue;
                    }
                    String name = m.group(1).trim();
                    String udid = m.group(2);
                    String state = m.group(3);
                    if (!name.toLowerCase().contains("iphone")) {
                        continue;
                    }
                    if ("Booted".equals(state)) {
                        return udid;
                    }
                    if (name.contains(want) || want.contains(name)) {
                        return udid;
                    }
                    if (fallback == null && "Shutdown".equals(state)) {
                        fallback = udid;
                    }
                }
                return fallback;
            } finally {
                process.waitFor();
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private static List<String> runIOSCommand(String... command) {
        List<String> result = new ArrayList<>();
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        result.add(trimmed);
                    }
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return result;
    }

    private static List<String> parseTideviceOutput(List<String> lines) {
        List<String> udids = new ArrayList<>();
        for (String line : lines) {
            if (line.contains("udid") || (line.length() == 40 && line.matches("[a-fA-F0-9]+"))) {
                String trimmed = line.replaceAll(".*\"udid\"\\s*:\\s*\"([^\"]+)\".*", "$1").trim();
                if (!trimmed.equals(line) && !trimmed.isEmpty()) {
                    udids.add(trimmed);
                } else if (line.matches("[a-fA-F0-9-]{36,40}")) {
                    udids.add(line.trim());
                }
            }
        }
        return udids;
    }

    // ================================================================
    //  AUTO DETECT PLATFORM
    // ================================================================

    public static String detectPlatform() {
        List<String> platforms = getAvailablePlatforms();
        if (platforms.contains("android")) {
            return "android";
        }
        if (platforms.contains("ios")) {
            return "ios";
        }
        throw new RuntimeException("[DeviceManager] Không tìm thấy thiết bị Android, iOS USB hay Simulator khả dụng.");
    }

    /**
     * Trả về danh sách các platform có thiết bị đang kết nối.
     */
    public static List<String> getAvailablePlatforms() {
        List<String> found = new ArrayList<>();
        if (hasConnectedAndroidDevice()) {
            found.add("android");
        }
        if (resolveIosUdid(true) != null) {
            found.add("ios");
        }
        return found;
    }
}
