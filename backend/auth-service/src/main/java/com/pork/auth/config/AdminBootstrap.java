package com.pork.auth.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.auth.entity.SysUser;
import com.pork.auth.mapper.SysUserMapper;
import com.pork.security.util.SM3Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "security.bootstrap-admin.enabled", havingValue = "true", matchIfMissing = true)
public class AdminBootstrap implements ApplicationRunner {
    private final SysUserMapper mapper;

    @Value("${security.bootstrap-admin.username:${BOOTSTRAP_ADMIN_USERNAME:admin}}")
    private String username;
    @Value("${security.bootstrap-admin.password:${BOOTSTRAP_ADMIN_PASSWORD:ChangeMe123!}}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (mapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username)) > 0) return;
        String salt = UUID.randomUUID().toString().replace("-", "");
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setNickname("系统管理员");
        user.setRealName("系统管理员");
        user.setPasswordSalt(salt);
        user.setPasswordHash(SM3Util.hashWithSalt(password, salt));
        user.setRole("ADMIN");
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        mapper.insert(user);
        log.warn("Bootstrap administrator '{}' was created; change its password immediately", username);
    }
}
