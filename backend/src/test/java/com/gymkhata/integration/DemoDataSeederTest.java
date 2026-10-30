package com.gymkhata.integration;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.auth.model.User;
import com.gymkhata.auth.repo.UserRepository;
import com.gymkhata.billing.BillingService;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.billing.model.Payment;
import com.gymkhata.billing.repo.InvoiceRepository;
import com.gymkhata.billing.repo.PaymentRepository;
import com.gymkhata.checkin.repo.CheckinRepository;
import com.gymkhata.common.audit.AuditService;
import com.gymkhata.common.config.DemoDataSeeder;
import com.gymkhata.common.config.MongoConfig;
import com.gymkhata.lead.repo.LeadRepository;
import com.gymkhata.member.repo.MemberRepository;
import com.gymkhata.membership.repo.MembershipRepository;
import com.gymkhata.messaging.repo.OutboxRepository;
import com.gymkhata.plan.repo.PlanRepository;
import com.gymkhata.pt.model.CommissionEntry;
import com.gymkhata.pt.repo.CommissionEntryRepository;
import com.gymkhata.pt.repo.PtEnrolmentRepository;
import com.gymkhata.pt.repo.TrainerRepository;
import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/gymkhata_demo}"
})
@ActiveProfiles("demo")
class DemoDataSeederTest {

    @Autowired private DemoDataSeeder demoDataSeeder;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private MongoConfig mongoConfig;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private GymRepository gymRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private BillingService billingService;
    @Autowired private CheckinRepository checkinRepository;
    @Autowired private LeadRepository leadRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private AuditService auditService;
    @Autowired private TrainerRepository trainerRepository;
    @Autowired private PtEnrolmentRepository ptEnrolmentRepository;
    @Autowired private CommissionEntryRepository commissionEntryRepository;

    @Test
    void refusesToRunUnderProdProfile() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        DemoDataSeeder prodSeeder = new DemoDataSeeder(
                prodEnv, mongoTemplate, mongoConfig, passwordEncoder,
                gymRepository, userRepository, planRepository, memberRepository,
                membershipRepository, invoiceRepository, paymentRepository, billingService,
                checkinRepository, leadRepository, outboxRepository, auditService, null, null
        );

