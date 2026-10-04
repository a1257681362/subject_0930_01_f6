package com.evops.coldchain;

/** 冷链台账状态与动作常量 */
public final class CcConstants {

    private CcConstants() {
    }

    // 档案/车辆状态
    public static final String ACTIVE = "ACTIVE";
    public static final String REVOKED = "REVOKED";

    // 档案版本状态
    public static final String EFFECTIVE = "EFFECTIVE";
    public static final String SUPERSEDED = "SUPERSEDED";

    // 路线季节版本状态（DRAFT 尚未发布 / EFFECTIVE 生效中 / SUPERSEDED 被热替换 / REVOKED 已撤回）
    public static final String ROUTE_DRAFT = "DRAFT";
    // EFFECTIVE / SUPERSEDED / REVOKED 复用上方常量

    // 季节（同一路线可按季节各维护一套版本；GENERAL 为不区分季节的通用配置）
    public static final String SEASON_SPRING = "SPRING";
    public static final String SEASON_SUMMER = "SUMMER";
    public static final String SEASON_AUTUMN = "AUTUMN";
    public static final String SEASON_WINTER = "WINTER";
    public static final String SEASON_GENERAL = "GENERAL";

    // 发货单状态
    public static final String ORDER_CREATED = "CREATED";
    public static final String ORDER_DISPATCHED = "DISPATCHED";
    public static final String ORDER_SHIPPED = "SHIPPED";
    public static final String ORDER_SIGNED = "SIGNED";
    public static final String ORDER_REVOKED = "REVOKED";

    // 运输批次状态
    public static final String BATCH_DISPATCHED = "DISPATCHED";
    public static final String BATCH_SHIPPED = "SHIPPED";
    public static final String BATCH_EXCEPTION = "EXCEPTION";
    public static final String BATCH_SIGNED = "SIGNED";
    public static final String BATCH_REVOKED = "REVOKED";

    // 异常
    public static final String EXC_TEMP = "TEMP_EXCURSION";
    public static final String EXC_DAMAGE = "DAMAGE";
    public static final String EXC_DELAY = "DELAY";
    public static final String EXC_OTHER = "OTHER";
    public static final String EXC_OPEN = "OPEN";
    public static final String EXC_RESOLVED = "RESOLVED";

    // 操作日志实体类型
    public static final String ENT_DRUG = "DRUG";
    public static final String ENT_VEHICLE = "VEHICLE";
    public static final String ENT_ORDER = "ORDER";
    public static final String ENT_BATCH = "BATCH";
    public static final String ENT_TEMPERATURE = "TEMPERATURE";
    public static final String ENT_EXCEPTION = "EXCEPTION";
    public static final String ENT_ROUTE = "ROUTE";
    // 车载温度探头 / 校准凭证
    public static final String ENT_PROBE = "PROBE";
    public static final String ENT_CERT = "CERT";
    // 分仓交接单 / 封签 / 权限开关
    public static final String ENT_HANDOVER = "HANDOVER";
    public static final String ENT_SEAL = "SEAL";
    public static final String ENT_PERMISSION = "PERMISSION";
    // 药品类别 / 温控预警模板 / 温控预警（V5）
    public static final String ENT_CATEGORY = "CATEGORY";
    public static final String ENT_ALERT_TEMPLATE = "ALERT_TEMPLATE";
    public static final String ENT_TEMP_ALERT = "TEMP_ALERT";

    // 校准凭证状态（VALID 有效 / VOID 作废；只追加 void* 字段，不删除原行）
    public static final String CERT_VALID = "VALID";
    public static final String CERT_VOID = "VOID";

    // 分仓交接单状态（DRAFT 待逐封签核验 / SUBMITTED 已提交责任转移 / VOIDED 已作废留痕）
    public static final String HANDOVER_DRAFT = "DRAFT";
    public static final String HANDOVER_SUBMITTED = "SUBMITTED";
    public static final String HANDOVER_VOIDED = "VOIDED";

    // 封签核验状态（PENDING 待核验 / CONFIRMED 已核验；确认事实只进不退）
    public static final String SEAL_PENDING = "PENDING";
    public static final String SEAL_CONFIRMED = "CONFIRMED";

    // 冷链药品运输（分仓交接）权限键：FALSE=权限收紧，交接停止推进，已确认封签事实保留
    public static final String PERM_COLD_CHAIN_HANDOVER = "COLD_CHAIN_HANDOVER";

