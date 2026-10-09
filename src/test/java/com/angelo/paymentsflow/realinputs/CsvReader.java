package com.angelo.paymentsflow.realinputs;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * A minimal, correct <a href="https://datatracker.ietf.org/doc/html/rfc4180">RFC-4180</a>
 * CSV reader used only by the real-inputs benchmark to parse the vendored
 * Online Retail snapshot. It handles quoted fields, embedded commas, embedded
 * newlines, and the {@code ""} escape for a literal double quote, so product
 * descriptions such as {@code "ASSORTED COLOUR BIRD ORNAMENT, SET OF 3"} are
 * read as a single field rather than split on the comma.
 *
 * <p>A dedicated CSV library would do the same; keeping this in the test source
 * set avoids pulling a parser dependency into a ledger service that has no other
 * use for one, while still parsing the real file exactly.
 */
final class CsvReader {

    private CsvReader() {
    }

    /** Parses every record (including the header row) into a list of string arrays. */
    static List<String[]> readAll(Reader reader) throws IOException {
        List<String[]> records = new ArrayList<>();
        List<String> field = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        int c;
        while ((c = reader.read()) != -1) {
            char ch = (char) c;
            if (inQuotes) {
                if (ch == '"') {
                    int next = reader.read();
                    if (next == '"') {
                        current.append('"'); // escaped quote
                    } else {
                        inQuotes = false;
                        if (next == -1) {
                            break;
                        }
                        // Re-process the char that followed the closing quote.
                        ch = (char) next;
                        if (ch == ',') {
                            field.add(current.toString());
                            current.setLength(0);
                            fieldStarted = false;
                        } else if (ch == '\n' || ch == '\r') {
                            endRecord(records, field, current);
                            fieldStarted = false;
                            consumeCrLf(reader, ch);
                        } else {
                            current.append(ch);
                        }
                    }
                } else {
                    current.append(ch);
                }
            } else {
                if (ch == '"' && !fieldStarted && current.length() == 0) {
                    inQuotes = true;
                    fieldStarted = true;
                } else if (ch == ',') {
                    field.add(current.toString());
                    current.setLength(0);
                    fieldStarted = false;
                } else if (ch == '\n' || ch == '\r') {
                    endRecord(records, field, current);
                    fieldStarted = false;
                    consumeCrLf(reader, ch);
                } else {
                    current.append(ch);
                    fieldStarted = true;
                }
            }
        }
        // Flush a trailing field/record with no final newline.
        if (current.length() > 0 || !field.isEmpty()) {
            field.add(current.toString());
            records.add(field.toArray(new String[0]));
        }
        return records;
    }

    private static void endRecord(List<String[]> records, List<String> field, StringBuilder current) {
        field.add(current.toString());
        current.setLength(0);
        records.add(field.toArray(new String[0]));
        field.clear();
    }

    /** Swallows the second half of a CRLF pair (reader is not peekable, so this is best-effort). */
    private static void consumeCrLf(Reader reader, char seen) throws IOException {
        if (seen == '\r' && reader.markSupported()) {
            reader.mark(1);
            int next = reader.read();
            if (next != '\n' && next != -1) {
                reader.reset();
            }
        }
    }
}
