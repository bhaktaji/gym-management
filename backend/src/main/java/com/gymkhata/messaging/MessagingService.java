package com.gymkhata.messaging;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.billing.repo.InvoiceRepository;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.domain.QuietHours;
import com.gymkhata.member.model.Member;
import com.gymkhata.member.repo.MemberRepository;
import com.gymkhata.membership.model.Membership;
import com.gymkhata.membership.repo.MembershipRepository;
import com.gymkhata.messaging.model.OutboxMessage;
import com.gymkhata.messaging.repo.OutboxRepository;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MessagingService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final int REMINDER_WINDOW_DAYS = 7;

    private final OutboxRepository outboxRepository;
    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final InvoiceRepository invoiceRepository;
    private final GymRepository gymRepository;
    private final MessagingProvider messagingProvider;
    private final Clock clock;

    public MessagingService(OutboxRepository outboxRepository,
                            MemberRepository memberRepository,
                            MembershipRepository membershipRepository,
                            InvoiceRepository invoiceRepository,
                            GymRepository gymRepository,
                            MessagingProvider messagingProvider,
                            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.memberRepository = memberRepository;
        this.membershipRepository = membershipRepository;
        this.invoiceRepository = invoiceRepository;
        this.gymRepository = gymRepository;
        this.messagingProvider = messagingProvider;
        this.clock = clock;
    }

    /**
     * Generates renewal-reminder outbox entries for members whose active membership
     * expires within the reminder window, who opted in to WhatsApp, and who don't
     * already have an entry today. Quiet hours (21:00-08:00 IST) are respected.
     */
    public List<OutboxMessage> generateRenewalReminders() {
        String gymId = SecurityUtils.currentGymId();
        Gym gym = gymRepository.findById(gymId).orElse(null);
        LocalDate today = clock.today();
        LocalTime now = clock.nowIst();
        boolean quiet = gym != null && gym.getSettings() != null
                && gym.getSettings().isQuietHoursEnabled() && QuietHours.isQuiet(now);

        List<OutboxMessage> created = new ArrayList<>();
        for (Membership m : membershipRepository.findByGymIdAndStatus(gymId, Membership.STATUS_ACTIVE)) {
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, m.getExpiryDate());
            if (daysLeft < 0 || daysLeft > REMINDER_WINDOW_DAYS) {
                continue;
            }
            Member member = memberRepository.findById(m.getMemberId()).orElse(null);
            if (member == null || !member.isWhatsappOptIn()) {
                continue;
            }
            if (outboxRepository.existsByGymIdAndMemberIdAndTypeAndDate(
                    gymId, member.getId(), OutboxMessage.TYPE_RENEWAL_REMINDER, today)) {
                continue;
            }
            Invoice latest = invoiceRepository
                    .findByGymIdAndMemberIdOrderByCreatedAtDesc(gymId, member.getId())
                    .stream().findFirst().orElse(null);

            Map<String, String> vars = Map.of(
                    "name", member.getName(),
                    "planName", m.getPlanName() == null ? "" : m.getPlanName(),
                    "expiryDate", m.getExpiryDate().format(DATE_FMT),
                    "amountDue", latest != null ? String.valueOf(latest.getTotalPaise()) : "0",
                    "payLink", latest != null && latest.getPayToken() != null
                            ? "/pay/" + latest.getPayToken() : "",
                    "gymName", gym != null ? gym.getName() : "");

            String text = renderTemplate(gym, "renewal", "EN", vars);
            String waLink = messagingProvider.buildWaLink(member.getPhone(), text);

            OutboxMessage msg = new OutboxMessage();
            msg.setGymId(gymId);
            msg.setMemberId(member.getId());
            msg.setMemberName(member.getName());
            msg.setPhone(member.getPhone());
            msg.setType(OutboxMessage.TYPE_RENEWAL_REMINDER);
            msg.setChannel(OutboxMessage.CHANNEL_WHATSAPP);
            msg.setTemplateKey("renewal");
            msg.setLanguage("EN");
            msg.setVariables(vars);
            msg.setWaLink(waLink);
            msg.setStatus(quiet ? OutboxMessage.STATUS_QUIET_HOURS : OutboxMessage.STATUS_PENDING);
            msg.setDate(today);
            msg.setCreatedAt(clock.now());
            created.add(outboxRepository.save(msg));
        }
        return created;
    }

    private String renderTemplate(Gym gym, String key, String lang, Map<String, String> vars) {
        Map<String, String> templates = "HI".equals(lang)
                ? (gym != null && gym.getSettings() != null ? gym.getSettings().getTemplatesHi() : Map.of())
                : (gym != null && gym.getSettings() != null ? gym.getSettings().getTemplatesEn() : Map.of());
        String template = templates.get(key);
        if (template == null || template.isBlank()) {
            template = "Hi {name}, your {planName} membership at {gymName} expires on {expiryDate}. "
                    + "Renew today: {payLink}";
        }
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    public List<OutboxMessage> todayOutbox() {
        return outboxRepository.findByGymIdAndDateOrderByCreatedAtAsc(SecurityUtils.currentGymId(), clock.today());
    }

    public OutboxMessage markSent(String id) {
        OutboxMessage msg = get(id);
        if (OutboxMessage.STATUS_SENT.equals(msg.getStatus())) {
            throw BusinessException.badRequest("ALREADY_SENT", "Message already marked sent");
        }
        msg.setStatus(OutboxMessage.STATUS_SENT);
        msg.setSentAt(clock.now());
        return outboxRepository.save(msg);
    }

    public OutboxMessage get(String id) {
        String gymId = SecurityUtils.currentGymId();
        return outboxRepository.findById(id)
                .filter(m -> gymId.equals(m.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("MESSAGE_NOT_FOUND", "Outbox message not found"));
    }
}
