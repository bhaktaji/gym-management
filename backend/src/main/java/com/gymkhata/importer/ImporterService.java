package com.gymkhata.importer;

import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.billing.model.Payment;
import com.gymkhata.billing.repo.InvoiceRepository;
import com.gymkhata.billing.repo.PaymentRepository;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.domain.Money;
import com.gymkhata.domain.PhoneUtils;
import com.gymkhata.domain.UpiUtils;
import com.gymkhata.member.MemberService;
import com.gymkhata.member.model.Member;
import com.gymkhata.member.repo.MemberRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ImporterService {

    public static final int MAX_CSV_CHARS = 512 * 1024;
    public static final int MAX_CSV_ROWS = 1000;

    private static final DateTimeFormatter DOB_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final Clock clock;

    public ImporterService(MemberRepository memberRepository,
                           MemberService memberService,
                           PaymentRepository paymentRepository,
                           InvoiceRepository invoiceRepository,
                           Clock clock) {
        this.memberRepository = memberRepository;
        this.memberService = memberService;
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.clock = clock;
    }

    public record ImportResult(int totalRows, int success, List<String> errors) {
    }

    /**
     * CSV import for members.
     * Supports standard positional columns (name,phone,email,gender,dob,address,state,whatsappOptIn,notes)
     * or named headers containing at least 'name' and 'phone'.
     */
    public ImportResult importMembers(String csv) {
        validateCsvEnvelope(csv);
        List<String[]> rows = CsvParser.parse(csv);
        if (rows.isEmpty()) {
            return new ImportResult(0, 0, List.of("Empty CSV"));
        }
        int dataRowCount = rows.size() - 1;
        if (dataRowCount > MAX_CSV_ROWS) {
            throw BusinessException.badRequest("CSV_TOO_MANY_ROWS",
                    "CSV exceeds maximum of " + MAX_CSV_ROWS + " data rows (received " + dataRowCount + ")");
        }

        Map<String, Integer> headerMap = buildHeaderMap(rows.get(0));
        if (!headerMap.containsKey("name") || !headerMap.containsKey("phone")) {
            throw BusinessException.badRequest("CSV_MISSING_COLUMNS",
                    "Member CSV header must include 'name' and 'phone' columns");
        }

        List<String> errors = new ArrayList<>();
        int success = 0;

        for (int i = 1; i < rows.size(); i++) {
            String[] r = rows.get(i);
            try {
                validateNoFormulaInjection(r, headerMap.getOrDefault("phone", 1));
                String name = getCol(r, headerMap, "name", 0);
                String phone = getCol(r, headerMap, "phone", 1);
                String email = getCol(r, headerMap, "email", 2);
                String gender = getCol(r, headerMap, "gender", 3);
                String dob = getCol(r, headerMap, "dob", 4);
                String address = getCol(r, headerMap, "address", 5);
                String state = getCol(r, headerMap, "state", 6);
                String wa = getCol(r, headerMap, "whatsappoptin", 7);
                String notes = getCol(r, headerMap, "notes", 8);

                memberService.create(
                        name,
                        phone,
                        email.isBlank() ? null : email,
                        gender.isBlank() ? null : gender,
                        dob.isBlank() ? null : LocalDate.parse(dob, DOB_FMT),
                        address.isBlank() ? null : address,
                        state.isBlank() ? null : state,
                        "true".equalsIgnoreCase(wa) || "yes".equalsIgnoreCase(wa) || "1".equals(wa),
                        notes.isBlank() ? null : notes
                );
                success++;
            } catch (Exception e) {
                errors.add("Row " + (i + 1) + ": " + e.getMessage());
            }
        }
        return new ImportResult(dataRowCount, success, errors);
    }

    /**
     * CSV import for payments.
     * Supports header: phone (or memberPhone), amount, method, utr (or reference), invoiceNumber (optional).
     * Uses BigDecimal integer paise conversion (never float/double) and persists payments to MongoDB.
     */
    public ImportResult importPayments(String csv) {
        validateCsvEnvelope(csv);
        List<String[]> rows = CsvParser.parse(csv);
        if (rows.isEmpty()) {
            return new ImportResult(0, 0, List.of("Empty CSV"));
        }
        int dataRowCount = rows.size() - 1;
        if (dataRowCount > MAX_CSV_ROWS) {
            throw BusinessException.badRequest("CSV_TOO_MANY_ROWS",
                    "CSV exceeds maximum of " + MAX_CSV_ROWS + " data rows (received " + dataRowCount + ")");
        }

        Map<String, Integer> headerMap = buildHeaderMap(rows.get(0));
        boolean hasPhoneCol = headerMap.containsKey("phone") || headerMap.containsKey("memberphone");
        if (!hasPhoneCol || !headerMap.containsKey("amount")) {
            throw BusinessException.badRequest("CSV_MISSING_COLUMNS",
                    "Payment CSV header must include 'phone' (or 'memberPhone') and 'amount' columns");
        }
        int phoneColIdx = headerMap.getOrDefault("phone", headerMap.getOrDefault("memberphone", 0));

        List<String> errors = new ArrayList<>();
        int success = 0;
        String gymId = SecurityUtils.currentGymId();

        for (int i = 1; i < rows.size(); i++) {
            String[] r = rows.get(i);
            try {
                validateNoFormulaInjection(r, phoneColIdx);
                String phone = col(r, phoneColIdx);
                String amount = getCol(r, headerMap, "amount", 1);
                String method = getCol(r, headerMap, "method", 2);
                String utrOrRef = headerMap.containsKey("utr")
                        ? col(r, headerMap.get("utr"))
                        : getCol(r, headerMap, "reference", 3);
                String invoiceNumber = getCol(r, headerMap, "invoicenumber", 4);

                String e164 = PhoneUtils.normalize(phone);
                if (e164 == null) {
                    throw new IllegalArgumentException("invalid phone");
                }
                Member member = memberRepository.findByGymIdAndPhone(gymId, e164)
                        .orElseThrow(() -> new IllegalArgumentException("member not found for phone"));

                long amountPaise = Money.ofRupees(new BigDecimal(amount.trim()));
                if (amountPaise <= 0) {
                    throw new IllegalArgumentException("amount must be > 0");
                }

                String invoiceId = null;
                if (!invoiceNumber.isBlank()) {
                    Invoice inv = invoiceRepository.findByGymIdOrderByCreatedAtDesc(gymId).stream()
                            .filter(x -> invoiceNumber.trim().equalsIgnoreCase(x.getInvoiceNumber()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("invoice not found: " + invoiceNumber));
                    if (Invoice.STATUS_VOID.equals(inv.getStatus())) {
                        throw new IllegalArgumentException("cannot pay void invoice: " + invoiceNumber);
                    }
                    invoiceId = inv.getId();
                } else {
                    // Link to most recent non-void invoice for this member if available
                    invoiceId = invoiceRepository.findByGymIdAndMemberIdOrderByCreatedAtDesc(gymId, member.getId()).stream()
                            .filter(x -> !Invoice.STATUS_VOID.equals(x.getStatus()))
                            .map(Invoice::getId)
                            .findFirst()
                            .orElse(null);
                }

                Payment p = new Payment();
                p.setGymId(gymId);
                p.setMemberId(member.getId());
                p.setInvoiceId(invoiceId);
                p.setAmountPaise(amountPaise);
                String normMethod = "UPI".equalsIgnoreCase(method) ? Payment.METHOD_UPI
                        : ("CARD".equalsIgnoreCase(method) ? Payment.METHOD_CARD : Payment.METHOD_CASH);
                p.setMethod(normMethod);
                p.setStatus(Payment.STATUS_CONFIRMED);
                if (UpiUtils.isValidUtr(utrOrRef)) {
                    String trimmedUtr = utrOrRef.trim();
                    if (paymentRepository.findByGymIdAndUtr(gymId, trimmedUtr).isPresent()) {
                        throw new IllegalArgumentException("duplicate UTR: " + trimmedUtr);
                    }
                    p.setUtr(trimmedUtr);
                }
                p.setRecordedBy(Payment.RECORDED_BY_OWNER);
                p.setConfirmedAt(clock.now());
                p.setCreatedAt(clock.now());
                paymentRepository.save(p);
                success++;
            } catch (Exception e) {
                errors.add("Row " + (i + 1) + ": " + e.getMessage());
            }
        }
        return new ImportResult(dataRowCount, success, errors);
    }

    private static void validateCsvEnvelope(String csv) {
        if (csv == null || csv.isBlank()) {
            throw BusinessException.badRequest("CSV_EMPTY", "CSV payload must not be empty");
        }
        if (csv.length() > MAX_CSV_CHARS) {
            throw BusinessException.badRequest("CSV_TOO_LARGE", "CSV exceeds maximum allowed size of 512 KB");
        }
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (c == '\uFFFD' || c == '\u0000' || (c < 0x20 && c != '\n' && c != '\r' && c != '\t')) {
                throw BusinessException.badRequest("CSV_INVALID_ENCODING",
                        "CSV contains invalid UTF-8 or binary control characters");
            }
        }
    }

    private static void validateNoFormulaInjection(String[] row, int phoneColIdx) {
        for (int c = 0; c < row.length; c++) {
            String cell = row[c];
            if (cell == null || cell.isEmpty()) {
                continue;
            }
            char first = cell.charAt(0);
            if (c == phoneColIdx && first == '+' && PhoneUtils.isValid(cell)) {
                continue;
            }
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
                throw new IllegalArgumentException(
                        "CSV formula injection detected in column " + (c + 1) + " (cells may not start with =, +, -, @)");
            }
        }
    }

    private static Map<String, Integer> buildHeaderMap(String[] headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headerRow.length; i++) {
            String key = headerRow[i] == null ? "" : headerRow[i].trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!key.isEmpty()) {
                map.put(key, i);
            }
        }
        return map;
    }

    private static String getCol(String[] row, Map<String, Integer> headerMap, String headerName, int fallbackIndex) {
        Integer idx = headerMap.get(headerName);
        if (idx != null) {
            return col(row, idx);
        }
        return headerMap.isEmpty() ? col(row, fallbackIndex) : "";
    }

    private static String col(String[] row, int i) {
        return (i >= 0 && row.length > i && row[i] != null) ? row[i].trim() : "";
    }
}
