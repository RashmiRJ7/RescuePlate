package com.foodrescue.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.foodrescue.entity.Donation;
import com.foodrescue.enums.DonationStatus;

@Repository
public interface DonationRepository extends JpaRepository<Donation, Long> {

    List<Donation> findByDonorIdOrderByCreatedAtDesc(Long donorId);

    List<Donation> findByDonorIdAndStatusOrderByCreatedAtDesc(Long donorId, DonationStatus status);

    List<Donation> findByStatusInAndSafeUntilAfter(Collection<DonationStatus> statuses, Instant now);

    List<Donation> findByStatusInAndSafeUntilBefore(Collection<DonationStatus> statuses, Instant now);
}
