package utils;

import base.BaseDriver;
import io.qameta.allure.Allure;
import io.qameta.allure.Allure.ThrowableRunnable;
import io.qameta.allure.Allure.ThrowableRunnableVoid;

/**
 * Wrap action thành Allure step, attach screenshot + page source khi step fail.
 *
 * <p>QUAN TRỌNG: khi step fail, lỗi thường khiến phiên WDA/UiAutomator2 cũng loạng choạng
 * (đặc biệt iOS với WDA — "socket hang up"). Nếu screenshot/page-source cũng fail bên trong
 * catch block thì exception phụ này sẽ CHE exception gốc → debug rất khó. Vì vậy bọc cả hai
 * trong try-catch nội bộ và <b>luôn rethrow exception gốc</b>.</p>
 */
public class StepUtils {

    public static <T> T step(String name, ThrowableRunnable<T> action) {
        return Allure.step(name, () -> {
            StepContext.setStep(name);
            try {
                return action.run();
            } catch (Throwable e) {
                attachFailureArtifactsQuietly(name);
                throw e;
            }
        });
    }

    public static void step(String name, ThrowableRunnableVoid action) {
        Allure.step(name, () -> {
            StepContext.setStep(name);
            try {
                action.run();
            } catch (Throwable e) {
                attachFailureArtifactsQuietly(name);
                throw e;
            }
        });
    }

    private static void attachFailureArtifactsQuietly(String name) {
        try {
            ScreenshotUtils.attachScreenshot(BaseDriver.getDriver(), "FAILED STEP - " + name);
        } catch (Throwable ignored) {
            System.out.println("[StepUtils] Bỏ qua screenshot ở step fail '" + name
                    + "' (WDA/UiAutomator2 mất phản hồi): " + ignored.getMessage());
        }

        try {
            Allure.addAttachment(
                    "Page Source - " + name,
                    BaseDriver.getDriver().getPageSource()
            );
        } catch (Throwable ignored) {
            System.out.println("[StepUtils] Bỏ qua page source ở step fail '" + name
                    + "': " + ignored.getMessage());
        }
    }
}
