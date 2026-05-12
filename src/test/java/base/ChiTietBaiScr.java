package base;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.ios.IOSDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import utils.StepUtils;
import utils.WaitUtils;

import java.util.List;

/**
 * Màn Chi tiết bài thu (PBT — Phát bài thu).
 *
 * <p>
 * Đặt ở package {@code base} vì màn này được mở từ nhiều entry point khác nhau
 * (Trang chủ → bài thu, Khám phá → Top bài thu, Profile → Tác phẩm, …) — không
 * gắn
 * riêng vào một tab.
 *
 * <p>
 * Dump tham chiếu: {@code scripts/xml_dumps/ios/ChiTietBaiScr_ios.xml} (iOS).
 * Android dump
 * chưa có — các accessibility-id dùng chung (Flutter semantics) thường khớp cả
 * hai nền,
 * nhưng cần verify lại bằng dump Android trước khi merge production.
 */
public class ChiTietBaiScr extends BaseScr {

    /**
     * Footer "Bình luận" tap để mở khung soạn comment (x=0, y=834, w=222, h=37
     * trong dump iOS).
     */
    private final By btnBinhLuanFooter = AppiumBy.accessibilityId("Bình luận");

    /** Section header dạng "Bình luận N" (N thay đổi theo số comment hiện có). */
    private final By lblBinhLuanHeader = AppiumBy.xpath(
            "//*[starts-with(@name,'Bình luận ') or starts-with(@label,'Bình luận ') "
                    + "or starts-with(@content-desc,'Bình luận ')]");

    private final By lblBanDangChoi = AppiumBy.accessibilityId("Bạn đang chơi");
    private final By btnTheoDoi = AppiumBy.accessibilityId("Theo dõi");
    private final By btnXemThem = AppiumBy.accessibilityId("Xem thêm");
    private final By btnTraLoi = AppiumBy.accessibilityId("Trả lời");

    /** "Quà\nN" (Flutter ghép multiline thành 1 accessibility label). */
    private final By lblQua = AppiumBy.xpath(
            "//*[starts-with(@name,'Quà') or starts-with(@label,'Quà') "
                    + "or starts-with(@content-desc,'Quà')]");

    /** "Thích N" cùng pattern như Quà. */
    private final By lblThich = AppiumBy.xpath(
            "//*[starts-with(@name,'Thích ') or starts-with(@label,'Thích ') "
                    + "or starts-with(@content-desc,'Thích ')]");

    /**
     * Back top-left: dump iOS không có name — locator theo type + vị trí header (y
     * ~53).
     */
    private final By btnBack;

    /**
     * Input nhập comment khi composer mở. Dump iOS (tc1_2.xml line 287):
     * {@code XCUIElementTypeTextField} không có acc-id/label → bắt theo class.
     * Trên màn composer chỉ có 1 TextField nên đủ phân biệt.
     */
    private final By txtCommentInput = AppiumBy.className("XCUIElementTypeTextField");

    /**
     * Nút gửi cạnh input. Dump iOS (tc1_2.xml line 288):
     * {@code XCUIElementTypeImage}
     * không có acc-id, đứng kế ngay sau TextField trong cùng parent →
     * following-sibling.
     */
    private final By btnSend = AppiumBy.xpath(
            "//XCUIElementTypeTextField/following-sibling::XCUIElementTypeImage[1]");

    public ChiTietBaiScr(AppiumDriver driver) {
        super(driver);
        this.btnBack = buildBackButton(driver);
    }

    private static By buildBackButton(AppiumDriver driver) {
        if (driver instanceof IOSDriver) {
            // iOS dump: XCUIElementTypeButton đầu tiên ở header (x=0, y=53, w=48, h=49).
            return AppiumBy.xpath("(//XCUIElementTypeButton[@y<100])[1]");
        }
        // Android: chưa có dump cụ thể — fallback content-desc thường gặp ("Back" /
        // "Quay lại").
        return AppiumBy.xpath(
                "//android.widget.ImageButton[@content-desc='Back' or @content-desc='Quay lại']"
                        + " | //android.widget.ImageView[@content-desc='Back' or @content-desc='Quay lại']");
    }

