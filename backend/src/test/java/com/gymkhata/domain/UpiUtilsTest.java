package com.gymkhata.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpiUtilsTest {

    @Test
    void validatesVpa() {
        assertTrue(UpiUtils.isValidVpa("owner@okhdfcbank"));
        assertTrue(UpiUtils.isValidVpa("john.doe@upi"));
        assertFalse(UpiUtils.isValidVpa("owner"));
        assertFalse(UpiUtils.isValidVpa("@bank"));
        assertFalse(UpiUtils.isValidVpa("a@b"));
    }

    @Test
    void validatesUtr() {
        assertTrue(UpiUtils.isValidUtr("123456789012"));
        assertFalse(UpiUtils.isValidUtr("12345678901"));
        assertFalse(UpiUtils.isValidUtr("12345678901a"));
    }

    @Test
    void buildsDeepLink() {
        String link = UpiUtils.buildDeepLink("owner@okhdfc", "Shakti Gym", 118000, "GK/26-27/000123");
        assertTrue(link.startsWith("upi://pay?"));
        assertTrue(link.contains("pa=owner%40okhdfc"));
        assertTrue(link.contains("pn=Shakti+Gym") || link.contains("pn=Shakti%20Gym"));
        assertTrue(link.contains("am=1180.00"));
        assertTrue(link.contains("cu=INR"));
        assertTrue(link.contains("tn=GK%2F26-27%2F000123"));
    }

    @Test
    void formatsAmountWithoutFloat() {
        assertEquals("1180.00", UpiUtils.amountToRupees(118000));
        assertEquals("0.05", UpiUtils.amountToRupees(5));
    }
}
