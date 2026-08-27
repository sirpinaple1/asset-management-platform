/**
 * M08 历史数据迁移（一次性工具，非长期业务模块）。
 *
 * <p>职责：读取旧系统导出的 4 份 Excel（资产档案 563 / 领用单 162 / 调拨单 56 / 操作日志 1773），
 * 经解析规则转换后写入新系统，建立历史数据基线。设计要点：</p>
 *
 * <ul>
 *   <li>幂等：资产/单据按 barcode/serial_no upsert；日志按"时间区段清除 + 重灌"
 *       （新系统诞生于 2026-08-19，早于该时间戳的 asset_log 只可能来自迁移，重复运行结果一致）</li>
 *   <li>直写 mapper 而非 service 业务方法：业务方法会重新生成编码、强制状态、以当前时间写日志，
 *       与迁移保真目标冲突（偏离 M08 文档"通过 service 层写入"建议的原因，见 STATUS.md）</li>
 *   <li>用户匹配：操作人文本提取工号（SK\d+）优先匹配 comm_public_basic sys_user.username；
 *       无工号按姓名匹配；均未命中且 app.migration.auto-create-users=true 时自动创建账号
 *       （BCrypt 默认密码，账号仅作 FK/姓名解析用途）</li>
 *   <li>sys_user 读写走独立 JDBC 连接（不走 HTTP、不走主数据源），连接信息只在
 *       application-local.yml / 环境变量提供（R3 红线）</li>
 * </ul>
 */
package com.sk.asset.migration;
