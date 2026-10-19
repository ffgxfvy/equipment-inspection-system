# 设计取舍记录

> 这份文档记录"为什么这么做"，而不是"做了什么"。
> 面试时被追问细节，答案都在这里。

---

## 1. 为什么鉴权用拦截器而不是 Spring Security 的过滤器链？

**考虑过的三个方案**：

| 方案 | 优点 | 缺点 |
|---|---|---|
| Spring Security 完整配置 | 生态成熟、功能全（含 CSRF、CORS 统一管理） | 配置文件长、概念多（FilterChain / AuthenticationManager / UserDetailsService），对一个小系统是过度设计 |
| Shiro | 中文资料多、上手快 | 社区活跃度下降 |
| **自定义拦截器 + 注解（选用）** | 链路短、一百行能讲清楚、便于按业务扩展 | 需要自己处理 CORS、异常响应格式 |

**选它的理由**：这个系统的权限模型只有两种角色，用注解 + 拦截器 30 行就能表达清楚；而面试时我能把整条链路讲透，比"我配了个 Spring Security"更有说服力。

**代价**：CORS 要在 `WebConfig` 里单独配；鉴权失败的响应格式要自己写（见 `AuthInterceptor.writeUnauthorized`）。

> 如果角色层级变多（比如要按部门做数据隔离），应该换成 Spring Security + 自定义 `AccessDecisionManager`。

---

## 2. 为什么用 ThreadLocal 保存当前用户？

**不用 ThreadLocal 的话**，Service 层每个方法都要加一个 `Long userId` 参数：

```java
// 对比：参数污染
public PageResult<InspectionRecord> page(RecordQuery query, Long currentUserId);
public InspectionRecord submit(RecordRequest request, Long currentUserId);
public void handle(Long id, HandleRequest request, Long currentUserId);
```

这些参数还会一路传到 Mapper，非常啰嗦。用 `UserContext.get()` 之后签名干净了。

**风险与处理**：

ThreadLocal 的线程隔离只在"一个请求一个线程且线程不复用"时成立。Tomcat 用线程池，**复用线程时残留数据会串到下一个请求**。所以必须在 `afterCompletion` 里清理：

```java
@Override
public void afterCompletion(...) {
    UserContext.clear();   // 漏掉这行就会出现"A 的记录显示成 B 提交的"
}
```

这是本项目里最值得记的一个坑。

---

## 3. 为什么统一响应体把 HTTP 状态码固定成 200？

严格按 REST 规范，参数错误该返回 400、找不到该返回 404。但实际项目里两种做法都有：

| 做法 | 前端处理 | 网关/监控 |
|---|---|---|
| HTTP 状态码表达语义 | 每个请求都要判断 `status`，还要处理 4xx 的响应体格式不一致 | 监控更标准 |
| **HTTP 固定 200 + body 里的 code（选用）** | 拦截器里统一判断一次 | 需要在应用层统计错误率 |

**选它的理由**：前端只有一个 `request.js`，200 以外的分支都在那里处理，页面代码里不用写任何错误判断。

**代价**：如果以后接入 APM 或网关的重试策略，需要额外配置才能识别业务错误。这个取舍我写在了 `docs/api.md` 的约定里，方便对接方知道。

---

## 4. 逻辑删除 vs 物理删除

所有业务表都有 `deleted` 字段，用 MyBatis-Plus 的 `@TableLogic` 自动处理：

- **查询**自动追加 `AND deleted = 0`
- **删除**实际执行 `UPDATE ... SET deleted = 1`

**为什么**：设备台账、巡检记录是**审计性质的数据**。把一台设备物理删掉，历史巡检记录里的 `equipment_id` 就变成了悬空引用，事后追查"这台设备什么时候出过什么问题"就查不到了。

**代价**：表会持续增长；唯一索引需要考虑"已删除的数据是否占用编号"。本项目的处理是 `code` 唯一索引**不做逻辑删除豁免**——即删掉的设备编号不能复用。这是个有意的选择：设备编号在现场是贴牌的，复用会造成混淆。

---

## 5. 为什么列表查询要专门做关联字段回填？

巡检任务表里只有 `equipment_id` 和 `inspector_id`，但前端要显示设备名和巡检员姓名。

**三种做法**：

| 做法 | SQL 次数（14 条数据） | 问题 |
|---|---|---|
| 每条记录单独查一次关联表 | 1 + 14 + 14 = **29 次** | N+1 问题，数据量一大就崩 |
| SQL 里 JOIN 三张表 | 1 次 | 要手写 XML，返回字段多、易错；分页与 JOIN 一起写容易出问题 |
| **查出主表后用 IN 批量查关联表（选用）** | 1 + 2 = **3 次** | 代码稍长（见 `fillNames`） |

**选它的理由**：次数上去了但代码全在 Java 里，不需要维护 XML；而且 MyBatis-Plus 的分页插件只对主表分页，不存在 JOIN 分页的坑。

**这段是面试中回答"N+1 你怎么解决"的现成例子**，可以给出具体数字。

---

## 6. 为什么任务编号按 `TK-yyyyMMdd-NNN` 生成？

**不直接用自增 ID 做业务编号的原因**：自增 ID 会暴露业务量（客户看到 TK-00042 就知道你是小公司），且迁移/合库时容易冲突。

**为什么带日期**：现场人员口头沟通时，"今天的第 3 个任务"比"任务 1042"好懂。

**实现方式**：`likeRight(taskNo, "TK-20261015-")` 统计当天已有的数量再 +1。

> ⚠️ **已知并发隐患**：高并发下两个请求可能算出同一个序号。当前靠数据库唯一索引 `uk_task_no` 兜底（第二个请求会失败）。正确做法是改用数据库序列或 Redis 自增。这个局限我写在 README 的"已知局限"里——**面试时主动说出自己的方案有边界，比被问出来要好。**

---

## 7. 为什么把业务规则放在 Service 而不是数据库约束？

比如"异常记录必须填写异常描述"，用数据库 `CHECK` 约束也能做。放在 Service 的原因：

1. **错误信息友好**：能返回"结论为异常时，必须填写异常描述"，而不是数据库抛出的约束名；
2. **便于组合**：这条规则要和"联动更新设备状态"放在同一个事务里；
3. **可测试**：`TaskStatusTest` 几毫秒跑完，不需要起数据库。

**代价**：如果有人直接连库改数据，绕过业务规则。所以关键唯一性约束（设备编号、任务编号）仍然在数据库层也建了唯一索引——**双保险，各管一层**。

---

## 8. H2 与 MySQL 双 profile 的意义

`dev` 用 H2 内存库，`prod` 用 MySQL。这不只是为了"方便"：

- **对新人是决定性的**：clone 下来不用装 MySQL、不用建库导数据，`mvn spring-boot:run` 就能跑起来看到效果。降低他人的上手成本本身是个工程能力。
- **对 CI 友好**：以后加 CI 时不需要在流水线里起 MySQL 容器。

**实现细节**：H2 连接串加了 `MODE=MySQL`，让 `AUTO_INCREMENT`、`TINYINT`、`COMMENT` 这些 MySQL 写法都能被 H2 接受。所以 `schema-h2.sql` 和 `schema-mysql.sql` 内容基本一致。

> 面试时如果被问"你怎么保证两种库行为一致"，诚实回答：**不能完全保证**，复杂 SQL（比如窗口函数、JSON 函数）会有差异；我刻意把 SQL 控制在基础语法范围内，并且所有查询都走 MyBatis-Plus 的 Wrapper 而不是手写 SQL，把差异面缩到最小。
