package listeners;

import base.BaseDriver;
import core.ConfigManager;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.model.Parameter;
import io.qameta.allure.model.Label;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;

public class AllureListener implements ITestListener {

    private static final ThreadLocal<Boolean> METADATA_ATTACHED = ThreadLocal.withInitial(() -> false);

    @Override
    public void onStart(ITestContext context) {
        // no-op
    }

    @Override
    public void onFinish(ITestContext context) {
        // no-op
    }

    @Override
    public void onTestStart(ITestResult result) {
        METADATA_ATTACHED.set(false);
        attachExecutionMetadata(result);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        attachExecutionMetadata(result);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        attachExecutionMetadata(result);
        attachFailureArtifacts();

        Throwable throwable = result.getThrowable();
        if (throwable != null) {
            Allure.addAttachment("Failure Message", throwable.toString());
            Allure.addAttachment("Failure Stacktrace", asStackTrace(throwable));
        }
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        attachExecutionMetadata(result);
        attachFailureArtifacts();

        Throwable throwable = result.getThrowable();
        if (throwable != null) {
            Allure.addAttachment("Skip Reason", throwable.toString());
        }
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        attachExecutionMetadata(result);
    }

    @Override
    public void onTestFailedWithTimeout(ITestResult result) {
        onTestFailure(result);
    }

