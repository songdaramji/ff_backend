package site.festifriends.domain.auth;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

@Getter
public class KakaoUserInfo {

    private String socialId;
    private String name;
    private String email;
    private String profileImage;

    public KakaoUserInfo(JsonNode userInfo) {
        this.socialId = userInfo.path("id").asText();
        this.name = userInfo.path("properties").path("nickname").asText("카카오 사용자");
        this.email = userInfo.path("kakao_account").path("email")
            .asText("kakao-" + socialId + "@users.festifriends.local");
        this.profileImage = userInfo.path("properties").path("profile_image").asText(null);
    }
}
