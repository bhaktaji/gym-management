package com.gymkhata.report;

import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.billing.model.Payment;
import com.gymkhata.billing.repo.InvoiceRepository;
import com.gymkhata.billing.repo.PaymentRepository;
import com.gymkhata.checkin.model.Checkin;
import com.gymkhata.checkin.repo.CheckinRepository;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.member.model.Member;
import com.gymkhata.member.repo.MemberRepository;
import com.gymkhata.membership.model.Membership;
import com.gymkhata.membership.repo.MembershipRepository;
import com.gymkhata.plan.repo.PlanRepository;
import com.gymkhata.pt.CommissionService;
import com.gymkhata.pt.model.PtEnrolment;
import com.gymkhata.pt.model.PtPackage;
import com.gymkhata.pt.model.Trainer;
import com.gymkhata.pt.repo.PtEnrolmentRepository;
import com.gymkhata.pt.repo.TrainerRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class ReportService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final MembershipRepository membershipRepository;
    private final CheckinRepository checkinRepository;
    private final MemberRepository memberRepository;
    private final PlanRepository planRepository;
    private final TrainerRepository trainerRepository;
    private final PtEnrolmentRepository ptEnrolmentRepository;
    private final CommissionService commissionService;
    private final MongoTemplate template;
    private final Clock clock;

    public ReportService(PaymentRepository paymentRepository,
                         InvoiceRepository invoiceRepository,
                         MembershipRepository membershipRepository,
                         CheckinRepository checkinRepository,
                         MemberRepository memberRepository,
                         PlanRepository planRepository,
                         TrainerRepository trainerRepository,
                         PtEnrolmentRepository ptEnrolmentRepository,
                         CommissionService commissionService,
                         MongoTemplate template,
                         Clock clock) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.membershipRepository = membershipRepository;
        this.checkinRepository = checkinRepository;
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.trainerRepository = trainerRepository;
        this.ptEnrolmentRepository = ptEnrolmentRepository;
        this.commissionService = commissionService;
        this.template = template;
        this.clock = clock;
    }

    /** Confirmed revenue grouped by month (IST), last 12 months. */
    public List<Document> revenueByMonth() {
        String gymId = SecurityUtils.currentGymId();
        LocalDate start = YearMonth.now(Clock.IST).minusMonths(11).atDay(1);
        List<Payment> payments = paymentRepository.findByGymIdAndStatus(gymId, Payment.STATUS_CONFIRMED);
        Map<YearMonth, Long> byMonth = new TreeMap<>();
        for (Payment p : payments) {
            if (p.getCreatedAt() == null) {
                continue;
            }
            YearMonth ym = YearMonth.from(p.getCreatedAt().atZone(Clock.IST).toLocalDate());
            if (!ym.isBefore(YearMonth.from(start))) {
                byMonth.merge(ym, p.getAmountPaise(), Long::sum);
            }
        }
        List<Document> out = new ArrayList<>();
        for (Map.Entry<YearMonth, Long> e : byMonth.entrySet()) {
            out.add(new Document("month", e.getKey().toString()).append("revenuePaise", e.getValue()));
        }
        return out;
    }

    /** Active memberships expiring in the next N days (from today inclusive up to today + N days). */
    public List<Document> expiringMemberships(int days) {
        String gymId = SecurityUtils.currentGymId();
        LocalDate today = clock.today();
        LocalDate horizon = today.plusDays(days);
        List<Membership> active = membershipRepository.findByGymIdAndStatus(gymId, Membership.STATUS_ACTIVE);
        List<Document> out = new ArrayList<>();
        for (Membership m : active) {
            if (m.getExpiryDate() != null && !m.getExpiryDate().isBefore(today) && !m.getExpiryDate().isAfter(horizon)) {
                Member member = memberRepository.findById(m.getMemberId()).orElse(null);
                out.add(new Document("memberId", m.getMemberId())
                        .append("memberName", member == null ? "" : member.getName())
                        .append("planName", m.getPlanName())
                        .append("expiryDate", m.getExpiryDate().toString())
                        .append("daysLeft", java.time.temporal.ChronoUnit.DAYS.between(today, m.getExpiryDate())));
            }
        }
        out.sort(Comparator.comparing(d -> d.getString("expiryDate")));
        return out;
    }

    /** Check-ins per day for the last N days (properly querying LocalDate BSON Date field). */
    public List<Document> checkinsPerDay(int days) {
        String gymId = SecurityUtils.currentGymId();
        LocalDate today = clock.today();
        LocalDate from = today.minusDays(Math.max(1, days) - 1L);
        Query q = Query.query(Criteria.where("gymId").is(gymId).and("date").gte(from).lte(today));
        List<Checkin> checkins = template.find(q, Checkin.class);
        Map<LocalDate, Integer> counts = new TreeMap<>();
        for (Checkin c : checkins) {
            if (c.getDate() != null) {
                counts.merge(c.getDate(), 1, Integer::sum);
            }
        }
        List<Document> out = new ArrayList<>();
        for (Map.Entry<LocalDate, Integer> e : counts.entrySet()) {
            out.add(new Document("date", e.getKey().toString()).append("count", e.getValue()));
        }
        return out;
    }

    /** Active member count per plan. */
    public List<Document> membersPerPlan() {
        String gymId = SecurityUtils.currentGymId();
        List<Membership> active = membershipRepository.findByGymIdAndStatus(gymId, Membership.STATUS_ACTIVE);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Membership m : active) {
            counts.merge(m.getPlanName() == null ? "Unknown" : m.getPlanName(), 1L, Long::sum);
        }
        List<Document> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            out.add(new Document("planName", e.getKey()).append("activeMembers", e.getValue()));
        }
        return out;
    }

    /** Confirmed Personal Training (PT) revenue grouped by month (IST), last 12 months. */
    public List<Document> ptRevenueByMonth() {
        String gymId = SecurityUtils.currentGymId();
        List<Invoice> gymInvoices = invoiceRepository.findByGymIdOrderByCreatedAtDesc(gymId);
        Set<String> ptInvoiceIds = new HashSet<>();
        for (Invoice inv : gymInvoices) {
            if (inv.getPtEnrolmentId() != null) {
                ptInvoiceIds.add(inv.getId());
            }
        }
        List<Payment> payments = paymentRepository.findByGymIdAndStatus(gymId, Payment.STATUS_CONFIRMED);
        Map<YearMonth, Long> byMonth = new TreeMap<>();
        for (Payment p : payments) {
            if (!ptInvoiceIds.contains(p.getInvoiceId()) || p.getCreatedAt() == null) {
                continue;
            }
            YearMonth ym = YearMonth.from(p.getCreatedAt().atZone(Clock.IST).toLocalDate());
            byMonth.merge(ym, p.getAmountPaise(), Long::sum);
        }
        List<Document> out = new ArrayList<>();
        for (Map.Entry<YearMonth, Long> e : byMonth.entrySet()) {
            out.add(new Document("month", e.getKey().toString()).append("ptRevenuePaise", e.getValue()));
        }
        return out;
    }

    /** Commission payable summary per trainer for a given month (YYYY-MM). */
    public List<Document> trainerCommissions(String month) {
        String gymId = SecurityUtils.currentGymId();
        String targetMonth = (month == null || month.isBlank())
                ? YearMonth.from(clock.today()).toString()
                : month.trim();
        List<Trainer> trainers = trainerRepository.findByGymIdOrderByNameAsc(gymId);
        List<Document> out = new ArrayList<>();
        for (Trainer t : trainers) {
            Map<String, Object> stmt = commissionService.getStatement(t.getId(), targetMonth);
            out.add(new Document("trainerId", t.getId())
                    .append("trainerName", t.getName())
                    .append("trainerPhone", t.getPhone())
                    .append("month", targetMonth)
                    .append("accruedPaise", stmt.get("accruedPaise"))
                    .append("reversalsPaise", stmt.get("reversalsPaise"))
                    .append("adjustmentsPaise", stmt.get("adjustmentsPaise"))
                    .append("netPayablePaise", stmt.get("netPayablePaise"))
                    .append("paidPaise", stmt.get("paidPaise"))
                    .append("balanceDuePaise", stmt.get("balanceDuePaise")));
        }
        return out;
    }

    /** Trainer-wise confirmed payment collections (PT + referral invoices). */
    public List<Document> trainerCollections() {
        String gymId = SecurityUtils.currentGymId();
        List<Trainer> trainers = trainerRepository.findByGymIdOrderByNameAsc(gymId);
        Map<String, String> trainerNames = new LinkedHashMap<>();
        Map<String, Long> collectedByTrainer = new LinkedHashMap<>();
        Map<String, Integer> packageCountByTrainer = new LinkedHashMap<>();
        for (Trainer t : trainers) {
            trainerNames.put(t.getId(), t.getName());
            collectedByTrainer.put(t.getId(), 0L);
            packageCountByTrainer.put(t.getId(), 0);
        }

        List<PtEnrolment> enrolments = ptEnrolmentRepository.findByGymIdOrderByCreatedAtDesc(gymId);
        for (PtEnrolment e : enrolments) {
            if (e.getTrainerId() != null) {
                packageCountByTrainer.merge(e.getTrainerId(), 1, Integer::sum);
            }
        }

        Map<String, String> invoiceToTrainer = new HashMap<>();
        for (Invoice inv : invoiceRepository.findByGymIdOrderByCreatedAtDesc(gymId)) {
            if (inv.getTrainerId() != null) {
                invoiceToTrainer.put(inv.getId(), inv.getTrainerId());
            }
        }

        for (Payment p : paymentRepository.findByGymIdAndStatus(gymId, Payment.STATUS_CONFIRMED)) {
            String trainerId = invoiceToTrainer.get(p.getInvoiceId());
            if (trainerId != null) {
                collectedByTrainer.merge(trainerId, p.getAmountPaise(), Long::sum);
            }
        }

        List<Document> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : trainerNames.entrySet()) {
            String tId = entry.getKey();
            out.add(new Document("trainerId", tId)
                    .append("trainerName", entry.getValue())
                    .append("packagesSold", packageCountByTrainer.getOrDefault(tId, 0))
                    .append("collectedPaise", collectedByTrainer.getOrDefault(tId, 0L)));
        }
        return out;
    }

    /** Active PT packages expiring in the next N days OR with <= 2 sessions remaining. */
    public List<Document> expiringPtPackages(int days) {
        String gymId = SecurityUtils.currentGymId();
        LocalDate today = clock.today();
        LocalDate horizon = today.plusDays(days);
        List<PtEnrolment> active = ptEnrolmentRepository.findByGymIdAndStatus(gymId, PtEnrolment.STATUS_ACTIVE);
        List<Document> out = new ArrayList<>();
        for (PtEnrolment e : active) {
            boolean dateExpiring = e.getExpiryDate() != null
                    && !e.getExpiryDate().isBefore(today)
                    && !e.getExpiryDate().isAfter(horizon);
            int remaining = e.getRemainingSessions() == null ? 0 : e.getRemainingSessions();
            boolean lowSessions = PtPackage.TYPE_SESSION_BASED.equals(e.getPackageType()) && remaining <= 2;
            if (dateExpiring || lowSessions) {
                Member m = memberRepository.findById(e.getMemberId()).orElse(null);
                Trainer t = trainerRepository.findById(e.getTrainerId()).orElse(null);
                long daysLeft = e.getExpiryDate() == null ? 0 : java.time.temporal.ChronoUnit.DAYS.between(today, e.getExpiryDate());
                out.add(new Document("enrolmentId", e.getId())
                        .append("memberId", e.getMemberId())
                        .append("memberName", m == null ? "" : m.getName())
                        .append("trainerId", e.getTrainerId())
                        .append("trainerName", t == null ? "" : t.getName())
                        .append("packageName", e.getPtPackageName())
                        .append("packageType", e.getPackageType())
                        .append("remainingSessions", remaining)
                        .append("totalSessions", e.getTotalSessions())
                        .append("expiryDate", e.getExpiryDate() == null ? "" : e.getExpiryDate().toString())
                        .append("daysLeft", daysLeft));
            }
        }
        return out;
    }

    /** Dashboard summary numbers. */
    public Map<String, Object> dashboard() {
        String gymId = SecurityUtils.currentGymId();
        long activeMembers = memberRepository.countByGymIdAndStatus(gymId, Member.STATUS_ACTIVE);
        long activeMemberships = membershipRepository.findByGymIdAndStatus(gymId, Membership.STATUS_ACTIVE).size();
        long pendingPayments = paymentRepository.findByGymIdAndStatus(gymId, Payment.STATUS_PENDING).size();
        long todayCheckins = checkinRepository.findByGymIdAndDateOrderByCheckInAtDesc(gymId, clock.today()).size();
        long activePtEnrolments = ptEnrolmentRepository.findByGymIdAndStatus(gymId, PtEnrolment.STATUS_ACTIVE).size();
        return Map.of(
                "activeMembers", activeMembers,
                "activeMemberships", activeMemberships,
                "pendingPayments", pendingPayments,
                "todayCheckins", todayCheckins,
                "expiringThisWeek", expiringMemberships(7).size(),
                "activePtEnrolments", activePtEnrolments,
                "ptExpiringSoon", expiringPtPackages(7).size());
    }
}
