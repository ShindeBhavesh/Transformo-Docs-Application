package com.docconvert.repository;

import com.docconvert.entity.SharedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SharedFileRepository extends JpaRepository<SharedFile, Long> {
    Optional<SharedFile> findByShareToken(String shareToken);
    List<SharedFile> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<SharedFile> findByUserIdAndIsActive(Long userId, Boolean isActive);

    @Modifying
    @Query("DELETE FROM SharedFile sf WHERE sf.file.id = :fileId")
    void deleteByFileId(Long fileId);
}