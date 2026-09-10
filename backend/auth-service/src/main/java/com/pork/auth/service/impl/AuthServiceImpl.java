package com.pork.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.auth.dto.AuthRequests;
import com.pork.auth.dto.LoginDTO;
import com.pork.auth.dto.RegisterDTO;
import com.pork.auth.entity.SysOrg;
import com.pork.auth.entity.SysUser;
import com.pork.auth.enums.RoleEnum;
import com.pork.auth.mapper.SysOrgMapper;
import com.pork.auth.mapper.SysUserMapper;
import com.pork.auth.service.AuthService;
import com.pork.auth.vo.LoginVO;
import com.pork.auth.vo.RegisterVO;
import com.pork.auth.vo.UserInfoVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.security.util.JwtUtil;
import com.pork.security.util.SM3Util;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private static final String BLACKLIST_PREFIX = "auth:blacklist:token:";
    private static final long ACCESS_EXPIRES_IN_SECONDS = 2 * 60 * 60;
    private static final Map<String, List<String>> ROLE_PERMISSIONS = Map.of(
            "FARMER", List.of("breeding:manage", "trace:read"),
            "SLAUGHTER_OP", List.of("slaughter:manage", "trace:read"),
            "DISTRIBUTOR", List.of("distribution:manage", "trace:read"),
            "RETAILER", List.of("sales:manage", "trace:read"),
            "SUPERVISOR", List.of("trace:read", "complaint:handle", "recall:manage", "blockchain:audit"),
            "ADMIN", List.of("breeding:manage", "slaughter:manage", "distribution:manage", "sales:manage",
                    "trace:read", "complaint:handle", "recall:manage", "blockchain:audit", "system:manage")
    );

    private final SysUserMapper userMapper;
    private final SysOrgMapper orgMapper;
    private final StringRedisTemplate redis;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginDTO dto) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !passwordMatches(dto.getPassword(), user)) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        ensureEnabled(user);
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);
        return tokensFor(user);
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        if (!"refresh".equals(JwtUtil.getTokenType(refreshToken))
                || Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + refreshToken))) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        SysUser user = findUser(JwtUtil.getUsername(refreshToken));
        ensureEnabled(user);
        blacklist(refreshToken);
        return tokensFor(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisterVO register(RegisterDTO dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_NOT_MATCH);
        }
        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, dto.getUsername())) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        String salt = newSalt();
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPasswordSalt(salt);
        user.setPasswordHash(SM3Util.hashWithSalt(dto.getPassword(), salt));
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setRole(resolveRole(dto.getRole()));
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
        return RegisterVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .role(user.getRole())
                .build();
    }

    @Override
    public UserInfoVO getUserInfo() {
        return toUserInfo(currentUser());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoVO updateProfile(AuthRequests.Profile request) {
        SysUser user = currentUser();
        if (request.nickname() != null) user.setNickname(request.nickname());
        if (request.realName() != null) user.setRealName(request.realName());
        if (request.phone() != null) user.setPhone(request.phone());
        if (request.email() != null) user.setEmail(request.email());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        return toUserInfo(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(AuthRequests.Password request) {
        SysUser user = currentUser();
        if (!passwordMatches(request.oldPassword(), user)) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "原密码错误");
        }
        String salt = newSalt();
        user.setPasswordSalt(salt);
        user.setPasswordHash(SM3Util.hashWithSalt(request.newPassword(), salt));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public void logout(String token) {
        if (StringUtils.hasText(token) && JwtUtil.isValid(token)) blacklist(token);
        SecurityContextHolder.clearContext();
    }

    private LoginVO tokensFor(SysUser user) {
        String subject = user.getId().toString();
        return LoginVO.builder()
                .token(JwtUtil.generateToken(subject, user.getRole()))
                .refreshToken(JwtUtil.generateRefreshToken(subject, user.getRole()))
                .expiresIn(ACCESS_EXPIRES_IN_SECONDS)
                .user(toUserInfo(user))
                .build();
    }

    private SysUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return findUser(authentication.getName());
    }

    private SysUser findUser(String subject) {
        try {
            SysUser user = subject == null ? null : userMapper.selectById(Long.valueOf(subject));
            if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            return user;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }

    private void ensureEnabled(SysUser user) {
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
    }

    private boolean passwordMatches(String rawPassword, SysUser user) {
        if (user == null || user.getPasswordHash() == null) return false;
        return StringUtils.hasText(user.getPasswordSalt())
                ? SM3Util.verifyWithSalt(rawPassword, user.getPasswordHash(), user.getPasswordSalt())
                : SM3Util.verify(rawPassword, user.getPasswordHash());
    }

    private UserInfoVO toUserInfo(SysUser user) {
        SysOrg org = user.getOrgId() == null ? null : orgMapper.selectById(user.getOrgId());
        return UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .orgId(user.getOrgId())
                .orgName(org == null ? null : org.getName())
                .role(user.getRole())
                .status(user.getStatus())
                .lastLoginTime(user.getLastLoginTime())
                .permissions(ROLE_PERMISSIONS.getOrDefault(user.getRole(), List.of()))
                .build();
    }

    private void blacklist(String token) {
        redis.opsForValue().set(BLACKLIST_PREFIX + token, "1", JwtUtil.remainingSeconds(token), TimeUnit.SECONDS);
    }

    private String newSalt() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveRole(String role) {
        if (!StringUtils.hasText(role)) return "USER";
        String code = role.trim().toUpperCase();
        if ("ADMIN".equals(code)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不允许自助注册管理员账号");
        }
        try {
            return RoleEnum.fromCode(code).getCode();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未知角色: " + role);
        }
    }
}
