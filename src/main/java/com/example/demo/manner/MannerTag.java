package com.example.demo.manner;

public enum MannerTag {
    PUNCTUAL("시간 약속을 잘 지켜요", true),
    KIND("친절하고 매너 있어요", true),
    COMFORTABLE("대화가 편해요", true),
    FAIR("비용 정산이 깔끔해요", true),
    AGAIN("또 같이 가고 싶어요", true),
    LATE("약속 시간을 안 지켰어요", false),
    RUDE("불쾌한 언행이 있었어요", false),
    NO_SHOW("약속 장소에 나오지 않았어요", false);

    private final String label;
    private final boolean positive;

    MannerTag(String label, boolean positive) {
        this.label = label;
        this.positive = positive;
    }

    public String label() {
        return label;
    }

    public boolean positive() {
        return positive;
    }
}
