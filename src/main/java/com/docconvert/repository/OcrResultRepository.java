package com.docconvert.repository;

import com.docconvert.entity.OcrResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OcrResultRepository extends JpaRepository<OcrResult, Long> {
    List<OcrResult> findByUserIdOrderByCreatedAtDesc(Long userId);
    long countByUserId(Long userId);
}
