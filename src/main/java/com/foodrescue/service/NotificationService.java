package com.foodrescue.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodrescue.algorithm.DistanceUtil;
import com.foodrescue.dto.NotificationResponse;
import com.foodrescue.entity.Claim;
import com.foodrescue.entity.Donation;
import com.foodrescue.entity.NgoProfile;
import com.foodrescue.entity.Notification;
import com.foodrescue.entity.User;
import com.foodrescue.enums.NotificationType;
import com.foodrescue.enums.Role;
import com.foodrescue.exception.ResourceNotFoundException;
import com.foodrescue.repository.NgoProfileRepository;
import com.foodrescue.repository.NotificationRepository;
import com.foodrescue.repository.UserRepository;

@Service
public class NotificationService {

    private final UserRepository userRepository;
    private final NgoProfileRepository ngoProfileRepository;
    private final NotificationRepository notificationRepository;

    public NotificationService(UserRepository userRepository,
                               NgoProfileRepository ngoProfileRepository,
                               NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.ngoProfileRepository = ngoProfileRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(String recipientEmail, int page, int size) {
        User recipient = userRepository.findByEmail(recipientEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + recipientEmail));

        int pageNum = page < 0 ? 0 : page;
        int pageSize = size <= 0 ? 20 : Math.min(size, 100);

        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(
                        recipient.getId(),
                        PageRequest.of(pageNum, pageSize))
                .getContent().stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String recipientEmail) {
        User recipient = userRepository.findByEmail(recipientEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + recipientEmail));
        return notificationRepository.countByRecipientIdAndReadFalse(recipient.getId());
    }

    @Transactional
    public void markRead(Long notificationId, String recipientEmail) {
        User recipient = userRepository.findByEmail(recipientEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + recipientEmail));

        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, recipient.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));
        notification.markRead();
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllRead(String recipientEmail) {
        User recipient = userRepository.findByEmail(recipientEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + recipientEmail));

        List<Notification> unread = notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(
                recipient.getId());
        for (Notification notification : unread) {
            notification.markRead();
        }
        notificationRepository.saveAll(unread);
    }

    /** Notify active, category-matching NGOs within their configured service radius. */
    @Transactional
    public void notifyNewDonation(Donation donation) {
        List<NgoProfile> profiles = ngoProfileRepository.findAll();
        if (donation.getLatitude() == null || donation.getLongitude() == null) {
            return;
        }

        for (NgoProfile profile : profiles) {
            User user = profile.getUser();
            if (user == null || !user.isActive() || user.getRole() != Role.NGO) {
                continue;
            }
            if (!profile.acceptsCategory(donation.getCategory())) {
                continue;
            }
            if (user.getLatitude() == null || user.getLongitude() == null
                    || profile.getServiceRadiusKm() == null) {
                continue;
            }

            double distanceKm = DistanceUtil.calculateDistanceKm(
                    user.getLatitude(),
                    user.getLongitude(),
                    donation.getLatitude(),
                    donation.getLongitude());
            if (distanceKm > profile.getServiceRadiusKm()) {
                continue;
            }

            String message = donation.getFoodName() + " · " + donation.getQuantity() + " meals · "
                    + donation.getPickupAddress() + " · " + Math.max(0, donation.getMinutesLeft(java.time.Instant.now()))
                    + " minutes until safe-until";
            createNotification(user, NotificationType.DONATION_NEW, "New food donation", message, donation.getId());
        }
    }

    @Transactional
    public void notifyClaimCreated(Claim claim) {
        Donation donation = claim.getDonation();
        String message = claim.getNgo().getName() + " claimed " + donation.getFoodName()
                + " (" + donation.getQuantity() + " meals).";
        createNotification(donation.getDonor(), NotificationType.CLAIM_CREATED,
                "Your donation was claimed", message, donation.getId());
    }

    @Transactional
    public void notifyClaimCancelled(Claim claim) {
        Donation donation = claim.getDonation();
        String reason = claim.getCancelReason() == null || claim.getCancelReason().isBlank()
                ? "No reason provided" : claim.getCancelReason();
        String message = claim.getNgo().getName() + " cancelled the claim for "
                + donation.getFoodName() + ". Reason: " + reason;
        createNotification(donation.getDonor(), NotificationType.CLAIM_CANCELLED,
                "A donation claim was cancelled", message, donation.getId());
        createNotification(claim.getNgo(), NotificationType.CLAIM_CANCELLED,
                "Your claim was cancelled", "Your claim for " + donation.getFoodName()
                        + " was cancelled. " + reason, donation.getId());
    }

    @Transactional
    public void notifyClaimPickedUp(Claim claim) {
        Donation donation = claim.getDonation();
        createNotification(donation.getDonor(), NotificationType.CLAIM_PICKED_UP,
                "Donation picked up", claim.getNgo().getName() + " picked up " + donation.getFoodName() + ".",
                donation.getId());
    }

    @Transactional
    public void notifyClaimDelivered(Claim claim) {
        Donation donation = claim.getDonation();
        String message = donation.getFoodName() + " was delivered by " + claim.getNgo().getName() + ".";
        createNotification(donation.getDonor(), NotificationType.CLAIM_DELIVERED,
                "Donation delivered", message, donation.getId());
        createNotification(claim.getNgo(), NotificationType.CLAIM_DELIVERED,
                "Delivery completed", message, donation.getId());
    }

    private void createNotification(User recipient, NotificationType type, String title, String message, Long donationId) {
        Notification notification = new Notification(recipient, type, title, message, donationId);
        notificationRepository.save(notification);
    }
}
