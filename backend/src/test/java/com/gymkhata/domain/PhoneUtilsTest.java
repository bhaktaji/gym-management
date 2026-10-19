package com.gymkhata.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneUtilsTest {

    @Test
    void normalizesAllInputForms() {
        assertEquals("+919876543210", PhoneUtils.normalize("9876543210"));
        assertEquals("+919876543210", PhoneUtils.normalize("09876543210"));
        assertEquals("+919876543210", PhoneUtils.normalize("+919876543210"));
        assertEquals("+919876543210", PhoneUtils.normalize("+91 98765 43210"));
        assertEquals("+919876543210", PhoneUtils.normalize("98765-43210"));
    }

    @Test
    void rejectsInvalidNumbers() {
        assertNull(PhoneUtils.normalize("12345"));
        assertNull(PhoneUtils.normalize("5876543210"));
        assertNull(PhoneUtils.normalize("98765432100"));
        assertNull(PhoneUtils.normalize(null));
        assertFalse(PhoneUtils.isValid("abc"));
    }

    @Test
    void masksForLogs() {
        assertEquals("+91*****0001", PhoneUtils.mask("+919000000001"));
        assertEquals("***", PhoneUtils.mask("123"));
    }
}
