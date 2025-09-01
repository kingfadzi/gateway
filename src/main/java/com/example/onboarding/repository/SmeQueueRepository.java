package com.example.onboarding.repository;

import com.example.onboarding.model.SmeQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SmeQueueRepository extends JpaRepository<SmeQueue, String> {
    
    List<SmeQueue> findBySmeId(String smeId);
    
    List<SmeQueue> findByRiskId(String riskId);
    
    List<SmeQueue> findByQueueStatus(String queueStatus);
    
    @Query("SELECT sq FROM SmeQueue sq WHERE sq.smeId = :smeId AND sq.queueStatus = :status ORDER BY sq.assignedAt ASC")
    List<SmeQueue> findBySmeIdAndStatusOrderByAssignedAt(@Param("smeId") String smeId, @Param("status") String status);
    
    @Query("SELECT sq FROM SmeQueue sq WHERE sq.smeId = :smeId AND sq.queueStatus = 'PENDING' ORDER BY sq.assignedAt ASC")
    List<SmeQueue> findPendingBySmeId(@Param("smeId") String smeId);
    
    Optional<SmeQueue> findByRiskIdAndSmeId(String riskId, String smeId);
    
    @Query("SELECT COUNT(sq) FROM SmeQueue sq WHERE sq.smeId = :smeId AND sq.queueStatus = 'PENDING'")
    long countPendingBySmeId(@Param("smeId") String smeId);
}