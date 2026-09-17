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
    @Value("${security.bootstrap-admin.password:${BOOTSTRAP_ADMIN_PASSWORD:123456}}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        initAdminUser();
        initOrgData();
    }

    private void initAdminUser() {
        SysUser existing = mapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (existing == null) {
            String salt = UUID.randomUUID().toString().replace("-", "");
            SysUser user = new SysUser();
            user.setUsername(username);
            user.setNickname("系统管理员");
            user.setRealName("系统管理员");
            user.setPasswordSalt(salt);
            user.setPasswordHash(SM3Util.hashWithSalt(password, salt));
            user.setRole("ADMIN");
            user.setOrgId(1L);
            user.setStatus(1);
            user.setPhone("010-12345678");
            user.setEmail("admin@porktrace.com");
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            mapper.insert(user);
            log.warn("Bootstrap administrator '{}' was created; change its password immediately", username);
        } else {
            boolean dirty = false;
            if (existing.getOrgId() == null) { existing.setOrgId(1L); dirty = true; }
            if (existing.getPhone() == null) { existing.setPhone("010-12345678"); dirty = true; }
            if (existing.getEmail() == null) { existing.setEmail("admin@porktrace.com"); dirty = true; }
            if (dirty) {
                existing.setUpdateTime(LocalDateTime.now());
                mapper.updateById(existing);
                log.warn("Bootstrap administrator '{}' contact info was backfilled", username);
            }
        }

        // 创建业务用户（每个角色 1 个，对应 sys_org.id=2/3/4/1001）
        createBusinessUser("farm_user", password, "FARMER", "李牧", 2L,
                "13800000001", "farmer@porktrace.com");
        createBusinessUser("slaughter_user", password, "SLAUGHTER_OP", "张场长", 3L,
                "010-87654321", "slaughter@porktrace.com");
        createBusinessUser("distribution_user", password, "DISTRIBUTOR", "陈经理", 4L,
                "010-55667788", "distributor@porktrace.com");

        // 为 14 个零售门店各创建 1 个 RETAILER 用户（org_id=1001..1014）
        String[][] retailUsers = {
                {"retail_user",     "孙店长", "13800000003", "retailer@porktrace.com",     "1001"},
                {"retail_user_1002","周店长", "13800000012", "retailer1002@porktrace.com", "1002"},
                {"retail_user_1003","吴店长", "13800000013", "retailer1003@porktrace.com", "1003"},
                {"retail_user_1004","郑店长", "13800000014", "retailer1004@porktrace.com", "1004"},
                {"retail_user_1005","王店长", "13800000015", "retailer1005@porktrace.com", "1005"},
                {"retail_user_1006","冯店长", "13800000016", "retailer1006@porktrace.com", "1006"},
                {"retail_user_1007","蒋店长", "13800000017", "retailer1007@porktrace.com", "1007"},
                {"retail_user_1008","韩店长", "13800000018", "retailer1008@porktrace.com", "1008"},
                {"retail_user_1009","杨店长", "13800000019", "retailer1009@porktrace.com", "1009"},
                {"retail_user_1010","朱店长", "13800000020", "retailer1010@porktrace.com", "1010"},
                {"retail_user_1011","徐店长", "13800000021", "retailer1011@porktrace.com", "1011"},
                {"retail_user_1012","黄店长", "13800000022", "retailer1012@porktrace.com", "1012"},
                {"retail_user_1013","彭店长", "13800000023", "retailer1013@porktrace.com", "1013"},
                {"retail_user_1014","鲁店长", "13800000024", "retailer1014@porktrace.com", "1014"}
        };
        for (String[] r : retailUsers) {
            createBusinessUser(r[0], password, "RETAILER", r[1], Long.parseLong(r[4]),
                    r[2], r[3]);
        }
    }

    private void createBusinessUser(String username, String rawPassword, String role, String realName, Long orgId,
                                    String phone, String email) {
        SysUser existing = mapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (existing == null) {
            String salt = UUID.randomUUID().toString().replace("-", "");
            SysUser user = new SysUser();
            user.setUsername(username);
            user.setNickname(realName);
            user.setRealName(realName);
            user.setPasswordSalt(salt);
            user.setPasswordHash(SM3Util.hashWithSalt(rawPassword, salt));
            user.setRole(role);
            user.setOrgId(orgId);
            user.setStatus(1);
            user.setPhone(phone);
            user.setEmail(email);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            mapper.insert(user);
            log.warn("Bootstrap business user '{}' (role={}, org_id={}) was created", username, role, orgId);
        } else {
            boolean dirty = false;
            if (existing.getOrgId() == null) { existing.setOrgId(orgId); dirty = true; }
            if (existing.getPhone() == null) { existing.setPhone(phone); dirty = true; }
            if (existing.getEmail() == null) { existing.setEmail(email); dirty = true; }
            if (dirty) {
                existing.setUpdateTime(LocalDateTime.now());
                mapper.updateById(existing);
                log.warn("Bootstrap business user '{}' contact info was backfilled", username);
            }
        }
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

        // 14 个零售门店（id=1001-1014，对应 retail_sale.store_id）
        String[][] stores = {
                {"1001", "安心生鲜门店", "孙店长", "13800000003", "北京市海淀区中关村大街99号", "直营店"},
                {"1002", "顺鑫生鲜门店", "周店长", "13800000012", "北京市东城区东直门内大街12号", "直营店"},
                {"1003", "绿康生鲜门店", "吴店长", "13800000013", "北京市西城区西直门外大街18号", "直营店"},
                {"1004", "金牧生鲜门店", "郑店长", "13800000014", "北京市丰台区南三环西路20号", "加盟店"},
                {"1005", "永发生鲜门店", "王店长", "13800000015", "北京市朝阳区建国路88号", "直营店"},
                {"1006", "昌盛生鲜门店", "冯店长", "13800000016", "北京市石景山区石景山路22号", "加盟店"},
                {"1007", "兴农生鲜门店", "蒋店长", "13800000017", "北京市通州区新华大街33号", "直营店"},
                {"1008", "惠农生鲜门店", "韩店长", "13800000018", "北京市昌平区政府街55号", "直营店"},
                {"1009", "福田生鲜门店", "杨店长", "13800000019", "北京市顺义区光明街道7号", "直营店"},
                {"1010", "大众生鲜门店", "朱店长", "13800000020", "北京市大兴区兴华大街9号", "加盟店"},
                {"1011", "利农生鲜门店", "徐店长", "13800000021", "北京市房山区良乡大街11号", "直营店"},
                {"1012", "安康生鲜门店", "黄店长", "13800000022", "北京市门头沟区新桥大街15号", "直营店"},
                {"1013", "丰禾生鲜门店", "彭店长", "13800000023", "北京市怀柔区青春路21号", "直营店"},
                {"1014", "宏图生鲜门店", "鲁店长", "13800000024", "北京市密云区鼓楼东大街6号", "直营店"}
        };
        for (String[] s : stores) {
            SysOrg store = new SysOrg();
            store.setId(Long.parseLong(s[0]));
            store.setParentId(4L);
            store.setType("retail");
            store.setName(s[1]);
            store.setManager(s[2]);
            store.setPhone(s[3]);
            store.setAddress(s[4]);
            store.setRemark(s[5]);
            store.setCreateTime(now);
            store.setUpdateTime(now);
            orgMapper.insert(store);
        }

        log.warn("Bootstrap org data was initialized with 18 default organizations (1 supervisor + 1 farm + 1 slaughter + 1 distribution + 14 retail)");
    }
}
