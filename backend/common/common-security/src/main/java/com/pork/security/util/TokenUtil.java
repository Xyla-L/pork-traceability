package com.pork.security.util;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.pork.core.exception.BusinessException;
import com.pork.core.result.Result;

/**
 * Token 工具类 (基于 Sa-Token 封装)
 */
public class TokenUtil {

    /**
     * 用户登录，生成 Token
     * @param loginId 用户ID (可以是String或Long)
     * @return Token 信息
     */
    public static SaTokenInfo login(Object loginId) {
        // 登录，第二个参数表示是否记住我（这里默认不记住）
        StpUtil.login(loginId, false);
        // 获取 Token 信息
        return StpUtil.getTokenInfo();
    }

    /**
     * 获取当前登录用户的 ID
     * @return 用户ID
     */
    public static Object getLoginId() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException(401, "用户未登录或Token已过期");
        }
        return StpUtil.getLoginId();
    }

    /**
     * 强制退出登录
     */
    public static void logout() {
        StpUtil.logout();
    }
}