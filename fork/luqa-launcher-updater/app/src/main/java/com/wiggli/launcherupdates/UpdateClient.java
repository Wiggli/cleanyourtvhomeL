package com.wiggli.launcherupdates;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class UpdateClient {
    static final String LAUNCHER_PACKAGE = "com.gathin.tvhome";
    static final String MANIFEST_URL = "https://github.com/Wiggli/cleanyourtvhomeL/releases/download/luqa-launcher-latest/update.json";
    private static final int MAX_MANIFEST_BYTES = 32 * 1024;
    private static final int MAX_APK_BYTES = 2 * 1024 * 1024;

    private UpdateClient() {
    }

    static CheckResult check(Context context) {
        try {
            PackageInfo installed = context.getPackageManager().getPackageInfo(LAUNCHER_PACKAGE,
                    signingFlags());
            long installedCode = versionCode(installed);
            String json = readText(MANIFEST_URL, MAX_MANIFEST_BYTES);
            JSONObject root = new JSONObject(json);
            UpdateInfo update = new UpdateInfo(
                    root.getLong("versionCode"),
                    root.getString("versionName"),
                    root.getString("commit"),
                    root.getString("sha256").toLowerCase(Locale.ROOT),
                    root.getString("apkUrl"));
            if (!UpdatePolicy.isSha256(update.sha256)) {
                throw new SecurityException("The release checksum is invalid");
            }
            if (!UpdatePolicy.isTrustedReleaseUrl(update.apkUrl)) {
                throw new IllegalStateException("The release points outside the expected GitHub repository");
            }
            return new CheckResult(installedCode, installed.versionName, update,
                    UpdatePolicy.isNewer(installedCode, update.versionCode), null);
        } catch (PackageManager.NameNotFoundException e) {
            return new CheckResult(0, null, null, false,
                    "The CleanYourTV launcher is not installed on this box");
        } catch (Exception e) {
            return new CheckResult(0, null, null, false,
                    "The update check could not finish: " + safeMessage(e));
        }
    }

    static File downloadAndVerify(Context context, UpdateInfo update) throws Exception {
        File target = new File(context.getCacheDir(), "luqa-launcher.apk");
        File partial = new File(context.getCacheDir(), "luqa-launcher.apk.part");
        if (partial.exists() && !partial.delete()) throw new IllegalStateException("Old partial download could not be removed");
        download(update.apkUrl, partial, MAX_APK_BYTES);
        String actualHash = sha256(partial);
        if (!actualHash.equals(update.sha256)) {
            partial.delete();
            throw new SecurityException("The downloaded launcher did not match GitHub's checksum");
        }
        verifyPackageAndSignature(context, partial, update.versionCode);
        if (target.exists() && !target.delete()) throw new IllegalStateException("Old update could not be replaced");
        if (!partial.renameTo(target)) throw new IllegalStateException("Verified update could not be prepared");
        return target;
    }

    private static void verifyPackageAndSignature(Context context, File apk, long expectedVersion) throws Exception {
        PackageManager pm = context.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), signingFlags());
        if (archive == null || !LAUNCHER_PACKAGE.equals(archive.packageName)) {
            throw new SecurityException("The downloaded APK is not the CleanYourTV launcher");
        }
        if (versionCode(archive) != expectedVersion) {
            throw new SecurityException("The downloaded APK version does not match the release manifest");
        }
        PackageInfo installed = pm.getPackageInfo(LAUNCHER_PACKAGE, signingFlags());
        if (!signatureDigests(installed).equals(signatureDigests(archive))) {
            throw new SecurityException("The update has a different signing key and was blocked");
        }
    }

    private static Set<String> signatureDigests(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners() : info.signingInfo.getSigningCertificateHistory();
        } else {
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0) throw new SecurityException("APK signing certificate is missing");
        Set<String> hashes = new HashSet<>();
        for (Signature signature : signatures) hashes.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
        return hashes;
    }

    private static String readText(String address, int limit) throws Exception {
        HttpURLConnection connection = open(address);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("GitHub returned HTTP " + connection.getResponseCode());
            }
            StringBuilder text = new StringBuilder();
            int count = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[2048];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    count += read;
                    if (count > limit) throw new SecurityException("Release manifest is unexpectedly large");
                    text.append(buffer, 0, read);
                }
            }
            return text.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static void download(String address, File output, int limit) throws Exception {
        HttpURLConnection connection = open(address);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("GitHub returned HTTP " + connection.getResponseCode());
            }
            int declared = connection.getContentLength();
            if (declared > limit) throw new SecurityException("Launcher package is unexpectedly large");
            int total = 0;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream outputStream = new FileOutputStream(output)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > limit) throw new SecurityException("Launcher package is unexpectedly large");
                    outputStream.write(buffer, 0, read);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Accept", "application/json, application/vnd.android.package-archive");
        connection.setRequestProperty("User-Agent", "LuqaLauncherUpdater/1.0");
        return connection;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return out.toString();
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    @SuppressWarnings("deprecation")
    private static int signingFlags() {
        return Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    static final class UpdateInfo {
        final long versionCode;
        final String versionName;
        final String commit;
        final String sha256;
        final String apkUrl;

        UpdateInfo(long versionCode, String versionName, String commit, String sha256, String apkUrl) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.commit = commit;
            this.sha256 = sha256;
            this.apkUrl = apkUrl;
        }
    }

    static final class CheckResult {
        final long installedVersionCode;
        final String installedVersionName;
        final UpdateInfo update;
        final boolean updateAvailable;
        final String error;

        CheckResult(long installedVersionCode, String installedVersionName, UpdateInfo update,
                    boolean updateAvailable, String error) {
            this.installedVersionCode = installedVersionCode;
            this.installedVersionName = installedVersionName;
            this.update = update;
            this.updateAvailable = updateAvailable;
            this.error = error;
        }
    }
}
