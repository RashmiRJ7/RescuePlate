package com.foodrescue.entity;

import java.time.Instant;

import com.foodrescue.enums.ClaimStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "claims",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_claims_active_donation", columnNames = {"active_donation_id"})
    },
    indexes = {
        @Index(name = "idx_claims_ngo_status", columnList = "ngo_id, status")
    }
)
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donation_id", nullable = false)
    private Donation donation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ngo_id", nullable = false)
    private User ngo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClaimStatus status = ClaimStatus.ACTIVE;

    /**
     * Nullable unique column ensuring at most one ACTIVE, PICKED_UP, or COMPLETED
     * claim exists per donation. When CANCELLED or EXPIRED, this is set to NULL.
     */
    @Column(name = "active_donation_id")
    private Long activeDonationId;

    @Column(name = "claimed_at", nullable = false, updatable = false)
    private Instant claimedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    public Claim() {
    }

    public Claim(Donation donation, User ngo) {
        this.donation = donation;
        this.ngo = ngo;
        this.status = ClaimStatus.ACTIVE;
        this.activeDonationId = donation != null ? donation.getId() : null;
    }

    @PrePersist
    protected void onCreate() {
        if (this.claimedAt == null) {
            this.claimedAt = Instant.now();
        }
        if (this.status == ClaimStatus.ACTIVE && this.activeDonationId == null && this.donation != null) {
            this.activeDonationId = this.donation.getId();
        }
    }

    // Helper methods for state management

    public void markPickedUp(Instant at) {
        this.status = ClaimStatus.PICKED_UP;
        this.pickedUpAt = at;
    }

    public void markDelivered(Instant at) {
        this.status = ClaimStatus.COMPLETED;
        this.deliveredAt = at;
    }

    public void markCancelled(String reason, Instant at) {
        this.status = ClaimStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = at;
        this.activeDonationId = null; // Release the unique constraint
    }

    public void markExpired() {
        this.status = ClaimStatus.EXPIRED;
        this.activeDonationId = null; // Release the unique constraint
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Donation getDonation() {
        return donation;
    }

    public void setDonation(Donation donation) {
        this.donation = donation;
    }

    public User getNgo() {
        return ngo;
    }

    public void setNgo(User ngo) {
        this.ngo = ngo;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public void setStatus(ClaimStatus status) {
        this.status = status;
    }

    public Long getActiveDonationId() {
        return activeDonationId;
    }

    public void setActiveDonationId(Long activeDonationId) {
        this.activeDonationId = activeDonationId;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public Instant getPickedUpAt() {
        return pickedUpAt;
    }

    public void setPickedUpAt(Instant pickedUpAt) {
        this.pickedUpAt = pickedUpAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }
}
