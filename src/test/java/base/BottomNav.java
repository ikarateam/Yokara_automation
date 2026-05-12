package base;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.ios.IOSDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import pages.trangchu.TrangChuScr;
import pages.tructuyen.tabtructuyen.TrucTuyenScr;
import pages.hat.HatScr;
import pages.tinnhan.TinNhanScr;
import pages.toi.ToiProfileScr;

import utils.GestureUtils;

import java.time.Duration;

/**
 * Thanh điều hướng dưới — các tab có {@code content-desc} / {@code name} trùng
 * chuỗi dùng chung
 * {@link AppiumBy#accessibilityId} (đối chiếu
 * {@code XML android Screen locator/HatScr_android.txt} dòng tab bar).
 * <p>
 * Riêng <strong>Hát</strong> và <strong>Tin nhắn</strong> (badge đổi số):
 * Android không có desc cố định / chuỗi khác iOS → {@link #byPlatform}.
 * </p>
 */
public class BottomNav extends BaseScr {

    private final By tabTrangChu = AppiumBy.accessibilityId("Trang chủ");
    private final By tabTrucTuyen = AppiumBy.accessibilityId("Trực tuyến");
    private final By tabTrucTuyenAndroidByUiAutomator = AppiumBy
            .androidUIAutomator("new UiSelector().description(\"Trực tuyến\")");
    private final By tabTrucTuyenAndroidByXpath = AppiumBy
            .xpath("//android.widget.ImageView[@content-desc='Trực tuyến']");
    /**
     * iOS: tùy build, Trang chủ / Trực tuyến có thể không phải XCUIElementTypeImage
     * liền kề.
     */
    private final By tabTrucTuyenIosFallback = AppiumBy.xpath(
            "//XCUIElementTypeImage[@name='Trang chủ']/following-sibling::XCUIElementTypeImage[1]");
    private final By tabTrucTuyenAfterTrangChuAny = AppiumBy.xpath(
            "//*[@name='Trang chủ']/following-sibling::*[1]");
    private final By tabHat;
    private final By tabTinNhan;
    private final By tabToi = AppiumBy.accessibilityId("Tôi");
    /**
     * iOS: cùng hàng tab với dump
     * {@code scripts/xml_dumps/ios/ToiProfileScr_ios.xml} (Hát → Tin nhắn → Tôi).
     */
    private final By tabToiIosFallback = AppiumBy.xpath(
            "//XCUIElementTypeImage[@name='Trực tuyến']/following-sibling::XCUIElementTypeImage[3]");

    public BottomNav(AppiumDriver driver) {
        super(driver);
        tabHat = byPlatform(driver, androidTabHat(), iosTabHat());
        tabTinNhan = byPlatform(driver, androidTabTinNhan(), iosTabTinNhan());
    }

    private static By androidTabHat() {
        return AppiumBy.xpath(
                "//android.widget.ImageView[@content-desc='Trực tuyến']"
                        + "/following-sibling::android.widget.ImageView[1]");
    }

    private static By iosTabHat() {
        return AppiumBy.xpath(
                "//XCUIElementTypeImage[@name='Trực tuyến']/following-sibling::XCUIElementTypeImage[1]");
    }

    private static By androidTabTinNhan() {
        return AppiumBy.xpath(
                "//android.widget.ImageView[@content-desc='Trực tuyến']"
                        + "/following-sibling::android.widget.ImageView[2]");
    }

    private static By iosTabTinNhan() {
        return AppiumBy.xpath(
                "//XCUIElementTypeImage[@name='Trực tuyến']/following-sibling::*[2]");
    }

