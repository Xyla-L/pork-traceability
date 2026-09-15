package com.pork.auth.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.auth.entity.SysOrg;
import com.pork.auth.entity.SysUser;
import com.pork.auth.mapper.SysOrgMapper;
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
    private final SysOrgMapper orgMapper;

    @Value("${security.bootstrap-admin.username:${BOOTSTRAP_ADMIN_USERNAME:admin}}")
    private String username;
    @Value("${security.bootstrap-admin.password:${BOOTSTRAP_ADMIN_PASSWORD:ChangeMe123!}}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        initAdminUser();
        initOrgData();
    }

    private void initAdminUser() {
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

    private void initOrgData() {
        if (orgMapper.selectCount(null) > 0) return;
        LocalDateTime now = LocalDateTime.now();
        SysOrg supervisor = new SysOrg();
        supervisor.setId(1L);
        supervisor.setParentId(0L);
        supervisor.setType("supervisor");
        supervisor.setName("市市场监督管理局");
        supervisor.setManager("王监管");
        supervisor.setPhone("010-12345678");
        supervisor.setAddress("北京市朝阳区监管大道100号");
        supervisor.setRemark("顶层监管机构");
        supervisor.setCreateTime(now);
        supervisor.setUpdateTime(now);
        orgMapper.insert(supervisor);

        SysOrg farm = new SysOrg();
        farm.setId(2L);
        farm.setParentId(1L);
        farm.setType("farm");
        farm.setName("示范生态养殖场");
        farm.setManager("李牧");
        farm.setPhone("13800000001");
        farm.setAddress("北京市顺义区示范路1号");
        farm.setRemark("合作养殖基地");
        farm.setCreateTime(now);
        farm.setUpdateTime(now);
        orgMapper.insert(farm);

        SysOrg slaughter = new SysOrg();
        slaughter.setId(3L);
        slaughter.setParentId(1L);
        slaughter.setType("slaughter");
        slaughter.setName("示范定点屠宰场");
        slaughter.setManager("张场长");
        slaughter.setPhone("010-87654321");
        slaughter.setAddress("北京市通州区屠宰路8号");
        slaughter.setRemark("定点屠宰企业");
        slaughter.setCreateTime(now);
        slaughter.setUpdateTime(now);
        orgMapper.insert(slaughter);

        SysOrg distribution = new SysOrg();
        distribution.setId(4L);
        distribution.setParentId(1L);
        distribution.setType("distribution");
        distribution.setName("冷链配送中心");
        distribution.setManager("陈经理");
        distribution.setPhone("010-55667788");
        distribution.setAddress("北京市大兴区冷链物流园");
        distribution.setRemark("中央仓配中心");
        distribution.setCreateTime(now);
        distribution.setUpdateTime(now);
        orgMapper.insert(distribution);

        SysOrg retail1 = new SysOrg();
        retail1.setId(5L);
        retail1.setParentId(4L);
        retail1.setType("retail");
        retail1.setName("安心生鲜门店");
        retail1.setManager("孙店长");
        retail1.setPhone("13800000003");
        retail1.setAddress("北京市海淀区中关村大街99号");
        retail1.setRemark("直营店");
        retail1.setCreateTime(now);
        retail1.setUpdateTime(now);
        orgMapper.insert(retail1);

        SysOrg retail2 = new SysOrg();
        retail2.setId(6L);
        retail2.setParentId(4L);
        retail2.setType("retail");
        retail2.setName("惠民社区超市");
        retail2.setManager("赵店长");
        retail2.setPhone("13800000004");
        retail2.setAddress("北京市西城区长安街200号");
        retail2.setRemark("社区店");
        retail2.setCreateTime(now);
        retail2.setUpdateTime(now);
        orgMapper.insert(retail2);

        log.warn("Bootstrap org data was initialized with 6 default organizations");
    }
}
