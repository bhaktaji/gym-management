package com.gymkhata.common.config;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.auth.model.User;
import com.gymkhata.auth.repo.PasswordResetTokenRepository;
import com.gymkhata.auth.repo.RefreshTokenRepository;
import com.gymkhata.auth.repo.UserRepository;
import com.gymkhata.billing.BillingService;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.billing.model.Payment;
import com.gymkhata.billing.repo.InvoiceRepository;
import com.gymkhata.billing.repo.PaymentRepository;
import com.gymkhata.checkin.model.Checkin;
import com.gymkhata.checkin.repo.CheckinRepository;
import com.gymkhata.common.audit.AuditService;
import com.gymkhata.domain.FinancialYear;
import com.gymkhata.domain.GstCalculator;
import com.gymkhata.domain.IndianNumberWords;
import com.gymkhata.domain.InvoiceNumbering;
import com.gymkhata.domain.PhoneUtils;
import com.gymkhata.lead.model.Lead;
import com.gymkhata.lead.repo.LeadRepository;
import com.gymkhata.member.model.Member;
import com.gymkhata.member.repo.MemberRepository;
import com.gymkhata.membership.model.Membership;
import com.gymkhata.membership.repo.MembershipRepository;
import com.gymkhata.messaging.model.OutboxMessage;
import com.gymkhata.messaging.repo.OutboxRepository;
import com.gymkhata.plan.model.Plan;
import com.gymkhata.plan.repo.PlanRepository;
import com.gymkhata.domain.CommissionRule;
import com.gymkhata.pt.CommissionService;
import com.gymkhata.pt.model.CommissionEntry;
import com.gymkhata.pt.model.PtEnrolment;
import com.gymkhata.pt.model.PtPackage;
import com.gymkhata.pt.model.Trainer;
import com.gymkhata.pt.repo.CommissionEntryRepository;
import com.gymkhata.pt.repo.PtEnrolmentRepository;
import com.gymkhata.pt.repo.PtPackageRepository;
import com.gymkhata.pt.repo.TrainerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Order(20)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final Environment environment;
    private final MongoTemplate mongoTemplate;
    private final MongoConfig mongoConfig;
    private final PasswordEncoder passwordEncoder;
    private final GymRepository gymRepository;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final BillingService billingService;
    private final CheckinRepository checkinRepository;
    private final LeadRepository leadRepository;
    private final OutboxRepository outboxRepository;
    private final AuditService auditService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired(required = false) private TrainerRepository trainerRepository;
    @Autowired(required = false) private PtPackageRepository ptPackageRepository;
    @Autowired(required = false) private PtEnrolmentRepository ptEnrolmentRepository;
    @Autowired(required = false) private CommissionEntryRepository commissionEntryRepository;
    @Autowired(required = false) private CommissionService commissionService;

    @Value("${app.demo.enabled:false}")
    private boolean demoEnabled;

    @Value("${app.demo.reset:false}")
    private boolean demoReset;

    @Value("${app.demo.super-admin-password:DemoAdmin@2026}")
    private String superAdminPassword;

    @Value("${app.demo.owner-password:DemoOwner@2026}")
    private String ownerPassword;

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    private static final String[] EN_NAMES = {
            "Aarav Sharma", "Priya Verma", "Rohan Tiwari", "Neha Gupta", "Siddharth Mishra",
            "Kavita Yadav", "Deepak Chauhan", "Pooja Patel", "Vikram Rathore", "Anjali Nair",
            "Harshvardhan Joshi", "Meenakshi Iyer", "Gaurav Pandey", "Swati Dubey", "Nikhil Saxena",
            "Ritu Agarwal", "Manish Srivastava", "Divya Chaturvedi", "Abhishek Tripathi", "Sneha Banerjee",
            "Karan Malhotra", "Shruti Kulkarni", "Prashant Thakur", "Nandini Deshpande", "Saurav Solanki"
    };

    private static final String[] HI_NAMES = {
            "रोहित शर्मा", "पूजा वर्मा", "अमित कुमार यादव", "नेहा तिवारी", "संदीप सिंह चौहान",
            "कविता मिश्रा", "विकास पाण्डेय", "अंजलि गुप्ता", "राहुल श्रीवास्तव", "सुनीता देवी",
            "दीपक मौर्य", "प्रियंका दुबे", "मनोज पटेल", "रेखा राठौर", "आकाश शुक्ला"
    };

    private static final String[] INTER_STATE_CUSTOMERS = {
            "Maharashtra", "Gujarat", "Rajasthan", "Uttar Pradesh", "Karnataka"
    };

    public DemoDataSeeder(Environment environment,
                          MongoTemplate mongoTemplate,
                          MongoConfig mongoConfig,
                          PasswordEncoder passwordEncoder,
                          GymRepository gymRepository,
                          UserRepository userRepository,
                          PlanRepository planRepository,
                          MemberRepository memberRepository,
                          MembershipRepository membershipRepository,
                          InvoiceRepository invoiceRepository,
                          PaymentRepository paymentRepository,
                          BillingService billingService,
                          CheckinRepository checkinRepository,
                          LeadRepository leadRepository,
                          OutboxRepository outboxRepository,
                          AuditService auditService,
                          RefreshTokenRepository refreshTokenRepository,
                          PasswordResetTokenRepository passwordResetTokenRepository) {
        this.environment = environment;
        this.mongoTemplate = mongoTemplate;
        this.mongoConfig = mongoConfig;
        this.passwordEncoder = passwordEncoder;
        this.gymRepository = gymRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.memberRepository = memberRepository;
        this.membershipRepository = membershipRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.billingService = billingService;
        this.checkinRepository = checkinRepository;
        this.leadRepository = leadRepository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean hasDemoProfile = activeProfiles.stream().anyMatch(p -> p.equalsIgnoreCase("demo"));
        if (!demoEnabled && !hasDemoProfile) {
            return;
        }
        boolean cliReset = args != null && (args.containsOption("demo.reset") || args.containsOption("app.demo.reset"));
        seedDemoData(demoReset || cliReset);
    }

    /**
     * Enforces Hard Rule 6 & Phase 2 safety invariant:
     * 1. Refuses to run if 'prod' or 'production' profile is active.
     * 2. Refuses to run unless 'demo' profile is active.
     * 3. Refuses to run against any MongoDB database whose name does not contain 'demo' or 'dev'.
     */
    public void verifyDemoSafety() {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        for (String profile : profiles) {
            String lower = profile.toLowerCase(Locale.ROOT);
            if (lower.equals("prod") || lower.equals("production") || lower.startsWith("prod-")) {
                throw new IllegalStateException("DEMO_SEED_REFUSED: DemoDataSeeder must never run when prod profile is active (active profiles: " + profiles + ").");
            }
        }
        boolean hasDemo = profiles.stream().anyMatch(p -> p.equalsIgnoreCase("demo"));
        if (!hasDemo) {
            throw new IllegalStateException("DEMO_SEED_REFUSED: DemoDataSeeder requires the 'demo' profile to be active (active profiles: " + profiles + ").");
        }
        String dbName = mongoTemplate.getDb().getName();
        String lowerDb = dbName == null ? "" : dbName.toLowerCase(Locale.ROOT);
        if (!lowerDb.contains("demo") && !lowerDb.contains("dev")) {
            throw new IllegalStateException("DEMO_SEED_REFUSED: Database name '" + dbName + "' does not contain 'demo' or 'dev'. Demo data and production data must never share a database.");
        }
    }

    public synchronized void seedDemoData(boolean forceReset) {
        verifyDemoSafety();
        String dbName = mongoTemplate.getDb().getName();

        if (forceReset) {
            log.info("Resetting demo database '{}' before seeding...", dbName);
            gymRepository.deleteAll();
            userRepository.deleteAll();
            planRepository.deleteAll();
            memberRepository.deleteAll();
            membershipRepository.deleteAll();
            invoiceRepository.deleteAll();
            paymentRepository.deleteAll();
            checkinRepository.deleteAll();
            leadRepository.deleteAll();
            outboxRepository.deleteAll();
            if (trainerRepository != null) trainerRepository.deleteAll();
            if (ptPackageRepository != null) ptPackageRepository.deleteAll();
            if (ptEnrolmentRepository != null) ptEnrolmentRepository.deleteAll();
            if (commissionEntryRepository != null) commissionEntryRepository.deleteAll();
            if (refreshTokenRepository != null) refreshTokenRepository.deleteAll();
            if (passwordResetTokenRepository != null) passwordResetTokenRepository.deleteAll();
            mongoTemplate.dropCollection("counters");
            mongoTemplate.dropCollection("audit_logs");
            mongoConfig.ensureIndexes();
        }

        User superAdmin = ensureSuperAdmin();

        if (!forceReset && gymRepository.findByCode("BAJRANG-LKO").isPresent()) {
            log.info("Demo dataset already present in database '{}'. Skipping duplicate seed (run with --app.demo.reset=true to reset).", dbName);
            return;
        }

        log.info("Seeding comprehensive GymKhata demo dataset into '{}'...", dbName);

        // (a) GST-registered, intra-state (Uttar Pradesh), UPI VPA set
        Gym gymA = createGym(
                "BAJRANG-LKO",
                "Bajrang Powerhouse & Fitness (बजरंग पावरहाउस)",
                "Rajesh Pratap Singh",
                "+919810010001",
                "bajrang.lko@demo.gymkhata.in",
                "42 Hazratganj Main Road",
                "Lucknow",
                "Uttar Pradesh",
                true,
                "09AABCB1234F1Z5",
                "BPF",
                "bajrangfitness@okaxis",
                "Bajrang Powerhouse Lucknow",
                Gym.STATUS_ACTIVE,
                199900L
        );
        User ownerA = createOwner(gymA, "+919810010001", "Rajesh Pratap Singh", "rajesh@demo.gymkhata.in");

        // (b) GST-registered (Madhya Pradesh) with inter-state customers (IGST)
        Gym gymB = createGym(
                "IRONCORE-IND",
                "IronCore Strength Club (आयरनकोर स्ट्रेंथ क्लब)",
                "Ananya Deshmukh",
                "+919810010002",
                "ironcore.indore@demo.gymkhata.in",
                "18 Vijay Nagar Square, AB Road",
                "Indore",
                "Madhya Pradesh",
                true,
                "23AAACI5678G1Z2",
                "ICS",
                "ironcoreindore@ybl",
                "IronCore Indore",
                Gym.STATUS_ACTIVE,
                199900L
        );
        User ownerB = createOwner(gymB, "+919810010002", "Ananya Deshmukh", "ananya@demo.gymkhata.in");

        // (c) Not GST-registered (Bill of Supply, Rajasthan)
        Gym gymC = createGym(
                "SURYA-JPR",
                "Surya Akhada & Gym (सूर्य अखाड़ा एवं जिम)",
                "Mahendra Singh Shekhawat",
                "+919810010003",
                "surya.jaipur@demo.gymkhata.in",
                "105 Vaishali Nagar",
                "Jaipur",
                "Rajasthan",
                false,
                null,
                "SAG",
                "suryaakhada@paytm",
                "Surya Akhada Jaipur",
                Gym.STATUS_ACTIVE,
                99900L
        );
        User ownerC = createOwner(gymC, "+919810010003", "Mahendra Singh Shekhawat", "mahendra@demo.gymkhata.in");

        // (d) SUSPENDED gym
        Gym gymD = createGym(
                "TITAN-NGP",
                "Titan Muscle Factory",
                "Saurabh Kulkarni",
                "+919810010004",
                "titan.nagpur@demo.gymkhata.in",
                "77 Dharampeth Extension",
                "Nagpur",
                "Maharashtra",
                true,
                "27AABCT9988K1Z9",
                "TMF",
                "titanmuscle@okicici",
                "Titan Muscle Nagpur",
                Gym.STATUS_SUSPENDED,
                99900L
        );
        createOwner(gymD, "+919810010004", "Saurabh Kulkarni", "saurabh@demo.gymkhata.in");

        // (e) PENDING self-signup gym
        Gym gymE = createGym(
                "KAVERI-MYS",
                "Kaveri Fitness Studio",
                "Karthik Gowda",
                "+919810010005",
                "kaveri.mysuru@demo.gymkhata.in",
                "14 Saraswathipuram",
                "Mysuru",
                "Karnataka",
                false,
                null,
                "KFS",
                null,
                null,
                Gym.STATUS_PENDING,
                0L
        );
        createOwner(gymE, "+919810010005", "Karthik Gowda", "karthik@demo.gymkhata.in");

        // Seed full operational dataset for the 3 active gyms + baseline members for suspended gym D
        seedGymDataset(gymA, ownerA, 1, false);
        seedGymDataset(gymB, ownerB, 2, true);
        seedGymDataset(gymC, ownerC, 3, false);
        seedSuspendedGymMembers(gymD, 4);

        if (auditService != null) {
            auditService.log(null, superAdmin.getId(), User.ROLE_SUPER_ADMIN, "DEMO_SEED_COMPLETED",
                    "gym", "ALL", "Seeded 5 gyms (3 ACTIVE, 1 SUSPENDED, 1 PENDING) into " + dbName);
        }

        log.info("Demo seeding complete in '{}': gyms={}, users={}, members={}, plans={}, memberships={}, invoices={}, payments={}, checkins={}, leads={}, outbox={}",
                dbName,
                gymRepository.count(),
                userRepository.count(),
                memberRepository.count(),
                planRepository.count(),
                membershipRepository.count(),
                invoiceRepository.count(),
                paymentRepository.count(),
                checkinRepository.count(),
                leadRepository.count(),
                outboxRepository.count());
    }

    private User ensureSuperAdmin() {
        String phone = PhoneUtils.normalize("+91 90000 00001");
        User admin = userRepository.findByPhone(phone).orElseGet(User::new);
        admin.setPhone(phone);
        admin.setName("Vikramaditya Rao");
        admin.setEmail("superadmin@demo.gymkhata.in");
        admin.setRole(User.ROLE_SUPER_ADMIN);
        admin.setGymId(null);
        admin.setStatus(User.STATUS_ACTIVE);
        admin.setPasswordHash(passwordEncoder.encode(superAdminPassword));
        admin.setUpdatedAt(Instant.now());
        if (admin.getCreatedAt() == null) {
            admin.setCreatedAt(Instant.now().minus(180, ChronoUnit.DAYS));
        }
        return userRepository.save(admin);
    }

    private Gym createGym(String code, String name, String ownerName, String phone, String email,
                          String address, String city, String state, boolean gstRegistered, String gstin,
                          String prefix, String upiVpa, String upiDisplayName,
                          String status, long feePaise) {
        Gym g = new Gym();
        g.setCode(code);
        g.setSlug(code.toLowerCase(Locale.ROOT));
        g.setName(name);
        g.setOwnerName(ownerName);
        g.setPhone(PhoneUtils.normalize(phone));
        g.setEmail(email);
        g.setAddress(address);
        g.setCity(city);
        g.setState(state);
        g.setGstRegistered(gstRegistered);
        g.setGstin(gstin);
        g.setInvoicePrefix(prefix);
        g.setStatus(status);
        g.setPlanPricePaise(feePaise);
        g.setSubscriptionValidUntil(LocalDate.now(IST).plusMonths(6));
        Gym.Settings settings = new Gym.Settings();
        settings.setUpiVpa(upiVpa);
        settings.setUpiName(upiDisplayName);
        settings.setQuietHoursEnabled(true);
        // Enable optional self-service attendance on Bajrang only; leave OFF on Surya Akhada & IronCore
        settings.setAttendanceEnabled("BAJRANG-LKO".equals(code));
        settings.setSelfCheckoutEnabled("BAJRANG-LKO".equals(code));
        settings.setRotatingTokenSecret("demo-qr-secret-" + code.toLowerCase(Locale.ROOT));
        settings.setTemplatesEn(Map.of(
                "RENEWAL_REMINDER", "Hi {{name}}, your membership at {{gym}} expires on {{expiry}}. Renew now: {{payLink}}"
        ));
        settings.setTemplatesHi(Map.of(
                "RENEWAL_REMINDER", "नमस्ते {{name}}, {{gym}} में आपकी सदस्यता {{expiry}} को समाप्त हो रही है। नवीनीकरण करें: {{payLink}}"
        ));
        g.setSettings(settings);
        g.setCreatedAt(Instant.now().minus(120, ChronoUnit.DAYS));
        g.setUpdatedAt(Instant.now());
        return gymRepository.save(g);
    }

    private User createOwner(Gym gym, String rawPhone, String name, String email) {
        String phone = PhoneUtils.normalize(rawPhone);
        User u = new User();
        u.setGymId(gym.getId());
        u.setPhone(phone);
        u.setName(name);
        u.setEmail(email);
        u.setRole(User.ROLE_GYM_OWNER);
        u.setStatus(User.STATUS_ACTIVE);
        u.setPasswordHash(passwordEncoder.encode(ownerPassword));
        u.setCreatedAt(Instant.now().minus(120, ChronoUnit.DAYS));
        u.setUpdatedAt(Instant.now());
        return userRepository.save(u);
    }

    private void seedSuspendedGymMembers(Gym gym, int gymIndex) {
        LocalDate today = LocalDate.now(IST);
        for (int i = 0; i < 32; i++) {
            Member m = new Member();
            m.setGymId(gym.getId());
            m.setName(i < 22 ? EN_NAMES[i % EN_NAMES.length] : HI_NAMES[i % HI_NAMES.length]);
            m.setPhone(formatAndNormalizePhone(gymIndex, i));
            m.setGender(i % 3 == 0 ? "FEMALE" : "MALE");
            m.setState(gym.getState());
            m.setStatus(Member.STATUS_ACTIVE);
            m.setWhatsappOptIn(i % 4 != 0);
            m.setWhatsappOptInAt(Instant.now().minus(60, ChronoUnit.DAYS));
            m.setJoinedAt(today.minusDays(60));
            m.setCreatedAt(Instant.now().minus(60, ChronoUnit.DAYS));
            m.setUpdatedAt(Instant.now());
            memberRepository.save(m);
        }
    }

    private void seedGymDataset(Gym gym, User owner, int gymIndex, boolean includeInterStateMembers) {
        LocalDate today = LocalDate.now(IST);

        // 1. Seed 9 Plans (monthly, quarterly, half-yearly, annual, joining fee, Rs 0 trial, Rs 999, Rs 1,180, Rs 3,333)
        List<Plan> plans = new ArrayList<>();
        plans.add(savePlan(gym.getId(), "Monthly Standard (मासिक)", 30, 150000L));
        plans.add(savePlan(gym.getId(), "Quarterly Power (त्रैमासिक)", 90, 400000L));
        plans.add(savePlan(gym.getId(), "Half-Yearly Transform (अर्धवार्षिक)", 180, 750000L));
        plans.add(savePlan(gym.getId(), "Annual Elite (वार्षिक)", 365, 1200000L));
        plans.add(savePlan(gym.getId(), "Starter + Joining Fee (₹1,200 + ₹500)", 30, 170000L));
        plans.add(savePlan(gym.getId(), "7-Day Free Trial (निःशुल्क ट्रायल)", 7, 0L));
        plans.add(savePlan(gym.getId(), "Express Monthly ₹999", 30, 99900L));
        plans.add(savePlan(gym.getId(), "Silver Monthly ₹1,180", 30, 118000L));
        plans.add(savePlan(gym.getId(), "Pro Quarterly ₹3,333", 90, 333300L));

        Plan monthly1500 = plans.get(0);
        Plan quarterly4000 = plans.get(1);
        Plan halfYearly7500 = plans.get(2);
        Plan annual12000 = plans.get(3);
        Plan joiningFeePlan = plans.get(4);
        Plan trialPlan = plans.get(5);
        Plan awkward999 = plans.get(6);
        Plan exact1180 = plans.get(7);
        Plan awkward3333 = plans.get(8);

        // 2. Seed 40 Members per active gym with all required states
        String defaultPinHash = passwordEncoder.encode("1234");
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Member m = new Member();
            m.setGymId(gym.getId());
            boolean useHindiName = (i % 3 == 2);
            String baseName = useHindiName
                    ? HI_NAMES[(i + gymIndex) % HI_NAMES.length]
                    : EN_NAMES[(i + gymIndex * 3) % EN_NAMES.length];

            if (i >= 38) {
                // DPDP-erased members (2 per gym to verify multiple erased members work without index collision)
                m.setName("ERASED");
                m.setPhone(null);
                m.setEmail(null);
                m.setAddress(null);
                m.setState(null);
                m.setNotes("DPDP_ERASED");
                m.setWhatsappOptIn(false);
                m.setStatus(Member.STATUS_INACTIVE);
                m.setJoinedAt(today.minusDays(90));
                m.setCreatedAt(Instant.now().minus(90, ChronoUnit.DAYS));
                m.setUpdatedAt(Instant.now().minus(5, ChronoUnit.DAYS));
            } else {
                m.setName(baseName);
                m.setPhone(formatAndNormalizePhone(gymIndex, i));
                m.setEmail(i % 3 == 0 ? null : ("member" + gymIndex + "_" + (i + 1) + "@demo.gymkhata.in"));
                m.setGender(i % 3 == 0 ? "F" : (i % 11 == 0 ? "O" : "M"));
                m.setDob(LocalDate.of(1988 + (i % 15), (i % 12) + 1, (i % 27) + 1));
                String memberState = gym.getState();
                if (includeInterStateMembers && i < 10) {
                    memberState = INTER_STATE_CUSTOMERS[i % INTER_STATE_CUSTOMERS.length];
                }
                m.setState(memberState);
                m.setAddress("Sector " + (i + 1) + ", " + memberState);
                m.setNotes(includeInterStateMembers && i < 10
                        ? "Inter-state corporate member (" + memberState + ")"
                        : (useHindiName ? "सुबह का बैच (Morning batch)" : "Evening strength training"));
                m.setWhatsappOptIn(i % 5 != 0);
                if (m.isWhatsappOptIn()) {
                    m.setWhatsappOptInAt(Instant.now().minus(90 - i, ChronoUnit.DAYS));
                }
                m.setPinHash(defaultPinHash);
                m.setJoinedAt(today.minusDays(90 - i));
                m.setStatus(i >= 26 && i < 33 ? Member.STATUS_EXPIRED : Member.STATUS_ACTIVE);
                m.setCreatedAt(Instant.now().minus(90 - i, ChronoUnit.DAYS));
                m.setUpdatedAt(Instant.now());
            }
            members.add(memberRepository.save(m));
        }

        // 3. Seed Memberships across states:
        //    - Slots 0..3: Renewed (prior CANCELLED/superseded membership + current ACTIVE membership)
        //    - Slots 4..17: Active (> 7 days remaining)
        //    - Slots 18..22: Expiring within 7 days (today + 1..5 days)
        //    - Slots 23..25: Expiring today (expiryDate == today)
        //    - Slots 26..32: Expired (expiryDate in past, status EXPIRED)
        //    - Slots 33..37: Never enrolled (no membership record)
        //    - Slots 38..39: DPDP-erased
        List<Membership> activeMemberships = new ArrayList<>();
        for (int i = 0; i < 33; i++) {
            Member m = members.get(i);
            if (i < 4) {
                Plan oldPlan = (i % 2 == 0) ? awkward999 : monthly1500;
                saveMembership(gym.getId(), m.getId(), oldPlan,
                        today.minusDays(45), today.minusDays(15), Membership.STATUS_CANCELLED);
                Plan newPlan = (i == 0) ? awkward3333 : ((i == 1) ? quarterly4000 : exact1180);
                activeMemberships.add(saveMembership(gym.getId(), m.getId(), newPlan,
                        today.minusDays(14), today.plusDays(newPlan.getDurationDays() - 14), Membership.STATUS_ACTIVE));
            } else if (i < 18) {
                Plan p = switch (i % 6) {
                    case 0 -> monthly1500;
                    case 1 -> quarterly4000;
                    case 2 -> halfYearly7500;
                    case 3 -> annual12000;
                    case 4 -> joiningFeePlan;
                    default -> trialPlan;
                };
                int daysElapsed = 3 + (i % 10);
                LocalDate start = today.minusDays(daysElapsed);
                LocalDate end = p == trialPlan ? today.plusDays(5) : start.plusDays(p.getDurationDays());
                activeMemberships.add(saveMembership(gym.getId(), m.getId(), p, start, end, Membership.STATUS_ACTIVE));
            } else if (i < 23) {
                int daysLeft = (i - 17); // 1..5 days from today
                Plan p = (i % 2 == 0) ? awkward999 : exact1180;
                activeMemberships.add(saveMembership(gym.getId(), m.getId(), p,
                        today.minusDays(p.getDurationDays() - daysLeft),
                        today.plusDays(daysLeft),
                        Membership.STATUS_ACTIVE));
            } else if (i < 26) {
                Plan p = monthly1500;
                activeMemberships.add(saveMembership(gym.getId(), m.getId(), p,
                        today.minusDays(29), today, Membership.STATUS_ACTIVE));
            } else {
                int expiredDaysAgo = (i - 25) * 5;
                Plan p = (i % 2 == 0) ? monthly1500 : awkward999;
                activeMemberships.add(saveMembership(gym.getId(), m.getId(), p,
                        today.minusDays(30 + expiredDaysAgo),
                        today.minusDays(expiredDaysAgo),
                        Membership.STATUS_EXPIRED));
            }
        }

        // 4. Seed Invoices & Payments spanning two Financial Years across 31 Mar / 1 Apr boundary
        LocalDate fy1Date1 = LocalDate.of(2026, 3, 29);
        LocalDate fy1Date2 = LocalDate.of(2026, 3, 30);
        LocalDate fy1Boundary = LocalDate.of(2026, 3, 31);
        LocalDate fy2Boundary = LocalDate.of(2026, 4, 1);

        // FY 25-26 invoices (ending 31 Mar 2026)
        Invoice fy1Inv1 = createDemoInvoice(gym, owner, members.get(0), activeMemberships.get(0), awkward999, fy1Date1,
                includeInterStateMembers ? "Maharashtra" : gym.getState(),
                Invoice.STATUS_ISSUED, "demo-fy2526-paid-" + gymIndex);
        createConfirmedPayment(gym.getId(), fy1Inv1, Payment.METHOD_CASH, fy1Inv1.getTotalPaise(), fy1Date1);

        Invoice fy1Inv2 = createDemoInvoice(gym, owner, members.get(1), activeMemberships.get(1), exact1180, fy1Date2,
                includeInterStateMembers ? "Gujarat" : gym.getState(),
                Invoice.STATUS_ISSUED, "demo-fy2526-card-" + gymIndex);
        createConfirmedPayment(gym.getId(), fy1Inv2, Payment.METHOD_CARD, fy1Inv2.getTotalPaise(), fy1Date2);

        Invoice fy1Inv3 = createDemoInvoice(gym, owner, members.get(2), activeMemberships.get(2), awkward3333, fy1Boundary,
                includeInterStateMembers ? "Rajasthan" : gym.getState(),
                Invoice.STATUS_ISSUED, "demo-fy2526-mar31-" + gymIndex);
        createUpiPayment(gym.getId(), fy1Inv3, fy1Inv3.getTotalPaise(),
                String.format("6090%d1000001", gymIndex), Payment.STATUS_CONFIRMED, Payment.RECORDED_BY_MEMBER);

        // FY 26-27 boundary invoice on 1 Apr 2026 (resets sequence to 000001!)
        Invoice fy2Inv1 = createDemoInvoice(gym, owner, members.get(3), activeMemberships.get(3), quarterly4000, fy2Boundary,
                includeInterStateMembers ? "Uttar Pradesh" : gym.getState(),
                Invoice.STATUS_ISSUED, "demo-fy2627-apr01-" + gymIndex);
        createUpiPayment(gym.getId(), fy2Inv1, fy2Inv1.getTotalPaise(),
                String.format("6090%d1000002", gymIndex), Payment.STATUS_CONFIRMED, Payment.RECORDED_BY_MEMBER);

        // Paid invoice with joining fee (2 line items: ₹1,200 plan + ₹500 joining fee)
        Invoice fy2JoiningInv = createDemoInvoiceWithJoiningFee(gym, owner, members.get(4), activeMemberships.get(4),
                120000L, 50000L, today.minusDays(18), gym.getState(), "demo-joining-paid-" + gymIndex);
        createConfirmedPayment(gym.getId(), fy2JoiningInv, Payment.METHOD_CASH, fy2JoiningInv.getTotalPaise(), today.minusDays(18));

        // Zero-rupee trial invoice (0 paise)
        createDemoInvoice(gym, owner, members.get(5), activeMemberships.get(5), trialPlan, today.minusDays(5),
                gym.getState(), Invoice.STATUS_ISSUED, "demo-trial-zero-" + gymIndex);

        // PARTIAL invoice: ₹1,180 invoice with ₹500 cash paid -> ₹680 balance due
        Invoice partialInv = createDemoInvoice(gym, owner, members.get(6), activeMemberships.get(6), exact1180, today.minusDays(10),
                gym.getState(), Invoice.STATUS_ISSUED,
                "demo-partial-token-" + gym.getCode().toLowerCase(Locale.ROOT));
        createConfirmedPayment(gym.getId(), partialInv, Payment.METHOD_CASH, 50000L, today.minusDays(10));

        // UNPAID (ISSUED) invoice with a PENDING UPI UTR awaiting owner confirmation/rejection
        Invoice pendingUtrInv = createDemoInvoice(gym, owner, members.get(7), activeMemberships.get(7), awkward999, today.minusDays(4),
                gym.getState(), Invoice.STATUS_ISSUED,
                "demo-pending-utr-" + gym.getCode().toLowerCase(Locale.ROOT));
        createUpiPayment(gym.getId(), pendingUtrInv, pendingUtrInv.getTotalPaise(),
                String.format("6090%d2000001", gymIndex), Payment.STATUS_PENDING, Payment.RECORDED_BY_MEMBER);

        // UNPAID (ISSUED) invoice with a REJECTED (FAILED) UPI UTR + ready for fresh payment on public pay page
        Invoice unpaidInv = createDemoInvoice(gym, owner, members.get(8), activeMemberships.get(8), monthly1500, today.minusDays(3),
                includeInterStateMembers ? "Maharashtra" : gym.getState(),
                Invoice.STATUS_ISSUED,
                "demo-unpaid-token-" + gym.getCode().toLowerCase(Locale.ROOT));
        createUpiPayment(gym.getId(), unpaidInv, unpaidInv.getTotalPaise(),
                String.format("6090%d3000001", gymIndex), Payment.STATUS_FAILED, Payment.RECORDED_BY_MEMBER);

        // VOID invoice
        Invoice voidInv = createDemoInvoice(gym, owner, members.get(10), activeMemberships.get(10), halfYearly7500, today.minusDays(12),
                gym.getState(), Invoice.STATUS_VOID,
                "demo-void-token-" + gym.getCode().toLowerCase(Locale.ROOT));
        voidInv.setVoidReason("Billed wrong plan tier; reissued as Quarterly Power");
        invoiceRepository.save(voidInv);

        // Invoice that gets a CREDIT NOTE (CN series)
        Invoice originalForCn = createDemoInvoice(gym, owner, members.get(11), activeMemberships.get(11), annual12000, today.minusDays(20),
                gym.getState(), Invoice.STATUS_ISSUED,
                "demo-cn-orig-" + gym.getCode().toLowerCase(Locale.ROOT));
        createDemoCreditNote(gym, owner, originalForCn, today.minusDays(15), "Medical relocation credit note");

        // Record duplicate-UTR attempt in audit log
        if (auditService != null) {
            auditService.log(gym.getId(), owner.getId(), User.ROLE_GYM_OWNER, "DUPLICATE_UTR_REJECTED",
                    "invoice", unpaidInv.getId(),
                    String.format("Rejected duplicate UTR submission 6090%d1000001 on invoice %s", gymIndex, unpaidInv.getInvoiceNumber()));
        }

        // 5. Seed 60 Days of Check-ins + 4 members "Inside Now"
        for (int dayOffset = 59; dayOffset >= 1; dayOffset--) {
            LocalDate d = today.minusDays(dayOffset);
            int dailyCount = 4 + ((dayOffset + gymIndex) % 6);
            for (int c = 0; c < dailyCount; c++) {
                Member m = members.get((dayOffset + c) % 26);
                Checkin chk = new Checkin();
                chk.setGymId(gym.getId());
                chk.setMemberId(m.getId());
                chk.setMemberName(m.getName());
                chk.setDate(d);
                chk.setMethod(c % 3 == 0 ? Checkin.METHOD_MANUAL : Checkin.METHOD_QR_SELF);
                ZonedDateTime inTime = d.atTime(6 + (c % 4), (c * 13) % 60).atZone(IST);
                chk.setCheckInAt(inTime.toInstant());
                chk.setCheckOutAt(inTime.plusMinutes(55 + (c * 10)).toInstant());
                checkinRepository.save(chk);
            }
        }
        // 4 members "inside now" (checkInAt today, checkOutAt == null)
        for (int c = 0; c < 4; c++) {
            Member m = members.get(c);
            Checkin inside = new Checkin();
            inside.setGymId(gym.getId());
            inside.setMemberId(m.getId());
            inside.setMemberName(m.getName());
            inside.setDate(today);
            inside.setMethod(Checkin.METHOD_QR_SELF);
            inside.setCheckInAt(Instant.now().minus(15 + (c * 12L), ChronoUnit.MINUTES));
            inside.setCheckOutAt(null);
            checkinRepository.save(inside);
        }

        // 6. Seed Leads at every stage (NEW, CONTACTED, CONVERTED, LOST)
        String[] leadStatuses = {
                Lead.STATUS_NEW, Lead.STATUS_NEW, Lead.STATUS_CONTACTED, Lead.STATUS_CONTACTED,
                Lead.STATUS_CONTACTED, Lead.STATUS_CONVERTED, Lead.STATUS_CONVERTED,
                Lead.STATUS_LOST, Lead.STATUS_LOST, Lead.STATUS_NEW
        };
        String[] leadSources = {"WALK_IN", "REFERRAL", "INSTAGRAM", "WHATSAPP", "GOOGLE"};
        String[] leadNames = {
                "Yashwant Chauhan", "रितिका पाण्डेय", "Arjun Malhotra", "सौरभ वर्मा",
                "Tanvi Joshi", members.get(12).getName(), members.get(13).getName(),
                "Kunal Kapoor", "नीरज सक्सेना", "मोहित अग्रवाल"
        };
        for (int l = 0; l < leadStatuses.length; l++) {
            Lead lead = new Lead();
            lead.setGymId(gym.getId());
            lead.setName(leadNames[l]);
            lead.setPhone(PhoneUtils.normalize("+91 9600" + gymIndex + String.format("%05d", l + 100)));
            lead.setSource(leadSources[l % leadSources.length]);
            lead.setStatus(leadStatuses[l]);
            lead.setNotes("Interested in " + plans.get(l % 4).getName() + " (" + leadStatuses[l] + ")");
            if (Lead.STATUS_CONVERTED.equals(leadStatuses[l])) {
                lead.setConvertedToMemberId(members.get(l == 5 ? 12 : 13).getId());
            }
            lead.setCreatedAt(Instant.now().minus(15 - l, ChronoUnit.DAYS));
            lead.setUpdatedAt(Instant.now());
            leadRepository.save(lead);
        }

        // 7. Seed WhatsApp Outbox items in every state (PENDING, QUIET_HOURS, SENT, FAILED)
        String[] outboxStates = {
                OutboxMessage.STATUS_PENDING,
                OutboxMessage.STATUS_PENDING,
                OutboxMessage.STATUS_QUIET_HOURS,
                OutboxMessage.STATUS_SENT,
                OutboxMessage.STATUS_SENT,
                OutboxMessage.STATUS_FAILED
        };
        for (int o = 0; o < outboxStates.length; o++) {
            Member target = members.get(18 + o);
            OutboxMessage msg = new OutboxMessage();
            msg.setGymId(gym.getId());
            msg.setMemberId(target.getId());
            msg.setMemberName(target.getName());
            msg.setPhone(target.getPhone());
            msg.setType(OutboxMessage.TYPE_RENEWAL_REMINDER);
            msg.setChannel(OutboxMessage.CHANNEL_WHATSAPP);
            msg.setTemplateKey("RENEWAL_REMINDER");
            msg.setLanguage(o % 2 == 0 ? "en" : "hi");
            String body = o % 2 == 0
                    ? ("Hi " + target.getName() + ", your membership at " + gym.getName() + " expires soon. Renew online: " + publicBaseUrl + "/pay/" + unpaidInv.getPayToken())
                    : ("नमस्ते " + target.getName() + ", " + gym.getName() + " की आपकी सदस्यता जल्द समाप्त हो रही है। नवीनीकरण लिंक: " + publicBaseUrl + "/pay/" + unpaidInv.getPayToken());
            msg.setVariables(Map.of(
                    "name", target.getName(),
                    "gym", gym.getName(),
                    "expiry", today.plusDays(2).toString(),
                    "payLink", publicBaseUrl + "/pay/" + unpaidInv.getPayToken()
            ));
            if (target.getPhone() != null) {
                msg.setWaLink("https://wa.me/" + target.getPhone().replace("+", "") + "?text=" + URLEncoder.encode(body, StandardCharsets.UTF_8));
            }
            msg.setStatus(outboxStates[o]);
            msg.setDate(today);
            msg.setCreatedAt(Instant.now().minus(o + 1, ChronoUnit.HOURS));
            if (OutboxMessage.STATUS_SENT.equals(outboxStates[o])) {
                msg.setSentAt(Instant.now().minus(o, ChronoUnit.HOURS));
            }
            outboxRepository.save(msg);
        }

        // 8. Seed Personal Training (4 trainers with different rule types, 10 PT enrolments in mixed states, and commission ledger with credit-note reversal)
        if (trainerRepository != null && ptPackageRepository != null && ptEnrolmentRepository != null && commissionService != null) {
            seedPtAndCommissions(gym, owner, members, gymIndex, today);
        }
    }

    private void seedPtAndCommissions(Gym gym, User owner, List<Member> members, int gymIndex, LocalDate today) {
        // 4 Trainers with distinct commission rules:
        // Trainer 1: FLAT ₹1,500 per package
        Trainer tFlatPkg = saveTrainer(gym.getId(), "Coach Vikramaditya Singh", "+919700" + gymIndex + "00001",
                CommissionRule.flatPerPackage(150000L));
        // Trainer 2: FLAT ₹250 per session
        Trainer tFlatSess = saveTrainer(gym.getId(), "Coach Neha Rathore (नेहा राठौर)", "+919700" + gymIndex + "00002",
                CommissionRule.flatPerSession(25000L));
        // Trainer 3: PERCENT 20% (2000 bps) of taxable value
        Trainer tPercent = saveTrainer(gym.getId(), "Coach Arjun Deshmukh", "+919700" + gymIndex + "00003",
                CommissionRule.percentOfTaxable(2000));
        // Trainer 4: SLAB (15% up to ₹50,000 monthly taxable, 25% above ₹50,000)
        Trainer tSlab = saveTrainer(gym.getId(), "Coach Surya Pratap (सूर्य प्रताप)", "+919700" + gymIndex + "00004",
                CommissionRule.slabRule(List.of(
                        new CommissionRule.Slab(5000000L, 1500),
                        new CommissionRule.Slab(0L, 2500)
                )));

        // 4 PT Packages
        PtPackage pkg12Sess = savePtPackage(gym.getId(), "12-Session Strength Pack", PtPackage.TYPE_SESSION_BASED, 12, 45, 590000L, null);
        PtPackage pkg24Sess = savePtPackage(gym.getId(), "24-Session Pro Transformation", PtPackage.TYPE_SESSION_BASED, 24, 90, 1180000L, null);
        PtPackage pkgMonthly = savePtPackage(gym.getId(), "Monthly Unlimited Personal Training", PtPackage.TYPE_TIME_BASED, null, 30, 472000L, null);
        PtPackage pkgOverride = savePtPackage(gym.getId(), "Contest Prep Elite (Package Override 30%)", PtPackage.TYPE_SESSION_BASED, 16, 60, 1416000L,
                CommissionRule.percentOfTaxable(3000));

        Trainer[] trainers = {tFlatPkg, tFlatSess, tPercent, tSlab};
        PtPackage[] pkgs = {pkg12Sess, pkg24Sess, pkgMonthly, pkgOverride};

        // Create 10 PT Enrolments in mixed states
        for (int idx = 0; idx < 10; idx++) {
            Member m = members.get(idx);
            Trainer tr = trainers[idx % 4];
            PtPackage pkg = pkgs[idx % 4];

            PtEnrolment enr = new PtEnrolment();
            enr.setGymId(gym.getId());
            enr.setMemberId(m.getId());
            enr.setMemberName(m.getName());
            enr.setTrainerId(tr.getId());
            enr.setTrainerName(tr.getName());
            enr.setPtPackageId(pkg.getId());
            enr.setPtPackageName(pkg.getName());
            enr.setPackageType(pkg.getPackageType());
            enr.setTotalSessions(pkg.getTotalSessions());
            enr.setPricePaise(pkg.getPricePaise());
            enr.setSacCode("999723");
            enr.setStartDate(today.minusDays(20 - idx));

            int totalSess = pkg.getTotalSessions() == null ? 0 : pkg.getTotalSessions();
            if (idx == 7) {
                // Expiring soon (in 3 days)
                enr.setExpiryDate(today.plusDays(3));
                enr.setDeliveredSessions(totalSess > 0 ? totalSess - 1 : 0);
                enr.setRemainingSessions(totalSess > 0 ? 1 : null);
                enr.setStatus(PtEnrolment.STATUS_ACTIVE);
            } else if (idx == 8) {
                // Completed all sessions
                enr.setExpiryDate(today.plusDays(15));
                enr.setDeliveredSessions(totalSess > 0 ? totalSess : 12);
                enr.setRemainingSessions(totalSess > 0 ? 0 : null);
                enr.setStatus(PtEnrolment.STATUS_COMPLETED);
            } else if (idx == 9) {
                // Cancelled / refunded via Credit Note
                enr.setExpiryDate(today.plusDays(30));
                enr.setDeliveredSessions(1);
                enr.setRemainingSessions(totalSess > 0 ? totalSess - 1 : null);
                enr.setStatus(PtEnrolment.STATUS_CANCELLED);
            } else {
                enr.setExpiryDate(today.plusDays(pkg.getValidityDays() - 10));
                int delivered = totalSess > 0 ? Math.min(idx + 2, totalSess - 2) : 0;
                enr.setDeliveredSessions(delivered);
                enr.setRemainingSessions(totalSess > 0 ? totalSess - delivered : null);
                enr.setStatus(PtEnrolment.STATUS_ACTIVE);
            }

            enr = ptEnrolmentRepository.save(enr);
            Invoice ptInv = billingService.generatePtInvoice(enr, m, gym);
            commissionService.snapshotRuleForEnrolment(enr, pkg, tr, gym, ptInv.totalTaxablePaise());
            enr.setInvoiceId(ptInv.getId());
            enr = ptEnrolmentRepository.save(enr);

            if (idx == 4) {
                // 60/40 partial payment (60% paid so far)
                long sixtyPct = (ptInv.getTotalPaise() * 60L) / 100L;
                Payment p60 = createConfirmedPayment(gym.getId(), ptInv, Payment.METHOD_UPI, sixtyPct, today.minusDays(5));
                commissionService.onPaymentConfirmed(ptInv, p60, 0L);
            } else if (idx == 9) {
                // Paid in full then reversed by a Credit Note
                Payment pFull = createConfirmedPayment(gym.getId(), ptInv, Payment.METHOD_CARD, ptInv.getTotalPaise(), today.minusDays(8));
                commissionService.onPaymentConfirmed(ptInv, pFull, 0L);
                Invoice cn = createDemoCreditNote(gym, owner, ptInv, today.minusDays(2), "PT package cancelled due to knee injury");
                commissionService.reverseOnCreditNote(ptInv, cn);
            } else {
                // Paid in full
                Payment pFull = createConfirmedPayment(gym.getId(), ptInv, Payment.METHOD_CASH, ptInv.getTotalPaise(), today.minusDays(10 - (idx % 5)));
                commissionService.onPaymentConfirmed(ptInv, pFull, 0L);
            }
        }

        // Also record a manual bonus adjustment and a partial payout for Trainer 3 so monthly statements show all ledger types
        String currentMonth = YearMonth.from(today).toString();
        CommissionEntry adj = new CommissionEntry();
        adj.setGymId(gym.getId());
        adj.setTrainerId(tPercent.getId());
        adj.setTrainerName(tPercent.getName());
        adj.setType(CommissionEntry.TYPE_ADJUSTMENT);
        adj.setTriggerType("MANUAL");
        adj.setAmountPaise(50000L);
        adj.setReason("Contest prep client transformation bonus");
        adj.setMonth(currentMonth);
        adj.setCreatedAt(Instant.now());
        commissionEntryRepository.save(adj);

        CommissionEntry payout = new CommissionEntry();
        payout.setGymId(gym.getId());
        payout.setTrainerId(tPercent.getId());
        payout.setTrainerName(tPercent.getName());
        payout.setType(CommissionEntry.TYPE_PAYOUT);
        payout.setTriggerType("PAYOUT");
        payout.setAmountPaise(100000L);
        payout.setPayoutMode("UPI");
        payout.setPayoutDate(today.minusDays(1));
        payout.setReason("Mid-month advance payout");
        payout.setMonth(currentMonth);
        payout.setCreatedAt(Instant.now());
        commissionEntryRepository.save(payout);
    }

    private Trainer saveTrainer(String gymId, String name, String phone, CommissionRule rule) {
        Trainer t = new Trainer();
        t.setGymId(gymId);
        t.setName(name);
        t.setPhone(PhoneUtils.normalize(phone));
        t.setActive(true);
        t.setDefaultCommissionRule(rule);
        t.setCreatedAt(Instant.now().minus(60, ChronoUnit.DAYS));
        t.setUpdatedAt(Instant.now());
        return trainerRepository.save(t);
    }

    private PtPackage savePtPackage(String gymId, String name, String packageType, Integer sessions, int validityDays, long pricePaise, CommissionRule overrideRule) {
        PtPackage pkg = new PtPackage();
        pkg.setGymId(gymId);
        pkg.setName(name);
        pkg.setPackageType(packageType);
        pkg.setTotalSessions(sessions);
        pkg.setValidityDays(validityDays);
        pkg.setPricePaise(pricePaise);
        pkg.setActive(true);
        pkg.setCommissionRuleOverride(overrideRule);
        pkg.setCreatedAt(Instant.now().minus(60, ChronoUnit.DAYS));
        pkg.setUpdatedAt(Instant.now());
        return ptPackageRepository.save(pkg);
    }

    private Plan savePlan(String gymId, String name, int durationDays, long pricePaise) {
        Plan p = new Plan();
        p.setGymId(gymId);
        p.setName(name);
        p.setDurationDays(durationDays);
        p.setPricePaise(pricePaise);
        p.setActive(true);
        p.setCreatedAt(Instant.now().minus(90, ChronoUnit.DAYS));
        p.setUpdatedAt(Instant.now());
        return planRepository.save(p);
    }

    private Membership saveMembership(String gymId, String memberId, Plan plan, LocalDate start, LocalDate expiry, String status) {
        Membership ms = new Membership();
        ms.setGymId(gymId);
        ms.setMemberId(memberId);
        ms.setPlanId(plan.getId());
        ms.setPlanName(plan.getName());
        ms.setPricePaise(plan.getPricePaise());
        ms.setStartDate(start);
        ms.setExpiryDate(expiry);
        ms.setStatus(status);
        ms.setCreatedAt(start.atStartOfDay(IST).toInstant());
        return membershipRepository.save(ms);
    }

    private Invoice createDemoInvoice(Gym gym, User owner, Member member, Membership membership,
                                      Plan plan, LocalDate issueDate, String placeOfSupplyState,
                                      String status, String payToken) {
        String fy = FinancialYear.fyOf(issueDate);
        long seq = billingService.nextNumber(gym.getId(), fy, BillingService.SERIES_INV);
        String number = InvoiceNumbering.format(gym.getInvoicePrefix(), fy, seq);

        Invoice inv = new Invoice();
        inv.setGymId(gym.getId());
        inv.setInvoiceNumber(number);
        inv.setFinancialYear(fy);
        inv.setMemberId(member.getId());
        inv.setMembershipId(membership.getId());
        inv.setSupplierState(gym.getState());
        inv.setPlaceOfSupplyState(placeOfSupplyState);
        inv.setGstRegistered(gym.isGstRegistered());
        inv.setGstin(gym.isGstRegistered() ? gym.getGstin() : null);
        inv.setStatus(status);
        inv.setPayToken(payToken);
        inv.setCreatedAt(issueDate.atTime(10, 15).atZone(IST).toInstant());
        inv.setCreatedBy(owner.getId());

        Invoice.Line line = buildLine("Membership - " + plan.getName(), plan.getPricePaise(), gym.isGstRegistered(),
                gym.getState(), placeOfSupplyState);
        inv.setLines(List.of(line));
        inv.setTotalPaise(plan.getPricePaise());
        inv.setAmountInWords(IndianNumberWords.amountInWords(plan.getPricePaise()));
        inv.setType(gym.isGstRegistered() ? Invoice.TYPE_TAX_INVOICE : Invoice.TYPE_BILL_OF_SUPPLY);
        return invoiceRepository.save(inv);
    }

    private Invoice createDemoInvoiceWithJoiningFee(Gym gym, User owner, Member member, Membership membership,
                                                    long planPaise, long joiningFeePaise, LocalDate issueDate,
                                                    String placeOfSupplyState, String payToken) {
        String fy = FinancialYear.fyOf(issueDate);
        long seq = billingService.nextNumber(gym.getId(), fy, BillingService.SERIES_INV);
        String number = InvoiceNumbering.format(gym.getInvoicePrefix(), fy, seq);

        Invoice inv = new Invoice();
        inv.setGymId(gym.getId());
        inv.setInvoiceNumber(number);
        inv.setFinancialYear(fy);
        inv.setMemberId(member.getId());
        inv.setMembershipId(membership.getId());
        inv.setSupplierState(gym.getState());
        inv.setPlaceOfSupplyState(placeOfSupplyState);
        inv.setGstRegistered(gym.isGstRegistered());
        inv.setGstin(gym.isGstRegistered() ? gym.getGstin() : null);
        inv.setStatus(Invoice.STATUS_ISSUED);
        inv.setPayToken(payToken);
        inv.setCreatedAt(issueDate.atTime(11, 0).atZone(IST).toInstant());
        inv.setCreatedBy(owner.getId());

        Invoice.Line l1 = buildLine("Membership - Starter Monthly", planPaise, gym.isGstRegistered(), gym.getState(), placeOfSupplyState);
        Invoice.Line l2 = buildLine("One-Time Joining Fee", joiningFeePaise, gym.isGstRegistered(), gym.getState(), placeOfSupplyState);
        long total = planPaise + joiningFeePaise;
        inv.setLines(List.of(l1, l2));
        inv.setTotalPaise(total);
        inv.setAmountInWords(IndianNumberWords.amountInWords(total));
        inv.setType(gym.isGstRegistered() ? Invoice.TYPE_TAX_INVOICE : Invoice.TYPE_BILL_OF_SUPPLY);
        return invoiceRepository.save(inv);
    }

    private Invoice.Line buildLine(String description, long inclusivePaise, boolean gstRegistered,
                                   String supplierState, String placeOfSupplyState) {
        Invoice.Line line = new Invoice.Line();
        line.setDescription(description);
        line.setQuantity(1);
        line.setRatePaise(inclusivePaise);
        line.setTotalPaise(inclusivePaise);
        if (gstRegistered) {
            line.setSac("999723");
            line.setGstRate(18);
            boolean interState = placeOfSupplyState != null && !placeOfSupplyState.equalsIgnoreCase(supplierState);
            GstCalculator.Breakdown b = interState
                    ? GstCalculator.splitInterstate(inclusivePaise, 18)
                    : GstCalculator.split(inclusivePaise, 18);
            line.setTaxablePaise(b.taxablePaise());
            line.setCgstPaise(b.cgstPaise());
            line.setSgstPaise(b.sgstPaise());
            line.setIgstPaise(b.igstPaise());
        } else {
            line.setGstRate(0);
            line.setTaxablePaise(inclusivePaise);
            line.setCgstPaise(0);
            line.setSgstPaise(0);
            line.setIgstPaise(0);
        }
        return line;
    }

    private Invoice createDemoCreditNote(Gym gym, User owner, Invoice original, LocalDate issueDate, String reason) {
        String fy = original.getFinancialYear();
        long seq = billingService.nextNumber(gym.getId(), fy, BillingService.SERIES_CN);
        String number = InvoiceNumbering.format("CN", fy, seq);

        Invoice cn = new Invoice();
        cn.setGymId(gym.getId());
        cn.setInvoiceNumber(number);
        cn.setFinancialYear(fy);
        cn.setType(Invoice.TYPE_CREDIT_NOTE);
        cn.setMemberId(original.getMemberId());
        cn.setMembershipId(original.getMembershipId());
        cn.setSupplierState(original.getSupplierState());
        cn.setPlaceOfSupplyState(original.getPlaceOfSupplyState());
        cn.setGstRegistered(original.isGstRegistered());
        cn.setGstin(original.getGstin());
        cn.setStatus(Invoice.STATUS_ISSUED);
        cn.setVoidReason(reason);
        cn.setCreatedAt(issueDate.atTime(14, 0).atZone(IST).toInstant());
        cn.setCreatedBy(owner.getId());

        List<Invoice.Line> lines = new ArrayList<>();
        for (Invoice.Line l : original.getLines()) {
            Invoice.Line cl = new Invoice.Line();
            cl.setDescription("Credit note for " + original.getInvoiceNumber() + " - " + l.getDescription());
            cl.setSac(l.getSac());
            cl.setQuantity(l.getQuantity());
            cl.setRatePaise(-l.getRatePaise());
            cl.setTaxablePaise(-l.getTaxablePaise());
            cl.setGstRate(l.getGstRate());
            cl.setCgstPaise(-l.getCgstPaise());
            cl.setSgstPaise(-l.getSgstPaise());
            cl.setIgstPaise(-l.getIgstPaise());
            cl.setTotalPaise(-l.getTotalPaise());
            lines.add(cl);
        }
        cn.setLines(lines);
        cn.setTotalPaise(-original.getTotalPaise());
        cn.setAmountInWords(IndianNumberWords.amountInWords(Math.abs(original.getTotalPaise())));
        return invoiceRepository.save(cn);
    }

    private Payment createConfirmedPayment(String gymId, Invoice inv, String method, long amountPaise, LocalDate paidDate) {
        Payment p = new Payment();
        p.setGymId(gymId);
        p.setInvoiceId(inv.getId());
        p.setMemberId(inv.getMemberId());
        p.setAmountPaise(amountPaise);
        p.setMethod(method);
        p.setStatus(Payment.STATUS_CONFIRMED);
        p.setRecordedBy(Payment.RECORDED_BY_OWNER);
        Instant ts = paidDate.atTime(11, 30).atZone(IST).toInstant();
        p.setConfirmedAt(ts);
        p.setCreatedAt(ts);
        return paymentRepository.save(p);
    }

    private Payment createUpiPayment(String gymId, Invoice inv, long amountPaise,
                                     String utr, String status, String recordedBy) {
        Payment p = new Payment();
        p.setGymId(gymId);
        p.setInvoiceId(inv.getId());
        p.setMemberId(inv.getMemberId());
        p.setAmountPaise(amountPaise);
        p.setMethod(Payment.METHOD_UPI);
        p.setStatus(status);
        p.setUtr(utr);
        p.setRecordedBy(recordedBy);
        p.setCreatedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        if (Payment.STATUS_CONFIRMED.equals(status) || Payment.STATUS_FAILED.equals(status)) {
            p.setConfirmedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        }
        return paymentRepository.save(p);
    }

    private String formatAndNormalizePhone(int gymIndex, int slot) {
        String tenDigits = String.format("9%d%08d", gymIndex, 10000000 + slot);
        String p1 = tenDigits.substring(0, 5);
        String p2 = tenDigits.substring(5);
        String raw = switch (slot % 5) {
            case 0 -> p1 + " " + p2;
            case 1 -> "0" + tenDigits;
            case 2 -> "+91-" + p1 + "-" + p2;
            case 3 -> "91" + tenDigits;
            default -> tenDigits;
        };
        return PhoneUtils.normalize(raw);
    }
}
