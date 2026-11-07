package com.wiggli.launcherupdates;

final class UpdatePolicy {
    private static final String RELEASE_PREFIX =
            "https://github.com/Wiggli/cleanyourtvhomeL/releases/";

    private UpdatePolicy() {
    }

    static boolean isTrustedReleaseUrl(String url) {
        return url != null && url.startsWith(RELEASE_PREFIX) && !url.contains("..")
                && !url.contains("?") && !url.contains("#");
    }

    static boolean isNewer(long installed, long published) {
        return installed >= 0 && published > installed;
    }

    static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }
}
