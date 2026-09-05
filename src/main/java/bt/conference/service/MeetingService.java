package bt.conference.service;

import bt.conference.dto.PagedResponse;
import bt.conference.entity.Conversation;
import bt.conference.entity.MeetingDetail;
import bt.conference.entity.UserDetail;
import bt.conference.entity.Users;
import bt.conference.model.GuestMeeting;
import bt.conference.model.TokenStatus;
import bt.conference.repository.ConversationRepository;
import bt.conference.repository.GlobalSearchRepository;
import bt.conference.repository.MeetingDetailRepository;
import bt.conference.repository.UsersRepository;
import bt.conference.serviceinterface.IMeetingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fierhub.database.service.DbManager;
import com.fierhub.database.utils.DbParameters;
import com.fierhub.database.utils.ProcedureManager;
import com.fierhub.model.UserSession;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MeetingService implements IMeetingService {
    @Autowired
    UserSession userSession;
    @Autowired
    ProcedureManager dbProcedureManager;
    @Autowired
    DbManager dbManager;
    @Autowired
    MeetingDetailRepository meetingDetailRepository;
    @Autowired
    ConversationService conversationService;
    @Autowired
    DbResultMapper resultMapper;
    @Autowired
    ConversationRepository conversationRepository;
    @Autowired
    UsersRepository usersRepository;

    private static final Logger logger = LoggerFactory.getLogger(GlobalSearchRepository.class);
    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789abcdefghijkmnopqrstuvwxyz";
    private static final SecureRandom random = new SecureRandom();

    public Map<String, Object> generateMeetingService(MeetingDetail meetingDetail) throws Exception {
        if (meetingDetail.getTitle() == null || meetingDetail.getTitle().isEmpty())
            throw new Exception("Invalid meeting title");

        if (meetingDetail.getDurationInSecond() <= 0)
            throw new Exception("Invalid meeting duration");

        var convertedDate = UtilService.toUtc(meetingDetail.getStartDate());
        meetingDetail.setStartDate(convertedDate);

        if (meetingDetail.getStartTime() != null && !meetingDetail.getStartTime().trim().isEmpty()) {
            meetingDetail.setStartTime(meetingDetail.getStartTime().trim());
        } else if (meetingDetail.getStartDate() != null) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
            meetingDetail.setStartTime(timeFormat.format(meetingDetail.getStartDate()));
        }

        if (meetingDetail.getEndDate() != null) {
            meetingDetail.setEndDate(UtilService.toUtc(meetingDetail.getEndDate()));
        } else if (meetingDetail.getStartDate() != null && meetingDetail.getDurationInSecond() > 0) {
            meetingDetail.setEndDate(new Date(meetingDetail.getStartDate().getTime() + (long) meetingDetail.getDurationInSecond() * 1000L));
        }

        if (meetingDetail.getEndTime() != null && !meetingDetail.getEndTime().trim().isEmpty()) {
            meetingDetail.setEndTime(meetingDetail.getEndTime().trim());
        } else if (meetingDetail.getEndDate() != null) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
            meetingDetail.setEndTime(timeFormat.format(meetingDetail.getEndDate()));
        }

        meetingDetail.setHasQuickMeeting(false);

        // Create corresponding Conversation in MongoDB so Go WebSocket / Rooms can load it
        var conversation = conversationService.createMeetingConversationService(userSession.getUserId(),
                meetingDetail.getTitle(), meetingDetail.getParticipantsId());
        meetingDetail.getParticipantsId().add(userSession.getUserId());
        meetingDetail.setParticipants(resultMapper.writeValueAsString(meetingDetail.getParticipantsId()));
        meetingDetail.setConversationId(conversation.getId());

        addMeetingDetail(meetingDetail);

        return getAllMeetingByOrganizerService();
    }

    private void addMeetingDetail(MeetingDetail meetingDetail) throws Exception {
        var userId = UtilService.extractEmployeeId(userSession.getUserId(), userSession.getClaimsValue().get("code"));
        var user = dbManager.getById(userId, UserDetail.class);
        if (user == null)
            throw new Exception("User not found");

        var fullName = user.getFirstName() + (user.getLastName() != null && !user.getLastName().isEmpty() ? " " + user.getLastName() : "");
        meetingDetail.setMeetingId(ManageMeetingService.generateToken(userId, fullName));
        meetingDetail.setMeetingPassword(generatePassword(6));
        meetingDetail.setOrganizedBy(userId);

        dbManager.save(meetingDetail);
    }

    public PagedResponse<Conversation> getRecentMeetingsService() throws Exception {
        var code = userSession.getClaimsValue().get("code");
        if (code == null || code.isEmpty())
            throw new Exception("Invalid user session");

        int userId = Integer.parseInt(userSession.getUserId().replace(code, ""));
        return conversationService.searchConversationsRecentGroup(String.valueOf(userId), 1, 5);
    }

    public Map<String, Object> generateQuickMeetingService(MeetingDetail meetingDetail) throws Exception {
        meetingDetail.setDurationInSecond(36000);
        java.util.Date utilDate = new java.util.Date();
        var date = new java.sql.Timestamp(utilDate.getTime());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        meetingDetail.setStartDate(date);
        meetingDetail.setStartTime(timeFormat.format(date));
        var endDate = new java.sql.Timestamp(utilDate.getTime() + 36000L * 1000L);
        meetingDetail.setEndDate(endDate);
        meetingDetail.setEndTime(timeFormat.format(endDate));
        meetingDetail.setHasQuickMeeting(true);

        var conversation = conversationService.createMeetingConversationService(userSession.getUserId(), meetingDetail.getTitle(), List.of());
        meetingDetail.getParticipantsId().add(userSession.getUserId());
        meetingDetail.setParticipants(resultMapper.writeValueAsString(meetingDetail.getParticipantsId()));
        meetingDetail.setConversationId(conversation.getId());

        addMeetingDetail(meetingDetail);
        return getAllMeetingByOrganizerService();
    }

    public Conversation validateMeetingService(String access_token) throws Exception {
        if (access_token == null || access_token.isEmpty())
            throw new Exception("Invalid access token used");

        try {
            access_token = java.net.URLDecoder.decode(access_token, StandardCharsets.UTF_8);
            Date nowUtc = new Date();
            GuestMeeting guestMeeting = dbProcedureManager.execute("sp_get_guest_access",
                    List.of(
                            new DbParameters("p_access_token", access_token, Types.VARCHAR)
                    ), GuestMeeting.class
            );

            GuestMeeting validToken = validateGuestToken(guestMeeting, nowUtc);

            var conv = conversationRepository.findById(guestMeeting.getMeetingId());

            if (conv.isEmpty())
                throw new Exception("Meeting detail not found");

            return conv.get();
        } catch (Exception ex) {
            throw new Exception("Fail to get record from access token");
        }
    }

    private GuestMeeting validateGuestToken(
            GuestMeeting token,
            Date requestTimeUtc
    ) throws Exception {

        // 1️⃣ Token existence check
        if (token == null) {
            throw new Exception("Invalid or expired access token");
        }

        // 2️⃣ Token status check
        if (token.getStatus() != TokenStatus.ACTIVE) {
            throw new Exception("Access token is not active");
        }

        // 3️⃣ Time window validation (UTC)
        if (requestTimeUtc.before(token.getValidFrom())) {
            throw new Exception("Access token is not valid yet");
        }

        if (requestTimeUtc.after(token.getValidUntil())) {
            throw new Exception("Access token has expired");
        }

        // 4️⃣ Usage count validation
        if (token.getMaxUsage() != null) {
            int used = token.getUsageCount() == null ? 0 : token.getUsageCount();

            if (used >= token.getMaxUsage()) {
                throw new Exception("Access token usage limit exceeded");
            }
        }

        // 5️⃣ Defensive checks (optional but recommended)
        if (token.getMeetingId() == null) {
            throw new Exception("Invalid token mapping");
        }

        // 6️⃣ Token is valid → allow access
        return token;
    }

    private void markTokenUsed(GuestMeeting token) {
        token.setUsageCount(
                token.getUsageCount() == null ? 1 : token.getUsageCount() + 1
        );

        if (token.getMaxUsage() != null &&
                token.getUsageCount() >= token.getMaxUsage()) {
            token.setStatus(TokenStatus.EXPIRED);
        }

        // guestTokenRepository.save(token);
    }

    private String generatePassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    public MeetingDetail validateMeetingIdPassCodeService(MeetingDetail meetingDetail) throws Exception {
        if (meetingDetail.getMeetingPassword() == null || meetingDetail.getMeetingPassword().isEmpty())
            throw new Exception("Invalid meeting passcode");

        if (meetingDetail.getMeetingId() == null || meetingDetail.getMeetingId().isEmpty())
            throw new Exception("Invalid meeting id passed");

        var existingMeeting = dbManager.queryRaw("select * from meeting_detail where meetingId = '"
                + meetingDetail.getMeetingId() + "' and meetingPassword = '"
                + meetingDetail.getMeetingPassword() + "'" , MeetingDetail.class);
        if (existingMeeting == null)
            throw new Exception("Meeting detail not found");

        if (existingMeeting.getConversationId() == null || existingMeeting.getConversationId().isEmpty())
            throw new Exception("Conversation detail not found");

        var user = dbManager.getById(String.valueOf(existingMeeting.getOrganizedBy()), UserDetail.class);
        if (user == null)
            throw new Exception("Admin detail not found");

        existingMeeting.setOrganizerName(user.getFirstName() + (user.getLastName() != null && !user.getLastName().isEmpty() ? " " + user.getLastName() : ""));
        return existingMeeting;
    }

    public Map<String, Object> getAllMeetingByOrganizerService() throws Exception {
        long currentUserId;
        try {
            currentUserId = Long.parseLong(userSession.getUserId());
        } catch (Exception e) {
            var code = userSession.getClaimsValue().get("code");
            currentUserId = UtilService.extractEmployeeId(userSession.getUserId(), code);
        }

        try {
            var result = dbProcedureManager.executeProcedure("sp_dashboard_get_by_userid",
                    List.of(new DbParameters("_userid", currentUserId, Types.BIGINT))
            );
            Map<String, Object> data = new HashMap<>();
            data.put("QuickMeetings", resultMapper.mapListResult(result, "#result-set-1", MeetingDetail.class));
            data.put("ScheduledMeetings", resultMapper.mapListResult(result, "#result-set-2", MeetingDetail.class));
            return data;
        } catch (Exception e) {
            logger.error("Error fetching meetings for user: " + currentUserId, e);
            throw new Exception("Error fetching meetings for user: " + currentUserId, e);
        }
    }

    public List<MeetingDetail> getAllScheduleMeetingByOrganizerService() throws Exception {
        var result = dbProcedureManager.executeProcedure("sp_get_meetings_by_participant",
                List.of(new DbParameters("_userid", userSession.getUserId(), Types.VARCHAR))
        );
        return resultMapper.mapListResult(result, "#result-set-1", MeetingDetail.class);
    }

    public MeetingDetail validateMeetingByIdService(String meetingId) throws Exception {
        if (meetingId == null || meetingId.isEmpty() || "undefined".equals(meetingId) || "undefined_undefined".equals(meetingId))
            throw new Exception("Invalid meeting id passed");

        var targetMeetingId = "";
        long targetDetailId = 0L;
        int lastUnderscore = meetingId.lastIndexOf('_');

        if (lastUnderscore != -1) {
            targetMeetingId = meetingId.substring(0, lastUnderscore);
            targetDetailId = Long.parseLong(meetingId.substring(lastUnderscore + 1));
        }
        var meetingDetail = dbManager.queryRaw("select * from meeting_detail where meetingDetailId = " + targetDetailId , MeetingDetail.class);
        if (meetingDetail == null || !meetingDetail.getMeetingId().equals(targetMeetingId))
            throw new Exception("invalid meeting link");

        if (meetingDetail.getConversationId() == null || meetingDetail.getConversationId().isEmpty())
            throw new Exception("Conversation detail not found");

        var user = dbManager.getById(String.valueOf(meetingDetail.getOrganizedBy()), UserDetail.class);
        if (user == null)
            throw new Exception("Admin detail not found");

        meetingDetail.setOrganizerName(user.getFirstName() + (user.getLastName() != null && !user.getLastName().isEmpty() ? " " + user.getLastName() : ""));
        return meetingDetail;
    }

    public MeetingDetail getMeetingDetailService(Long meetingDetailId, String meetingId) throws Exception {
        MeetingDetail meetingDetail = null;

        if (meetingDetailId != null && meetingDetailId > 0) {
            meetingDetail = meetingDetailRepository.findById(meetingDetailId).orElse(null);
        }

        if (meetingDetail == null && meetingId != null && !meetingId.trim().isEmpty()) {
            var targetMeetingId = meetingId.trim();
            long targetDetailId = 0L;
            int lastUnderscore = targetMeetingId.lastIndexOf('_');
            if (lastUnderscore != -1) {
                try {
                    targetDetailId = Long.parseLong(targetMeetingId.substring(lastUnderscore + 1));
                    targetMeetingId = targetMeetingId.substring(0, lastUnderscore);
                    meetingDetail = meetingDetailRepository.findById(targetDetailId).orElse(null);
                } catch (NumberFormatException ignored) {}
            }
            if (meetingDetail == null) {
                meetingDetail = meetingDetailRepository.findByMeetingId(targetMeetingId).orElse(null);
            }
        }

        if (meetingDetail == null) {
            throw new Exception("Meeting detail not found");
        }

        if (meetingDetail.getOrganizedBy() > 0) {
            var user = dbManager.getById(String.valueOf(meetingDetail.getOrganizedBy()), UserDetail.class);
            if (user != null) {
                meetingDetail.setOrganizerName(user.getFirstName() + (user.getLastName() != null && !user.getLastName().isEmpty() ? " " + user.getLastName() : ""));
            }
        }

        meetingDetail.setParticipantCount(0);
        if (meetingDetail.getParticipants() != null && !meetingDetail.getParticipants().trim().isEmpty()) {
            try {
                List<String> pList = resultMapper.readValue(meetingDetail.getParticipants(), new TypeReference<List<String>>() {});
                meetingDetail.setParticipantsId(pList);
                meetingDetail.setParticipantCount(pList.size());
            } catch (Exception ignored) {}
        }

        populateParticipantsDetail(meetingDetail);

        return meetingDetail;
    }

    public MeetingDetail updateMeetingDetailService(MeetingDetail meetingDetail) throws Exception {
        if (meetingDetail == null) {
            throw new Exception("Meeting detail request body cannot be null");
        }

        MeetingDetail existing = null;
        if (meetingDetail.getMeetingDetailId() > 0) {
            existing = meetingDetailRepository.findById(meetingDetail.getMeetingDetailId()).orElse(null);
        }
        if (existing == null && meetingDetail.getMeetingId() != null && !meetingDetail.getMeetingId().trim().isEmpty()) {
            existing = meetingDetailRepository.findByMeetingId(meetingDetail.getMeetingId().trim()).orElse(null);
        }

        if (existing == null) {
            throw new Exception("Meeting detail not found for update");
        }

        // 1. Update Title
        if (meetingDetail.getTitle() != null && !meetingDetail.getTitle().trim().isEmpty()) {
            existing.setTitle(meetingDetail.getTitle().trim());
        }

        // 2. Update Agenda
        if (meetingDetail.getAgenda() != null) {
            existing.setAgenda(meetingDetail.getAgenda().trim());
        }

        // 3. Update Start Date, Start Time, End Date, End Time & Duration
        if (meetingDetail.getStartDate() != null) {
            existing.setStartDate(UtilService.toUtc(meetingDetail.getStartDate()));
        }
        if (meetingDetail.getStartTime() != null && !meetingDetail.getStartTime().trim().isEmpty()) {
            existing.setStartTime(meetingDetail.getStartTime().trim());
        } else if (meetingDetail.getStartDate() != null && (existing.getStartTime() == null || existing.getStartTime().trim().isEmpty())) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
            existing.setStartTime(timeFormat.format(existing.getStartDate()));
        }

        if (meetingDetail.getDurationInSecond() > 0) {
            existing.setDurationInSecond(meetingDetail.getDurationInSecond());
        }

        if (meetingDetail.getEndDate() != null) {
            existing.setEndDate(UtilService.toUtc(meetingDetail.getEndDate()));
        } else if (meetingDetail.getStartDate() != null && existing.getDurationInSecond() > 0) {
            existing.setEndDate(new Date(existing.getStartDate().getTime() + (long) existing.getDurationInSecond() * 1000L));
        }

        if (meetingDetail.getEndTime() != null && !meetingDetail.getEndTime().trim().isEmpty()) {
            existing.setEndTime(meetingDetail.getEndTime().trim());
        } else if (meetingDetail.getEndDate() != null) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
            existing.setEndTime(timeFormat.format(existing.getEndDate()));
        } else if (existing.getEndDate() != null && meetingDetail.getStartDate() != null) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
            existing.setEndTime(timeFormat.format(existing.getEndDate()));
        }

        // 4. Update Flags
        existing.setAllDay(meetingDetail.isAllDay());
        if (meetingDetail.getRepeatType() >= 0) {
            existing.setRepeatType(meetingDetail.getRepeatType());
        }
        existing.setHasQuickMeeting(meetingDetail.isHasQuickMeeting());

        // 5. Update Password if specified
        if (meetingDetail.getMeetingPassword() != null && !meetingDetail.getMeetingPassword().trim().isEmpty()) {
            existing.setMeetingPassword(meetingDetail.getMeetingPassword().trim());
        }

        // 6. Update Participants if provided
        if (meetingDetail.getParticipantsId() != null && !meetingDetail.getParticipantsId().isEmpty()) {
            existing.setParticipantsId(meetingDetail.getParticipantsId());
            existing.setParticipants(resultMapper.writeValueAsString(meetingDetail.getParticipantsId()));
        } else if (meetingDetail.getParticipants() != null && !meetingDetail.getParticipants().trim().isEmpty()) {
            existing.setParticipants(meetingDetail.getParticipants().trim());
        }

        // 7. Persist to MySQL via repository
        meetingDetailRepository.save(existing);

        // 8. Synchronize with MongoDB Conversation if conversationId exists
        if (existing.getConversationId() != null && !existing.getConversationId().trim().isEmpty()) {
            try {
                var optConv = conversationRepository.findById(existing.getConversationId());
                if (optConv.isPresent()) {
                    Conversation conversation = optConv.get();
                    if (existing.getTitle() != null && !existing.getTitle().trim().isEmpty()) {
                        conversation.setTitle(existing.getTitle());
                    }
                    if (existing.getAgenda() != null && !existing.getAgenda().trim().isEmpty()) {
                        conversation.setDescription(existing.getAgenda());
                    }
                    conversation.setLastMessageAt(Instant.now());
                    conversationRepository.save(conversation);
                }
            } catch (Exception ignored) {}
        }

        // Attach organizer name
        if (existing.getOrganizedBy() > 0) {
            var user = dbManager.getById(String.valueOf(existing.getOrganizedBy()), UserDetail.class);
            if (user != null) {
                existing.setOrganizerName(user.getFirstName() + (user.getLastName() != null && !user.getLastName().isEmpty() ? " " + user.getLastName() : ""));
            }
        }

        populateParticipantsDetail(existing);

        return existing;
    }

    private void populateParticipantsDetail(MeetingDetail meetingDetail) {
        if (meetingDetail.getParticipantsId() == null || meetingDetail.getParticipantsId().isEmpty()) {
            meetingDetail.setParticipantsDetail(new ArrayList<>());
            return;
        }

        List<String> top10Ids = meetingDetail.getParticipantsId().stream()
                .filter(id -> id != null && !id.trim().isEmpty())
                .distinct()
                .limit(10)
                .toList();

        if (top10Ids.isEmpty()) {
            meetingDetail.setParticipantsDetail(new ArrayList<>());
            return;
        }

        List<Users> mongoUsers = usersRepository.findAllById(top10Ids);
        Map<String, Users> userMap = mongoUsers.stream()
                .collect(Collectors.toMap(Users::getId, u -> u, (u1, u2) -> u1));

        List<Conversation.Participant> details = new ArrayList<>();
        for (String id : top10Ids) {
            Users u = userMap.get(id);
            if (u != null) {
                details.add(Conversation.Participant.builder()
                        .userId(u.getId())
                        .firstName(u.getFirstName())
                        .lastName(u.getLastName())
                        .email(u.getEmail())
                        .avatar(u.getAvatarUrl())
                        .status(u.getStatus())
                        .role("participant")
                        .build());
            } else {
                try {
                    long numericId = Long.parseLong(id.replaceAll("\\D+", ""));
                    String formattedId = String.format("BOT%05d", numericId);
                    Optional<Users> optU = usersRepository.findById(formattedId);
                    if (optU.isPresent()) {
                        Users found = optU.get();
                        details.add(Conversation.Participant.builder()
                                .userId(found.getId())
                                .firstName(found.getFirstName())
                                .lastName(found.getLastName())
                                .email(found.getEmail())
                                .avatar(found.getAvatarUrl())
                                .status(found.getStatus())
                                .role("participant")
                                .build());
                        continue;
                    }

                    UserDetail userDetail = dbManager.getById(numericId, UserDetail.class);
                    if (userDetail != null) {
                        details.add(Conversation.Participant.builder()
                                .userId(String.valueOf(userDetail.getUserId()))
                                .firstName(userDetail.getFirstName())
                                .lastName(userDetail.getLastName())
                                .email(userDetail.getEmail())
                                .avatar(userDetail.getImageURL())
                                .status(userDetail.isActive() ? "ACTIVE" : "INACTIVE")
                                .role("participant")
                                .build());
                    }
                } catch (Exception ignored) {}
            }
        }
        meetingDetail.setParticipantsDetail(details);
    }
}
