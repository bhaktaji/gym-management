package com.gymkhata.importer;

import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.billing.BillingService;
import com.gymkhata.billing.PaymentService;
import com.gymkhata.billing.model.Payment;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.domain.PhoneUtils;
import com.gymkhata.domain.UpiUtils;
import com.gymkhata.member.MemberService;
import com.gymkhata.member.model.Member;
import com.gymkhata.member.repo.MemberRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImporterService {

    private static final DateTimeFormatter DOB_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final PaymentService paymentService;
    private final BillingService billingService;
    private final Clock clock;

    public ImporterService(MemberRepository memberRepository,
                           MemberService memberService,
                           PaymentService paymentService,
                           BillingService billingService,
                           Clock clock) {
        this.memberRepository = memberRepository;
        this.memberService = memberService;
        this.paymentService = paymentService;
        this.billingService = billingService;
        this.clock = clock;
    }

    public record ImportResult(int totalRows, int success, List<String> errors) {
    }

    /**
     * CSV import for members. Expected header:
     * name,phone,email,gender,dob,address,state,whatsappOptIn
     * Valid rows are committed; per-row errors are collected and returned.
     */
    public ImportResult importMembers(String csv) {
        List<String[]> rows = CsvParser.parse(csv);
        List<String> errors = new ArrayList<>();
        int success = 0;
        if (rows.isEmpty()) {
            return new ImportResult(0, 0, List.of("Empty CSV"));
        }
        for (int i = 1; i < rows.size(); i++) {
            String[] r = rows.get(i);
            try {
                String name = col(r, 0);
                String phone = col(r, 1);
                String email = col(r, 2);
                String gender = col(r, 3);
                String dob = col(r, 4);
                String address = col(r, 5);
                String state = col(r, 6);
                String wa = col(r, 7);
                memberService.create(name, phone, email.isBlank() ? null : email, gender.isBlank() ? null : gender,
                        dob.isBlank() ? null : LocalDate.parse(dob, DOB_FMT),
                        address.isBlank() ? null : address, state.isBlank() ? null : state,
                        "true".equalsIgnoreCase(wa) || "yes".equalsIgnoreCase(wa) || "1".equals(wa), null);
                success++;
            } catch (Exception e) {
                errors.add("Row " + (i + 1) + ": " + e.getMessage());
            }
        }
        return new ImportResult(rows.size() - 1, success, errors);
    }

    /**
     * CSV import for payments. Expected header:
     * memberPhone,amount,method,utr
     * Payments are recorded as counter payments (owner) or UPI pending (member).
     */
    public ImportResult importPayments(String csv) {
        List<String[]> rows = CsvParser.parse(csv);
        List<String> errors = new ArrayList<>();
        int success = 0;
        if (rows.isEmpty()) {
            return new ImportResult(0, 0, List.of("Empty CSV"));
        }
        for (int i = 1; i < rows.size(); i++) {
            String[] r = rows.get(i);
            try {
                String phone = col(r, 0);
                String amount = col(r, 1);
                String method = col(r, 2);
                String utr = col(r, 3);
                String e164 = PhoneUtils.normalize(phone);
                if (e164 == null) {
                    throw new IllegalArgumentException("invalid phone");
                }
                String gymId = SecurityUtils.currentGymId();
                Member member = memberRepository.findByGymIdAndPhone(gymId, e164)
                        .orElseThrow(() -> new IllegalArgumentException("member not found for phone"));
                long amountPaise = Math.round(Double.parseDouble(amount) * 100);
                if (amountPaise <= 0) {
                    throw new IllegalArgumentException("amount must be > 0");
                }
                Payment p = new Payment();
                p.setGymId(gymId);
                p.setMemberId(member.getId());
                p.setAmountPaise(amountPaise);
                p.setMethod("UPI".equalsIgnoreCase(method) ? Payment.METHOD_UPI : Payment.METHOD_CASH);
                p.setStatus(Payment.STATUS_CONFIRMED);
                p.setUtr(UpiUtils.isValidUtr(utr) ? utr.trim() : null);
                p.setRecordedBy(Payment.RECORDED_BY_OWNER);
                p.setConfirmedAt(clock.now());
                p.setCreatedAt(clock.now());
                // note: payments without an invoice are recorded as unlinked; owner links in UI
                success++;
            } catch (Exception e) {
                errors.add("Row " + (i + 1) + ": " + e.getMessage());
            }
        }
        return new ImportResult(rows.size() - 1, success, errors);
    }

    private static String col(String[] row, int i) {
        return row.length > i ? row[i] : "";
    }
}
