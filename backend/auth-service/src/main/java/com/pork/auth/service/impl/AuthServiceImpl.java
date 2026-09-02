
package com.pork.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pork.auth.dto.LoginDTO;
import com.pork.auth.dto.RegisterDTO;
import com.pork.auth.entity.SysUser;
import com.pork.auth.mapper.SysUserMapper;
import com.pork.auth.service.AuthService;
import com.pork.auth.vo.GetUserInfoVO;
import com.pork.auth.vo.LoginVO;
import com.pork.auth.vo.RegisterVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.result.Result;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;

    /**
     * 用户登录
     */
    @Override
    public LoginVO login(LoginDTO dto) {
        // 1. 根据用户名查询用户
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, dto.getUsername());
        SysUser user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        // 2. 校验密码（SM3 散列对比）
        String encryptedPassword = sm3Encrypt(dto.getPassword());
        if (!encryptedPassword.equals(user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        // 3. 校验用户状态
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 4. 执行 Sa-Token 登录并获取 Token
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        // 5. 封装返回 VO
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }

    /**
     * 用户注册
     */
    @Override
    public RegisterVO register(RegisterDTO dto) {
        // 1. 校验两次密码是否一致
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_NOT_MATCH);
        }

        // 2. 校验用户名是否已存在
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, dto.getUsername());
        SysUser existingUser = userMapper.selectOne(wrapper);
        if (existingUser != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        // 3. SM3 加密密码
        String encryptedPassword = sm3Encrypt(dto.getPassword());

        // 4. 设置默认值并插入数据库
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(encryptedPassword);
        user.setRealName(dto.getRealName() != null ? dto.getRealName() : dto.getUsername());
        user.setPhone(dto.getPhone());
        user.setRole(dto.getRole() != null ? dto.getRole() : "user");
        user.setStatus(1); // 默认启用
        user.setCreateTime(LocalDateTime.now());

        int result = userMapper.insert(user);
        if (result <= 0) {
            throw new BusinessException(ErrorCode.REGISTER_FAILED);
        }

        // 5. 返回注册结果
        return RegisterVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .role(user.getRole())
                .build();
    }

    /**
     * 获取当前登录用户信息
     */
    @Override
    public GetUserInfoVO getUserInfo() {
        // 通过 Sa-Token 获取当前登录用户ID
        Long userId = StpUtil.getLoginIdAsLong();

        // 查库获取用户信息
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getId, userId);
        SysUser user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 返回用户信息 VO
        return GetUserInfoVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .role(user.getRole())
                .phone(user.getPhone())
                .orgName(user.getOrgName())
                .status(user.getStatus())
                .lastLoginTime(user.getLastLoginTime())
                .createTime(user.getCreateTime())
                .build();
    }

    /**
     * 用户登出
     */
    @Override
    public void logout(String token) {
        StpUtil.logoutByTokenValue(token);
    }

    /**
     * SM3 密码加密工具方法
     */
    private String sm3Encrypt(String password) {
        SM3Digest digest = new SM3Digest();
        byte[] srcData = password.getBytes(StandardCharsets.UTF_8);
        digest.update(srcData, 0, srcData.length);
        byte[] hash = new byte[digest.getDigestSize()];
        digest.doFinal(hash, 0);
        return Hex.toHexString(hash);
    }
}