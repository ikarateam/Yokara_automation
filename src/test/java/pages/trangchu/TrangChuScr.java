package pages.trangchu;

import base.BaseScr;
import base.ChiTietBaiScr;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;

/**
 * Trang chủ — accessibility id trùng {@code content-desc} (Android) /
 * {@code name} (iOS) từ dump;
 * riêng icon header phải tách XPath vì không có label cố định.
 */
public class TrangChuScr extends BaseScr {

    private final By lblTitle = AppiumBy.accessibilityId("Trang chủ");
    private final By btnTopRightAction;

    private final By btnSuKien;
    private final By btnQuanhDay;

    private final By lblMvNoiBat;

    private final base.BottomNav bottomNav;

    public TrangChuScr(AppiumDriver driver) {
        super(driver);
        bottomNav = new base.BottomNav(driver);

        this.btnTopRightAction = byPlatform(driver, androidHeaderSearch(), iosHeaderSearch());
        this.btnSuKien = AppiumBy.accessibilityId("Sự kiện");
        this.btnQuanhDay = AppiumBy.accessibilityId("Quanh đây");
        this.lblMvNoiBat = AppiumBy.accessibilityId("MV nổi bật");
    }

    private static By androidHeaderSearch() {
        return AppiumBy.xpath(
                "//android.view.View[@content-desc='Trang chủ' and @clickable='false']"
                        + "/following-sibling::android.widget.ImageView[1]");
    }

    /** Một Image 30×30 sau StaticText tiêu đề (dump iOS Trang chủ). */
    private static By iosHeaderSearch() {
        return AppiumBy.xpath(
                "//XCUIElementTypeStaticText[@name='Trang chủ']/following-sibling::XCUIElementTypeImage[1]");
    }

    public base.BottomNav nav() {
        return bottomNav;
    }

    public boolean isLoaded() {
        return isDisplayed(lblTitle);
    }

    public void clickTopRightAction() {
        click(btnTopRightAction);
    }

    public void clickSuKien() {
        click(btnSuKien);
    }

    public void clickQuanhDay() {
        click(btnQuanhDay);
    }

    public boolean isMvNoiBatDisplayed() {
        return isDisplayed(lblMvNoiBat);
    }

    public ChiTietBaiScr openRandomMvNoiBat() {
        By firstMvItem = AppiumBy.xpath(
                "//XCUIElementTypeApplication[@name=\"Yokara\"]/XCUIElementTypeWindow[1]"
                        + "/XCUIElementTypeOther/XCUIElementTypeOther/XCUIElementTypeOther"
                        + "/XCUIElementTypeOther[1]/XCUIElementTypeOther/XCUIElementTypeOther"
                        + "/XCUIElementTypeOther/XCUIElementTypeOther[2]/XCUIElementTypeOther[2]"
                        + "/XCUIElementTypeOther[2]/XCUIElementTypeOther[2]/XCUIElementTypeOther[2]"
                        + "/XCUIElementTypeOther[1]/XCUIElementTypeOther[2]/XCUIElementTypeOther[1]"
                        + "/XCUIElementTypeImage[2]");
        // select(): chỉ chờ visible, không yêu cầu clickable (XCUIElementTypeImage iOS
        // thường không pass elementToBeClickable của WDA).
        select(firstMvItem);
        return new ChiTietBaiScr(driver);
    }
}
