package com.example.demo.companion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentDeletionLogRepository extends JpaRepository<ContentDeletionLog, Long> {

    List<ContentDeletionLog> findByTargetTypeAndTargetIdOrderByIdDesc(String targetType, Long targetId);

    List<ContentDeletionLog> findTop20ByActorIdOrderByIdDesc(Long actorId);
}
