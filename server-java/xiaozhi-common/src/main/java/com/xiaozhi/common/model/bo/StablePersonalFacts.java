package com.xiaozhi.common.model.bo;

import com.xiaozhi.common.port.PersonalMemoryStore.Fact;
import java.util.*;
import java.util.regex.Pattern;

/** Conservative extraction from this utterance only. No history mining or inferred personal data. */
public final class StablePersonalFacts {
    private StablePersonalFacts() {}
    public static final Set<String> KEYS = Set.of("preference", "hobby", "routine", "communication_style");
    private static final Pattern SENSITIVE = Pattern.compile("(?iu)(mật khẩu|password|token|otp|căn cước|số tài khoản|địa chỉ|điện thoại|bệnh|thuốc|sức khỏe|tôn giáo|chính trị|tình dục|thu nhập|lương|nợ|tài chính|bí mật)");
    public static boolean safe(Fact fact) {
        return fact != null && KEYS.contains(fact.key()) && fact.value()!=null && !fact.value().isBlank()
                && fact.value().length()<=160 && !SENSITIVE.matcher(fact.value()).find()
                && fact.value().codePoints().noneMatch(Character::isISOControl);
    }
    public static List<Fact> extract(String text) {
        if(text==null || text.length()>2000 || SENSITIVE.matcher(text).find() || text.contains("\"") || text.contains("“")
                || text.contains("?") || text.toLowerCase(Locale.ROOT).matches(".*(nếu|ví dụ|giả sử|anh ấy|cô ấy|bạn tôi|không thích).*")) return List.of();
        var out = new ArrayList<Fact>();
        var rules = Map.of("preference", "(?:tôi|mình) (?:rất )?thích", "hobby", "sở thích của (?:tôi|mình) là",
                "routine", "(?:tôi|mình) thường", "communication_style", "(?:tôi|mình) muốn bạn trả lời");
        for(var rule:rules.entrySet()) {
            var match=Pattern.compile("(?iu)(?:^|[.!;]\\s*)"+rule.getValue()+"\\s+([^.!;\\n]{2,120})(?:[.!;]|$)").matcher(text.strip());
            if(match.find()) { var fact=new Fact(rule.getKey(),match.group(1).strip()); if(safe(fact)) out.add(fact); }
        }
        return List.copyOf(out);
    }
}
