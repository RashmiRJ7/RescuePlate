package com.foodrescue.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.foodrescue.entity.Claim;
import com.foodrescue.enums.ClaimStatus;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long> {

    Optional<Claim> findByActiveDonationId(Long donationId);

    Optional<Claim> findByDonationIdAndStatus(Long donationId, ClaimStatus status);

    List<Claim> findByNgoIdOrderByClaimedAtDesc(Long ngoId);

    List<Claim> findByNgoIdAndStatusOrderByClaimedAtDesc(Long ngoId, ClaimStatus status);
}
