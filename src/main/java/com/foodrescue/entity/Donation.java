package com.foodrescue.entity;

import java.time.Duration;
import java.time.Instant;

import com.foodrescue.enums.DonationStatus;
import com.foodrescue.enums.FoodCategory;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(
    name = "donations",
    indexes = {
        @Index(name = "idx_donations_status_safe_until", columnList = "status, safe_until")
    }
)
public class Donation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_id", nullable = false)
    private User donor;

    @Column(name = "food_name", nullable = false, length = 100)
    private String foodName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FoodCategory category;

    @Column(nullable = false)
    private Integer quantity;

    @Column(length = 500)
    private String description;

    @Column(name = "pickup_address", nullable = false, length = 255)
    private String pickupAddress;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "ready_from", nullable = false)
    private Instant readyFrom;

    @Column(name = "safe_until", nullable = false)
    private Instant safeUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DonationStatus status = DonationStatus.AVAILABLE;

    @Column(name = "dispatch_round", nullable = false)
    private int dispatchRound = 0;

    @Column(name = "round_ends_at")
    private Instant roundEndsAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Donation() {
    }

    public Donation(User donor, String foodName, FoodCategory category, Integer quantity,
                    String pickupAddress, Double latitude, Double longitude,
                    Instant readyFrom, Instant safeUntil) {
        this.donor = donor;
        this.foodName = foodName;
        this.category = category;
        this.quantity = quantity;
        this.pickupAddress = pickupAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.readyFrom = readyFrom;
        this.safeUntil = safeUntil;
        this.status = DonationStatus.AVAILABLE;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isExpired(Instant now) {
        return safeUntil != null && now.isAfter(safeUntil);
    }

    public long getMinutesLeft(Instant now) {
        if (safeUntil == null) {
            return 0;
        }
        return Math.max(0, Duration.between(now, safeUntil).toMinutes());
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getDonor() {
        return donor;
    }

    public void setDonor(User donor) {
        this.donor = donor;
    }

    public String getFoodName() {
        return foodName;
    }

    public void setFoodName(String foodName) {
        this.foodName = foodName;
    }

    public FoodCategory getCategory() {
        return category;
    }

    public void setCategory(FoodCategory category) {
        this.category = category;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPickupAddress() {
        return pickupAddress;
    }

    public void setPickupAddress(String pickupAddress) {
        this.pickupAddress = pickupAddress;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Instant getReadyFrom() {
        return readyFrom;
    }

    public void setReadyFrom(Instant readyFrom) {
        this.readyFrom = readyFrom;
    }

    public Instant getSafeUntil() {
        return safeUntil;
    }

    public void setSafeUntil(Instant safeUntil) {
        this.safeUntil = safeUntil;
    }

    public DonationStatus getStatus() {
        return status;
    }

    public void setStatus(DonationStatus status) {
        this.status = status;
    }

    public int getDispatchRound() {
        return dispatchRound;
    }

    public void setDispatchRound(int dispatchRound) {
        this.dispatchRound = dispatchRound;
    }

    public Instant getRoundEndsAt() {
        return roundEndsAt;
    }

    public void setRoundEndsAt(Instant roundEndsAt) {
        this.roundEndsAt = roundEndsAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
