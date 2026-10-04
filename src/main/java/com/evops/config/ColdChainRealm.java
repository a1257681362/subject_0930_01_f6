package com.evops.config;

import com.evops.common.UserPrincipal;
import com.evops.coldchain.CcConstants;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.credential.SimpleCredentialsMatcher;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 冷链台账内置演示账号：登录主体为 UserPrincipal（携带操作者 ID）。
 * V5 起同时提供授权信息（Shiro 权限点）：温控预警的查看/导出/闭环、类别模板维护按权限点校验。
 * V1 为本地静态账号，后续版本可对接统一身份。
 */
public class ColdChainRealm extends AuthorizingRealm {

    /** username -> (id, password, role) */
    private static final Map<String, long[]> IDS = new LinkedHashMap<>();
    private static final Map<String, String> PASSWORDS = new LinkedHashMap<>();
    private static final Map<String, String> ROLES = new LinkedHashMap<>();
    /** username -> Shiro 权限点集合 */
    private static final Map<String, Set<String>> PERMISSIONS = new LinkedHashMap<>();

    static {
        put("bootstrap", 1L, "bootstrap", "BOOTSTRAP", Collections.emptySet());
        put("dispatcher", 2L, "dispatcher123", "DISPATCHER", new LinkedHashSet<>(Arrays.asList(
                CcConstants.PERM_TEMPLATE_MANAGE,
                CcConstants.PERM_ALERT_VIEW,
                CcConstants.PERM_ALERT_RESOLVE)));
        put("carrier", 3L, "carrier123", "CARRIER", Collections.emptySet());
        put("receiver", 4L, "receiver123", "RECEIVER",
                new LinkedHashSet<>(Collections.singletonList(CcConstants.PERM_ALERT_VIEW)));
        // 管理员：冷链温控预警查看/导出/闭环/扫描 + 类别模板维护
        put("admin", 5L, "admin123", "ADMIN", new LinkedHashSet<>(Arrays.asList(
                CcConstants.PERM_ALERT_VIEW,
                CcConstants.PERM_ALERT_EXPORT,
                CcConstants.PERM_ALERT_RESOLVE,
                CcConstants.PERM_ALERT_SCAN,
                CcConstants.PERM_TEMPLATE_MANAGE,
                "*")));
    }

    private static void put(String username, long id, String password, String role, Set<String> perms) {
        IDS.put(username, new long[]{id});
        PASSWORDS.put(username, password);
        ROLES.put(username, role);
        PERMISSIONS.put(username, perms);
    }

    public ColdChainRealm() {
        // SimpleCredentialsMatcher 会把 HTTP Basic 提交的 char[] 密码转成字符串后比对
        setCredentialsMatcher(new SimpleCredentialsMatcher());
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        String username = token.getPrincipal() == null ? null : String.valueOf(token.getPrincipal());
        if (username == null || !PASSWORDS.containsKey(username)) {
            return null;
        }
        UserPrincipal principal = new UserPrincipal(IDS.get(username)[0], username);
        // 凭证由 SimpleCredentialsMatcher 与 token 中的 char[] 密码比对
        return new SimpleAuthenticationInfo(principal, PASSWORDS.get(username), getName());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        Object primary = principals.getPrimaryPrincipal();
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        if (primary instanceof UserPrincipal) {
            String username = ((UserPrincipal) primary).getUsername();
            String role = ROLES.get(username);
            if (role != null) {
                info.addRole(role);
            }
            Set<String> perms = PERMISSIONS.get(username);
            if (perms != null) {
                info.addStringPermissions(perms);
            }
        }
        return info;
    }
}