        assertThatThrownBy(() -> prodSeeder.seedDemoData(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_SEED_REFUSED")
                .hasMessageContaining("prod");
    }

    @Test
    void refusesToRunEvenIfBothDemoAndProdProfilesAreActive() {
        MockEnvironment mixedEnv = new MockEnvironment();
        mixedEnv.setActiveProfiles("demo", "prod");
        DemoDataSeeder mixedSeeder = new DemoDataSeeder(
                mixedEnv, mongoTemplate, mongoConfig, passwordEncoder,
                gymRepository, userRepository, planRepository, memberRepository,
                membershipRepository, invoiceRepository, paymentRepository, billingService,
                checkinRepository, leadRepository, outboxRepository, auditService, null, null
        );

        assertThatThrownBy(() -> mixedSeeder.seedDemoData(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_SEED_REFUSED");
    }

    @Test
    void refusesToRunAgainstDatabaseWithoutDemoOrDevInName() {
        MockEnvironment demoEnv = new MockEnvironment();
        demoEnv.setActiveProfiles("demo");

        MongoTemplate prodDbTemplate = Mockito.mock(MongoTemplate.class);
        MongoDatabase prodDb = Mockito.mock(MongoDatabase.class);
        Mockito.when(prodDb.getName()).thenReturn("gymkhata_production");
        Mockito.when(prodDbTemplate.getDb()).thenReturn(prodDb);

        DemoDataSeeder badDbSeeder = new DemoDataSeeder(
                demoEnv, prodDbTemplate, mongoConfig, passwordEncoder,
                gymRepository, userRepository, planRepository, memberRepository,
                membershipRepository, invoiceRepository, paymentRepository, billingService,
                checkinRepository, leadRepository, outboxRepository, auditService, null, null
        );

        assertThatThrownBy(() -> badDbSeeder.seedDemoData(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_SEED_REFUSED")
                .hasMessageContaining("gymkhata_production");
    }

    @Test
    void seedsIdempotentlyAndSupportsResetWithAllRequiredScenarios() {
        // 1. Seed with reset = true
        demoDataSeeder.seedDemoData(true);

        List<Gym> gyms = gymRepository.findAll();
        assertThat(gyms).hasSize(5);
        assertThat(gyms).extracting(Gym::getCode)
                .containsExactlyInAnyOrder("BAJRANG-LKO", "IRONCORE-IND", "SURYA-JPR", "TITAN-NGP", "KAVERI-MYS");

        // Exactly 1 SUPER_ADMIN and 5 GYM_OWNERs (1 per gym)
        assertThat(userRepository.findAll().stream().filter(u -> User.ROLE_SUPER_ADMIN.equals(u.getRole())).count()).isEqualTo(1);
        assertThat(userRepository.findAll().stream().filter(u -> User.ROLE_GYM_OWNER.equals(u.getRole())).count()).isEqualTo(5);

        Gym gymA = gymRepository.findByCode("BAJRANG-LKO").orElseThrow();
        Gym gymB = gymRepository.findByCode("IRONCORE-IND").orElseThrow();
        Gym gymC = gymRepository.findByCode("SURYA-JPR").orElseThrow();

        // 40 members per active gym (between 30 and 60) including 2 DPDP-erased members per gym
        assertThat(memberRepository.findAll().stream().filter(m -> gymA.getId().equals(m.getGymId())).count()).isEqualTo(40);
        assertThat(memberRepository.findAll().stream().filter(m -> gymB.getId().equals(m.getGymId())).count()).isEqualTo(40);
        assertThat(memberRepository.findAll().stream().filter(m -> gymC.getId().equals(m.getGymId())).count()).isEqualTo(40);
        assertThat(memberRepository.findAll().stream()
                .filter(m -> gymA.getId().equals(m.getGymId()) && "ERASED".equals(m.getName()) && m.getPhone() == null).count()).isEqualTo(2);

        // 9 plans per active gym including ₹0 trial, ₹999, ₹1,180, ₹3,333
        assertThat(planRepository.findByGymIdAndActive(gymA.getId(), true)).hasSize(9);

        // Invoices across FY 25-26 (31 Mar) and FY 26-27 (1 Apr) + Credit Note series
        List<Invoice> gymAInvoices = invoiceRepository.findByGymIdOrderByCreatedAtDesc(gymA.getId());
        assertThat(gymAInvoices).extracting(Invoice::getInvoiceNumber)
                .contains("BPF/25-26/000001", "BPF/25-26/000002", "BPF/25-26/000003", "BPF/26-27/000001", "CN/26-27/000001");

        // Gym B has inter-state IGST invoices
        List<Invoice> gymBInvoices = invoiceRepository.findByGymIdOrderByCreatedAtDesc(gymB.getId());
        assertThat(gymBInvoices.stream().anyMatch(i -> i.getLines() != null
                && i.getLines().stream().anyMatch(l -> l.getIgstPaise() > 0))).isTrue();

        // Gym C has Bill of Supply invoices with 0 tax
        List<Invoice> gymCInvoices = invoiceRepository.findByGymIdOrderByCreatedAtDesc(gymC.getId());
        assertThat(gymCInvoices).allMatch(i -> Invoice.TYPE_BILL_OF_SUPPLY.equals(i.getType())
                || Invoice.TYPE_CREDIT_NOTE.equals(i.getType()));

        // Payments include CASH, CARD, UPI CONFIRMED, UPI PENDING_VERIFICATION, UPI FAILED
        List<Payment> gymAPayments = paymentRepository.findAll().stream()
                .filter(p -> gymA.getId().equals(p.getGymId())).toList();
        assertThat(gymAPayments).extracting(Payment::getStatus)
                .contains(Payment.STATUS_CONFIRMED, Payment.STATUS_PENDING, Payment.STATUS_FAILED);

        // 60 days of check-ins + 4 members inside now
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        assertThat(checkinRepository.findByGymIdAndDateOrderByCheckInAtDesc(gymA.getId(), today).stream()
                .filter(c -> c.getCheckOutAt() == null).count()).isEqualTo(4);

        // 4 trainers per active gym with distinct commission rules, 10 PT enrolments per active gym, and commission entries including REVERSAL
        assertThat(trainerRepository.findByGymIdOrderByNameAsc(gymA.getId())).hasSize(4);
        assertThat(ptEnrolmentRepository.findByGymIdOrderByCreatedAtDesc(gymA.getId())).hasSize(10);
        List<CommissionEntry> gymACommissions = commissionEntryRepository.findByGymIdOrderByCreatedAtDesc(gymA.getId());
        assertThat(gymACommissions).extracting(CommissionEntry::getType)
                .contains(CommissionEntry.TYPE_ACCRUAL, CommissionEntry.TYPE_REVERSAL, CommissionEntry.TYPE_ADJUSTMENT, CommissionEntry.TYPE_PAYOUT);

        long initialMemberCount = memberRepository.count();
        long initialInvoiceCount = invoiceRepository.count();

        // 2. Run again with forceReset = false -> must be 100% idempotent
        demoDataSeeder.seedDemoData(false);
        assertThat(memberRepository.count()).isEqualTo(initialMemberCount);
        assertThat(invoiceRepository.count()).isEqualTo(initialInvoiceCount);
    }
}