    private void attachExecutionMetadata(ITestResult result) {
        if (Boolean.TRUE.equals(METADATA_ATTACHED.get())) {
            return;
        }

        String platform = firstNonBlank(
                System.getProperty("platform"),
                parameter(result, "suitePlatform"),
                "unknown"
        ).toLowerCase();

        String udid = "android".equals(platform)
                ? firstNonBlank(System.getProperty("android.udid"), parameter(result, "suiteUdid"))
                : firstNonBlank(System.getProperty("ios.udid"), parameter(result, "suiteUdid"));

        // Thứ tự ưu tiên (đồng bộ với BaseDriver.buildSuiteAppiumUrl + DriverFactory):
        //   1) suiteAppiumPort  — port thực được Driver kết nối, đến từ
        //      testng-multidevice.xml do GenerateHcBoxSuite sinh (1 server / device).
        //   2) -DappiumServer   — CLI override (vd profile -Plocal trỏ về 4723).
        //   3) ConfigManager    — appiumServer trong config/config.properties.
        //   4) "unknown"        — không xác định được.
        String suiteAppiumPort = parameter(result, "suiteAppiumPort");
        String appiumServer = firstNonBlank(
                buildLocalUrl(suiteAppiumPort),
                System.getProperty("appiumServer"),
                ConfigManager.get("appiumServer"),
                "unknown");

        String jenkinsSlot = firstNonBlank(System.getProperty("jenkins.slot"));
        String appiumPort = firstNonBlank(
                suiteAppiumPort,
                System.getProperty("jenkins.appiumPort"),
                portFromUrl(appiumServer));
        String androidSystemPort = firstNonBlank(System.getProperty("jenkins.systemPort"));
        String iosWdaPort = firstNonBlank(System.getProperty("jenkins.wdaLocalPort"));
        String iosMjpegPort = firstNonBlank(System.getProperty("jenkins.mjpegServerPort"));

        String deviceKey = buildDeviceKey(platform, udid);
        String originalTestName = result.getMethod().getMethodName();
        String portSummary = buildPortSummary(platform, appiumPort, androidSystemPort, iosWdaPort, iosMjpegPort);
        String displayName = originalTestName + " [" + deviceKey
                + (portSummary.isEmpty() ? "" : " · " + portSummary) + "]";

        AllureLifecycle lifecycle = Allure.getLifecycle();

        // TestNG @Test(description="...") được allure-testng map vào trường `name`
        // (test title) → bị listener này ghi đè bằng displayName. Phải khôi phục
        // description xuống trường `description`/`descriptionHtml` để Allure UI
        // hiển thị panel "Description" thay vì rỗng.
        String testNgDescription = null;
        try {
            testNgDescription = result.getMethod().getDescription();
        } catch (Exception ignored) {
        }
        final String descriptionToSet = (testNgDescription != null && !testNgDescription.isBlank())
                ? testNgDescription.trim()
                : null;

        try {
            lifecycle.updateTestCase(testResult -> {
                testResult.setName(displayName);
                if (descriptionToSet != null) {
                    testResult.setDescription(descriptionToSet);
                    testResult.setDescriptionHtml(descriptionToSet);
                }

                upsertLabel(testResult, "platform", platform);
                upsertLabel(testResult, "device", deviceKey);
                upsertLabel(testResult, "framework", "testng");
                upsertLabel(testResult, "host", safe(System.getProperty("user.name")));

                String branchName = firstNonBlank(System.getProperty("jenkins.branchName"), deviceKey);
                upsertLabel(testResult, "parentSuite", "Devices");
                upsertLabel(testResult, "suite", branchName);
                upsertLabel(testResult, "subSuite", "Appium Port " + firstNonBlank(appiumPort, "unknown"));
                upsertLabel(testResult, "device.branch", branchName);
                upsertLabel(testResult, "device.platform", platform);
                if (udid != null && !udid.isBlank()) {
                    upsertLabel(testResult, "device.udid", udid);
                }
                if (appiumPort != null && !appiumPort.isBlank()) {
                    upsertLabel(testResult, "device.appiumPort", appiumPort);
                }
                if ("android".equals(platform) && androidSystemPort != null && !androidSystemPort.isBlank()) {
                    upsertLabel(testResult, "device.extraPort1", androidSystemPort);
                }
                if ("ios".equals(platform)) {
                    if (iosWdaPort != null && !iosWdaPort.isBlank()) {
                        upsertLabel(testResult, "device.extraPort1", iosWdaPort);
                    }
                    if (iosMjpegPort != null && !iosMjpegPort.isBlank()) {
                        upsertLabel(testResult, "device.extraPort2", iosMjpegPort);
                    }
                }

                if (jenkinsSlot != null && !jenkinsSlot.isBlank()) {
                    upsertLabel(testResult, "jenkinsSlot", jenkinsSlot);
                }
                if (appiumPort != null && !appiumPort.isBlank()) {
                    upsertLabel(testResult, "appiumPort", appiumPort);
                }
                if (androidSystemPort != null && !androidSystemPort.isBlank()) {
                    upsertLabel(testResult, "androidSystemPort", androidSystemPort);
                }
                if (iosWdaPort != null && !iosWdaPort.isBlank()) {
                    upsertLabel(testResult, "wdaLocalPort", iosWdaPort);
                }
                if (iosMjpegPort != null && !iosMjpegPort.isBlank()) {
                    upsertLabel(testResult, "mjpegServerPort", iosMjpegPort);
                }

                upsertParameter(testResult, "platform", platform);
                upsertParameter(testResult, "device", deviceKey);

                if (udid != null && !udid.isBlank()) {
                    upsertParameter(testResult, "udid", udid);
                }
                if (appiumServer != null && !appiumServer.isBlank()) {
                    upsertParameter(testResult, "appiumServer", appiumServer);
                }
                if (jenkinsSlot != null && !jenkinsSlot.isBlank()) {
                    upsertParameter(testResult, "jenkins.slot", jenkinsSlot);
                }
                if (appiumPort != null && !appiumPort.isBlank()) {
                    upsertParameter(testResult, "appium.port", appiumPort);
                }
                if (androidSystemPort != null && !androidSystemPort.isBlank()) {
                    upsertParameter(testResult, "android.systemPort", androidSystemPort);
                }
                if (iosWdaPort != null && !iosWdaPort.isBlank()) {
                    upsertParameter(testResult, "ios.wdaLocalPort", iosWdaPort);
                }
                if (iosMjpegPort != null && !iosMjpegPort.isBlank()) {
                    upsertParameter(testResult, "ios.mjpegServerPort", iosMjpegPort);
                }
            });

            Allure.parameter("platform", platform);
            Allure.parameter("device", deviceKey);
            if (udid != null && !udid.isBlank()) {
                Allure.parameter("udid", udid);
            }
            if (appiumServer != null && !appiumServer.isBlank()) {
                Allure.parameter("appiumServer", appiumServer);
            }
            if (jenkinsSlot != null && !jenkinsSlot.isBlank()) {
                Allure.parameter("jenkins.slot", jenkinsSlot);
            }
            if (appiumPort != null && !appiumPort.isBlank()) {
                Allure.parameter("appium.port", appiumPort);
            }
            if (androidSystemPort != null && !androidSystemPort.isBlank()) {
                Allure.parameter("android.systemPort", androidSystemPort);
            }
            if (iosWdaPort != null && !iosWdaPort.isBlank()) {
                Allure.parameter("ios.wdaLocalPort", iosWdaPort);
            }
            if (iosMjpegPort != null && !iosMjpegPort.isBlank()) {
                Allure.parameter("ios.mjpegServerPort", iosMjpegPort);
            }

            METADATA_ATTACHED.set(true);
        } catch (Exception ex) {
            // Không để listener làm hỏng test run
            System.out.println("[AllureListener] attachExecutionMetadata failed: " + ex.getMessage());
        }
    }

