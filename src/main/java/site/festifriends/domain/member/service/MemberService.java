package site.festifriends.domain.member.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.festifriends.common.exception.BusinessException;
import site.festifriends.common.exception.ErrorCode;
import site.festifriends.common.jwt.TokenResolver;
import site.festifriends.common.response.CursorResponseWrapper;
import site.festifriends.domain.auth.KakaoOAuthProvider;
import site.festifriends.domain.auth.KakaoUserInfo;
import site.festifriends.domain.auth.service.BlackListTokenService;
import site.festifriends.domain.chat.repository.MemberChatRoomRepository;
import site.festifriends.domain.comment.repository.CommentRepository;
import site.festifriends.domain.member.dto.LikedMemberDto;
import site.festifriends.domain.member.dto.LikedMemberResponse;
import site.festifriends.domain.member.dto.LikedPerformanceDto;
import site.festifriends.domain.member.dto.LikedPerformanceResponse;
import site.festifriends.domain.member.dto.ToggleUserLikeResponse;
import site.festifriends.domain.member.repository.BookmarkRepository;
import site.festifriends.domain.member.repository.MemberGroupRepository;
import site.festifriends.domain.member.repository.MemberImageRepository;
import site.festifriends.domain.member.repository.MemberRepository;
import site.festifriends.domain.performance.repository.PerformanceRepository;
import site.festifriends.entity.Bookmark;
import site.festifriends.entity.Member;
import site.festifriends.entity.MemberGroup;
import site.festifriends.entity.MemberImage;
import site.festifriends.entity.enums.BookmarkType;
import site.festifriends.entity.enums.Gender;
import site.festifriends.entity.enums.MemberRole;
import site.festifriends.entity.enums.Role;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final BlackListTokenService blackListTokenService;
    private final PerformanceRepository performanceRepository;
    private final BookmarkRepository bookmarkRepository;
    private final MemberImageRepository memberImageRepository;
    private final KakaoOAuthProvider kakaoOAuthProvider;

    private static final String DEFAULT_PROFILE_IMAGE_URL = "http://img1.kakaocdn.net/thumb/R640x640.q70/?fname=http://t1.kakaocdn.net/account_images/default_profile.jpeg";
    private final MemberChatRoomRepository memberChatRoomRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final CommentRepository commentRepository;

    @Transactional
    public Member loginOrSignUp(KakaoUserInfo userInfo) {

        Member member = memberRepository.findBySocialId(userInfo.getSocialId()).orElse(null);

        if (member == null) {
            String image = userInfo.getProfileImage();

            image = DEFAULT_PROFILE_IMAGE_URL.equals(image) ? null : image;

            Member newMember = memberRepository.save(Member.builder()
                .socialId(userInfo.getSocialId())
                .nickname(userInfo.getName())
                .email(userInfo.getEmail())
                .age(0)
                .profileImageUrl(image)
                .gender(Gender.ALL)
                .introduction("")
                .memberRole(MemberRole.
                    USER)
                .build());

            MemberImage memberImage = MemberImage.builder()
                .member(newMember)
                .src(image)
                .alt("멤버 이미지")
                .build();

            memberImageRepository.save(memberImage);

            return newMember;
        }

        if (member.getSuspendedAt() != null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "정지된 회원입니다.");
        }
        return member;
    }

    @Transactional
    public void saveRefreshToken(Long memberId, String refreshToken) {
        Member member = getMemberById(memberId);
        member.updateRefreshToken(refreshToken);
        memberRepository.save(member);
    }

    @Transactional
    public void deleteMember(Long memberId, HttpServletRequest request) {
        Member member = getMemberById(memberId);

        if (!kakaoOAuthProvider.unlinkKakaoAccount(member.getSocialId())) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "카카오 계정 연결 해제에 실패했습니다.");
        }

        List<MemberGroup> memberGroups = memberGroupRepository.findAllByMember(member);

        for (MemberGroup memberGroup : memberGroups) {
            if (memberGroup.getRole().equals(Role.HOST)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "방장인 모임이 있어 탈퇴할 수 없습니다.");
            }
            memberGroup.delete();
        }

        memberChatRoomRepository.deleteAllByMember(member);

        member.withdrawal();
        memberRepository.deleteMember(member);

        String accessToken = TokenResolver.extractAccessToken(request);
        String refreshToken = TokenResolver.extractRefreshToken(request);

        blackListTokenService.addBlackListToken(accessToken);
        blackListTokenService.addBlackListToken(refreshToken);
    }

    @Transactional(readOnly = true)
    public CursorResponseWrapper<LikedMemberResponse> getMyLikedMembers(Long memberId, Long cursorId, int size) {
        Pageable pageable = PageRequest.of(0, size);

        Slice<LikedMemberDto> slice = memberRepository.getMyLikedMembers(memberId, cursorId, pageable);

        if (slice.isEmpty()) {
            return CursorResponseWrapper.empty("요청이 성공적으로 처리되었습니다.");
        }

        List<LikedMemberResponse> response = new ArrayList<>();

        for (LikedMemberDto likedMember : slice.getContent()) {
            response.add(new LikedMemberResponse(
                likedMember.getName(),
                likedMember.getGender(),
                likedMember.getAge(),
                likedMember.getUserUid(),
                likedMember.getProfileImage(),
                likedMember.getHashtag(),
                true
            ));
        }
        Long nextCursorId = null;
        if (slice.hasNext()) {
            response.remove(response.size() - 1);
            nextCursorId = slice.getContent().get(size).getBookmarkId();
        }

        return CursorResponseWrapper.success(
            "요청이 성공적으로 처리되었습니다.",
            response,
            nextCursorId,
            slice.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public Long getMyLikedMembersCount(Long memberId) {
        return memberRepository.countMyLikedMembers(memberId);
    }

    @Transactional(readOnly = true)
    public CursorResponseWrapper<LikedPerformanceResponse> getMyLikedPerformances(Long memberId, Long cursorId,
        int size) {
        Pageable pageable = PageRequest.of(0, size);

        Slice<LikedPerformanceDto> slice = memberRepository.getMyLikedPerformances(memberId, cursorId, pageable);

        if (slice.isEmpty()) {
            return CursorResponseWrapper.empty("요청이 성공적으로 처리되었습니다.");
        }

        List<Long> performanceIds = slice.getContent().stream()
            .map(LikedPerformanceDto::getId)
            .collect(Collectors.toList());

        Map<Long, Integer> groupCounts = performanceRepository.getGroupCountsByPerformanceIds(performanceIds);
        Map<Long, Integer> favoriteCounts = bookmarkRepository.getCountByPerformanceIds(performanceIds);

        List<LikedPerformanceResponse> response = new ArrayList<>();

        for (LikedPerformanceDto likedPerformance : slice.getContent()) {
            response.add(new LikedPerformanceResponse(
                likedPerformance.getId(),
                likedPerformance.getTitle(),
                likedPerformance.getStartDate(),
                likedPerformance.getEndDate(),
                likedPerformance.getLocation(),
                likedPerformance.getCast(),
                likedPerformance.getCrew(),
                likedPerformance.getRuntime(),
                likedPerformance.getAge(),
                likedPerformance.getProductionCompany(),
                likedPerformance.getAgency(),
                likedPerformance.getHost(),
                likedPerformance.getOrganizer(),
                likedPerformance.getPrice(),
                likedPerformance.getPoster(),
                likedPerformance.getState(),
                likedPerformance.getVisit(),
                likedPerformance.getImages(),
                likedPerformance.getTime(),
                groupCounts.getOrDefault(likedPerformance.getId(), 0),
                favoriteCounts.getOrDefault(likedPerformance.getId(), 0),
                true
            ));
        }

        Long nextCursorId = null;

        if (slice.hasNext()) {
            response.remove(response.size() - 1);
            nextCursorId = slice.getContent().get(size).getBookmarkId();
        }

        return CursorResponseWrapper.success(
            "요청이 성공적으로 처리되었습니다.",
            response,
            nextCursorId,
            slice.hasNext()
        );
    }

    @Transactional
    public void updateMemberProfile(Long memberId, String name, Integer age, String description, Gender gender,
        String profileImageUrl,
        List<String> hashtag,
        String sns) {
        Member member = getMemberById(memberId);

        member.updateProfile(name, age, description, gender, profileImageUrl, hashtag, sns);
    }

    @Transactional
    public ToggleUserLikeResponse toggleLikeMember(Long memberId, Long targetId, boolean like) {
        getMemberById(targetId);

        Bookmark bookmark = bookmarkRepository.findByMemberIdAndTypeAndTargetId(memberId, BookmarkType.MEMBER, targetId)
            .orElse(null);

        boolean alreadyLiked = bookmark != null;

        if (alreadyLiked && like) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "이미 찜한 사용자를 다시 찜할 수 없습니다.");
        }

        if (!alreadyLiked && !like) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "찜하지 않은 사용자를 찜 취소할 수 없습니다.");
        }

        if (!alreadyLiked && like) {
            Member member = getMemberById(memberId);
            Bookmark newBookmark = Bookmark.builder()
                .member(member)
                .type(BookmarkType.MEMBER)
                .targetId(targetId)
                .build();
            bookmarkRepository.save(newBookmark);

            return new ToggleUserLikeResponse(
                true,
                targetId
            );
        }

        if (alreadyLiked && !like) {
            bookmarkRepository.delete(bookmark);

            return new ToggleUserLikeResponse(
                false,
                targetId
            );
        }

        return null;
    }

    public Member getMemberById(Long memberId) {
        return memberRepository.findById(memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 회원입니다."));
    }

    public boolean checkNicknameDuplication(String nickname) {
        return !memberRepository.existsByNickname(nickname);
    }
}
