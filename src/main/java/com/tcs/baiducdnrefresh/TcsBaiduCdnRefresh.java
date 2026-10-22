package com.tcs.baiducdnrefresh;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.TimeZone;

public class TcsBaiduCdnRefresh extends JavaPlugin {

    private String accessKey;
    private String secretKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadCredentials();

        if (isCredentialMissing()) {
            getLogger().warning("========================================");
            getLogger().warning("未配置百度云 AccessKey / SecretKey！");
            getLogger().warning("请编辑 plugins/TcsBaiduCdnRefresh/config.yml");
            getLogger().warning("填写 access-key 和 secret-key 后重启服务器。");
            getLogger().warning("========================================");
        }

        getLogger().info("tcsbaiducdnrefresh 插件已启用");
    }

    @Override
    public void onDisable() {
        getLogger().info("tcsbaiducdnrefresh 插件已禁用");
    }

    private void loadCredentials() {
        this.accessKey = getConfig().getString("access-key", "").trim();
        this.secretKey = getConfig().getString("secret-key", "").trim();
    }

    private boolean isCredentialMissing() {
        return accessKey.isEmpty()
                || secretKey.isEmpty()
                || accessKey.equals("your-access-key-here")
                || secretKey.equals("your-secret-key-here");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("baiducdnrefresh")) return false;

        if (!sender.hasPermission("tcs.baiducdnrefresh")) {
            sender.sendMessage("§c你没有权限执行这个命令！");
            return true;
        }

        loadCredentials();
        if (isCredentialMissing()) {
            sender.sendMessage("§c插件未配置百度云 AccessKey / SecretKey，请联系管理员！");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§c用法: /baiducdnrefresh <url> [directory|file]");
            return true;
        }

        String url = args[0];
        String type = args.length > 1 ? args[1].toLowerCase() : "file";

        if (type.equals("directory") && !url.endsWith("/")) {
            sender.sendMessage("§c目录刷新必须以 / 结尾！");
            return true;
        }

        sender.sendMessage("§e正在提交刷新任务，请稍候...");

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String taskJson = String.format("{\"tasks\":[{\"url\":\"%s\",\"type\":\"%s\"}]}", url, type);
                String response = sendPostRequest("https://cdn.baidubce.com/v2/cache/purge", taskJson);

                sender.sendMessage("§a刷新请求已提交，返回信息：");
                sender.sendMessage(response);
            } catch (Exception e) {
                sender.sendMessage("§c请求失败: " + e.getMessage());
                getLogger().log(Level.SEVERE, "请求百度CDN刷新失败", e);
            }
        });

        return true;
    }

    private String sendPostRequest(String requestUrl, String jsonBody) throws Exception {
        URL url = new URL(requestUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        String auth = generateAuthorization("POST", url.getPath(), "", new HashMap<>(), secretKey, accessKey);
        conn.setRequestProperty("Authorization", auth);
        conn.setRequestProperty("Host", url.getHost());

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        InputStream is = conn.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST
                ? conn.getInputStream() : conn.getErrorStream();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    private static String generateAuthorization(String method, String path, String queryString,
                                                 Map<String, String> headers, String sk, String ak) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = sdf.format(new Date());

        int expirationInSeconds = 1800;

        Map<String, String> signHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        signHeaders.put("host", headers.getOrDefault("Host", "cdn.baidubce.com"));

        String signedHeaders = "host";

        String authStringPrefix = String.format("bce-auth-v1/%s/%s/%d", ak, timestamp, expirationInSeconds);

        String signingKey = sha256Hex(sk, authStringPrefix);

        String canonicalURI = pathEncode(path);
        String canonicalQueryString = queryString == null ? "" : queryString;
        String canonicalHeaders = "host:" + signHeaders.get("host").trim().toLowerCase();
        String canonicalRequest = String.format("%s\n%s\n%s\n%s", method.toUpperCase(), canonicalURI, canonicalQueryString, canonicalHeaders);

        String signature = sha256Hex(signingKey, canonicalRequest);

        return String.format("%s/%s/%s", authStringPrefix, signedHeaders, signature);
    }

    private static String sha256Hex(String key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : rawHmac) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String pathEncode(String path) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        for (String seg : path.split("/")) {
            if (!seg.isEmpty()) {
                sb.append("/").append(URLEncoder.encode(seg, "UTF-8")
                        .replace("+", "%20")
                        .replace("*", "%2A")
                        .replace("%7E", "~"));
            }
        }
        return sb.length() > 0 ? sb.toString() : "/";
    }
}