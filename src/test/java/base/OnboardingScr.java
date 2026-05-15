package base;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;

import utils.WaitUtils;

/**
 * Màn onboarding marketing — xuất hiện mỗi khi cài mới hoặc relaunch app:
 * <ol>
 *   <li>Start: 1 màn intro ("Ứng dụng hát karaoke hàng đầu Việt Nam") +
 *       CTA "Bắt đầu".</li>
 *   <li>Questions: "Hãy trả lời 3 câu hỏi nhỏ trước khi vào hát nhé!" +
 *       nút "Bỏ qua" (top-right) để skip.</li>
 * </ol>
 *
 * <p>Dump tham chiếu:
 * {@code scripts/xml_dumps/obscr_start.xml},
 * {@code scripts/xml_dumps/obscr_boqua.xml}.
 *
 * <p>Tạm thời chỉ implement iOS (dump duy nhất hiện có). Android pending —
 * khi có dump sẽ chuyển sang locator cross-platform qua {@code byPlatform()}.
 */
public class OnboardingScr extends BaseScr {

    private final By btnBatDau;
    private final By btnBoQua;
    private final By lblStartSignature;
    private final By lblBoQuaSignature;

    public OnboardingScr(AppiumDriver driver) {
        super(driver);

        this.btnBatDau = AppiumBy.accessibilityId("Bắt đầu");
        this.btnBoQua = AppiumBy.accessibilityId("Bỏ qua");
        // Signature unique để dò màn onboarding mà không bị nhầm với dialog
        // ngẫu nhiên cùng có text "Bắt đầu" / "Bỏ qua" ở chỗ khác.
        this.lblStartSignature = AppiumBy.accessibilityId(
                "Ứng dụng hát karaoke hàng đầu Việt Nam");
        this.lblBoQuaSignature = AppiumBy.accessibilityId(
                "Hãy trả lời 3 câu hỏi nhỏ trước khi vào hát nhé!");
    }

    /**
     * Có đang ở màn onboarding (start hoặc questions) không. Check non-blocking,
     * dùng SHORT_TIMEOUT để không kéo dài setup khi onboarding không có.
     */
    public boolean isPresent() {
        return WaitUtils.isVisibleWithin(driver, lblStartSignature, WaitUtils.SHORT_TIMEOUT)
                || WaitUtils.isVisibleWithin(driver, lblBoQuaSignature, 1);
    }

    /**
     * Skip onboarding nếu hiện: tap "Bắt đầu" → tap "Bỏ qua". Mỗi bước check
     * trước khi tap để xử cả case chỉ còn 1 trong 2 màn (vd resume từ giữa).
     *
     * <p>Quiet — log nếu fail nhưng không throw, vì onboarding không phải core
     * flow của test (chỉ là cản đường).
     */
    public void skipIfPresent() {
        try {
            if (WaitUtils.isVisibleWithin(driver, lblStartSignature, WaitUtils.SHORT_TIMEOUT)) {
                click(btnBatDau);
                System.out.println("[OnboardingScr] đã tap 'Bắt đầu'");
            }
        } catch (Exception e) {
            System.out.println("[OnboardingScr] tap 'Bắt đầu' bỏ qua: " + e.getMessage());
        }

        try {
            if (WaitUtils.isVisibleWithin(driver, lblBoQuaSignature, WaitUtils.SHORT_TIMEOUT)) {
                click(btnBoQua);
                System.out.println("[OnboardingScr] đã tap 'Bỏ qua'");
            }
        } catch (Exception e) {
            System.out.println("[OnboardingScr] tap 'Bỏ qua' bỏ qua: " + e.getMessage());
        }
    }
}
