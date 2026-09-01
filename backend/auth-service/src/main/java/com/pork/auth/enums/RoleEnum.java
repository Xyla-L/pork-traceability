package com.pork.auth.enums;

public enum RoleEnum {
    ADMIN("ADMIN", "系统管理员"),
    FARMER("FARMER", "养殖户"),
    SLAUGHTER_OP("SLAUGHTER_OP", "屠宰场操作员"),
    DISTRIBUTOR("DISTRIBUTOR", "分销商"),
    RETAILER("RETAILER", "零售商"),
    SUPERVISOR("SUPERVISOR", "监管人员");

    private final String code;
    private final String desc;

    RoleEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static RoleEnum fromCode(String code) {
        for (RoleEnum role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知角色: " + code);
    }
}