package com.pork.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pork.auth.dto.LoginDTO;
import com.pork.auth.entity.SysUser;
import com.pork.auth.mapper.SysUserMapper;
import com.pork.auth.service.AuthService;
import com.pork.auth.vo.LoginVO;
import com.pork.core.enums.ErrorCode;         // 引入你写好的 ErrorCode 枚举
import com.pork.core.exception.BusinessException; // 引入 BusinessException

import lombok.RequiredArgsConstructor;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;

    @Override
    public LoginVO login(LoginDTO dto) {
        // 1. 根据用户名查询用户
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, dto.getUsername());
        SysUser user = userMapper.selectOne(wrapper);

        if (user == null) {
            // 使用全局错误码抛出异常
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