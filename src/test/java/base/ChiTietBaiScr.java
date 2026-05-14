package base;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.ios.IOSDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import utils.GestureUtils;
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
 * Dump tham chiếu:
 * <ul>
 * <li>iOS: {@code scripts/xml_dumps/ios/ChiTietBaiScr_ios.xml},
 * {@code tc1_2.xml}, {@code fail_page.xml}.</li>
 * <li>Android: {@code scripts/xml_dumps/ChiTietBai_android.xml} (player),
 * {@code scripts/xml_dumps/ios/comment_android.xml} (composer + reply),
 * {@code scripts/xml_dumps/ios/fail_page.xml} (popup like detail).</li>
 * </ul>
 */
public class ChiTietBaiScr extends BaseScr {

    /**
     * Footer "Bình luận" tap để mở khung soạn comment.
     * iOS: name="Bình luận"; Android: content-desc="Bình luận".
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
     * Input nhập comment khi composer mở.
     * iOS (tc1_2.xml): {@code XCUIElementTypeTextField} không có acc-id — bắt theo
     * class.
     * Android (comment_android.xml line 61): {@code android.widget.EditText} có
     * {@code hint="Nói gì đi nào!"}.
     */
    private final By txtCommentInput;

    /**
     * Nút gửi cạnh input — sibling Image ngay sau input field (cả iOS lẫn Android
     * cùng pattern). Chỉ có 1 input trên composer state nên following-sibling đủ
     * phân biệt.
     */
    private final By btnSend;

