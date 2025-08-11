package com.rafhi.helper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// import com.github.axet.lookup.common.ImageBinaryGrey;

public class DateToWordsHelper {
    private final LocalDate date;

    public DateToWordsHelper(String dateStr) {
        this.date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    public String getDayOfWeek() {
        return date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.forLanguageTag("id-ID"));
    }

    public String getDay() {
        return numberToWords(date.getDayOfMonth());
    }

    public String getMonth() {
        return date.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.forLanguageTag("id-ID"));
    }

    public String getYear() {
        return numberToWords(date.getYear());
    }

    public String getFullDate() {
        return date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
    }

    // Simple number to words for Indonesian (1-31 and years)
    private String numberToWords(int number) {
        String[] units = {
            "", "Satu", "Dua", "Tiga", "Empat", "Lima", "Enam", "Tujuh", "Delapan", "Sembilan", "Sepuluh",
            "Sebelas", "Dua Belas", "Tiga Belas", "Empat Belas", "Lima Belas", "Enam Belas", "Tujuh Belas", "Delapan Belas", "Sembilan Belas"
        };
        String[] tens = {
            "", "", "Dua Puluh", "Tiga Puluh", "Empat Puluh", "Lima Puluh", "Enam Puluh", "Tujuh Puluh", "Delapan Puluh", "Sembilan Puluh"
        };

        if (number < 20) {
            return units[number];
        } else if (number < 100) {
            return tens[number / 10] + (number % 10 != 0 ? " " + units[number % 10] : "");
        } else if (number < 200) {
            return "Seratus" + (number % 100 != 0 ? " " + numberToWords(number % 100) : "");
        } else if (number < 1000) {
            return units[number / 100] + " Ratus" + (number % 100 != 0 ? " " + numberToWords(number % 100) : "");
        } else if (number < 2000) {
            return "Seribu" + (number % 1000 != 0 ? " " + numberToWords(number % 1000) : "");
        } else if (number < 10000) {
            return units[number / 1000] + " Ribu" + (number % 1000 != 0 ? " " + numberToWords(number % 1000) : "");
        } else {
            return Integer.toString(number);
        }
    }

    public String getFormattedDate() {
        // d-MMMM-yyyy akan menghasilkan format seperti "1-Juli-2025"
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("id-ID"));
        return date.format(formatter);
    }
}