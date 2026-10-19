package com.gymkhata.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndianNumberWordsTest {

    @Test
    void zero() {
        assertEquals("Zero Rupees Only", IndianNumberWords.amountInWords(0));
    }

    @Test
    void simpleAmounts() {
        assertEquals("One Rupees Only", IndianNumberWords.amountInWords(100));
        assertEquals("Twenty Five Rupees Only", IndianNumberWords.amountInWords(2500));
        assertEquals("One Hundred Rupees Only", IndianNumberWords.amountInWords(10000));
    }

    @Test
    void indianGroupingInWords() {
        assertEquals("One Thousand One Hundred Eighty Rupees Only", IndianNumberWords.amountInWords(118000));
        assertEquals("One Lakh Rupees Only", IndianNumberWords.amountInWords(10000000));
        assertEquals("Twelve Lakh Thirty Four Thousand Five Hundred Sixty Seven Rupees Only",
                IndianNumberWords.amountInWords(123456700L));
        assertEquals("One Crore Rupees Only", IndianNumberWords.amountInWords(1000000000L));
    }

    @Test
    void includesPaise() {
        assertEquals("One Hundred Rupees and Fifty Paise Only", IndianNumberWords.amountInWords(10050));
        assertEquals("Five Paise Only", IndianNumberWords.amountInWords(5));
    }
}
