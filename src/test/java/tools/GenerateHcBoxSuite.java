package tools;

import core.AutomationDeviceSlot;
import core.DeviceManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Sinh {@code testng-multidevice.xml}: một {@code &lt;test&gt;} / thiết bị,
 * song song toàn bộ;
 * đồng thời ghi {@code target/appium-ports.txt} (mỗi dòng:
 * {@code port&lt;TAB&gt;platform&lt;TAB&gt;udid}) để
 * Jenkinsfile spawn 1 Appium server / device.
 */
public final class GenerateHcBoxSuite {

    private GenerateHcBoxSuite() {
    }

    public static void main(String[] args) throws Exception {
        List<AutomationDeviceSlot> slots = DeviceManager.listAutomationSlots();
        if (slots.isEmpty()) {
            System.err.println("[GenerateHcBoxSuite] Không có thiết bị Android/iOS USB. Kiểm tra adb / idevice_id.");
            System.exit(1);
            return;
        }

        Path suiteOut = Paths.get("testng-multidevice.xml");
        Files.writeString(suiteOut, buildXml(slots), StandardCharsets.UTF_8);
        System.out.println("[GenerateHcBoxSuite] " + slots.size() + " thiết bị → " + suiteOut.toAbsolutePath());

        Path portsOut = Paths.get("target", "appium-ports.txt");
        Files.createDirectories(portsOut.getParent());
        Files.writeString(portsOut, buildPortsFile(slots), StandardCharsets.UTF_8);
        System.out.println("[GenerateHcBoxSuite] ports → " + portsOut.toAbsolutePath());
    }

    private static String buildXml(List<AutomationDeviceSlot> slots) {
        int n = Math.max(slots.size(), 1);
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE suite SYSTEM \"https://testng.org/testng-1.0.dtd\">\n");
        sb.append("<!-- Auto-generated bởi tools.GenerateHcBoxSuite (process-test-classes).\n");
        sb.append("     KHÔNG sửa tay — sẽ bị overwrite mỗi lần `mvn test`.\n");
        sb.append("     1 <test> / device USB được detect; không cắm device = không có block. -->\n");
        // Suite name = "Devices" để khớp với parentSuite label do
        // AllureListener.attachExecutionMetadata gắn — Allure tree chỉ có 1 nhánh
        // tổ "Devices" thay vì 2 (khi allure-testng adapter set parentSuite từ
        // TestNG suite name win race condition với AllureListener override).
        sb.append("<suite name=\"Devices\" parallel=\"tests\" thread-count=\"")
                .append(n).append("\">\n\n");
        sb.append("    <!-- AllureTestNg: SPI allure-testng — tránh khai báo trùng trong suite -->\n");
        sb.append("    <listeners>\n");
        sb.append("        <listener class-name=\"listeners.AllureListener\"/>\n");
        sb.append("    </listeners>\n\n");

        for (AutomationDeviceSlot s : slots) {
            sb.append("    <test name=\"").append(escAttr(s.testNgTestName())).append("\">\n");
            sb.append("        <parameter name=\"suitePlatform\" value=\"").append(escAttr(s.platform()))
                    .append("\"/>\n");
            sb.append("        <parameter name=\"suiteUdid\" value=\"").append(escAttr(s.udid())).append("\"/>\n");
            sb.append("        <parameter name=\"suiteDeviceLabel\" value=\"").append(escAttr(humanLabel(s)))
                    .append("\"/>\n");
            sb.append("        <parameter name=\"suiteDeviceFolder\" value=\"").append(escAttr(s.reportFolderName()))
                    .append("\"/>\n");
            sb.append("        <parameter name=\"suiteAppiumPort\" value=\"").append(s.appiumPort()).append("\"/>\n");
            sb.append("        <classes>\n");
            sb.append("            <class name=\"tests.ChiTietBaiTest\"/>\n");
            sb.append("        </classes>\n");
            sb.append("    </test>\n\n");
        }

        sb.append("</suite>\n");
        return sb.toString();
    }

    private static String buildPortsFile(List<AutomationDeviceSlot> slots) {
        StringBuilder sb = new StringBuilder(256);
        for (AutomationDeviceSlot s : slots) {
            sb.append(s.appiumPort()).append('\t')
                    .append(s.platform()).append('\t')
                    .append(s.udid()).append('\n');
        }
        return sb.toString();
    }

    private static String humanLabel(AutomationDeviceSlot s) {
        return s.platform().toUpperCase() + " · " + s.displayName() + " · " + s.udid();
    }

    private static String escAttr(String v) {
        if (v == null) {
            return "";
        }
        return v.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
