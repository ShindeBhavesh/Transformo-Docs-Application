package com.docconvert.repository;

import com.docconvert.entity.UserFile;
import com.docconvert.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserFileRepository extends JpaRepository<UserFile, Long> {
    List<UserFile> findByUserOrderByUploadedAtDesc(User user);
    List<UserFile> findByUserIdOrderByUploadedAtDesc(Long userId);
    
    @Query("SELECT SUM(f.fileSize) FROM UserFile f WHERE f.user.id = :userId")
    Long getTotalStorageByUserId(Long userId);
    
    long countByUserId(Long userId);
    
    List<UserFile> findByUserIdAndIsConverted(Long userId, Boolean isConverted);
}
