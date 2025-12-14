package bt.conference.service;

import bt.conference.dto.SendMessage;
import bt.conference.entity.Conversation.LastMessage;
import bt.conference.entity.Message;
import bt.conference.entity.UserCache;
import bt.conference.repository.MessageRepository;
import bt.conference.repository.UserCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserCacheRepository userCacheRepository;
    private final ConversationService conversationService;

    /**
     * Send a message
     */
    public Message sendMessage(String senderId, SendMessage dto) {
        // Get sender info
        UserCache senderUser = userCacheRepository.findByUserId(senderId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Create message
        Message message = new Message();
        message.setConversationId(dto.getConversationId());
        message.setBody(dto.getContent());
        message.setType(dto.getMessageType() != null ? dto.getMessageType() : "text");
        message.setStatus(2);
        message.setCreatedAt(Instant.now());
        message.setSenderId(senderUser.getUserId());
        message.setAvatar(senderUser.getAvatar());

        Message savedMessage = messageRepository.save(message);

        // Update conversation's last message
        LastMessage lastMessage = new LastMessage();
        lastMessage.setMessageId(savedMessage.getId());
        lastMessage.setContent(truncate(dto.getContent(), 100));
        lastMessage.setSenderId(senderId);
        lastMessage.setSenderName(senderUser.getFirstName() + " " + senderUser.getLastName());
        lastMessage.setSentAt(Instant.now());

        conversationService.updateLastMessage(dto.getConversationId(), lastMessage);

        return savedMessage;
    }

    private String truncate(String content, int maxLength) {
        if (content == null) return "";
        return content.length() <= maxLength ? content : content.substring(0, maxLength) + "...";
    }
}