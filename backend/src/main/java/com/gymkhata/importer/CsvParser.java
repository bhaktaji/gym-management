package com.gymkhata.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC-4180-ish CSV parser (handles quoted fields, commas, newlines).
 */
public final class CsvParser {

    private CsvParser() {
    }

    public static List<String[]> parse(String csv) {
        List<String[]> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean rowHasContent = false;

        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else {
                switch (c) {
                    case '"' -> {
                        inQuotes = true;
                        rowHasContent = true;
                    }
                    case ',' -> {
                        current.add(field.toString().trim());
                        field.setLength(0);
                        rowHasContent = true;
                    }
                    case '\r' -> {
                    }
                    case '\n' -> {
                        current.add(field.toString().trim());
                        field.setLength(0);
                        if (rowHasContent || !current.isEmpty()) {
                            rows.add(current.toArray(new String[0]));
                        }
                        current.clear();
                        rowHasContent = false;
                    }
                    default -> {
                        field.append(c);
                        rowHasContent = true;
                    }
                }
            }
        }
        if (field.length() > 0 || rowHasContent || !current.isEmpty()) {
            current.add(field.toString().trim());
            if (current.stream().anyMatch(s -> !s.isEmpty())) {
                rows.add(current.toArray(new String[0]));
            }
        }
        return rows;
    }
}
