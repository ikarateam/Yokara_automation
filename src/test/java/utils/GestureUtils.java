//Xử lý gesture của mobile ví dụ Swipe, scrool, zoom, tap, drag

package utils;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Pause;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.Collections;

public class GestureUtils {

    public static void swipeUp(AndroidDriver driver) {

        Dimension size = driver.manage().window().getSize();

        int startX = size.width / 2;
        int startY = (int) (size.height * 0.8);
        int endY = (int) (size.height * 0.2);

        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");

        Sequence swipe = new Sequence(finger, 1);

        swipe.addAction(finger.createPointerMove(Duration.ZERO,
                PointerInput.Origin.viewport(), startX, startY));

        swipe.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));

        swipe.addAction(finger.createPointerMove(Duration.ofMillis(600),
                PointerInput.Origin.viewport(), startX, endY));

        swipe.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));

        driver.perform(Collections.singletonList(swipe));
    }

    public static void longPress(AndroidDriver driver, int x, int y) {

        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");

        Sequence longPress = new Sequence(finger, 1);

        longPress.addAction(finger.createPointerMove(Duration.ZERO,
                PointerInput.Origin.viewport(), x, y));

        longPress.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));

        longPress.addAction(new org.openqa.selenium.interactions.Pause(finger, Duration.ofSeconds(2)));

        longPress.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));

        driver.perform(Collections.singletonList(longPress));
    }

    /**
     * Tap một điểm theo tọa độ viewport (iOS/Android) — tránh
     * {@code mobile: clickGesture} chỉ x,y khi server không hỗ trợ.
     */
    public static void tapViewport(AppiumDriver driver, int x, int y) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence tap = new Sequence(finger, 1);
        tap.addAction(finger.createPointerMove(Duration.ZERO,
                PointerInput.Origin.viewport(), x, y));
        tap.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        tap.addAction(new Pause(finger, Duration.ofMillis(50)));
        tap.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(Collections.singletonList(tap));
    }

    /**
     * Tap tại tâm phần tử (W3C pointer) — iOS {@code StaticText} đôi khi không nhận
     * {@code WebElement#click()}
     * nhưng vẫn nhận tap tọa độ.
     */
    public static void tapElementCenter(AppiumDriver driver, WebElement el) {
        org.openqa.selenium.Rectangle r = el.getRect();
        int x = r.getX() + Math.max(1, r.getWidth()) / 2;
        int y = r.getY() + Math.max(1, r.getHeight()) / 2;
        Dimension win = driver.manage().window().getSize();
        x = Math.max(2, Math.min(x, win.getWidth() - 3));
        y = Math.max(2, Math.min(y, win.getHeight() - 3));
        tapViewport(driver, x, y);
    }

    /**
     * Long press tại tâm phần tử (W3C pointer) — cross-platform iOS + Android,
     * dùng khi cần trigger gesture giữ lâu (vd: mở popup chi tiết lượt like).
     * {@code durationMs} = thời gian giữ ngón tay xuống (typical 600–1000ms).
     */
    public static void longPressElement(AppiumDriver driver, WebElement el, int durationMs) {
        org.openqa.selenium.Rectangle r = el.getRect();
        int x = r.getX() + Math.max(1, r.getWidth()) / 2;
        int y = r.getY() + Math.max(1, r.getHeight()) / 2;
        Dimension win = driver.manage().window().getSize();
        x = Math.max(2, Math.min(x, win.getWidth() - 3));
        y = Math.max(2, Math.min(y, win.getHeight() - 3));

        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence seq = new Sequence(finger, 1);
        seq.addAction(finger.createPointerMove(Duration.ZERO,
                PointerInput.Origin.viewport(), x, y));
        seq.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        seq.addAction(new Pause(finger, Duration.ofMillis(durationMs)));
        seq.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(Collections.singletonList(seq));
    }

}