package com.pork.breeding.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.breeding.entity.Farm;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.mapper.FarmMapper;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.mapper.SysOrgRefMapper;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.web.scope.OrgScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 养殖角色的数据边界：把「这个登录人能看哪家场」收敛到一处（与 OrgScope 对零售商的角色对称）。
 * <p>
 * <b>机构到场怎么换算：</b>令牌里的 {@code orgId} 指向 {@code sys_org(type='farm')}，
 * 业务表 {@code pig_individual.farm_id} 指向 {@code farm} 表——两套 id 靠名称对应
 * （机构管理同步养殖场档案时按名称入库）。这里顺着 orgId → 机构名 → farm.id 换算，
 * 换算不出（机构缺名/场不存在或停用）就拒绝，<b>绝不放行到全平台</b>——
 * 那是越权，不是降级。
 * <p>
 * <b>只圈读不圈写：</b>写入侧的实名与来源管控由 SourceGuard / 判定器负责，
 * 这里只回答「列表该出哪些行」「这条猪的明细能不能看」。
 * 设备通道、服务间 internal 调用没有登录头，{@code scopedFarmId()} 返回 null，
 * 天然不受影响。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FarmScope {

    private final OrgScope scope;
    private final SysOrgRefMapper sysOrgRefMapper;
    private final FarmMapper farmMapper;
    private final PigIndividualMapper pigIndividualMapper;

    /**
     * 当前登录养殖角色能看的场 id；非养殖角色返回 null（不限）。
     * 养殖角色换算失败直接 403，不放行全量。
     */
    public Long scopedFarmId() {
        if (!scope.farmer()) return null;
        Long orgId = scope.orgId();
        if (orgId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "登录信息缺少所属机构，请重新登录后再试");
        }
        String orgName = sysOrgRefMapper.selectNameById(orgId);
        if (!StringUtils.hasText(orgName)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "所属机构不存在或已被删除，请联系管理员");
        }
        Farm farm = farmMapper.selectOne(Wrappers.<Farm>lambdaQuery()
                .eq(Farm::getFarmName, orgName)
                .eq(Farm::getStatus, 1)
                .last("LIMIT 1"));
        if (farm == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "所属机构【" + orgName + "】尚未登记养殖场档案，无法确定数据范围，请联系管理员");
        }
        return farm.getId();
    }

    /**
     * 合并「调用方传的场」与「他实际能看的场」：养殖角色一律强制自己的场，
     * 传别人的场号不会变成别人的视角；其他角色原样放行。
     */
    public Long restrict(Long requestedFarmId) {
        Long own = scopedFarmId();
        return own != null ? own : requestedFarmId;
    }

    /**
     * pigId 维度的过滤 SQL（给 MyBatis-Plus 的 inSql 下推用，分页不会被内存过滤截断）。
     * 非养殖角色返回 null 表示不过滤。farmId 由本类解析（Long），拼接无注入面。
     */
    public String pigIdsInFarmSql() {
        Long farmId = scopedFarmId();
        return farmId == null ? null : "SELECT id FROM pig_individual WHERE deleted = 0 AND farm_id = " + farmId;
    }

    /**
     * 校验一条「挂在猪上」的记录确实属于当前养殖角色自己的场（明细/作废等单条入口用）。
     * 列表过滤挡不住「拿着别家猪的 id 直接调详情接口」，和 OrgScope.requireStore 同一条纪律。
     */
    public void requirePig(Long pigId, Long pigFarmId, String subject) {
        Long own = scopedFarmId();
        if (own == null) return;
        if (!own.equals(pigFarmId)) {
            log.warn("farm_scope_denied subject={} scopedFarm={} actualFarm={}", subject, own, pigFarmId);
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "【数据隔离】" + subject + "不属于当前登录机构所属养殖场，无权查看或操作");
        }
    }

    /**
     * 只有 pigId 时的归属校验：自己查猪、自己比对。猪不存在按业务异常报，
     * 不属于隔离范畴（隔离报 403，不存在报 404 语义的业务错）。
     */
    public void requirePigOfFarm(Long pigId, String subject) {
        Long own = scopedFarmId();
        if (own == null) return;
        PigIndividual pig = pigIndividualMapper.selectById(pigId);
        if (pig == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, subject + "对应的生猪不存在");
        }
        requirePig(pigId, pig.getFarmId(), subject);
    }
}
