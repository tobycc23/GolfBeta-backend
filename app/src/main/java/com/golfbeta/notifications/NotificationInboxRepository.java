package com.golfbeta.notifications;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationInboxRepository extends JpaRepository<NotificationInbox, Long> {

    @Query("""
            select n from NotificationInbox n
            where n.userId = :userId
            order by n.createdAt desc
            """)
    List<NotificationInbox> findLatest(@Param("userId") UUID userId);
}
