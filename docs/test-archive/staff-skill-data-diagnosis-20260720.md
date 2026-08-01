# staff_skill 数据诊断（Bug 1：排班发布后用户端发现不了）

## 背景

Bug 现象：管理员端发布新的员工排班后，用户端 H5 在对应日期/服务下看不到任何可预约时段。

根因（详见本次修复的交接说明）：预约查询 `BookingApplicationServiceImpl.getAvailability` 先按 `staff_skill.service_category_id` 过滤"会做这个服务的员工"。
岗位自由化改动（commit `efd8632` "员工启用/禁用对称 + 岗位自由化"）把 `staff.role` 从枚举（`GROOMER|WALKER|FEEDER|MANAGER`）改为自由文本后，
员工"能做什么服务"**完全由 `staff_skill` 关联表决定**。因此——**任何 0 技能的在职员工，其排班再多也不会出现在用户预约里**。

代码侧已通过前端健壮性修复规避未来再产生 0 技能员工（见本次 `staff/index.vue` 改动）；
本文件给出 SQL 帮助核查/修复**历史数据**中已经存在的 0 技能员工。

## 适用范围

- 仅作为运维核查与历史数据修复的参考。
- 不属于 V1 业务流程，不在应用启动时自动执行。
- 所有 SQL 请先在测试库验证、再在生产库执行；执行前务必备份 `staff_skill` 表。

---

## 1. 核查：哪些在职员工没有任何服务技能（0 技能）

```sql
-- 列出所有 ACTIVE 员工的技能数；skill_count = 0 的即为问题员工
SELECT
    s.id           AS staff_id,
    s.name,
    s.role,
    s.status,
    COUNT(sk.id)   AS skill_count
FROM staff s
LEFT JOIN staff_skill sk ON sk.staff_id = s.id
WHERE s.deleted = 0
  AND s.status = 'ACTIVE'
GROUP BY s.id, s.name, s.role, s.status
ORDER BY skill_count ASC, s.id;
```

只看 0 技能的精简版：

```sql
SELECT s.id, s.name, s.role
FROM staff s
LEFT JOIN staff_skill sk ON sk.staff_id = s.id
WHERE s.deleted = 0 AND s.status = 'ACTIVE'
GROUP BY s.id
HAVING COUNT(sk.id) = 0;
```

> 期望结果：`skill_count` 列对每个在职员工都 ≥ 1。若出现 0，说明该员工的排班在用户端不可见。

## 2. 核查：每个服务类别下有多少在职员工可服务

```sql
-- 用于确认某个服务（service_item → category_id）是否真的有员工可接单
SELECT
    sc.id          AS category_id,
    sc.name        AS category_name,
    COUNT(DISTINCT s.id) AS active_staff_count
FROM service_category sc
LEFT JOIN staff_skill sk ON sk.service_category_id = sc.id
LEFT JOIN staff s ON s.id = sk.staff_id AND s.deleted = 0 AND s.status = 'ACTIVE'
GROUP BY sc.id, sc.name
ORDER BY sc.id;
```

> 期望结果：每个上架服务对应的 `active_staff_count` ≥ 1。若为 0，该服务在用户端将永远显示"无可预约时段"。

## 3. 核查：存在排班但 0 技能的"隐形员工"（直接定位 Bug 数据）

```sql
-- 这些员工的排班虽然存在，但用户端永远看不到——就是本次 Bug 的直接数据表征
SELECT
    s.id AS staff_id, s.name, s.role,
    COUNT(DISTINCT sk.id) AS skill_count,
    COUNT(DISTINCT sch.id) AS schedule_count
FROM staff s
LEFT JOIN staff_skill sk ON sk.staff_id = s.id
LEFT JOIN staff_schedule sch ON sch.staff_id = s.id
   AND sch.deleted = 0 AND sch.status = 'AVAILABLE'
   AND sch.work_date >= CURDATE()
WHERE s.deleted = 0 AND s.status = 'ACTIVE'
GROUP BY s.id, s.name, s.role
HAVING skill_count = 0 AND schedule_count > 0;
```

