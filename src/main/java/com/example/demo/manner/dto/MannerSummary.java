package com.example.demo.manner.dto;

import com.example.demo.manner.MannerRating;
import com.example.demo.manner.MannerTag;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 받은 평가 요약.
 * 동행 온도는 36.5도에서 시작해 별점에 따라 오르내립니다. 노쇼 횟수는 운영 참고용이라 회원 화면에는 보여 주지 않습니다.
 */
public record MannerSummary(
        double average,
        long count,
        List<String> topTags,
        long noShows,
        double temperature
) {

    public static final double BASE_TEMPERATURE = 36.5;
    private static final double LEGACY_NO_SHOW_PENALTY = 2.0;
    private static final double RATED_BONUS = 0.2;
    private static final double SCORE_WEIGHT = 0.15;

    public static final MannerSummary EMPTY = new MannerSummary(0, 0, List.of(), 0, BASE_TEMPERATURE);

    public static MannerSummary of(List<MannerRating> ratings) {
        if (ratings.isEmpty()) {
            return EMPTY;
        }
        List<MannerRating> scored = ratings.stream().filter(MannerRating::attendedOrDefault).toList();
        long legacyNoShows = ratings.size() - scored.size();
        long noShows = ratings.stream()
                .filter(r -> !r.attendedOrDefault() || r.tagList().contains(MannerTag.NO_SHOW))
                .count();

        double avg = scored.stream().mapToInt(MannerRating::getScore).average().orElse(0);
        double temperature = BASE_TEMPERATURE
                + scored.stream().mapToDouble(r -> RATED_BONUS + (r.getScore() - 3) * SCORE_WEIGHT).sum()
                - legacyNoShows * LEGACY_NO_SHOW_PENALTY;
        temperature = Math.max(0, Math.min(99, temperature));

        Map<MannerTag, Long> counts = scored.stream()
                .flatMap(r -> r.tagList().stream())
                .filter(MannerTag::positive)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<String> top = counts.entrySet().stream()
                .sorted(Map.Entry.<MannerTag, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(3)
                .map(e -> e.getKey().label())
                .toList();

        return new MannerSummary(round1(avg), ratings.size(), top, noShows, round1(temperature));
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
