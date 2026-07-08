package com.app.backend.global.util;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

public final class NicknameOrder {

    private static final Collator COLLATOR = Collator.getInstance(Locale.KOREAN);

    // 호환 자모 → 같은 초성의 첫 음절 (정렬 키 변환용, 표시에는 사용하지 않음)
    private static final Map<Character, Character> JAMO_TO_SYLLABLE = Map.ofEntries(
            Map.entry('ㄱ', '가'), Map.entry('ㄲ', '까'), Map.entry('ㄴ', '나'),
            Map.entry('ㄷ', '다'), Map.entry('ㄸ', '따'), Map.entry('ㄹ', '라'),
            Map.entry('ㅁ', '마'), Map.entry('ㅂ', '바'), Map.entry('ㅃ', '빠'),
            Map.entry('ㅅ', '사'), Map.entry('ㅆ', '싸'), Map.entry('ㅇ', '아'),
            Map.entry('ㅈ', '자'), Map.entry('ㅉ', '짜'), Map.entry('ㅊ', '차'),
            Map.entry('ㅋ', '카'), Map.entry('ㅌ', '타'), Map.entry('ㅍ', '파'),
            Map.entry('ㅎ', '하'));

    public static final Comparator<String> COMPARATOR =
            Comparator.comparingInt(NicknameOrder::group)
                    .thenComparing(NicknameOrder::sortKey, COLLATOR);

    private NicknameOrder() {
    }

    private static int group(String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return 1;
        }
        char first = sortKey(nickname).charAt(0);
        return (first >= '가' && first <= '힣') ? 0 : 1;
    }

    private static String sortKey(String nickname) {
        if (nickname == null) {
            return "";
        }
        StringBuilder key = new StringBuilder(nickname.length());
        for (char c : nickname.toCharArray()) {
            key.append(JAMO_TO_SYLLABLE.getOrDefault(c, c));
        }
        return key.toString();
    }
}
