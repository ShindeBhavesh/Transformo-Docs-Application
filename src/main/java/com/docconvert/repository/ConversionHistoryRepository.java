package com.docconvert.repository;

import com.docconvert.entity.ConversionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ConversionHistoryRepository extends JpaRepository<ConversionHistory, Long> {
    List<ConversionHistory> findByUserIdOrderByStartedAtDesc(Long userId);

    long countByUserId(Long userId);

    @Query("SELECT COUNT(c) FROM ConversionHistory c WHERE c.user.id = :userId AND c.conversionType = :type")
    long countByUserIdAndConversionType(Long userId, String type);

    List<ConversionHistory> findByUserIdAndStatus(Long userId, ConversionHistory.ConversionStatus status);

    @Modifying
    @Query("DELETE FROM ConversionHistory ch WHERE ch.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
