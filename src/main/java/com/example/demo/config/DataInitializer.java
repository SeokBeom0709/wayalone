package com.example.demo.config;

import com.example.demo.place.Place;
import com.example.demo.place.PlaceCategory;
import com.example.demo.place.PlaceRepository;
import com.example.demo.place.SoloDining;
import com.example.demo.review.SoloReview;
import com.example.demo.review.SoloReviewRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

    private final PlaceRepository placeRepository;
    private final SoloReviewRepository reviewRepository;

    public DataInitializer(PlaceRepository placeRepository, SoloReviewRepository reviewRepository) {
        this.placeRepository = placeRepository;
        this.reviewRepository = reviewRepository;
    }

    @Override
    public void run(String... args) {
        if (placeRepository.count() > 0) {
            return;
        }

        Place ramen = save(place("을지로 카운터 라멘", PlaceCategory.RESTAURANT, "서울 중구",
                "서울 중구 을지로 12길 8",
                "1인석이 많고 주문이 빨라서 혼자 들어가기 편한 라멘집.",
                SoloDining.COUNTER, 5, 37.5663, 126.9910,
                List.of("1인석", "회전 빠름", "혼밥 추천")));
        Place kimbap = save(place("성수 한 줄 김밥", PlaceCategory.RESTAURANT, "서울 성동구",
                "서울 성동구 연무장길 41",
                "줄은 서지만 포장·홀 모두 혼자 먹기 부담 없는 단품 메뉴.",
                SoloDining.YES, 4, 37.5446, 127.0559,
                List.of("단품", "캐주얼", "혼밥")));
        Place cafe = save(place("북촌 창가 카페", PlaceCategory.CAFE, "서울 종로구",
                "서울 종로구 북촌로 31",
                "창가 1인 테이블이 있어 노트북·독서를 하기 좋다.",
                SoloDining.YES, 5, 37.5826, 126.9830,
                List.of("창가석", "조용함", "혼자 카페")));
        Place bar = save(place("합정 노가리 포차", PlaceCategory.RESTAURANT, "서울 마포구",
                "서울 마포구 양화로 45",
                "단체 손님이 많아 혼자면 어색할 수 있다. 바 자리는 가끔 비어 있다.",
                SoloDining.NO, 2, 37.5495, 126.9136,
                List.of("단체", "시끄러움")));
        Place palace = save(place("경복궁 이른 아침", PlaceCategory.SPOT, "서울 종로구",
                "서울 종로구 사직로 161",
                "개장 직후면 한산해서 혼자 천천히 걷기 좋다.",
                SoloDining.UNKNOWN, 5, 37.5796, 126.9770,
                List.of("산책", "포토", "혼자 여행")));
        Place hangang = save(place("여의도 한강공원", PlaceCategory.SPOT, "서울 영등포구",
                "서울 영등포구 여의동로 330",
                "자전거·돗자리 모두 1인으로 부담 없다. 해질녘이 특히 좋다.",
                SoloDining.UNKNOWN, 4, 37.5283, 126.9340,
                List.of("야외", "해질녘", "혼자 걷기")));
        Place museum = save(place("국립현대미술관 서울", PlaceCategory.SPOT, "서울 종로구",
                "서울 종로구 삼청로 30",
                "관람 동선이 혼자 보기 편하고, 카페도 1인석이 있다.",
                SoloDining.YES, 5, 37.5786, 126.9803,
                List.of("전시", "실내", "비 오는 날")));
        Place gamcheon = save(place("감천문화마을", PlaceCategory.SPOT, "부산 사하구",
                "부산 사하구 감내2로 203",
                "골목 탐험형 명소. 주말 낮만 피하면 혼자 사진 찍기 수월하다.",
                SoloDining.UNKNOWN, 4, 35.0975, 129.0103,
                List.of("골목", "사진", "부산")));
        Place haeundae = save(place("해운대 일출 산책", PlaceCategory.SPOT, "부산 해운대구",
                "부산 해운대구 해운대해변로 264",
                "아침 해변은 한산해서 혼자 걷기 좋다. 근처 카페는 1인석이 많다.",
                SoloDining.YES, 5, 35.1586, 129.1603,
                List.of("해변", "일출", "산책")));
        Place busanCafe = save(place("남포 좌식 국밥", PlaceCategory.RESTAURANT, "부산 중구",
                "부산 중구 구덕로 48",
                "빠른 회전의 국밥집. 혼자 들어가도 자연스럽다.",
                SoloDining.YES, 5, 35.0980, 129.0305,
                List.of("국밥", "혼밥", "빠른 식사")));

        review(ramen, "민준", 5, "카운터석만 있어서 혼자인 게 오히려 편했어요.", true, 4);
        review(ramen, "수아", 4, "점심 12시만 피하면 웨이팅 거의 없어요.", true, 2);
        review(cafe, "하늘", 5, "창가 1인석에서 두 시간 앉아도 눈치 안 보였어요.", true, 6);
        review(bar, "지호", 2, "단체석 위주라 혼자면 자리가 애매해요.", true, 8);
        review(palace, "은지", 5, "9시 입장하면 한적해서 사진 찍기 좋아요.", true, 3);
        review(museum, "도윤", 4, "주말에도 동선이 넓어서 혼자 관람하기 괜찮았습니다.", true, 1);
        review(kimbap, "하린", 4, "포장해서 성수 골목에서 먹어도 좋아요.", true, 5);
        review(gamcheon, "시우", 4, "평일 오전이면 골목이 한산해요.", true, 10);
        review(haeundae, "유나", 5, "일출 본 다음 근처 카페에서 혼카페 하기 최고.", true, 7);
        review(busanCafe, "준서", 5, "1인 손님이 많아서 어색하지 않아요.", true, 2);
        review(hangang, "소연", 4, "자전거 대여해서 혼자 한 바퀴 돌기 좋아요.", true, 9);
    }

    private Place save(Place place) {
        return placeRepository.save(place);
    }

    private static Place place(
            String name,
            PlaceCategory category,
            String area,
            String address,
            String summary,
            SoloDining soloDining,
            int soloScore,
            double lat,
            double lng,
            List<String> tags
    ) {
        Place place = new Place();
        place.setName(name);
        place.setCategory(category);
        place.setArea(area);
        place.setAddress(address);
        place.setSummary(summary);
        place.setSoloDining(soloDining);
        place.setSoloScore(soloScore);
        place.setLatitude(lat);
        place.setLongitude(lng);
        place.setTags(tags);
        return place;
    }

    private void review(Place place, String nickname, int rating, String content, boolean wentAlone, int daysAgo) {
        SoloReview review = new SoloReview();
        review.setPlace(place);
        review.setNickname(nickname);
        review.setRating(rating);
        review.setContent(content);
        review.setWentAlone(wentAlone);
        review.setCreatedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        reviewRepository.save(review);
    }
}