    // —— V5 温控预警模板 / 预警 Shiro 权限点 ——
    /** 查看（列表/详情/台账）温控预警 */
    public static final String PERM_ALERT_VIEW = "coldchain:alert:view";
    /** 导出温控预警 */
    public static final String PERM_ALERT_EXPORT = "coldchain:alert:export";
    /** 预警处理闭环 */
    public static final String PERM_ALERT_RESOLVE = "coldchain:alert:resolve";
    /** 药品类别与温控预警模板维护 */
    public static final String PERM_TEMPLATE_MANAGE = "coldchain:template:manage";
    /** 手动触发温控预警扫描（定时任务运维入口，仅管理员） */
    public static final String PERM_ALERT_SCAN = "coldchain:alert:scan";

    // 默认温区代码（未显式指定温区时按药品档案储存温区映射的默认温区）
    public static final String ZONE_REFRIGERATE = "REFRIGERATE";
    public static final String ZONE_REFRIGERATE_NAME = "冷藏温区";

    // 连续超限计时状态（ACTIVE 连续超限中含抖动合并 / BROKEN 被超窗正常段断开）
    public static final String STREAK_ACTIVE = "ACTIVE";
    public static final String STREAK_BROKEN = "BROKEN";

    // 温控预警状态（OPEN 待处理 / RESOLVED 已处理；只追加 resolve*，不随模板变更消失）
    public static final String ALERT_OPEN = "OPEN";
    public static final String ALERT_RESOLVED = "RESOLVED";

    // 操作动作
    public static final String ACT_CREATE = "CREATE";
    public static final String ACT_CHANGE = "CHANGE";
    public static final String ACT_DISPATCH = "DISPATCH";
    public static final String ACT_SHIP = "SHIP";
    public static final String ACT_TEMP_REPORT = "TEMP_REPORT";
    public static final String ACT_EXCEPTION_MARK = "EXCEPTION_MARK";
    public static final String ACT_EVIDENCE_APPEND = "EVIDENCE_APPEND";
    public static final String ACT_EXCEPTION_RESOLVE = "EXCEPTION_RESOLVE";
    public static final String ACT_SIGN = "SIGN";
    public static final String ACT_REVOKE = "REVOKE";
    // 路线温区分段：草稿编排 / 发布（热替换）/ 撤回未生效版本 / 发布前差异核对
    public static final String ACT_ROUTE_DRAFT = "ROUTE_DRAFT";
    public static final String ACT_ROUTE_PUBLISH = "ROUTE_PUBLISH";
    public static final String ACT_ROUTE_WITHDRAW = "ROUTE_WITHDRAW";
    public static final String ACT_ROUTE_DIFF = "ROUTE_DIFF";
    // 车载探头：建档 / 证书上传 / 证书下载 / 证书作废 / 凭证关联（绑定/替换）
    public static final String ACT_PROBE_CREATE = "PROBE_CREATE";
    public static final String ACT_CERT_UPLOAD = "CERT_UPLOAD";
    public static final String ACT_CERT_DOWNLOAD = "CERT_DOWNLOAD";
    public static final String ACT_CERT_VOID = "CERT_VOID";
    public static final String ACT_PROBE_BIND = "PROBE_BIND";
    public static final String ACT_PROBE_REPLACE = "PROBE_REPLACE";
    // 分仓交接：拆单建单 / 封签核验 / 提交责任转移 / 作废 / 权限收紧放行切换
    public static final String ACT_HANDOVER_CREATE = "HANDOVER_CREATE";
    public static final String ACT_SEAL_VERIFY = "SEAL_VERIFY";
    public static final String ACT_HANDOVER_SUBMIT = "HANDOVER_SUBMIT";
    public static final String ACT_HANDOVER_VOID = "HANDOVER_VOID";
    public static final String ACT_PERMISSION_TIGHTEN = "PERMISSION_TIGHTEN";
    public static final String ACT_PERMISSION_RESTORE = "PERMISSION_RESTORE";
    // 一车货到达中转仓（交接前一个运输节点，登记到仓总箱数作为分箱平衡基准）
    public static final String ACT_ARRIVE = "ARRIVE";
    // V5：类别建档/变更、预警模板维护、预警产生（系统扫描）、预警闭环
    public static final String ACT_CATEGORY_CREATE = "CATEGORY_CREATE";
    public static final String ACT_CATEGORY_CHANGE = "CATEGORY_CHANGE";
    public static final String ACT_CATEGORY_REVOKE = "CATEGORY_REVOKE";
    public static final String ACT_DRUG_ASSIGN_CATEGORY = "DRUG_ASSIGN_CATEGORY";
    public static final String ACT_TEMPLATE_SAVE = "ALERT_TEMPLATE_SAVE";
    public static final String ACT_ALERT_RAISE = "TEMP_ALERT_RAISE";
    public static final String ACT_ALERT_SCAN = "TEMP_ALERT_SCAN";
    public static final String ACT_ALERT_RESOLVE = "TEMP_ALERT_RESOLVE";
    public static final String ACT_ALERT_EXPORT = "TEMP_ALERT_EXPORT";
}