---

## 4. 参考修复：按历史 role 枚举批量补 `staff_skill`

> ⚠️ 仅适用于"老员工 role 字段仍是英文枚举（GROOMER/WALKER/FEEDER/MANAGER）"的库。
> 对于已经混入自由文本 role（如"前台收银"、"实习美容师"）的库，请按真实能力手工补，不要套用本映射。

历史 role 与服务类别的对应关系，源自 `data-dev.sql:141-147` 的种子数据：

| role     | 对应 service_category_id | 类别名 |
|----------|--------------------------|--------|
| GROOMER  | 2001, 2002               | 洗护、美容 |
| WALKER   | 2003                     | 上门照护 |
| FEEDER   | 2003, 2004               | 上门照护、寄养 |
| MANAGER  | （不直接接单，通常无需补） | — |

参考 SQL（**仅对缺失记录插入，不删除已有**，先 `SELECT` 核查再改 `INSERT`）：

```sql
-- 先预览：会为哪些 (staff, category) 组合补记录
SELECT s.id AS staff_id, s.name, s.role, cat.category_id
FROM staff s
JOIN (
    SELECT 'GROOMER' AS role, 2001 AS category_id UNION ALL
    SELECT 'GROOMER', 2002 UNION ALL
    SELECT 'WALKER',  2003 UNION ALL
    SELECT 'FEEDER',  2003 UNION ALL
    SELECT 'FEEDER',  2004
) cat ON cat.role = s.role
LEFT JOIN staff_skill sk
    ON sk.staff_id = s.id AND sk.service_category_id = cat.category_id
WHERE s.deleted = 0 AND s.status = 'ACTIVE' AND sk.id IS NULL;
```

确认预览结果合理后，再执行插入（请把 `staff_skill.id` 主键策略换成你库的实际方案；下方用 `(SELECT COALESCE(MAX(id),0)+1 FROM staff_skill)` 仅作占位示例）：

```sql
-- 占位示例：实际执行前请按你库的主键/自增策略调整
INSERT INTO staff_skill (staff_id, service_category_id)
SELECT s.id, cat.category_id
FROM staff s
JOIN (
    SELECT 'GROOMER' AS role, 2001 AS category_id UNION ALL
    SELECT 'GROOMER', 2002 UNION ALL
    SELECT 'WALKER',  2003 UNION ALL
    SELECT 'FEEDER',  2003 UNION ALL
    SELECT 'FEEDER',  2004
) cat ON cat.role = s.role
LEFT JOIN staff_skill sk
    ON sk.staff_id = s.id AND sk.service_category_id = cat.category_id
WHERE s.deleted = 0 AND s.status = 'ACTIVE' AND sk.id IS NULL;
```

---

## 5. 业务规则的回归守卫（接管场景）

本文件不动业务代码。下列已存在的永久回归守卫持续覆盖"预约查询必须按 `staff_skill` 过滤"这一业务规则：

- `BookingApplicationServiceTest`（`src/test/java/com/petcare/booking/service/BookingApplicationServiceTest.java`）
  - 覆盖 `getAvailability`：无技能员工不出现、有技能员工才出现。
- `BookingTransactionServiceImpl` 相关的事务/并发测试（属于 AGENTS.md 第 4 节"预约并发"强制守卫）。
- 前端契约：`frontend/admin-web/src/__tests__/staff-scheduling.test.ts` 的"Staff skill editing in form"断言，确保技能多选入口始终存在于员工表单中（本次修复同步更新过该断言）。

## 6. 相关提交

- `efd8632` — 员工启用/禁用对称 + 岗位自由化（引入 0 技能员工的可能性）。
- 本次修复（待提交）— `staff/index.vue` 前端健壮性 + 本诊断文档。
