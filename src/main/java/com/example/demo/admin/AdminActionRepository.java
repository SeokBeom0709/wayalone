package com.example.demo.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminActionRepository extends JpaRepository<AdminAction, Long> {

    List<AdminAction> findTop100ByOrderByCreatedAtDesc();

    List<AdminAction> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

    List<AdminAction> findByTargetTypeAndTargetIdOrderByIdDesc(String targetType, Long targetId);
}
