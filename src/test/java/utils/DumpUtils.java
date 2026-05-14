package utils;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.ios.IOSDriver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Ghi page-source XML ra file đĩa khi cần debug locator thất bại — bổ sung cho
 * {@link StepUtils} (đã attach page-source vào Allure mỗi step fail) bằng việc
 * ghi file đứng riêng đọc bằng editor được, không cần mở Allure report.
 *
 * <p>Dùng cho các locator quan trọng đang flaky / nghi sai cấu trúc, không phải
 * bật mặc định cho mọi step (đã có Allure attachment).
 */
public final class DumpUtils {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private DumpUtils() {
    }

    /**
     * Ghi {@code driver.getPageSource()} vào {@code scripts/xml_dumps/<platform>/<tag>_<timestamp>.xml}.
     * Log path ra stdout. Im lặng nếu fail (không che lỗi gốc của caller).
     *
     * @param tag prefix gọn không khoảng trắng, vd {@code "reply_fail"}.
     * @return path file đã ghi, hoặc {@code null} nếu fail.
     */
    public static Path dumpPageSourceQuietly(AppiumDriver driver, String tag) {
        try {
            String platformDir = (driver instanceof IOSDriver) ? "ios" : "android";
            Path dir = Paths.get("scripts", "xml_dumps", platformDir);
            Files.createDirectories(dir);
            String ts = LocalDateTime.now().format(TS);
            Path file = dir.resolve(tag + "_" + ts + ".xml");
            Files.writeString(file, driver.getPageSource(), StandardCharsets.UTF_8);
            System.out.println("[DumpUtils] Dump page source: " + file.toAbsolutePath());
            return file;
        } catch (Throwable t) {
            System.out.println("[DumpUtils] Dump fail (bỏ qua): " + t.getMessage());
            return null;
        }
    }
}
