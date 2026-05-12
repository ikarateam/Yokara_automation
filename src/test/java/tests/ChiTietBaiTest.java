package tests;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import base.BaseDriver;
import base.BottomNav;
import base.ChiTietBaiScr;
import pages.trangchu.TrangChuScr;

import utils.StepUtils;

/**
 * Testcase: Comment bài thu khi chưa đăng nhập.
 *
 * <p>Precondition: User đã đăng xuất.
 *
 * <p>Steps / Expected:
 * <ol>
 *   <li>Mở app                                                 → Hiển thị màn hình Tab Home.</li>
 *   <li>Nhấn vào Trang chủ                                     → Hiện loading dữ liệu màn Trang chủ.</li>
 *   <li>Chờ 2s                                                 → Hiển thị danh sách top MV và danh sách bài thu.</li>
 *   <li>Lướt xuống danh sách bài thu, mở 1 bài đã có bình luận → Mở màn hình PBT (Phát bài thu).</li>
 *   <li>Nhấn ô nhập bình luận ở footer                         → Chuyển hướng qua màn hình đăng nhập.</li>
 * </ol>
 */
public class ChiTietBaiTest extends BaseDriver {

    @Test
    public void testCommentBaiThuKhiChuaDangNhap() {
        BottomNav bottomNav = new BottomNav(driver);

        // Step 1 — Mở app: BaseDriver.setup() đã đảm bảo AUT foreground + có bottom tab bar.
        // Expected: Hiển thị màn hình Tab Home (sau setup, app đứng ở Trang chủ mặc định).
        TrangChuScr trangChuScr = new TrangChuScr(driver);
        StepUtils.step("Mở app — verify tab Home hiển thị",
            () -> Assert.assertTrue(trangChuScr.isLoaded(), "Sau khi mở app, không thấy tab Trang chủ")
        );

        // Step 2 — Nhấn vào Trang chủ.
        // Expected: Hiện giao diện loading dữ liệu Trang chủ.
        TrangChuScr trangChuAfterTap = StepUtils.step("Nhấn vào tab Trang chủ", bottomNav::goToTrangChu);

        // Step 3 — Chờ 2s.
        // Expected: Hiển thị danh sách top MV và danh sách bài thu.
        // Note: thay flat-sleep 2s bằng explicit wait nội tại của isMvNoiBatDisplayed()
        // (BaseScr default 20s, theo CLAUDE.md §5 "Không thêm Thread.sleep mới").
        StepUtils.step("Chờ Trang chủ load xong và verify Top MV hiển thị",
            () -> Assert.assertTrue(trangChuAfterTap.isMvNoiBatDisplayed(),
                "Không thấy khối 'MV nổi bật' trên Trang chủ sau khi load")
        );

        // ====================================================================
        // Step 4 — Lướt xuống danh sách bài thu và nhấn mở 1 bài đã có bình luận.
        //          Expected: Mở màn hình PBT (Phát bài thu).
        //
        // Step 5 — Nhấn vào ô nhập bình luận ở footer.
        //          Expected: Chuyển hướng qua màn hình đăng nhập.
        //
        // PENDING — chưa có Page Object cho:
        //   - Danh sách "bài thu" trên Trang chủ (locator item card + helper scroll-to)
        //   - Màn PBT (Phát bài thu): footer comment input
        //   - LoginMethodScr điều hướng từ PBT khi user chưa đăng nhập
        //
        // Khi đã có PO, các step tiếp theo:
        //   StepUtils.step("Lướt xuống và mở bài thu đã có bình luận",
        //       () -> trangChuAfterTap.openFirstBaiThuWithComment());
        //   PhatBaiThuScr pbt = new PhatBaiThuScr(driver);
        //   StepUtils.step("Verify màn PBT đã mở",
        //       () -> Assert.assertTrue(pbt.isLoaded(), "Không vào được màn Phát bài thu"));
        //
        //   StepUtils.step("Nhấn ô nhập bình luận ở footer", pbt::tapCommentFooter);
        //   LoginMethodScr loginScr = new LoginMethodScr(driver);
        //   StepUtils.step("Verify chuyển hướng sang màn đăng nhập",
        //       () -> Assert.assertTrue(loginScr.isLoaded(),
        //           "Sau khi tap comment, không thấy màn đăng nhập"));
        // ====================================================================
        throw new SkipException(
            "Pending PO: bài thu list trên TrangChu, PhatBaiThuScr, điều hướng login khi chưa đăng nhập."
        );
    }

    /**
     * Testcase: Reply 1 comment cụ thể (xác định bằng user + content).
     *
     * <p>Precondition: đã đứng ở màn ChiTietBai (PBT) có comment "Hat hay qua c oi giong co luyen thanh"
     * của user "Abysss" — tham chiếu dump {@code scripts/xml_dumps/ios/ChiTietBaiScr_ios.xml}.
     *
     * <p>BLOCKED — chưa có dump XML khi reply input đang mở; bước nhập + gửi nội dung reply
     * hiện đang ném {@code UnsupportedOperationException} từ {@link ChiTietBaiScr#replyComment}
     * (TODO trong PO). Cần dump màn ở trạng thái reply để bổ sung locator input + nút gửi.
     */
    @Test
    public void testReplyComment() {
        ChiTietBaiScr chiTietBaiScr = new ChiTietBaiScr(driver);

        StepUtils.step("Reply comment 'Hat hay qua c oi giong co luyen thanh' của Abysss",
            () -> chiTietBaiScr.replyComment(
                "Abysss",
                "Hat hay qua c oi giong co luyen thanh",
                "haha"
            )
        );
    }
}
