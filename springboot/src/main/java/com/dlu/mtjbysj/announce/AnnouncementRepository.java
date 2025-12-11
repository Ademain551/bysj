package com.dlu.mtjbysj.announce;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByPublishedTrueOrderByCreatedAtDesc();
    Optional<Announcement> findByIdAndPublishedTrue(Long id);
}