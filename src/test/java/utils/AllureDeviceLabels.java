package utils;

import io.qameta.allure.Allure;

public class AllureDeviceLabels {

    private AllureDeviceLabels() {
    }

    public static void attach(
            String branchName,
            String platform,
            String deviceName,
            String udid,
            String appiumPort,
            String extraPort1,
            String extraPort2
    ) {
        Allure.label("parentSuite", "Devices");
        Allure.label("suite", branchName);
        Allure.label("subSuite", "Appium Port " + appiumPort);

        Allure.label("device.branch", branchName);
        Allure.label("device.platform", platform);
        if (deviceName != null && !deviceName.isEmpty()) {
            Allure.label("device.name", deviceName);
        }
        Allure.label("device.udid", udid);
        Allure.label("device.appiumPort", appiumPort);

        if (extraPort1 != null && !extraPort1.isEmpty()) {
            Allure.label("device.extraPort1", extraPort1);
        }
        if (extraPort2 != null && !extraPort2.isEmpty()) {
            Allure.label("device.extraPort2", extraPort2);
        }
    }
}
