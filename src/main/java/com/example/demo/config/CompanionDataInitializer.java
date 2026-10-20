package com.example.demo.config;

import com.example.demo.companion.CompanionApplication;
import com.example.demo.companion.CompanionApplicationRepository;
import com.example.demo.companion.CompanionPost;
import com.example.demo.companion.CompanionPostRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class CompanionDataInitializer implements CommandLineRunner {

    private final CompanionPostRepository postRepository;
    private final CompanionApplicationRepository applicationRepository;

    public CompanionDataInitializer(CompanionPostRepository postRepository,
                                    CompanionApplicationRepository applicationRepository) {
        this.postRepository = postRepository;
        this.applicationRepository = applicationRepository;
    }

    @Override
    public void run(String... args) {
        if (postRepository.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now();

        CompanionPost car = post("제주 3박 4일 렌터카 같이 타실 분", "제주", today.plusDays(10), today.plusDays(13), 2,
                "서연", "혼자 렌터카 빌리기엔 부담돼서 같이 쓰실 분을 찾아요. 동쪽 위주로 카페와 맛집을 다니고, 저녁은 각자 먹거나 혼밥 가능한 곳으로 가려고 해요.",
                "렌터카·맛집");
        post("제주 동쪽 올레길 같이 걸어요", "제주", today.plusDays(20), today.plusDays(22), 1,
                "민호", "올레 3코스부터 걸을 예정이고 하루 15km 정도 생각 중입니다. 사진 찍으며 천천히 걷는 걸 좋아해요.",
                "걷기·사진");
        post("한라산 성판악 코스 동행 구해요", "제주", today.plusDays(7), today.plusDays(7), 3,
                "지우", "새벽 5시 출발 예정이에요. 혼자 오르기 불안해서 함께 오르실 분, 페이스는 천천히 맞출게요.",
                "등산");
        post("부산 1박 2일 맛집 투어", "부산", today.plusDays(14), today.plusDays(15), 2,
                "하린", "국밥, 밀면, 돼지국밥처럼 혼자 시키기 애매한 메뉴를 같이 드실 분 환영합니다.",
                "맛집");

        CompanionApplication application = new CompanionApplication();
        application.setPost(car);
        application.setNickname("도윤");
        application.setMessage("저 한 명 같이 가고 싶어요. 운전은 번갈아 할 수 있어요.");
        application.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        applicationRepository.save(application);
    }

    private CompanionPost post(String title, String destination, LocalDate start, LocalDate end,
                               int recruit, String nickname, String content, String style) {
        CompanionPost post = new CompanionPost();
        post.setTitle(title);
        post.setDestination(destination);
        post.setStartDate(start);
        post.setEndDate(end);
        post.setRecruitCount(recruit);
        post.setNickname(nickname);
        post.setContent(content);
        post.setStyle(style);
        post.setCreatedAt(Instant.now());
        return postRepository.save(post);
    }
}
