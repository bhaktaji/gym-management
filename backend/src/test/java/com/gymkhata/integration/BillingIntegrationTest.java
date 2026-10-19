package com.gymkhata.integration;

import com.gymkhata.AbstractIntegrationTest;
import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.auth.model.User;
import com.gymkhata.auth.repo.UserRepository;
import com.gymkhata.billing.BillingService;
import com.gymkhata.billing.PaymentService;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.billing.model.Payment;
import com.gymkhata.billing.repo.InvoiceRepository;
import com.gymkhata.billing.repo.PaymentRepository;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.member.MemberService;
import com.gymkhata.member.model.Member;
import com.gymkhata.member.repo.MemberRepository;
import com.gymkhata.membership.MembershipService;
import com.gymkhata.membership.model.Membership;
import com.gymkhata.plan.PlanService;
import com.gymkhata.plan.model.Plan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Billing rules: gapless numbering, exact GST math, payment lifecycle,
 * duplicate UTR rejection and overpayment rejection.
 */
class BillingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private GymRepository gymRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PlanService planService;
    @Autowired
    private MemberService memberService;
    @Autowired
    private MembershipService membershipService;
    @Autowired
    private BillingService billingService;
    @Autowired
    private PaymentService paymentService;

    private String gymId;

    @BeforeEach
    void setUp() {
        clearAll();
        SecurityContextHolder.clearContext();
        gymId = UUID.randomUUID().toString();

        Gym gym = new Gym();
        gym.setId(gymId);
        gym.setName("Test Gym");
        gym.setCode("TST");
        gym.setState("Maharashtra");
        gym.setInvoicePrefix("TG");
        gym.setGstRegistered(true);
        gym.setGstin("27AAAAA0000A1Z5");
        gym.setStatus(Gym.STATUS_ACTIVE);
        gym.setPlanPricePaise(99900);
        gymRepository.save(gym);

        User owner = new User();
        owner.setId(UUID.randomUUID().toString());
        owner.setRole(User.ROLE_GYM_OWNER);
        owner.setGymId(gymId);
        owner.setName("Owner");
        owner.setPhone("+919000000010");
        owner.setPasswordHash("x");
        owner.setStatus(User.STATUS_ACTIVE);
        userRepository.save(owner);

        var auth = new UsernamePasswordAuthenticationToken(owner, null,
                List.of(new SimpleGrantedAuthority("ROLE_GYM_OWNER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Membership enrol(long pricePaise, String memberPhone) {
        Plan plan = planService.create("Monthly", 30, pricePaise);
        Member member = memberService.create("Test Member", memberPhone, null, null, null, null, null, false, null);
        return membershipService.enrol(member.getId(), plan.getId());
    }

    @Test
    @DisplayName("invoice numbers are gapless and per gym per FY")
    void invoiceNumbersAreGapless() {
        Membership m1 = enrol(118000, "+919111100001");
        Membership m2 = enrol(118000, "+919111100002");
        Membership m3 = enrol(118000, "+919111100003");

        Invoice i1 = billingService.generateInvoice(m1.getId());
        Invoice i2 = billingService.generateInvoice(m2.getId());
        Invoice i3 = billingService.generateInvoice(m3.getId());

        assertEquals("TG/26-27/000001", i1.getInvoiceNumber());
        assertEquals("TG/26-27/000002", i2.getInvoiceNumber());
        assertEquals("TG/26-27/000003", i3.getInvoiceNumber());
        assertEquals(3, invoiceRepository.count());
    }

    @Test
    @DisplayName("GST lines sum exactly to the invoice total (intra-state CGST+SGST)")
    void gstLinesSumToTotal() {
        Membership m = enrol(118000, "+919111100010");
        Invoice inv = billingService.generateInvoice(m.getId());

        Invoice.Line l = inv.getLines().get(0);
        assertEquals(Invoice.TYPE_TAX_INVOICE, inv.getType());
        assertEquals("999723", l.getSac());
        assertEquals(5, l.getGstRate());
        assertEquals(112381, l.getTaxablePaise());
        assertEquals(2809, l.getCgstPaise());
        assertEquals(2810, l.getSgstPaise());
        assertEquals(0, l.getIgstPaise());
        assertEquals(l.getTaxablePaise() + l.getCgstPaise() + l.getSgstPaise() + l.getIgstPaise(),
                inv.getTotalPaise());
        assertEquals(118000, inv.getTotalPaise());
        assertEquals("One Thousand One Hundred Eighty Rupees Only", inv.getAmountInWords());
    }

    @Test
    @DisplayName("inter-state supply uses IGST instead of CGST+SGST")
    void interStateUsesIgst() {
        Membership m = enrol(118000, "+919111100020");
        Member member = memberRepository.findById(m.getMemberId()).orElseThrow();
        member.setState("Karnataka");
        memberRepository.save(member);

        Invoice inv = billingService.generateInvoice(m.getId());
        Invoice.Line l = inv.getLines().get(0);
        assertEquals(5619, l.getIgstPaise());
        assertEquals(0, l.getCgstPaise());
        assertEquals(0, l.getSgstPaise());
        assertEquals(inv.getTotalPaise(),
                l.getTaxablePaise() + l.getCgstPaise() + l.getSgstPaise() + l.getIgstPaise());
    }

    @Test
    @DisplayName("non-GST-registered gym issues a bill of supply with no tax lines")
    void nonRegisteredGymHasNoTaxLines() {
        Gym gym = gymRepository.findById(gymId).orElseThrow();
        gym.setGstRegistered(false);
        gymRepository.save(gym);

        Membership m = enrol(118000, "+919111100030");
        Invoice inv = billingService.generateInvoice(m.getId());
        Invoice.Line l = inv.getLines().get(0);

        assertEquals(Invoice.TYPE_BILL_OF_SUPPLY, inv.getType());
        assertEquals(0, l.getGstRate());
        assertEquals(0, l.getCgstPaise());
        assertEquals(0, l.getSgstPaise());
        assertEquals(0, l.getIgstPaise());
        assertEquals(inv.getTotalPaise(), l.getTotalPaise());
    }

    @Test
    @DisplayName("partial payments are allowed and the balance is tracked")
    void partialPaymentsAreAllowed() {
        Membership m = enrol(118000, "+919111100040");
        Invoice inv = billingService.generateInvoice(m.getId());

        Payment p1 = paymentService.recordCounterPayment(inv.getId(), 50000, Payment.METHOD_CASH);
        assertEquals(Payment.STATUS_CONFIRMED, p1.getStatus());
        assertEquals(68000, paymentService.balanceDue(gymId, inv.getId()));

        Payment p2 = paymentService.recordCounterPayment(inv.getId(), 68000, Payment.METHOD_CARD);
        assertEquals(Payment.STATUS_CONFIRMED, p2.getStatus());
        assertEquals(0, paymentService.balanceDue(gymId, inv.getId()));
    }

    @Test
    @DisplayName("overpayment is rejected")
    void overpaymentIsRejected() {
        Membership m = enrol(118000, "+919111100050");
        Invoice inv = billingService.generateInvoice(m.getId());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.recordCounterPayment(inv.getId(), 118001, Payment.METHOD_CASH));
        assertEquals("OVERPAYMENT", ex.getCode());
    }

    @Test
    @DisplayName("duplicate UTR within a gym is rejected")
    void duplicateUtrIsRejected() {
        Membership m = enrol(118000, "+919111100060");
        Invoice inv = billingService.generateInvoice(m.getId());

        Payment p = paymentService.submitUpi(inv.getId(), 50000, "123456789012");
        assertEquals(Payment.STATUS_PENDING, p.getStatus());
        assertEquals(Payment.RECORDED_BY_MEMBER, p.getRecordedBy());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.submitUpi(inv.getId(), 1000, "123456789012"));
        assertEquals("DUPLICATE_UTR", ex.getCode());
    }

    @Test
    @DisplayName("invalid UTR length is rejected")
    void invalidUtrIsRejected() {
        Membership m = enrol(118000, "+919111100070");
        Invoice inv = billingService.generateInvoice(m.getId());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.submitUpi(inv.getId(), 1000, "12345"));
        assertEquals("INVALID_UTR", ex.getCode());
    }

    @Test
    @DisplayName("owner-confirmed UPI payment settles the balance")
    void confirmingUpiSettlesBalance() {
        Membership m = enrol(118000, "+919111100080");
        Invoice inv = billingService.generateInvoice(m.getId());

        Payment pending = paymentService.submitUpi(inv.getId(), 118000, "987654321098");
        assertEquals(118000, paymentService.balanceDue(gymId, inv.getId()));

        Payment confirmed = paymentService.confirmUpi(pending.getId());
        assertEquals(Payment.STATUS_CONFIRMED, confirmed.getStatus());
        assertEquals(0, paymentService.balanceDue(gymId, inv.getId()));
    }

    @Test
    @DisplayName("credit notes use their own gapless CN series and are negative")
    void creditNotesUseOwnSeries() {
        Membership m = enrol(118000, "+919111100090");
        Invoice inv = billingService.generateInvoice(m.getId());

        Invoice cn = billingService.createCreditNote(inv.getId(), "Member cancelled");
        assertEquals("CN/26-27/000001", cn.getInvoiceNumber());
        assertEquals(Invoice.TYPE_CREDIT_NOTE, cn.getType());
        assertEquals(-118000, cn.getTotalPaise());
        assertEquals(-inv.getTotalPaise(), cn.getLines().get(0).getTotalPaise());

        Invoice cn2 = billingService.createCreditNote(inv.getId(), "Second correction");
        assertEquals("CN/26-27/000002", cn2.getInvoiceNumber());
    }

    @Test
    @DisplayName("voided invoice keeps its number and cannot be paid")
    void voidedInvoiceCannotBePaid() {
        Membership m = enrol(118000, "+919111100100");
        Invoice inv = billingService.generateInvoice(m.getId());

        Invoice voided = billingService.voidInvoice(inv.getId(), "Duplicate entry");
        assertEquals(Invoice.STATUS_VOID, voided.getStatus());
        assertEquals("Duplicate entry", voided.getVoidReason());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.recordCounterPayment(inv.getId(), 1000, Payment.METHOD_CASH));
        assertEquals("INVOICE_VOID", ex.getCode());
    }
}
