package com.example.demo;

import com.example.demo.place.PlaceService;
import com.example.demo.place.dto.PlaceResponse;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 목록 조회에서 장소 수에 비례해 쿼리가 늘어나는 문제(N+1)가 없는지, 실제 실행된 SQL 개수로 확인합니다. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:querycount;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "kakao.rest-key="
})
class PlaceQueryCountTests {

    @Autowired
    PlaceService placeService;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private long statementsFor(Runnable action) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        action.run();
        return statistics.getPrepareStatementCount();
    }

    @Test
    void 장소_목록_조회의_SQL_개수는_장소_수와_무관하다() {
        List<PlaceResponse> places = placeService.search(null, null, null, null, null);
        long statements = statementsFor(() -> placeService.search(null, null, null, null, null));

        System.out.println("[QUERY-COUNT] places=" + places.size() + " statements=" + statements);
        assertThat(places).isNotEmpty();
        assertThat(statements).as("장소 %d개 조회에 실행된 SQL 개수".formatted(places.size())).isLessThanOrEqualTo(4);
    }
}
