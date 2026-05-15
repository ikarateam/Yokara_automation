package base;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.pagefactory.AppiumFieldDecorator;
import org.openqa.selenium.*;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Map;
import java.util.List;

/**
 * Lớp cơ sở cho tất cả Page Object.
 *
 * <p>Sử dụng {@link AppiumFieldDecorator} để các subclass có thể dùng
 * annotation {@code @AndroidFindBy} hoặc {@code @iOSXCUITFindBy}
 * cho locator đa nền tảng.</p>
 *
 * <pre>
 * Ví dụ trong Page class con:
 *
 * {@literal @}AndroidFindBy(id = "com.example:id/btn_login")
 * {@literal @}iOSXCUITFindBy(accessibility = "LoginButton")
 * private WebElement btnLogin;
 * </pre>
 */
public class BaseScr {

    protected AppiumDriver driver;
    protected WebDriverWait wait;

    private static final int DEFAULT_WAIT_SECONDS = 20;

    // Pattern chuẩn của Appium PageFactory: this đã fully initialized trước khi truyền vào
    @SuppressWarnings("LeakingThisInConstructor")
    public BaseScr(AppiumDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_WAIT_SECONDS));

        // Khởi tạo các field được đánh dấu @AndroidFindBy / @iOSXCUITFindBy
        PageFactory.initElements(new AppiumFieldDecorator(driver), this);
    }

    /**
     * Thứ tự trên <strong>mọi nền tảng</strong>: (1) Android {@code resource-id} nếu có,
     * (2) {@link AppiumBy#accessibilityId} (Android: thường khớp content-desc; iOS: accessibility identifier),
     * (3) {@code fallback} (XPath, iOS predicate, …).
     * <p>Truyền {@code null} hoặc chuỗi rỗng để bỏ qua bước tương ứng.</p>
     */
    protected static By byIdThenFallback(
            AppiumDriver driver,
            String androidResourceId,
            String accessibilityId,
            By fallback) {
        return new By() {
            @Override
            public List<WebElement> findElements(SearchContext context) {
                if (driver instanceof AndroidDriver
                        && androidResourceId != null
                        && !androidResourceId.isBlank()) {
                    List<WebElement> byId = context.findElements(AppiumBy.id(androidResourceId));
                    if (!byId.isEmpty()) {
                        return byId;
                    }
                }
                if (accessibilityId != null && !accessibilityId.isBlank()) {
                    List<WebElement> byAcc = context.findElements(AppiumBy.accessibilityId(accessibilityId));
                    if (!byAcc.isEmpty()) {
                        return byAcc;
                    }
                }
                return fallback.findElements(context);
            }
        };
    }

    /**
     * Chọn locator theo nền tảng (multi-device / parallel): Android vs iOS.
     * Dùng khi đã bắt riêng từng nền (resource-id / XPath / accessibility).
     *
     * @see core.LocatorPolicy Quy ước: ưu tiên chuỗi chung (accessibility) trước khi tách Android/iOS.
     */
    protected static By byPlatform(AppiumDriver driver, By androidLocator, By iosLocator) {
        return driver instanceof IOSDriver ? iosLocator : androidLocator;
    }

    /* ================= FIND ================= */

    protected WebElement find(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected List<WebElement> finds(By locator) {
        return driver.findElements(locator);
    }

    /* ================= CLICK ================= */

    protected void click(By locator) {
        for (int i = 0; i < 3; i++) {
            try {
                WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
                element.click();
                return;
            } catch (StaleElementReferenceException e) {
                if (i == 2) throw e;
            }
        }
    }

    /**
     * Chọn / tap phần tử sau khi <strong>hiển thị</strong> ({@link ExpectedConditions#visibilityOfElementLocated}).
     * Ổn định hơn {@link #click(By)} khi WDA không đánh dấu clickable (iOS {@code StaticText}, một số icon).
     */
    protected void select(By locator) {
        for (int i = 0; i < 3; i++) {
            try {
                WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
                element.click();
                return;
            } catch (StaleElementReferenceException e) {
                if (i == 2) throw e;
            }
        }
    }

    /* ================= TYPE ================= */

    protected void type(By locator, String text) {
        for (int i = 0; i < 3; i++) {
            try {
                WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
                element.click();
                element.clear();
                element.sendKeys(text);
                return;
            } catch (StaleElementReferenceException e) {
                if (i == 2) throw e;
            }
        }
    }

    /* ================= DISPLAY & WAIT ================= */

    /**
     * Kiểm tra nhanh phần tử có hiển thị hay không (không dùng timeout dài).
     */
    protected boolean isDisplayed(By locator) {
        try {
            List<WebElement> els = driver.findElements(locator);
            return !els.isEmpty() && els.get(0).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Đợi tối đa timeout DEFAULT cho phần tử xuất hiện và hiển thị.
     */
    protected WebElement waitForVisible(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    /**
     * Đợi phần tử biến mất khỏi màn hình hoặc DOM.
     */
    protected void waitForInvisibility(By locator) {
        wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    /**
     * Đợi phần tử có trạng thái Clickable.
     */
    protected WebElement waitForClickable(By locator) {
        return wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    /* ================= SWIPE ================= */

    public void swipeUp() {
        if (driver instanceof io.appium.java_client.ios.IOSDriver) {
            Map<String, Object> params = new java.util.HashMap<>();
            params.put("direction", "up");
            driver.executeScript("mobile: swipe", params);
        } else {
            Dimension size = driver.manage().window().getSize();
            int startX = size.width / 2;
            int startY = (int) (size.height * 0.8);

            driver.executeScript("mobile: swipeGesture", Map.of(
                    "left", startX,
                    "top", startY,
                    "width", size.width,
                    "height", size.height,
                    "direction", "up",
                    "percent", 0.7
            ));
        }
    }

    /* ================= SCROLL ================= */

    public void scrollToElement(By locator) {
        int maxScroll = 6;

        for (int i = 0; i < maxScroll; i++) {
            if (isDisplayed(locator)) {
                return;
            }
            swipeUp();
        }

        throw new RuntimeException("Không tìm thấy element sau khi scroll: " + locator);
    }

    /* ================= STARTUP POPUPS ================= */

    /**
     * Xử lý các popup hệ thống / Welcome khi vừa mở app (notifications,
     * tracking, location, EULA, v.v.).
     *
     * <p>3 vòng retry, mỗi vòng:
     * <ul>
     *   <li>iOS: thử {@code mobile: alert(action=accept)} — system alert nằm
     *       ngoài page-source mặc định, xpath bên dưới không bắt được.</li>
     *   <li>In-app dialog có nút text "Đồng ý / Cho phép / Allow / AGREE":
     *       xpath cross-platform (Android {@code @text}, iOS {@code @name/@label}).</li>
     * </ul>
     * Lặp tới khi 1 vòng không bắt được popup nào → return. Cho phép app có
     * nhiều popup tuần tự (vd notifications → tracking → location).
     */
    public void handleStartupPopups() {
        By btnCommon = AppiumBy.xpath(
                "//*[contains(@text, 'Đồng ý') or contains(@name, 'Đồng ý') or contains(@label, 'Đồng ý')"
                        + " or contains(@text, 'Cho phép') or contains(@name, 'Cho phép') or contains(@label, 'Cho phép')"
                        + " or contains(@text, 'Allow') or contains(@name, 'Allow') or contains(@label, 'Allow')"
                        + " or contains(@text, 'AGREE') or contains(@name, 'AGREE')]"
        );

        for (int attempt = 0; attempt < 3; attempt++) {
            boolean handled = false;

            // 1) iOS native alert (UIAlertController) — không có trong page-source thường.
            if (driver instanceof IOSDriver) {
                try {
                    driver.executeScript("mobile: alert", Map.of("action", "accept"));
                    handled = true;
                } catch (Exception ignored) {
                    // Không có alert nào đang mở.
                }
            }

            // 2) In-app dialog (kể cả Android runtime permission). Polling ngắn 1s
            //    để bắt popup xuất hiện chậm hơn launch nhưng không kéo dài flow.
            try {
                if (utils.WaitUtils.isVisibleWithin(driver, btnCommon, 1)) {
                    click(btnCommon);
                    handled = true;
                }
            } catch (Exception ignored) {
            }

            if (!handled) {
                return;
            }
        }
    }

    /* ================= MAP CLICK ================= */

    protected void clickByKey(Map<String, By> map, String key) {
        By locator = map.get(key.toLowerCase());

        if (locator == null) {
            throw new RuntimeException("Locator not found for key: " + key);
        }

        click(locator);
    }

    /**
     * Tìm và click vào button dựa trên text (hỗ trợ cả Android & iOS).
     */
    protected void clickByText(String text) {
        By locator = AppiumBy.xpath(
                "//*[(self::button or self::android.widget.Button or self::XCUIElementTypeButton)" +
                        " and (contains(@text, '" + text + "') or contains(@name, '" + text + "') or contains(@label, '" + text + "'))]"
        );
        click(locator);
    }
}
