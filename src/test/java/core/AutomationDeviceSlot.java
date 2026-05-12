package core;

/**
 * Một slot chạy automation: một UDID + tên hiển thị + thư mục Allure (đã sanitize) + port Appium riêng.
 *
 * <p>{@code appiumPort} là port của Appium server được spawn riêng cho slot này (mô hình 1 server / device,
 * cô lập flaky). Nếu chạy local chưa spawn server riêng, BaseDriver sẽ fallback về
 * {@code -DappiumServer} hoặc {@code appiumServer} trong config.</p>
 */
public record AutomationDeviceSlot(String platform,
                                   String udid,
                                   String displayName,
                                   String reportFolderName,
                                   int appiumPort) {

    public String testNgTestName() {
        String shortId = udid.length() > 8 ? udid.substring(udid.length() - 8) : udid;
        return platform + "-" + reportFolderName + "-" + shortId;
    }
}
