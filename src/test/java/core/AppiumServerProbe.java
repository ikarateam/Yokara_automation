package core;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Probe một danh sách candidate URL Appium theo thứ tự, trả về URL đầu tiên
 * phản hồi {@code 200 /status}.
 *
 * <p>Tồn tại để giải quyết "3 nguồn port không đồng bộ" giữa Jenkins
 * (spawn ở base 4700+), config local ({@code appiumServer}), và PM2 dev
 * (thường 4723 nhưng có thể đổi). Thay vì chọn cứng một nguồn rồi fail nếu
 * lệch, ta thử từng candidate trong vòng &lt;1s.
 *
 * <p>Thứ tự ưu tiên xem {@link #buildDefaultCandidates}.
 */
public final class AppiumServerProbe {

    /** Một candidate URL kèm nguồn (chỉ để in log/hint khi không có URL nào ready). */
    public record Candidate(String url, String source) {
    }

    private AppiumServerProbe() {
    }

    /**
     * Probe từng candidate. Trả về URL đầu tiên ready ({@code GET /status} = 200),
     * hoặc {@code null} nếu không URL nào đáp.
     *
     * <p>Trùng URL được dedup theo thứ tự xuất hiện (giữ candidate đầu tiên).
     */
    public static String firstReadyOrNull(List<Candidate> candidates, int connectTimeoutMs, int readTimeoutMs) {
        Set<String> seen = new LinkedHashSet<>();
        for (Candidate c : candidates) {
            if (c == null || c.url() == null || c.url().isBlank()) {
                continue;
            }
            String normalized = c.url().trim().replaceAll("/+$", "");
            if (!seen.add(normalized)) {
                continue;
            }
            if (probe(normalized, connectTimeoutMs, readTimeoutMs)) {
                return normalized;
            }
        }
        return null;
    }

    private static boolean probe(String baseUrl, int connectMs, int readMs) {
        HttpURLConnection con = null;
        try {
            URL u = new URL(baseUrl + "/status");
            con = (HttpURLConnection) u.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(connectMs);
            con.setReadTimeout(readMs);
            int code = con.getResponseCode();
            return code == 200;
        } catch (Exception e) {
            return false;
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    /**
     * Build candidate list mặc định theo thứ tự ưu tiên:
     * <ol>
     *   <li>{@code suitePortOrUrl} — từ TestNG suite param {@code suiteAppiumPort}
     *       (Jenkins truyền vào theo spawn map).</li>
     *   <li>{@code cliAppiumServer} — {@code -DappiumServer=...} escape hatch.</li>
     *   <li>{@code configAppiumServer} — {@code config/config.properties}.</li>
     *   <li>{@code http://127.0.0.1:4723} — Appium PM2 default.</li>
     *   <li>{@code http://127.0.0.1:4725} — PM2 alternate (đã gặp trên máy dev).</li>
     * </ol>
     *
     * <p>Jenkins luôn match #1 ngay (vì vừa spawn xong). Local: #1 fail (chưa
     * spawn) → rơi sang #3/#4/#5 nơi PM2 đang chạy. Cho phép {@code mvn test}
     * thuần hoạt động mà không cần truyền cờ.
     */
    public static List<Candidate> buildDefaultCandidates(String suitePortOrUrl,
                                                         String cliAppiumServer,
                                                         String configAppiumServer) {
        List<Candidate> out = new ArrayList<>();
        addIfPresent(out, toUrl(suitePortOrUrl), "suite param suiteAppiumPort");
        addIfPresent(out, cliAppiumServer, "-DappiumServer");
        addIfPresent(out, configAppiumServer, "config appiumServer");
        out.add(new Candidate("http://127.0.0.1:4723", "Appium default 4723"));
        out.add(new Candidate("http://127.0.0.1:4725", "PM2 fallback 4725"));
        return out;
    }

    private static void addIfPresent(List<Candidate> out, String url, String source) {
        if (url == null || url.isBlank()) {
            return;
        }
        out.add(new Candidate(url.trim(), source));
    }

    /** "4700" → "http://127.0.0.1:4700"; "http://..." giữ nguyên; null/blank → null. */
    private static String toUrl(String portOrUrl) {
        if (portOrUrl == null || portOrUrl.isBlank()) {
            return null;
        }
        String s = portOrUrl.trim();
        if (s.startsWith("http://") || s.startsWith("https://")) {
            return s;
        }
        return "http://127.0.0.1:" + s;
    }

    /**
     * Format candidate list thành text in được vào log/exception.
     * Mỗi dòng: {@code "  - <url>  (<source>)"}.
     */
    public static String formatCandidatesForLog(List<Candidate> candidates) {
        StringBuilder sb = new StringBuilder();
        for (Candidate c : candidates) {
            sb.append("  - ").append(c.url()).append("  (").append(c.source()).append(")\n");
        }
        return sb.toString();
    }
}
