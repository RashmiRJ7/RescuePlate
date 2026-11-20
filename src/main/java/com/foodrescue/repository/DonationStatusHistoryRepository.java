package com.foodrescue.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.foodrescue.entity.DonationStatusHistory;

@Repository
public interface DonationStatusHistoryRepository extends JpaRepository<DonationStatusHistory, Long> {

    List<DonationStatusHistory> findByDonationIdOrderByChangedAtAsc(Long donationId);
}