    public ChiTietBaiScr(AppiumDriver driver) {
        super(driver);
        this.btnBack = buildBackButton(driver);

        boolean ios = driver instanceof IOSDriver;
        this.txtCommentInput = ios
                ? AppiumBy.className("XCUIElementTypeTextField")
                : AppiumBy.xpath("//android.widget.EditText[@hint='Nói gì đi nào!']");
        this.btnSend = ios
                ? AppiumBy.xpath("//XCUIElementTypeTextField/following-sibling::XCUIElementTypeImage[1]")
                : AppiumBy.xpath("//android.widget.EditText/following-sibling::android.widget.ImageView[1]");
        this.firstSuggestion = ios
                ? AppiumBy.xpath(
                        "//XCUIElementTypeTextField[@name='Nói gì đi nào!']"
                                + "/../XCUIElementTypeOther/XCUIElementTypeStaticText[1]")
                // Android: anchor theo EditText composer (hint 'Nói gì đi nào!' khi mở từ footer,
                // hoặc 'Trả lời @...' khi reply) — HorizontalScrollView suggestion là
                // preceding-sibling, child View đầu tiên có content-desc là gợi ý đầu (dump
                // scripts/xml_dumps/ios/comment_android.xml line 68–72).
                : AppiumBy.xpath(
                        "//android.widget.EditText[contains(@hint,'Nói gì đi nào')"
                                + " or starts-with(@hint,'Trả lời @')]"
                                + "/preceding-sibling::android.widget.HorizontalScrollView[1]"
                                + "/android.view.View[@content-desc and string-length(@content-desc)>0][1]");
        this.lblLikeDetailHeader = AppiumBy.xpath(
                "//*[(self::XCUIElementTypeStaticText and starts-with(@name,'Tất cả '))"
                        + " or (self::android.view.View and starts-with(@content-desc,'Tất cả '))]");
        this.btnUnlikeInLikeDetail = ios
                ? AppiumBy.xpath(
                        "//XCUIElementTypeStaticText[starts-with(@name,'Tất cả ')]"
                                + "/following-sibling::XCUIElementTypeImage[1]")
                // Android: popup không có nút bỏ-like riêng — tap row chính user (row đầu tiên
                // clickable sau header "Tất cả N"). Test scenario chỉ có 1 row (mình tự like
                // bài mình).
                : AppiumBy.xpath(
                        "//android.view.View[starts-with(@content-desc,'Tất cả ')]"
                                + "/following::android.view.View[@clickable='true'][1]");
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
     * <p>
     * Sau khi tap, input composer sẽ tự được fill bằng text này — không tự gửi.
     */
    public void selectRecommendMessage(String value) {
        // Cross-platform: Flutter set accessibility identifier = text gợi ý →
        // iOS expose qua name/label, Android expose qua content-desc. Cả hai
        // đều match được bằng AppiumBy.accessibilityId. Tránh XPath chứa
        // emoji/diacritics (vốn không cần thiết và dễ vỡ).
        select(AppiumBy.accessibilityId(value));
    }

    /**
     * Locator gợi ý đầu tiên trong composer.
     *
     * <p>Cấu trúc DOM (dump scripts/xml_dumps/ios/fail_page.xml line 352-376):
     * <pre>
     * Other (composer outer wrapper, parent TextField)
     *   ├─ Other (invisible)
     *   ├─ Other (suggestion bar wrapper)         ← chứa 16 StaticText
     *   │     ├─ ScrollView
     *   │     ├─ StaticText[1]  ← gợi ý đầu tiên (leftmost)
     *   │     └─ ... 16 StaticText
     *   ├─ Image
     *   ├─ TextField "Nói gì đi nào!"             ← anchor
     *   └─ Image
     * </pre>
     *
     * <p>StaticText gợi ý là <em>cháu</em> (không phải con trực tiếp) của parent
     * TextField → phải đi qua thêm 1 cấp {@code XCUIElementTypeOther}. XPath chỉ
     * dùng child axis + {@code [1]} (XPath 1.0 cơ bản, WDA xử lý ổn định).
     *
     * <p>App random thứ tự gợi ý mỗi lần mở composer → text trả về khác nhau
     * giữa các lần chạy. Caller phải đọc runtime, không hardcode.
     */
    private final By firstSuggestion;

    /**
     * Đọc text của gợi ý đầu tiên trong composer. Yêu cầu composer đã mở.
     * Throw nếu không đọc được name/label (vd: khung gợi ý chưa render xong).
     */
    public String readFirstSuggestionText() {
        WebElement el = WaitUtils.waitForVisible(driver, firstSuggestion);
        String text;
        if (driver instanceof IOSDriver) {
            text = firstNonBlank(
                    safeReadAttr(el, "name"),
                    safeReadAttr(el, "label"),
                    safeReadAttr(el, "value"));
        } else {
            // UiAutomator2 chỉ hỗ trợ {text,name} và {content-desc,contentDescription} —
            // KHÔNG hỗ trợ 'label' (sẽ throw UnsupportedCommandException).
            text = firstNonBlank(
                    safeReadAttr(el, "content-desc"),
                    safeReadAttr(el, "text"));
        }
        if (text == null || text.isBlank()) {
            throw new RuntimeException(
                    "[ChiTietBaiScr] Không đọc được text gợi ý đầu tiên (content-desc/text/name đều rỗng).");
        }
        return text;
    }

    private static String safeReadAttr(WebElement el, String attr) {
        try {
            return el.getAttribute(attr);
        } catch (Exception ignored) {
            return null;
        }
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
     * Flow TC2: mở composer → đọc gợi ý đầu tiên (app trả random nên không
     * hardcode)
     * → tap suggestion → gửi.
     *
     * @return text của gợi ý đã gửi để caller assert
     *         {@code isCommentDisplayed(...)}.
     */
    public String sendCommentWithFirstSuggestion() {
        StepUtils.step("Chọn Bình luận", this::openComposer);
        String picked = StepUtils.step("Đọc gợi ý đầu tiên",
                this::readFirstSuggestionText);
        StepUtils.step("Chọn Bình luận gợi ý '" + picked + "'",
                () -> selectRecommendMessage(picked));
        StepUtils.step("Bấm gửi", this::tapSend);
        return picked;
    }

    /**
     * Verify 1 comment có nội dung {@code content} đã xuất hiện trong list.
     * Dùng acc-id = content (cell wrapper Flutter set
     * {@code name} (iOS) / {@code content-desc} (Android) = content).
     *
     * <p>Sau {@code tapSend()}, list comment cần thời gian refresh từ BE rồi prepend
     * cell mới — phải dùng {@code wait.until(visibility...)} (DEFAULT 20s) thay vì
     * {@link #isDisplayed} fail-fast. UiAutomator2 (Android) render chậm hơn WDA (iOS),
     * fail-fast hay miss cell vừa gửi.
     */
    public boolean isCommentDisplayed(String content) {
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    AppiumBy.accessibilityId(content)));
            return true;
        } catch (Exception e) {
            return false;
        }
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
        By replyBtn = byPlatform(driver,
                AppiumBy.xpath(commentReplyButtonAndroidXpath(user, content)),
                AppiumBy.xpath(commentReplyButtonIosXpath(user, content)));

