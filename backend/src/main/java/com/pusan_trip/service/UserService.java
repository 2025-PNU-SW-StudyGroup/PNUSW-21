package com.pusan_trip.service;

import com.pusan_trip.domain.User;
import com.pusan_trip.dto.LoginRequestDto;
import com.pusan_trip.dto.LoginResponseDto;

import com.pusan_trip.dto.SignupRequestDto;
import com.pusan_trip.dto.SignupResponseDto;
import com.pusan_trip.exception.DuplicateUserException;

import com.pusan_trip.dto.NicknameRequest;
import com.pusan_trip.dto.PasswordChangeRequest;
import com.pusan_trip.dto.MyPageResponseDto;

import com.pusan_trip.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pusan_trip.config.JwtUtil;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final int MIN_PASSWORD_LENGTH = 10;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponseDto login(LoginRequestDto dto) {
        User user = userRepository.findByUserId(dto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        String accessToken = jwtUtil.generateToken(user.getUserId());
        return new LoginResponseDto(user.getName(), user.getId(), accessToken);
    }


    public SignupResponseDto signup(SignupRequestDto dto) {
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new DuplicateUserException("이미 존재하는 이메일입니다.");
        }

        if (userRepository.findByUserId(dto.getUserId()).isPresent()) {
            throw new DuplicateUserException("이미 존재하는 사용자 ID입니다.");
        }

        String encodedPassword = passwordEncoder.encode(dto.getPassword());

        User user = new User(
                dto.getUserId(),
                encodedPassword,
                dto.getName(),
                dto.getEmail()
        );
        
        // 프로필 사진이 제공된 경우 설정
        if (dto.getProfileImage() != null && !dto.getProfileImage().trim().isEmpty()) {
            user.updateProfileImage(dto.getProfileImage());
        }

        userRepository.save(user);

        return new SignupResponseDto(user.getId(), user.getUserId(), user.getName());
    }

    public boolean isNicknameAvailable(String nickname) {
        return !userRepository.existsByName(nickname);
    }

    @Transactional
    public void updateNickname(Long userId, NicknameRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        
        String newNickname = request.getNewNickname();
        if (newNickname == null || newNickname.trim().isEmpty()) {
            throw new IllegalArgumentException("닉네임은 비어있을 수 없습니다.");
        }
        
        if (!isNicknameAvailable(newNickname)) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }
        
        user.updateName(newNickname);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
        }
    }

    @Transactional
    public void updatePassword(Long userId, PasswordChangeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        // 새 비밀번호와 확인 비밀번호 일치 여부 확인
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
        }

        // 새 비밀번호 유효성 검사
        validatePassword(request.getNewPassword());

        // 새 비밀번호 암호화 및 업데이트
        String encodedNewPassword = passwordEncoder.encode(request.getNewPassword());
        user.updatePassword(encodedNewPassword);
    }

    @Transactional
    public void updateProfileImage(Long userId, String profileImage) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        
        if (profileImage == null || profileImage.trim().isEmpty()) {
            throw new IllegalArgumentException("프로필 사진 URL은 비어있을 수 없습니다.");
        }
        
        user.updateProfileImage(profileImage);
    }

    @Transactional(readOnly = true)
    public MyPageResponseDto getMyPage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        
        return new MyPageResponseDto(
                user.getId(),
                user.getUserId(),
                user.getName(),
                user.getProfileImage()
        );
    }
}