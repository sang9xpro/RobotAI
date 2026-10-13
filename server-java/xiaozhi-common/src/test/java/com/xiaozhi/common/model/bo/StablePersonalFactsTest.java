package com.xiaozhi.common.model.bo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StablePersonalFactsTest {
    @Test void learnsOnlyExplicitStableSelfDeclarations() {
        assertThat(StablePersonalFacts.extract("Tôi thích trà xanh.")).hasSize(1);
        assertThat(StablePersonalFacts.extract("Mình thường đọc sách buổi tối.")).hasSize(1);
        assertThat(StablePersonalFacts.extract("Nếu tôi thích trà thì sao?")).isEmpty();
        assertThat(StablePersonalFacts.extract("Bạn tôi thích trà xanh.")).isEmpty();
        assertThat(StablePersonalFacts.extract("Tôi không thích trà xanh.")).isEmpty();
        assertThat(StablePersonalFacts.extract("Tôi thích thuốc điều trị bệnh.")).isEmpty();
        assertThat(StablePersonalFacts.extract("Ví dụ: tôi thích trà.")).isEmpty();
    }
}