        StepUtils.step("Reply comment '" + content + "' của " + user, () -> {
            // Sau khi gửi 2+ comment liên tiếp, Flutter iOS có thể để cell "Nllb…"
            // ở trạng thái ghost (cell wrapper visible nhưng inner widget visible=false
            // + coords sai). XPath iOS đã thêm @visible='true' để loại ghost; helper
            // dưới đây ép scroll cell vào view trước, best-effort.
            ensureCellRendered(content);
            try {
                WaitUtils.waitForClickable(driver, replyBtn).click();
            } catch (Throwable t) {
                utils.DumpUtils.dumpPageSourceQuietly(driver, "reply_fail");
                throw t;
            }
            // sau click sẽ hiện input box trả lời với placeholder "Trả lời @<user>"
            try {
                typeReplyAndSend(reply, user);
            } catch (Throwable t) {
                utils.DumpUtils.dumpPageSourceQuietly(driver, "reply_compose_fail");
                throw t;
            }
        });
    }

    /**
     * iOS-only: ép XCUITest scroll list để cell có {@code @label=content} trở
     * thành visible thực sự (Flutter đôi khi để cell ghost {@code visible="false"}
     * với coords sai sau khi re-layout). Quiet — fail không throw để caller vẫn
     * có cơ hội với {@code waitForClickable} kế tiếp.
     */
    private void ensureCellRendered(String content) {
        if (!(driver instanceof IOSDriver)) {
            return;
        }
        try {
            driver.executeScript("mobile: scroll", java.util.Map.of(
                    "predicateString", "label == \"" + content.replace("\"", "\\\"") + "\"",
                    "toVisible", true));
        } catch (Exception e) {
            System.out.println("[ChiTietBaiScr] ensureCellRendered('" + content
                    + "') bỏ qua: " + e.getMessage());
        }
    }

    private static String commentReplyButtonIosXpath(String user, String content) {
        return "//XCUIElementTypeOther[" +
                ".//*[@name=" + xQuote(content) + "]" +
                " and .//XCUIElementTypeStaticText[@name=" + xQuote(user) + "]" +
                " and ./XCUIElementTypeStaticText[@name='Trả lời' and @visible='true']" +
                "]/XCUIElementTypeStaticText[@name='Trả lời' and @visible='true']";
    }

    /**
     * Cell wrapper Android = {@code android.view.View} có content-desc thời gian
     * (vd "Vừa xong", "1 phút trước"), chứa direct children: ImageView avatar,
     * View comment body (content-desc=content) lồng View user, View "Trả lời",
     * ImageView like (dump XML android Screen locator/ChiTietBaiScr_android.txt
     * line 65-72).
     */
    private static String commentReplyButtonAndroidXpath(String user, String content) {
        return "//android.view.View[" +
                ".//android.view.View[@content-desc=" + xQuote(content) + "]" +
                " and .//android.view.View[@content-desc=" + xQuote(user) + "]" +
                " and ./android.view.View[@content-desc='Trả lời']" +
                "]/android.view.View[@content-desc='Trả lời']";
    }

    /**
     * Nhập nội dung reply vào input và gửi.
     *
     * <p>
     * Sau khi tap "Trả lời" của 1 comment, iOS render input là
     * {@code XCUIElementTypeTextField} có {@code name="Trả lời @<repliedUser>"}.
     * Nút gửi tái dùng {@link #btnSend} (sibling Image kế TextField — chỉ có 1
     * TextField trên màn lúc reply mode mở).
     */
    private void typeReplyAndSend(String reply, String repliedUser) {
        // iOS Flutter: TextField name = "Trả lời @<user>" → accessibility id.
        // Android UiAutomator2: EditText không có content-desc, chỉ có hint
        // (dump scripts/xml_dumps/ios/comment_android.xml line 74) → phải dùng XPath @hint.
        By replyInput = byPlatform(driver,
                AppiumBy.xpath("//android.widget.EditText[@hint=" + xQuote("Trả lời @" + repliedUser) + "]"),
                AppiumBy.accessibilityId("Trả lời @" + repliedUser));
        type(replyInput, reply);
        click(btnSend);
    }

    /* ================= LIKE COMMENT ================= */

    /**
     * Like comment xác định bởi ({@code user} + {@code content}) {@code times} lần.
     *
     * <p>
     * Trong dump iOS, mỗi cell comment có 2 Image direct child: index=1 là avatar,
     * index=2 là nút like (không có acc-id). Locate cell theo cùng pattern
     * {@link #replyComment} (acc-id content + StaticText user + child trực tiếp
     * "Trả lời") rồi chọn {@code XCUIElementTypeImage[last()]} = nút like.
     *
     * <p>
     * Dùng {@link #select} thay vì {@code click} vì Image iOS thường không pass
     * {@code elementToBeClickable} của WDA.
     */
    public void likeComment(String user, String content, int times) {
        By likeBtn = byPlatform(driver,
                AppiumBy.xpath(commentLikeButtonAndroidXpath(user, content)),
                AppiumBy.xpath(commentLikeButtonIosXpath(user, content)));

        StepUtils.step("Like comment '" + content + "' của " + user + " x" + times, () -> {
            for (int i = 0; i < times; i++) {
                select(likeBtn);
            }
        });
    }

    /**
     * Mở popup chi tiết lượt like của comment xác định ({@code user} +
     * {@code content})
     * bằng long-press lên đúng nút like — cùng locator như {@link #likeComment}
     * nhưng
     * thay tap bằng W3C pointer long press (~800ms).
     *
     * <p>
     * Sau gesture, popup hiện header "Tất cả N" + list user đã like (dump
     * {@code scripts/xml_dumps/ios/tc1_2.xml}). Method block chờ header visible
     * trước khi return để các bước sau (vd: {@link #tapUnlikeInLikeDetail}) chạy
     * ổn định.
     */
    public void openLikeDetail(String user, String content) {
        By likeBtn = byPlatform(driver,
                AppiumBy.xpath(commentLikeButtonAndroidXpath(user, content)),
                AppiumBy.xpath(commentLikeButtonIosXpath(user, content)));

        StepUtils.step("Mở chi tiết lượt like của '" + content + "' của " + user, () -> {
            WebElement btn = WaitUtils.waitForVisible(driver, likeBtn);
            GestureUtils.longPressElement(driver, btn, 800);
            WaitUtils.waitForVisible(driver, lblLikeDetailHeader);
        });
    }

    /** Header popup chi tiết lượt like: "Tất cả N" (N thay đổi theo số like). */
    private final By lblLikeDetailHeader;

    /**
     * Nút bỏ like trong popup chi tiết: Image sibling kế header "Tất cả N"
     * (dump tc1_2.xml: cùng parent, x=378 vs StaticText x=16). Không có acc-id
     * nên locate qua following-sibling thay vì xpath theo cấu trúc cố định.
     */
    private final By btnUnlikeInLikeDetail;

    /** Tap Image bỏ like trong popup chi tiết. Yêu cầu popup đã mở. */
    public void tapUnlikeInLikeDetail() {
        select(btnUnlikeInLikeDetail);
    }

    private static String commentLikeButtonIosXpath(String user, String content) {
        return "//XCUIElementTypeOther[" +
                ".//*[@name=" + xQuote(content) + "]" +
                " and .//XCUIElementTypeStaticText[@name=" + xQuote(user) + "]" +
                " and ./XCUIElementTypeStaticText[@name='Trả lời']" +
                "]/XCUIElementTypeImage[last()]";
    }

    /**
     * Android cell wrapper giống {@link #commentReplyButtonAndroidXpath} —
     * lấy ImageView long-clickable cuối trong cell (avatar Image clickable nhưng
     * không long-clickable; like Image long-clickable=true — dump
     * XML android Screen locator/ChiTietBaiScr_android.txt line 71/79/86).
     */
    private static String commentLikeButtonAndroidXpath(String user, String content) {
        return "//android.view.View[" +
                ".//android.view.View[@content-desc=" + xQuote(content) + "]" +
                " and .//android.view.View[@content-desc=" + xQuote(user) + "]" +
                " and ./android.view.View[@content-desc='Trả lời']" +
                "]/android.widget.ImageView[@long-clickable='true'][last()]";
    }

    /* ================= DELETE COMMENT ================= */

    /**
     * Tap vào content bubble bên trong cell comment của ({@code user} +
     * {@code content}) để mở action sheet "Trả lời / Xoá".
     *
     * <p>Cell anchor (user + child trực tiếp {@code "Trả lời"}) chỉ dùng để scope
     * — đích tap là element {@code @name/@content-desc=content} bên trong. Lý do:
     * tâm cell wrapper có thể rơi vào action row "Trả lời", gây nhầm tap.
     *
     * <p>Dump: iOS {@code scripts/xml_dumps/ios/delete_comment.xml};
     * Android {@code scripts/xml_dumps/ios/PopupXoa.xml} (action sheet sau tap).
     */
    public void tapComment(String user, String content) {
        // Android: dùng accessibilityId trực tiếp — UiAutomator2 crash với XPath
        // nested 2-axis kiểu `//A[.//B and ./C]//D`. Content text là unique trong
        // scope test nên không cần anchor by user.
        // iOS: vẫn scope bằng cell anchor (user + child 'Trả lời') vì WDA xử lý
        // XPath tốt; đích là element @name=content bên trong cell, tránh tap rơi
        // vào action row 'Trả lời'.
        By target = byPlatform(driver,
                AppiumBy.accessibilityId(content),
                AppiumBy.xpath(commentContentIosXpath(user, content)));
        select(target);
    }

    private static String commentContentIosXpath(String user, String content) {
        return "//XCUIElementTypeOther[" +
                ".//XCUIElementTypeStaticText[@name=" + xQuote(user) + "]" +
                " and ./XCUIElementTypeStaticText[@name='Trả lời']" +
                "]//*[@name=" + xQuote(content) + "]";
    }

    /**
     * "Xoá" trong action sheet hiện ra sau {@link #tapComment}.
     *
     * <p>Element type khác platform (iOS Image / Android ImageView) nhưng cùng
     * acc-id "Xoá". Bắt theo type để phân biệt với StaticText "Trả lời"/"Xoá" của
     * cell comment hoặc keyboard key "Xóa" (lưu ý dấu — keyboard dùng "Xóa", action
     * sheet dùng "Xoá").
     */
    private final By btnXoaInActionSheet = byPlatform(driver,
            AppiumBy.xpath("//android.widget.ImageView[@content-desc='Xoá']"),
            AppiumBy.xpath("//XCUIElementTypeImage[@name='Xoá']"));

    public void tapXoaInActionSheet() {
        select(btnXoaInActionSheet);
    }

    /**
     * Nút Có / Không trong popup "Thông báo — Bạn có chắc chắn muốn xoá …?".
     *
     * <p>Cả iOS (XCUIElementTypeButton) và Android (android.widget.Button) đều có
     * acc-id "Có" / "Không" — dùng {@link AppiumBy#accessibilityId} cross-platform
     * 1 dòng (không cần byPlatform).
     */
    private final By btnKhongInConfirmDelete = AppiumBy.accessibilityId("Không");
    private final By btnCoInConfirmDelete = AppiumBy.accessibilityId("Có");

    public void tapKhongInConfirmDelete() {
        click(btnKhongInConfirmDelete);
    }

    public void tapCoInConfirmDelete() {
        click(btnCoInConfirmDelete);
    }

    /**
     * Đóng action sheet "Trả lời / Xoá" sau khi user chọn "Không" — popup confirm
     * đã tắt nhưng action sheet vẫn nổi (verified bằng test tay trên cả 2 platform).
     *
     * <p>Cả iOS (StaticText) và Android (View) đều expose overlay scrim với
     * acc-id "Dismiss" full screen.
     */
    private final By overlayDismiss = AppiumBy.accessibilityId("Dismiss");

    public void dismissActionSheet() {
        select(overlayDismiss);
    }

    /**
     * Flow xoá 1 comment: tap content → "Xoá" → "Có"/"Không" trong confirm popup.
     *
     * @param status {@code "Có"} (xác nhận xoá) hoặc {@code "Không"} (huỷ).
     *               Chấp nhận không phân biệt hoa/thường và không dấu
     *               (vd {@code "co"}, {@code "khong"}). Input khác →
     *               {@link IllegalArgumentException}.
     */
    public void deleteComment(String user, String content, String status) {
        boolean confirmYes = parseDeleteStatus(status);

        StepUtils.step("Ấn vào bình luận '" + content + "' của " + user,
                () -> tapComment(user, content));
        StepUtils.step("Chọn Xoá ở action sheet",
                this::tapXoaInActionSheet);
        if (confirmYes) {
            StepUtils.step("Chọn Có ở dialog xác nhận",
                    this::tapCoInConfirmDelete);
        } else {
            StepUtils.step("Chọn Không ở dialog xác nhận",
                    this::tapKhongInConfirmDelete);
            // App không tự đóng action sheet sau khi chọn "Không" — phải tap
            // overlay Dismiss; nếu không các thao tác sau (vd tap comment khác)
            // sẽ rơi vào sheet còn nổi.
            StepUtils.step("Đóng action sheet (tap overlay Dismiss)",
                    this::dismissActionSheet);
        }
    }

    private static boolean parseDeleteStatus(String status) {
        if (status == null) {
            throw new IllegalArgumentException(
                    "[deleteComment] status null — chấp nhận 'Có' hoặc 'Không'.");
        }
        String norm = java.text.Normalizer.normalize(status.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase();
        switch (norm) {
            case "co":
            case "yes":
                return true;
            case "khong":
            case "no":
                return false;
            default:
                throw new IllegalArgumentException(
                        "[deleteComment] status='" + status + "' không hợp lệ — chấp nhận 'Có' / 'Không'.");
        }
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
