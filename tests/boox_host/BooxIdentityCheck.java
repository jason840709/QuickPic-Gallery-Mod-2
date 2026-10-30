// SPDX-License-Identifier: MIT
package com.alensw.jni;

/** Exercises the fail-closed system identity boundary without running Android services. */
public final class BooxIdentityCheck {
    private static final String OEM =
            "92a5d646b797817fe1ebf9cd57734a320d71834e68bbe74b2e09e8be7c7668ec";
    private static int checks;

    private static void expect(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        final int system = 1, updatedSystem = 128;
        expect(BooxScreensaver.matchesMetadata("Onyx", 1000, system, true, OEM),
                "verified OEM system");
        expect(BooxScreensaver.matchesMetadata("ONYX", 1000, system, true, OEM),
                "case-insensitive manufacturer");
        expect(BooxScreensaver.matchesMetadata("Onyx", 1000, updatedSystem, true, OEM),
                "verified system app update");
        expect(!BooxScreensaver.matchesMetadata("Bigme", 1000, system, true, OEM),
                "another manufacturer with a copied certificate is rejected");
        expect(!BooxScreensaver.matchesMetadata("BOOX", 1000, system, true, OEM),
                "brand string is not the confirmed manufacturer");
        expect(!BooxScreensaver.matchesMetadata(null, 1000, system, true, OEM),
                "unknown manufacturer is rejected");
        expect(!BooxScreensaver.matchesMetadata("Onyx", 10234, 0, true, OEM),
                "sideloaded OEM reader is rejected");
        expect(!BooxScreensaver.matchesMetadata("Onyx", 1000, 0, true, OEM),
                "system UID alone is insufficient");
        expect(!BooxScreensaver.matchesMetadata("Onyx", 1000, system, false, OEM),
                "disabled OEM service is rejected");
        expect(!BooxScreensaver.matchesMetadata("Onyx", 1000, system, true, "other"),
                "untrusted signer is rejected");
        expect(!BooxScreensaver.matchesMetadata("Onyx", 1000, system, true, null),
                "missing signer is rejected");
        expect(!BooxScreensaver.supports(null), "null context is unsupported");
        expect(ScreensaverDevice.defaultAction(null) == 1, "null context keeps generic default");
        System.out.println("BOOX identity checks passed: " + checks);
    }
}
