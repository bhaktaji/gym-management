package com.gymkhata.checkin;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.billing.PaymentService;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.billing.repo.InvoiceRepository;
import com.gymkhata.billing.pdf.PdfRenderer;
import com.gymkhata.checkin.model.Checkin;
import com.gymkhata.checkin.repo.CheckinRepository;
import com.gymkhata.common.audit.AuditService;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.domain.CheckinRules;
import com.gymkhata.domain.GeofenceAndQrUtils;
import com.gymkhata.domain.PhoneUtils;
import com.gymkhata.domain.QuietHours;
import com.gymkhata.domain.UpiUtils;
import com.gymkhata.member.model.Member;
import com.gymkhata.member.repo.MemberRepository;
import com.gymkhata.membership.model.Membership;
import com.gymkhata.membership.repo.MembershipRepository;
import com.gymkhata.messaging.MessagingProvider;
import com.gymkhata.messaging.model.OutboxMessage;
import com.gymkhata.messaging.repo.OutboxRepository;
import com.gymkhata.pt.PtService;
import com.gymkhata.pt.model.PtEnrolment;
import com.gymkhata.pt.repo.PtEnrolmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CheckinService {

    public static final int MAX_PIN_ATTEMPTS = 5;
    public static final long PIN_LOCKOUT_SECONDS = 15 * 60L;
    public static final int MAX_BOUND_DEVICES = 2;

    private final CheckinRepository checkinRepository;
    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final GymRepository gymRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentService paymentService;
    private final OutboxRepository outboxRepository;
    private final MessagingProvider messagingProvider;
    private final PtEnrolmentRepository ptEnrolmentRepository;
    private final PtService ptService;
    private final PdfRenderer pdfRenderer;
    private final PasswordEncoder passwordEncoder;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;
    private final AuditService auditService;
    private final String publicBaseUrl;

    public CheckinService(CheckinRepository checkinRepository,
                          MemberRepository memberRepository,
                          MembershipRepository membershipRepository,
                          GymRepository gymRepository,
                          InvoiceRepository invoiceRepository,
                          PaymentService paymentService,
                          OutboxRepository outboxRepository,
                          MessagingProvider messagingProvider,
                          PtEnrolmentRepository ptEnrolmentRepository,
                          PtService ptService,
                          PdfRenderer pdfRenderer,
                          PasswordEncoder passwordEncoder,
                          MongoTemplate mongoTemplate,
                          Clock clock,
                          AuditService auditService,
                          @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.checkinRepository = checkinRepository;
        this.memberRepository = memberRepository;
        this.membershipRepository = membershipRepository;
        this.gymRepository = gymRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentService = paymentService;
        this.outboxRepository = outboxRepository;
        this.messagingProvider = messagingProvider;
        this.ptEnrolmentRepository = ptEnrolmentRepository;
        this.ptService = ptService;
        this.pdfRenderer = pdfRenderer;
        this.passwordEncoder = passwordEncoder;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
        this.auditService = auditService;
        this.publicBaseUrl = publicBaseUrl;
    }

    private Gym requireCurrentGymWithAttendanceEnabled() {
        String gymId = SecurityUtils.currentGymId();
        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> BusinessException.notFound("GYM_NOT_FOUND", "Gym not found"));
        requireAttendanceEnabled(gym);
        return gym;
    }

    public Gym resolveGymBySlugWithAttendanceEnabled(String slug) {
        if (slug == null || slug.isBlank()) {
            throw BusinessException.notFound("GYM_NOT_FOUND", "Gym not found");
        }
        String norm = slug.trim().toLowerCase(Locale.ROOT);
        Gym gym = gymRepository.findBySlug(norm)
                .or(() -> gymRepository.findByCodeIgnoreCase(norm))
                .or(() -> gymRepository.findById(slug.trim()))
                .orElseThrow(() -> BusinessException.notFound("GYM_NOT_FOUND", "Gym not found"));
        requireAttendanceEnabled(gym);
        return gym;
    }

    public void requireAttendanceEnabled(Gym gym) {
        if (gym == null || gym.getSettings() == null || !gym.getSettings().isAttendanceEnabled()) {
            throw BusinessException.forbidden("FEATURE_DISABLED", "Attendance feature is disabled for this gym");
        }
    }

    /**
     * Auto-closes open check-in sessions that have exceeded autoCloseDurationMinutes (default 120m)
     * or passed the gym's closingTimeIst.
     */
    public int autoCloseStaleSessions(Gym gym) {
        if (gym == null) {
            return 0;
        }
        List<Checkin> openSessions = checkinRepository.findByGymIdAndCheckOutAtIsNull(gym.getId());
        if (openSessions.isEmpty()) {
            return 0;
        }
        Instant now = clock.now();
        int durationMins = gym.getSettings().getAutoCloseDurationMinutes();
        String closingTimeStr = gym.getSettings().getClosingTimeIst();
        int closedCount = 0;

        for (Checkin c : openSessions) {
            if (c.getCheckInAt() == null) {
                continue;
            }
            Instant deadline = c.getCheckInAt().plusSeconds(durationMins * 60L);
            if (closingTimeStr != null && !closingTimeStr.isBlank() && c.getDate() != null) {
                try {
                    LocalTime closeTime = LocalTime.parse(closingTimeStr.trim());
                    Instant closingInstant = ZonedDateTime.of(c.getDate(), closeTime, Clock.IST).toInstant();
                    if (closingInstant.isAfter(c.getCheckInAt()) && closingInstant.isBefore(deadline)) {
                        deadline = closingInstant;
                    }
                } catch (Exception ignored) {
                }
            }
            if (!now.isBefore(deadline) || (c.getDate() != null && c.getDate().isBefore(clock.today()))) {
                c.setCheckOutAt(deadline);
                c.setAutoClosed(true);
                checkinRepository.save(c);
                closedCount++;
            }
        }
        return closedCount;
    }

    /**
     * Owner manual check-in fallback. Hidden/disabled when attendanceEnabled == false.
     */
    public Checkin checkIn(String memberId) {
        Gym gym = requireCurrentGymWithAttendanceEnabled();
        autoCloseStaleSessions(gym);
        String gymId = gym.getId();
        Member member = memberRepository.findById(memberId)
                .filter(m -> gymId.equals(m.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("MEMBER_NOT_FOUND", "Member not found"));
        Membership active = membershipRepository
                .findFirstByGymIdAndMemberIdAndStatusOrderByExpiryDateDesc(gymId, memberId, Membership.STATUS_ACTIVE);
        LocalDate today = clock.today();
        boolean alreadyIn = !checkinRepository
                .findByGymIdAndMemberIdAndDateOrderByCheckInAtDesc(gymId, memberId, today)
                .isEmpty();

        CheckinRules.Decision decision = CheckinRules.evaluate(new CheckinRules.Input(
                member.getStatus(),
                active == null ? null : active.getStatus(),
                active == null ? null : active.getExpiryDate(),
                today,
                alreadyIn));

        switch (decision) {
            case ALLOWED -> {
                Checkin c = new Checkin();
                c.setGymId(gymId);
                c.setMemberId(memberId);
                c.setMemberName(member.getName());
                c.setCheckInAt(clock.now());
                c.setDate(today);
                c.setMethod(Checkin.METHOD_MANUAL);
                c.setMembershipBadge("GREEN");
                Checkin saved = checkinRepository.save(c);
                auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "CHECKIN_MANUAL", "member", memberId, member.getName());
                return saved;
            }
            case ALREADY_CHECKED_IN_TODAY ->
                    throw BusinessException.conflict("ALREADY_CHECKED_IN", "Member already checked in today");
            case MEMBER_INACTIVE ->
                    throw BusinessException.badRequest("MEMBER_INACTIVE", "Member is not active");
            case NO_ACTIVE_MEMBERSHIP ->
                    throw BusinessException.badRequest("NO_ACTIVE_MEMBERSHIP", "Member has no active membership");
            case MEMBERSHIP_EXPIRED ->
                    throw BusinessException.badRequest("MEMBERSHIP_EXPIRED", "Membership has expired");
            default -> throw BusinessException.badRequest("CHECKIN_DENIED", "Check-in not allowed");
        }
    }

    public Checkin checkOut(String checkinId) {
        Gym gym = requireCurrentGymWithAttendanceEnabled();
        String gymId = gym.getId();
        Checkin c = checkinRepository.findById(checkinId)
                .filter(x -> gymId.equals(x.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("CHECKIN_NOT_FOUND", "Check-in not found"));
        if (c.getCheckOutAt() != null) {
            throw BusinessException.badRequest("ALREADY_CHECKED_OUT", "Member already checked out");
        }
        c.setCheckOutAt(clock.now());
        return checkinRepository.save(c);
    }

    public List<Checkin> today() {
        Gym gym = requireCurrentGymWithAttendanceEnabled();
        autoCloseStaleSessions(gym);
        return checkinRepository.findByGymIdAndDateOrderByCheckInAtDesc(gym.getId(), clock.today());
    }

    public List<Checkin> history(String memberId) {
        Gym gym = requireCurrentGymWithAttendanceEnabled();
        autoCloseStaleSessions(gym);
        return checkinRepository.findByGymIdAndMemberIdOrderByCheckInAtDesc(gym.getId(), memberId);
    }

    public byte[] renderPosterPdf() {
        Gym gym = requireCurrentGymWithAttendanceEnabled();
        String url = publicBaseUrl + "/g/" + gym.getSlug() + "/in";
        return pdfRenderer.renderQrPoster(gym, url);
    }

    // --- Owner Analytics & Nudge (replaces "Who is Inside Now") ---

    public Map<String, Object> analytics(Integer notSeenDaysOverride) {
        Gym gym = requireCurrentGymWithAttendanceEnabled();
        autoCloseStaleSessions(gym);
        String gymId = gym.getId();
        LocalDate today = clock.today();

        int todayVisits = checkinRepository.findByGymIdAndDateOrderByCheckInAtDesc(gymId, today).size();

        // Peak hours over the last 30 days
        LocalDate from30 = today.minusDays(29);
        Query q = Query.query(Criteria.where("gymId").is(gymId).and("date").gte(from30).lte(today));
        List<Checkin> recentCheckins = mongoTemplate.find(q, Checkin.class);
        int[] hourlyCounts = new int[24];
        for (Checkin c : recentCheckins) {
            if (c.getCheckInAt() != null) {
                int hr = c.getCheckInAt().atZone(Clock.IST).getHour();
                if (hr >= 0 && hr < 24) {
                    hourlyCounts[hr]++;
                }
            }
        }
        List<Map<String, Object>> peakHours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            peakHours.add(Map.of(
                    "hour", h,
                    "label", String.format("%02d:00", h),
                    "count", hourlyCounts[h]
            ));
        }

        // Not seen in N days (default 14)
        int thresholdDays = (notSeenDaysOverride != null && notSeenDaysOverride > 0)
                ? notSeenDaysOverride
                : gym.getSettings().getNotSeenDaysThreshold();
        List<Member> activeMembers = memberRepository.findByGymIdAndStatus(gymId, Member.STATUS_ACTIVE);
        List<Map<String, Object>> notSeenList = new ArrayList<>();

        for (Member m : activeMembers) {
            Checkin last = checkinRepository.findFirstByGymIdAndMemberIdOrderByCheckInAtDesc(gymId, m.getId());
            LocalDate lastDate = last != null ? last.getDate() : m.getJoinedAt();
            long daysUnseen = lastDate != null
                    ? ChronoUnit.DAYS.between(lastDate, today)
                    : thresholdDays;
            if (daysUnseen >= thresholdDays) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("memberId", m.getId());
                row.put("memberName", m.getName());
                row.put("phone", m.getPhone());
                row.put("lastSeenDate", last != null && last.getDate() != null ? last.getDate().toString() : null);
                row.put("daysSinceLastVisit", daysUnseen);
                row.put("whatsappOptIn", m.isWhatsappOptIn());
                notSeenList.add(row);
            }
        }
        notSeenList.sort(Comparator.comparingLong(r -> -((Long) r.get("daysSinceLastVisit"))));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("todayVisits", todayVisits);
        out.put("notSeenDaysThreshold", thresholdDays);
        out.put("peakHours", peakHours);
        out.put("notSeenMembers", notSeenList);
        return out;
    }

    public OutboxMessage nudgeMember(String memberId) {
        Gym gym = requireCurrentGymWithAttendanceEnabled();
        String gymId = gym.getId();
        Member member = memberRepository.findById(memberId)
                .filter(m -> gymId.equals(m.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("MEMBER_NOT_FOUND", "Member not found"));
        if (!member.isWhatsappOptIn() || member.getPhone() == null) {
            throw BusinessException.badRequest("WHATSAPP_NOT_OPTED_IN", "Member has not opted in to WhatsApp messages");
        }
        LocalDate today = clock.today();
        boolean quiet = gym.getSettings().isQuietHoursEnabled() && QuietHours.isQuiet(clock.nowIst());

        String text = String.format(
                "Hi %s, we missed you at %s! Stay consistent with your fitness goals—see you at the gym soon! / नमस्ते %s, %s में हमने आपको मिस किया! आज ही अपने वर्कआउट के लिए आएं!",
                member.getName(), gym.getName(), member.getName(), gym.getName());
        String waLink = messagingProvider.buildWaLink(member.getPhone(), text);

        OutboxMessage msg = new OutboxMessage();
        msg.setGymId(gymId);
        msg.setMemberId(member.getId());
        msg.setMemberName(member.getName());
        msg.setPhone(member.getPhone());
        msg.setType(OutboxMessage.TYPE_WE_MISSED_YOU);
        msg.setChannel(OutboxMessage.CHANNEL_WHATSAPP);
        msg.setTemplateKey("WE_MISSED_YOU");
        msg.setLanguage("BILINGUAL");
        msg.setVariables(Map.of("name", member.getName(), "gymName", gym.getName()));
        msg.setWaLink(waLink);
        msg.setStatus(quiet ? OutboxMessage.STATUS_QUIET_HOURS : OutboxMessage.STATUS_PENDING);
        msg.setDate(today);
        msg.setCreatedAt(clock.now());
        OutboxMessage saved = outboxRepository.save(msg);

        auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "ATTENDANCE_NUDGE_QUEUED",
                "member", member.getId(), member.getName());
        return saved;
    }

    // --- Public Self-Service QR Check-In (/g/{gymSlug}/in & /g/{gymSlug}/screen) ---

    public Map<String, Object> getPublicAttendanceInfo(String gymSlug, String deviceToken) {
        Gym gym = resolveGymBySlugWithAttendanceEnabled(gymSlug);
        autoCloseStaleSessions(gym);
        Member boundMember = resolveMemberFromDeviceToken(gym.getId(), deviceToken);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gymId", gym.getId());
        out.put("gymName", gym.getName());
        out.put("gymSlug", gym.getSlug());
        out.put("gymPhone", gym.getPhone() == null ? "" : gym.getPhone());
        out.put("attendanceEnabled", gym.getSettings().isAttendanceEnabled());
        out.put("strictQrMode", gym.getSettings().isStrictQrMode());
        out.put("selfCheckoutEnabled", gym.getSettings().isSelfCheckoutEnabled());
        out.put("deviceBound", boundMember != null);
        if (boundMember != null) {
            out.put("boundMemberId", boundMember.getId());
            out.put("boundMemberName", boundMember.getName());
        }
        return out;
    }

    public Map<String, Object> getRotatingScreenToken(String gymSlug) {
        Gym gym = resolveGymBySlugWithAttendanceEnabled(gymSlug);
        long nowSec = clock.now().getEpochSecond();
        String token = GeofenceAndQrUtils.generateRotatingToken(gym.getSettings().getRotatingTokenSecret(), nowSec);
        long expiresIn = GeofenceAndQrUtils.ROTATION_WINDOW_SECONDS - (nowSec % GeofenceAndQrUtils.ROTATION_WINDOW_SECONDS);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gymName", gym.getName());
        out.put("gymSlug", gym.getSlug());
        out.put("strictQrMode", gym.getSettings().isStrictQrMode());
        out.put("token", token);
        out.put("expiresInSeconds", expiresIn);
        out.put("checkinUrl", publicBaseUrl + "/g/" + gym.getSlug() + "/in?token=" + token);
        return out;
    }

    public record SelfCheckinOutcome(Map<String, Object> body, String newDeviceCookieToken) {
    }

    private final java.util.concurrent.ConcurrentHashMap<String, AttemptTracker> phoneIpLockouts = new java.util.concurrent.ConcurrentHashMap<>();

    private static class AttemptTracker {
        int failures;
        int level;
        Instant lockedUntil;
    }

    public static String lockoutKey(String phone, String clientIp) {
        String p = (phone != null && !phone.isBlank()) ? phone.trim() : "unknown";
        String ip = (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "local";
        return p + "|" + ip;
    }

    public void clearLockoutsForPhone(String phone) {
        if (phone == null) return;
        String prefix = phone.trim() + "|";
        phoneIpLockouts.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public static long calculateLockoutSeconds(int level) {
        return switch (level) {
            case 1 -> 60L;     // 1st lockout: 1 min
            case 2 -> 300L;    // 2nd lockout: 5 mins
            case 3 -> 900L;    // 3rd lockout: 15 mins
            default -> 3600L;  // 4th+ lockout: 1 hour
        };
    }

    private void checkLockout(String phone, String clientIp, Instant now) {
        String key = lockoutKey(phone, clientIp);
        AttemptTracker tracker = phoneIpLockouts.get(key);
        if (tracker != null && tracker.lockedUntil != null && tracker.lockedUntil.isAfter(now)) {
            long remaining = Duration.between(now, tracker.lockedUntil).toSeconds();
            throw BusinessException.tooManyRequests("PIN_LOCKED",
                    "Too many failed PIN attempts from this device/IP. Locked for " + remaining + " seconds.");
        }
    }

    private AttemptTracker recordFailure(String phone, String clientIp, Instant now) {
        String key = lockoutKey(phone, clientIp);
        return phoneIpLockouts.compute(key, (k, v) -> {
            if (v == null) {
                AttemptTracker nw = new AttemptTracker();
                nw.failures = 1;
                nw.level = 1;
                return nw;
            }
            if (v.lockedUntil != null && !v.lockedUntil.isAfter(now)) {
                v.failures = 1;
                v.level = Math.min(v.level + 1, 4);
                v.lockedUntil = null;
                return v;
            }
            v.failures++;
            if (v.failures >= MAX_PIN_ATTEMPTS) {
                long duration = calculateLockoutSeconds(v.level);
                v.lockedUntil = now.plusSeconds(duration);
            }
            return v;
        });
    }

    public SelfCheckinOutcome selfCheckIn(String gymSlug,
                                          String phone,
                                          String pin,
                                          String deviceToken,
                                          String rotatingToken,
                                          Double latitude,
                                          Double longitude,
                                          String userAgent) {
        return selfCheckIn(gymSlug, phone, pin, deviceToken, rotatingToken, latitude, longitude, userAgent, null);
    }

    public SelfCheckinOutcome selfCheckIn(String gymSlug,
                                          String phone,
                                          String pin,
                                          String deviceToken,
                                          String rotatingToken,
                                          Double latitude,
                                          Double longitude,
                                          String userAgent,
                                          String clientIp) {
        Gym gym = resolveGymBySlugWithAttendanceEnabled(gymSlug);
        autoCloseStaleSessions(gym);
        String gymId = gym.getId();

        // 1. Strict QR rotating token verification (when enabled)
        if (gym.getSettings().isStrictQrMode()) {
            long nowSec = clock.now().getEpochSecond();
            if (!GeofenceAndQrUtils.isValidRotatingToken(gym.getSettings().getRotatingTokenSecret(), rotatingToken, nowSec)) {
                throw BusinessException.badRequest("INVALID_QR_TOKEN", "Rotating QR token is invalid or has expired");
            }
        }

        // 2. Identify member (via bound device token OR phone + 4-digit PIN)
        Member member = resolveMemberFromDeviceToken(gymId, deviceToken);
        String issuedCookieToken = null;

        if (member == null) {
            Instant now = clock.now();

            if (phone == null || phone.isBlank() || pin == null || pin.isBlank()) {
                throw BusinessException.unauthorized("AUTH_REQUIRED",
                        "Enter your mobile number and 4-digit PIN to bind this device");
            }
            String e164 = PhoneUtils.normalize(phone);
            if (e164 == null) {
                throw BusinessException.badRequest("INVALID_PHONE", "Enter a valid 10-digit mobile number");
            }
            checkLockout(e164, clientIp, now);

            member = memberRepository.findByGymIdAndPhone(gymId, e164)
                    .orElseThrow(() -> BusinessException.notFound("MEMBER_NOT_FOUND", "Member not found"));

            if (member.getPinHash() == null || !passwordEncoder.matches(pin.trim(), member.getPinHash())) {
                AttemptTracker tracker = recordFailure(e164, clientIp, now);
                int attempts = member.getPinFailedAttempts() + 1;
                member.setPinFailedAttempts(attempts);
                if (tracker.lockedUntil != null && tracker.lockedUntil.isAfter(now)) {
                    member.setPinLockedUntil(tracker.lockedUntil);
                    memberRepository.save(member);

                    long lockSecs = Duration.between(now, tracker.lockedUntil).toSeconds();
                    // Alert the gym owner via outbox notification
                    OutboxMessage alert = new OutboxMessage();
                    alert.setGymId(gymId);
                    alert.setMemberId(member.getId());
                    alert.setMemberName(member.getName());
                    alert.setPhone(gym.getPhone());
                    alert.setType(OutboxMessage.TYPE_ALERT);
                    alert.setChannel(OutboxMessage.CHANNEL_WHATSAPP);
                    alert.setLanguage("EN");
                    alert.setStatus(OutboxMessage.STATUS_PENDING);
                    alert.setVariables(Map.of("alert", "Multiple failed PIN attempts for member " + member.getName() + " (" + member.getPhone() + "). Device/IP locked for " + lockSecs + " seconds (backoff level " + tracker.level + ")."));
                    alert.setDate(clock.today());
                    alert.setCreatedAt(now);
                    outboxRepository.save(alert);

                    throw BusinessException.tooManyRequests("PIN_LOCKED",
                            "Too many failed PIN attempts. Locked for " + lockSecs + " seconds.");
                }
                memberRepository.save(member);
                throw BusinessException.unauthorized("INVALID_PIN", "Invalid 4-digit PIN");
            }

            // Valid PIN: reset lockout counters for this phone+IP & bind device (max 2 bound devices per member)
            phoneIpLockouts.remove(lockoutKey(e164, clientIp));
            member.setPinFailedAttempts(0);
            member.setPinLockedUntil(null);

            if (member.getBoundDevices().size() >= MAX_BOUND_DEVICES) {
                throw BusinessException.forbidden("MAX_DEVICES_REACHED",
                        "Maximum 2 devices bound to this member. Ask the gym owner to revoke an old device.");
            }

            String deviceId = UUID.randomUUID().toString().substring(0, 12);
            String rawSecret = UUID.randomUUID().toString().replace("-", "");
            issuedCookieToken = member.getId() + ":" + rawSecret;
            String tokenHash = sha256Hex(rawSecret);

            member.getBoundDevices().add(new Member.BoundDevice(
                    deviceId,
                    tokenHash,
                    userAgent == null ? "Browser" : userAgent,
                    now));
            member.setUpdatedAt(now);
            member = memberRepository.save(member);
        }

        // 3. Enforce 1 check-in per member per local day (or configurable minCheckinGapMinutes)
        LocalDate today = clock.today();
        List<Checkin> todayCheckins = checkinRepository
                .findByGymIdAndMemberIdAndDateOrderByCheckInAtDesc(gymId, member.getId(), today);
        if (!todayCheckins.isEmpty()) {
            int minGap = gym.getSettings().getMinCheckinGapMinutes();
            Checkin latestToday = todayCheckins.getFirst();
            boolean blocked = (minGap <= 0) || (latestToday.getCheckInAt() != null
                    && Duration.between(latestToday.getCheckInAt(), clock.now()).toMinutes() < minGap);
            if (blocked) {
                throw BusinessException.conflict("ALREADY_CHECKED_IN", "Member already checked in today");
            }
        }

        // 4. Evaluate non-blocking membership status (GREEN / AMBER / RED)
        Map<String, Object> statusPayload = buildMemberStatusPayload(gym, member, today);
        String badge = (String) statusPayload.get("badge");

        // 5. Evaluate optional soft geofence (never blocks entry)
        Boolean withinGeofence = GeofenceAndQrUtils.evaluateSoftGeofence(
                latitude, longitude,
                gym.getSettings().getGeofenceLat(),
                gym.getSettings().getGeofenceLng(),
                gym.getSettings().getGeofenceRadiusMeters());

        Checkin c = new Checkin();
        c.setGymId(gymId);
        c.setMemberId(member.getId());
        c.setMemberName(member.getName());
        c.setCheckInAt(clock.now());
        c.setDate(today);
        c.setMethod(Checkin.METHOD_QR_SELF);
        c.setLatitude(latitude);
        c.setLongitude(longitude);
        c.setWithinGeofence(withinGeofence);
        c.setMembershipBadge(badge);
        Checkin saved = checkinRepository.save(c);

        statusPayload.put("badgeColor", badge);
        statusPayload.put("checkinId", saved.getId());
        statusPayload.put("checkInAt", saved.getCheckInAt());
        statusPayload.put("method", saved.getMethod());
        statusPayload.put("withinGeofence", saved.getWithinGeofence());
        statusPayload.put("deviceBound", true);
        if (issuedCookieToken != null) {
            statusPayload.put("deviceToken", issuedCookieToken);
        }

        return new SelfCheckinOutcome(statusPayload, issuedCookieToken);
    }

    public Checkin selfCheckOut(String gymSlug, String deviceToken) {
        Gym gym = resolveGymBySlugWithAttendanceEnabled(gymSlug);
        if (!gym.getSettings().isSelfCheckoutEnabled()) {
            throw BusinessException.forbidden("SELF_CHECKOUT_DISABLED", "Self check-out is not enabled for this gym");
        }
        Member member = resolveMemberFromDeviceToken(gym.getId(), deviceToken);
        if (member == null) {
            throw BusinessException.unauthorized("AUTH_REQUIRED", "Bound device required for self check-out");
        }
        Checkin open = checkinRepository
                .findByGymIdAndMemberIdAndDateAndCheckOutAtIsNull(gym.getId(), member.getId(), clock.today())
                .orElseThrow(() -> BusinessException.notFound("CHECKIN_NOT_FOUND", "No open check-in session found today"));
        open.setCheckOutAt(clock.now());
        return checkinRepository.save(open);
    }

    public PtEnrolment selfPtSessionDone(String gymSlug, String deviceToken) {
        Gym gym = resolveGymBySlugWithAttendanceEnabled(gymSlug);
        Member member = resolveMemberFromDeviceToken(gym.getId(), deviceToken);
        if (member == null) {
            throw BusinessException.unauthorized("AUTH_REQUIRED", "Bound device required to mark PT session done");
        }
        return ptService.markSessionDoneForMemberSelfService(gym.getId(), member.getId());
    }

    private Map<String, Object> buildMemberStatusPayload(Gym gym, Member member, LocalDate today) {
        String gymId = gym.getId();
        Membership latestMs = membershipRepository.findFirstByGymIdAndMemberIdOrderByExpiryDateDesc(gymId, member.getId());

        Invoice latestInvoice = invoiceRepository.findByGymIdAndMemberIdOrderByCreatedAtDesc(gymId, member.getId())
                .stream()
                .filter(inv -> !Invoice.STATUS_VOID.equals(inv.getStatus()) && !Invoice.TYPE_CREDIT_NOTE.equals(inv.getType()))
                .findFirst()
                .orElse(null);

        String payUrl = "";
        String upiDeepLink = "";
        long amountDuePaise = 0L;
        if (latestInvoice != null) {
            long bal = paymentService.balanceDue(gymId, latestInvoice.getId());
            amountDuePaise = bal > 0 ? bal : latestInvoice.getTotalPaise();
            if (latestInvoice.getPayToken() != null) {
                payUrl = "/pay/" + latestInvoice.getPayToken();
            }
            String vpa = gym.getSettings().getUpiVpa();
            String upiName = gym.getSettings().getUpiName();
            if (vpa != null && !vpa.isBlank() && amountDuePaise > 0) {
                upiDeepLink = UpiUtils.buildDeepLink(vpa, upiName, amountDuePaise, latestInvoice.getInvoiceNumber());
            }
        } else if (gym.getSettings().getUpiVpa() != null && !gym.getSettings().getUpiVpa().isBlank()) {
            long fallbackPaise = latestMs != null && latestMs.getPricePaise() > 0 ? latestMs.getPricePaise() : 100000L;
            upiDeepLink = UpiUtils.buildDeepLink(gym.getSettings().getUpiVpa(), gym.getSettings().getUpiName(), fallbackPaise, "RENEW");
        }

        String badge;
        String message;
        String expiryDateStr = latestMs != null && latestMs.getExpiryDate() != null
                ? latestMs.getExpiryDate().toString()
                : null;
        long daysLeft = -1L;

        if (latestMs != null
                && Membership.STATUS_ACTIVE.equals(latestMs.getStatus())
                && latestMs.getExpiryDate() != null
                && !latestMs.getExpiryDate().isBefore(today)) {
            daysLeft = ChronoUnit.DAYS.between(today, latestMs.getExpiryDate());
            if (daysLeft <= 7) {
                badge = "AMBER";
                message = "Expires in " + daysLeft + (daysLeft == 1 ? " day" : " days");
            } else {
                badge = "GREEN";
                message = "Active till " + expiryDateStr;
            }
        } else {
            badge = "RED";
            message = "Expired";
            if (latestMs != null && latestMs.getExpiryDate() != null) {
                daysLeft = ChronoUnit.DAYS.between(today, latestMs.getExpiryDate());
            }
        }

        List<PtEnrolment> activePt = ptEnrolmentRepository.findByGymIdAndMemberIdOrderByCreatedAtDesc(gymId, member.getId())
                .stream()
                .filter(e -> PtEnrolment.STATUS_ACTIVE.equals(e.getStatus()))
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("memberId", member.getId());
        out.put("memberName", member.getName());
        out.put("gymName", gym.getName());
        out.put("gymPhone", gym.getPhone() == null ? "" : gym.getPhone());
        out.put("badge", badge);
        out.put("statusMessage", message);
        out.put("expiryDate", expiryDateStr);
        out.put("daysLeft", daysLeft);
        out.put("payUrl", payUrl);
        out.put("payLink", !payUrl.isBlank() ? payUrl : upiDeepLink);
        out.put("upiDeepLink", upiDeepLink);
        out.put("selfCheckoutEnabled", gym.getSettings().isSelfCheckoutEnabled());
        out.put("activePtEnrolments", activePt);
        return out;
    }

    public Member resolveMemberFromDeviceToken(String gymId, String deviceToken) {
        if (deviceToken == null || !deviceToken.contains(":")) {
            return null;
        }
        String[] parts = deviceToken.split(":", 2);
        String memberId = parts[0];
        String rawSecret = parts[1];
        if (memberId.isBlank() || rawSecret.isBlank()) {
            return null;
        }
        Member member = memberRepository.findById(memberId)
                .filter(m -> gymId.equals(m.getGymId()))
                .orElse(null);
        if (member == null || member.getBoundDevices() == null) {
            return null;
        }
        String expectedHash = sha256Hex(rawSecret);
        boolean matched = member.getBoundDevices().stream()
                .anyMatch(d -> expectedHash.equals(d.getTokenHash()));
        return matched ? member : null;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
