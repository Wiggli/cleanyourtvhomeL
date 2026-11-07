package com.wiggli.launcherupdates;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class UpdatePolicyTest {
    @Test public void acceptsOnlyTheForkReleasePath() {
        assertTrue(UpdatePolicy.isTrustedReleaseUrl(
                "https://github.com/Wiggli/cleanyourtvhomeL/releases/download/luqa-launcher-latest/luqa-launcher.apk"));
        assertFalse(UpdatePolicy.isTrustedReleaseUrl("https://example.com/luqa-launcher.apk"));
        assertFalse(UpdatePolicy.isTrustedReleaseUrl(
                "https://github.com/Wiggli/cleanyourtvhomeL/releases/../other.apk"));
    }

    @Test public void installsOnlyAIncreasingVersionCode() {
        assertTrue(UpdatePolicy.isNewer(13, 14));
        assertFalse(UpdatePolicy.isNewer(13, 13));
        assertFalse(UpdatePolicy.isNewer(13, 12));
    }

    @Test public void acceptsOnlyACompleteSha256Digest() {
        assertTrue(UpdatePolicy.isSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
        assertFalse(UpdatePolicy.isSha256("0123456789abcdef"));
        assertFalse(UpdatePolicy.isSha256(null));
    }
}
