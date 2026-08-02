package site.festifriends.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class KakaoUserInfoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void usesSafeDefaultsWhenOptionalKakaoFieldsAreMissing() throws Exception {
        KakaoUserInfo userInfo = new KakaoUserInfo(objectMapper.readTree("{\"id\":12345}"));

        assertThat(userInfo.getSocialId()).isEqualTo("12345");
        assertThat(userInfo.getName()).isEqualTo("카카오 사용자");
        assertThat(userInfo.getEmail()).isEqualTo("kakao-12345@users.festifriends.local");
        assertThat(userInfo.getProfileImage()).isNull();
    }
}
