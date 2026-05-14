package tests;

import org.testng.Assert;
import org.testng.annotations.Test;

import base.BaseDriver;
import base.BottomNav;
import base.ChiTietBaiScr;
import pages.trangchu.TrangChuScr;

import utils.StepUtils;

public class ChiTietBaiTest extends BaseDriver {

        @Test(description = "Testcase chạy luồng bình luận, trả lời bình luận, like trong chi tiết bài thu")
        public void testCommentBaiThu() {
                BottomNav bottomNav = new BottomNav(driver);
                TrangChuScr trangChuScr = new TrangChuScr(driver);
                String content = "Nllb binh luan";

                StepUtils.step("Xác nhận mở app",
                                () -> Assert.assertTrue(trangChuScr.isLoaded(), "Không thấy tab Trang chủ"));

                TrangChuScr trangChu = StepUtils.step("Nhấn vào Trang chủ", bottomNav::goToTrangChu);

                ChiTietBaiScr pbt = StepUtils.step("Mở MV bất kì ở phần 'MV nổi bật'",
                                trangChu::openRandomMvNoiBat);

                StepUtils.step("Gửi bình luận '" + content + "'", () -> pbt.sendComment(content));
                StepUtils.step("Xác nhận bình luận '" + content + "' đã hiển thị",
                                () -> Assert.assertTrue(pbt.isCommentDisplayed(content),
                                                "Không thấy bình luận '" + content + "' trong list"));

                String suggestionSent = StepUtils.step("Gửi bình luận gợi ý đầu tiên",
                                pbt::sendCommentWithFirstSuggestion);
                StepUtils.step("Xác nhận đã gửi bình luận \"" + suggestionSent + "\"",
                                () -> Assert.assertTrue(pbt.isCommentDisplayed(suggestionSent),
                                                "Không thấy bình luận \"" + suggestionSent + "\" trong list"));

                StepUtils.step("Trả lời bình luận của chính bản thân",
                                () -> pbt.replyComment("Lê Minh Hô", "Nllb binh luan", "reply comment"));

                String me = "Lê Minh Hô";

                StepUtils.step("Like bình luận của bản thân '" + content + "'",
                                () -> pbt.likeComment(me, content, 1));

                StepUtils.step("Like thêm 2 lần",
                                () -> pbt.likeComment(me, content, 2));

                StepUtils.step("Mở chi tiết lượt like",
                                () -> pbt.openLikeDetail(me, content));

                StepUtils.step("Bỏ chọn like trong popup chi tiết",
                                pbt::tapUnlikeInLikeDetail);

                StepUtils.step("[TC_PBT_05] Check xoá bình luận TH chọn Không",
                                () -> pbt.deleteComment(me, content, "Không"));

                StepUtils.step("[TC_PBT_06] Check xoá bình luận TH chọn Có",
                                () -> pbt.deleteComment(me, content, "Có"));
        }
}
