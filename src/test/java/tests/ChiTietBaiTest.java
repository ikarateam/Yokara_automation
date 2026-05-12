package tests;

import org.testng.Assert;
import org.testng.annotations.Test;

import base.BaseDriver;
import base.BottomNav;
import base.ChiTietBaiScr;
import pages.trangchu.TrangChuScr;

import utils.StepUtils;

public class ChiTietBaiTest extends BaseDriver {

        @Test
        public void testCommentBaiThu() {
                BottomNav bottomNav = new BottomNav(driver);
                TrangChuScr trangChuScr = new TrangChuScr(driver);
                String content = "Nllb binh luan";
                String suggestion = "Mến chào cs";

                StepUtils.step("Xác nhận mở app",
                                () -> Assert.assertTrue(trangChuScr.isLoaded(), "Không thấy tab Trang chủ"));

                TrangChuScr trangChu = StepUtils.step("Nhấn vào Trang chủ", bottomNav::goToTrangChu);

                ChiTietBaiScr pbt = StepUtils.step("Mở MV bất kì ở phần 'MV nổi bật'",
                                trangChu::openRandomMvNoiBat);

                StepUtils.step("Gửi bình luận '" + content + "'", () -> pbt.sendComment(content));
                StepUtils.step("Xác nhận bình luận '" + content + "' đã hiển thị",
                                () -> Assert.assertTrue(pbt.isCommentDisplayed(content),
                                                "Không thấy bình luận '" + content + "' trong list"));

                StepUtils.step("Gửi bình luận gợi ý hiển thị '" + suggestion + "'",
                                () -> pbt.sendCommentWithSuggestion(suggestion));
                StepUtils.step("Xác nhận bình luận'" + suggestion + "' đã hiển thị",
                                () -> Assert.assertTrue(pbt.isCommentDisplayed(suggestion),
                                                "Không thấy bình luận'" + suggestion + "' trong list"));

                StepUtils.step("Trả lời bình luận của chính bản thân",
                                () -> pbt.replyComment("Lê Minh Hô", "Nllb binh luan", "reply comment"));
        }
}