    /**
     * Smoke: tín hiệu nhận diện màn — section "Bạn đang chơi" + header "Bình luận
     * N".
     * <p>
     * Không dùng footer "Bình luận" vì khi user scroll sâu vào comment list (dump
     * song-ca),
     * footer có thể visible=false; trong khi header "Bình luận N" luôn hiện diện
     * trên màn.
     */
    public boolean isLoaded() {
        try {
            wait.until(ExpectedConditions.and(
                    ExpectedConditions.visibilityOfElementLocated(lblBanDangChoi),
                    ExpectedConditions.visibilityOfElementLocated(lblBinhLuanHeader)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void tapCommentFooter() {
        click(btnBinhLuanFooter);
    }

    /* ================= COMPOSER ================= */

    /**
     * Mở composer bằng tap footer "Bình luận", chờ input xuất hiện.
     * iOS only (dump tc1_2.xml). Android pending dump.
     */
    public void openComposer() {
        click(btnBinhLuanFooter);
        WaitUtils.waitForVisible(driver, txtCommentInput);
    }

    /** Gõ text vào input composer (clear text cũ trước). Yêu cầu composer đã mở. */
    public void typeComment(String text) {
        type(txtCommentInput, text);
    }

    /**
     * Tap 1 bình luận đề xuất theo attribute {@code value} (= nội dung suggestion).
     * Dump iOS cho thấy mỗi suggestion là {@code XCUIElementTypeStaticText} với
     * name = value = label = text gợi ý. Dùng {@code @value} (thay vì acc-id) để
     * khớp đúng yêu cầu match theo value.
     *
     * <p>Sau khi tap, input composer sẽ tự được fill bằng text này — không tự gửi.
     */
    public void selectRecommendMessage(String value) {
        select(AppiumBy.xpath(
                "//XCUIElementTypeStaticText[@value=" + xQuote(value) + "]"));
    }

    
    /** Tap nút gửi cạnh input. */
    public void tapSend() {
        click(btnSend);
    }

    /** Flow TC1: mở composer → nhập text → gửi. Mỗi bước wrap thành Allure step. */
    public void sendComment(String content) {
        StepUtils.step("Chạm vào textbox Bình luận", this::openComposer);
        StepUtils.step("Nhập '" + content + "'", () -> typeComment(content));
        StepUtils.step("Bấm gửi", this::tapSend);
    }

    /**
     * Flow TC2: mở composer → gõ {@code typing} → chọn suggestion (overwrite input)
     * → gửi. Comment thực gửi đi sẽ có nội dung = {@code suggestion}.
     */
    public void sendCommentWithSuggestion(String suggestion) {
        StepUtils.step("Chọn Bình luận", this::openComposer);
        StepUtils.step("Chọn Bình luận gợi ý '" + suggestion + "'",
                () -> selectRecommendMessage(suggestion));
        StepUtils.step("Bấm gửi", this::tapSend);
    }

    /**
     * Verify 1 comment có nội dung {@code content} đã xuất hiện trong list.
     * Dùng acc-id = content (cell wrapper Flutter set name = content).
     */
    public boolean isCommentDisplayed(String content) {
        return isDisplayed(AppiumBy.accessibilityId(content));
    }

    public void tapBack() {
        click(btnBack);
    }

    public void tapTheoDoi() {
        click(btnTheoDoi);
    }

    /**
     * Tap "Theo dõi" theo {@code index} — dùng cho bài song ca (duet) có 2 nút Theo
     * dõi:
     * index=0 là chủ bài thu, index=1 là partner song ca (theo thứ tự DOM trong
     * dump).
     */
    public void tapTheoDoi(int index) {
        List<WebElement> nodes = wait.until(
                ExpectedConditions.presenceOfAllElementsLocatedBy(btnTheoDoi));
        if (index < 0 || index >= nodes.size()) {
            throw new IndexOutOfBoundsException(
                    "tapTheoDoi(index=" + index + "): chỉ có " + nodes.size() + " nút Theo dõi");
        }
        nodes.get(index).click();
    }

    /** Số nút "Theo dõi" trên màn — 1 = bài solo, 2 = bài song ca. */
    public int countTheoDoiButtons() {
        return finds(btnTheoDoi).size();
    }

    public void tapXemThem() {
        click(btnXemThem);
    }

    public void tapTraLoi() {
        click(btnTraLoi);
    }

    public boolean isBinhLuanHeaderDisplayed() {
        return isDisplayed(lblBinhLuanHeader);
    }

    /**
     * Trả về số comment lấy từ header "Bình luận N", hoặc -1 nếu không parse được.
     */
    public int getBinhLuanCount() {
        return parseTrailingInt(getAccessibilityText(lblBinhLuanHeader));
    }

    /** Trả về số lượt thích lấy từ label "Thích N", hoặc -1. */
    public int getThichCount() {
        return parseTrailingInt(getAccessibilityText(lblThich));
    }

    /**
     * Lấy text accessibility theo platform: iOS dùng name/label/value, Android dùng
     * content-desc.
     */
    private String getAccessibilityText(By locator) {
        try {
            WebElement el = find(locator);
            if (driver instanceof IOSDriver) {
                return firstNonBlank(el.getAttribute("name"), el.getAttribute("label"), el.getAttribute("value"));
            }
            return el.getAttribute("content-desc");
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String... parts) {
        if (parts == null)
            return null;
        for (String p : parts) {
            if (p != null && !p.isBlank())
                return p.trim();
        }
        return null;
    }

    /**
     * "Bình luận 13" → 13; "Quà\n190" → 190; "Thích 111" → 111. Trả về -1 khi không
     * có số cuối.
     */
    private static int parseTrailingInt(String raw) {
        if (raw == null || raw.isBlank())
            return -1;
        String s = raw.replace('\n', ' ').trim();
        int i = s.length();
        while (i > 0 && Character.isDigit(s.charAt(i - 1)))
            i--;
        if (i == s.length())
            return -1;
        try {
            return Integer.parseInt(s.substring(i));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /* ================= REPLY COMMENT ================= */

    /**
     * Click nút "Trả lời" của comment được xác định bởi ({@code user} +
     * {@code content}),
     * sau đó nhập {@code reply} và gửi.
     *
     * <p>
     * Cách định vị: tìm cell container (XCUIElementTypeOther) là tổ tiên gần nhất
     * của
     * cả accessibility-id content lẫn StaticText username, đồng thời chứa <em>child
     * trực tiếp</em>
     * là StaticText "Trả lời" → tránh dính "Trả lời" của reply lồng bên trong.
     *
     * <p>
     * Android: chưa có dump → chỉ chạy đúng trên iOS. Cần dump XML Android list
     * comment
     * trước khi mở rộng cho Android (CLAUDE.md §6).
     */
    public void replyComment(String user, String content, String reply) {
        String cellXpath = "//XCUIElementTypeOther[" +
                ".//*[@name=" + xQuote(content) + "]" +
                " and .//XCUIElementTypeStaticText[@name=" + xQuote(user) + "]" +
                " and ./XCUIElementTypeStaticText[@name='Trả lời']" +
                "]/XCUIElementTypeStaticText[@name='Trả lời']";

        StepUtils.step("Reply comment '" + content + "' của " + user, () -> {
            WaitUtils.waitForClickable(driver, AppiumBy.xpath(cellXpath)).click();
            // sau click sẽ hiện input box trả lời với placeholder "Trả lời @<user>"
            typeReplyAndSend(reply, user);
        });
    }

    /**
     * Nhập nội dung reply vào input và gửi.
     *
     * <p>Sau khi tap "Trả lời" của 1 comment, iOS render input là
     * {@code XCUIElementTypeTextField} có {@code name="Trả lời @<repliedUser>"}.
     * Nút gửi tái dùng {@link #btnSend} (sibling Image kế TextField — chỉ có 1
     * TextField trên màn lúc reply mode mở).
     */
    private void typeReplyAndSend(String reply, String repliedUser) {
        By replyInput = AppiumBy.accessibilityId("Trả lời @" + repliedUser);
        type(replyInput, reply);
        click(btnSend);
    }

    /* ================= LIKE COMMENT ================= */

    /**
     * Like comment xác định bởi ({@code user} + {@code content}) {@code times} lần.
     *
     * <p>Trong dump iOS, mỗi cell comment có 2 Image direct child: index=1 là avatar,
     * index=2 là nút like (không có acc-id). Locate cell theo cùng pattern
     * {@link #replyComment} (acc-id content + StaticText user + child trực tiếp
     * "Trả lời") rồi chọn {@code XCUIElementTypeImage[last()]} = nút like.
     *
     * <p>Dùng {@link #select} thay vì {@code click} vì Image iOS thường không pass
     * {@code elementToBeClickable} của WDA.
     */
    public void likeComment(String user, String content, int times) {
        String likeXpath = "//XCUIElementTypeOther[" +
                ".//*[@name=" + xQuote(content) + "]" +
                " and .//XCUIElementTypeStaticText[@name=" + xQuote(user) + "]" +
                " and ./XCUIElementTypeStaticText[@name='Trả lời']" +
                "]/XCUIElementTypeImage[last()]";
        By likeBtn = AppiumBy.xpath(likeXpath);

        StepUtils.step("Like comment '" + content + "' của " + user + " x" + times, () -> {
            for (int i = 0; i < times; i++) {
                select(likeBtn);
            }
        });
    }

    /**
     * Escape chuỗi để nhúng vào XPath an toàn khi chuỗi có thể chứa cả {@code '} và
     * {@code "}.
     *
     * <ul>
     * <li>Không có {@code '} → bọc bằng {@code '...'}.</li>
     * <li>Không có {@code "} → bọc bằng {@code "..."}.</li>
     * <li>Có cả hai → dùng {@code concat(...)} ghép chuỗi con.</li>
     * </ul>
     */
    private static String xQuote(String s) {
        if (!s.contains("'"))
            return "'" + s + "'";
        if (!s.contains("\""))
            return "\"" + s + "\"";
        return "concat('" + s.replace("'", "',\"'\",'") + "')";
    }
}