    /**
     * iOS: icon tab đôi khi {@code visible=false} nhưng vẫn tap — dùng presence +
     * click.
     */
    private void tapBottomTab(By tab) {
        if (driver instanceof IOSDriver) {
            // iOS: tap bằng presence và đợi transition
            wait.until(ExpectedConditions.presenceOfElementLocated(tab)).click();
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            } // Minimal stable pause for iOS
        } else {
            click(tab);
        }
    }

    public TrangChuScr goToTrangChu() {
        tapBottomTab(tabTrangChu);
        return new TrangChuScr(driver);
    }

    /**
     * Tab thứ 2 trên thanh dưới (≈ 20% chiều ngang màn) khi WDA không resolve
     * accessibility.
     */
    private void tapIosApproxSecondBottomTab() {
        org.openqa.selenium.Dimension dim = driver.manage().window().getSize();
        int x = dim.getWidth() / 5;
        int y = (int) (dim.getHeight() * 0.93);
        GestureUtils.tapViewport(driver, x, y);
    }

    private void tapIosTrucTuyenTabWithFallbacks() {
        By[] attempts = new By[] {
                tabTrucTuyen,
                AppiumBy.iOSNsPredicateString("name == 'Trực tuyến' OR label == 'Trực tuyến'"),
                tabTrucTuyenAfterTrangChuAny,
                tabTrucTuyenIosFallback,
                AppiumBy.xpath("//XCUIElementTypeButton[@name='Trực tuyến']")
        };
        TimeoutException last = null;
        for (By by : attempts) {
            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(by)).click();
                return;
            } catch (TimeoutException e) {
                last = e;
            }
        }
        tapIosApproxSecondBottomTab();
        if (last != null) {
            try {
                Thread.sleep(400);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void tapAndroidTrucTuyenTabWithFallbacks() {
        By[] attempts = new By[] {
                tabTrucTuyen,
                tabTrucTuyenAndroidByUiAutomator,
                tabTrucTuyenAndroidByXpath
        };
        TimeoutException last = null;
        for (By by : attempts) {
            try {
                click(by);
                return;
            } catch (TimeoutException e) {
                last = e;
            }
        }
        // Khi app còn popup/chưa settle, thử xử lý popup rồi tap lại một lần.
        handleStartupPopups();
        for (By by : attempts) {
            try {
                click(by);
                return;
            } catch (TimeoutException e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
    }

    public TrucTuyenScr goToTrucTuyen() {
        if (driver instanceof IOSDriver) {
            try {
                tapBottomTab(tabTrangChu);
            } catch (TimeoutException ignored) {
                // vẫn thử Trực tuyến
            }
            tapIosTrucTuyenTabWithFallbacks();
            try {
                new WebDriverWait(driver, Duration.ofSeconds(15))
                        .until(d -> !d.findElements(AppiumBy.accessibilityId("Tạo phòng")).isEmpty()
                                || !d.findElements(AppiumBy.accessibilityId("Đề cử")).isEmpty()
                                || !d.findElements(AppiumBy.accessibilityId("Khám phá")).isEmpty());
            } catch (TimeoutException ignored) {
                tapIosTrucTuyenTabWithFallbacks();
            }
            TrucTuyenScr scr = new TrucTuyenScr(driver);
            Duration settle = Duration.ofSeconds(28);
            if (!scr.waitForScreenReady(settle)) {
                for (int retry = 0; retry < 3; retry++) {
                    tapIosTrucTuyenTabWithFallbacks();
                    if (scr.waitForScreenReady(Duration.ofSeconds(12))) {
                        break;
                    }
                }
            }
            return scr;
        }
        tapAndroidTrucTuyenTabWithFallbacks();
        TrucTuyenScr scr = new TrucTuyenScr(driver);
        scr.waitForScreenReady(Duration.ofSeconds(18));
        return scr;
    }

    public HatScr goToHat() {
        tapBottomTab(tabHat);
        return new HatScr(driver);
    }

    public TinNhanScr goToTinNhan() {
        tapBottomTab(tabTinNhan);
        return new TinNhanScr(driver);
    }

    public ToiProfileScr goToToi() {
        if (driver instanceof IOSDriver) {
            try {
                tapBottomTab(tabToi);
            } catch (TimeoutException e) {
                tapBottomTab(tabToiIosFallback);
            }
        } else {
            tapBottomTab(tabToi);
        }
        return new ToiProfileScr(driver);
    }
}
