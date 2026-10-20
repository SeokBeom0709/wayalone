package com.example.demo.manner;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface MannerRatingRepository extends JpaRepository<MannerRating, Long> {

    boolean existsByPostIdAndRaterIdAndTargetId(Long postId, Long raterId, Long targetId);

    List<MannerRating> findByTargetIdIn(Collection<Long> targetIds);

    List<MannerRating> findByPostIdAndRaterId(Long postId, Long raterId);

    List<MannerRating> findByTargetIdOrderByCreatedAtDesc(Long targetId);

    @Query("SELECT COUNT(r) FROM MannerRating r WHERE r.attended = FALSE OR r.tags LIKE '%NO_SHOW%'")
    long countNoShows();
}
