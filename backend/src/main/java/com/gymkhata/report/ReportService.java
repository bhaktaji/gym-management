package com.gymkhata.report;

import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.billing.model.Payment;
import com.gymkhata.billing.repo.PaymentRepository;
import com.gymkhata.checkin.model.Checkin;
import com.gymkhata.checkin.repo.CheckinRepository;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.member.model.Member;
import com.gymkhata.member.repo.MemberRepository;
import com.gymkhata.membership.model.Membership;
import com.gymkhata.membership.repo.MembershipRepository;
import com.gymkhata.plan.model.Plan;
import com.gymkhata.plan.repo.PlanRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

@Service
public class ReportService {

    private final PaymentRepository paymentRepository;
    private final MembershipRepository membershipRepository;
    private final CheckinRepository checkinRepository;
    private final MemberRepository memberRepository;
    private final PlanRepository planRepository;
    private final MongoTemplate template;
    private final Clock clock;

    public ReportService(PaymentRepository paymentRepository,
                         MembershipRepository membershipRepository,
                         CheckinRepository checkinRepository,
                         MemberRepository memberRepository,
                         PlanRepository planRepository,
                         MongoTemplate template,
                         Clock clock) {
        this.paymentRepository = paymentRepository;
        this.membershipRepository = membershipRepository;
        this.checkinRepository = checkinRepository;
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.template = template;
        this.clock = clock;
    }

    /** Confirmed revenue grouped by month (IST), last 12 months. */
    public List<Document> revenueByMonth() {
        String gymId = SecurityUtils.currentGymId();
        LocalDate start = YearMonth.now(com.gymkhata.common.clock.Clock.IST).minusMonths(11).atDay(1);
        List<Payment> payments = paymentRepository.findByGymIdAndStatus(gymId, Payment.STATUS_CONFIRMED);
        Map<YearMonth, Long> byMonth = new TreeMap<>();
        for (Payment p : payments) {
            YearMonth ym = YearMonth.from(p.getCreatedAt().atZone(com.gymkhata.common.clock.Clock.IST).toLocalDate());
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

    /** Active memberships expiring in the next N days. */
    public List<Document> expiringMemberships(int days) {
        String gymId = SecurityUtils.currentGymId();
        LocalDate today = clock.today();
        LocalDate horizon = today.plusDays(days);
        List<Membership> active = membershipRepository.findByGymIdAndStatus(gymId, Membership.STATUS_ACTIVE);
        List<Document> out = new ArrayList<>();
        for (Membership m : active) {
            if (!m.getExpiryDate().isAfter(horizon)) {
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

    /** Check-ins per day for the last N days. */
    public List<Document> checkinsPerDay(int days) {
        String gymId = SecurityUtils.currentGymId();
        LocalDate today = clock.today();
        LocalDate from = today.minusDays(days - 1);
        List<Bson> pipeline = List.of(
                Aggregates.match(Filters.and(
                        Filters.eq("gymId", gymId),
                        Filters.gte("date", from.toString()))),
                Aggregates.group("$date", Accumulators.sum("count", 1)));
        List<Document> out = new ArrayList<>();
        template.getCollection("checkins").aggregate(pipeline).forEach(out::add);
        out.sort(Comparator.comparing(d -> d.getString("_id")));
        for (Document d : out) {
            d.put("date", d.getString("_id"));
            d.remove("_id");
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

    /** Dashboard summary numbers. */
    public Map<String, Object> dashboard() {
        String gymId = SecurityUtils.currentGymId();
        long activeMembers = memberRepository.countByGymIdAndStatus(gymId, Member.STATUS_ACTIVE);
        long activeMemberships = membershipRepository.findByGymIdAndStatus(gymId, Membership.STATUS_ACTIVE).size();
        long pendingPayments = paymentRepository.findByGymIdAndStatus(gymId, Payment.STATUS_PENDING).size();
        long todayCheckins = checkinRepository.findByGymIdAndDateOrderByCheckInAtDesc(gymId, clock.today()).size();
        return Map.of(
                "activeMembers", activeMembers,
                "activeMemberships", activeMemberships,
                "pendingPayments", pendingPayments,
                "todayCheckins", todayCheckins,
                "expiringThisWeek", expiringMemberships(7).size());
    }
}