    private String parameter(ITestResult result, String key) {
        if (result == null || result.getTestContext() == null) {
            return null;
        }
        try {
            return result.getTestContext().getCurrentXmlTest().getParameter(key);
        } catch (Exception e) {
            return null;
        }
    }

    private void upsertLabel(io.qameta.allure.model.TestResult testResult, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        for (Label label : testResult.getLabels()) {
            if (name.equals(label.getName())) {
                label.setValue(value);
                return;
            }
        }

        testResult.getLabels().add(new Label().setName(name).setValue(value));
    }

    private void upsertParameter(io.qameta.allure.model.TestResult testResult, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        for (Parameter p : testResult.getParameters()) {
            if (name.equals(p.getName())) {
                p.setValue(value);
                return;
            }
        }

        testResult.getParameters().add(new Parameter().setName(name).setValue(value));
    }

    private String buildDeviceKey(String platform, String udid) {
        if (udid == null || udid.isBlank()) {
            return platform;
        }

        String shortUdid = udid.length() > 8 ? udid.substring(udid.length() - 8) : udid;
        return platform + "-" + shortUdid;
    }

    private static String portFromUrl(String url) {
        if (url == null || url.isBlank() || "unknown".equalsIgnoreCase(url.trim())) {
            return null;
        }
        try {
            URI u = URI.create(url.trim());
            int p = u.getPort();
            return p > 0 ? String.valueOf(p) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Build URL Appium loopback từ {@code suiteAppiumPort} (TestNG parameter).
     * Trả {@code null} nếu port rỗng / không hợp lệ → caller fallback nguồn tiếp theo.
     */
    private static String buildLocalUrl(String port) {
        if (port == null || port.isBlank()) {
            return null;
        }
        try {
            int p = Integer.parseInt(port.trim());
            if (p <= 0) {
                return null;
            }
            return "http://127.0.0.1:" + p;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String buildPortSummary(String platform, String appiumPort,
            String androidSystemPort, String iosWdaPort, String iosMjpegPort) {
        StringBuilder sb = new StringBuilder();
        if (appiumPort != null && !appiumPort.isBlank()) {
            sb.append("A:").append(appiumPort);
        }
        if ("android".equals(platform) && androidSystemPort != null && !androidSystemPort.isBlank()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append("S:").append(androidSystemPort);
        }
        if ("ios".equals(platform)) {
            if (iosWdaPort != null && !iosWdaPort.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append("W:").append(iosWdaPort);
            }
            if (iosMjpegPort != null && !iosMjpegPort.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append("M:").append(iosMjpegPort);
            }
        }
        return sb.toString();
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
        return value == null ? "" : value;
    }

    private String asStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private void attachFailureArtifacts() {
        try {
            AppiumDriver driver = BaseDriver.getDriver();
            if (driver == null) {
                return;
            }

            byte[] screenshot = driver.getScreenshotAs(org.openqa.selenium.OutputType.BYTES);
            Allure.addAttachment("Failure Screenshot", "image/png",
                    new java.io.ByteArrayInputStream(screenshot), ".png");
            Allure.addAttachment("Failure Page Source", driver.getPageSource());
        } catch (Exception ignored) {
            // Có case fail rất sớm chưa có session/driver -> bỏ qua an toàn.
        }
    }
}