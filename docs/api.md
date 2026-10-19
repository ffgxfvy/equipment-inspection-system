# 接口清单

启动后端后访问 **http://localhost:8080/api/swagger-ui.html** 可交互调试。

## 约定

- 所有接口前缀 `/api`（通过 `server.servlet.context-path` 配置）
- 除 `/auth/login` 外，都需要在请求头带 `Authorization: Bearer <token>`
- 统一响应结构：

```json
{ "code": 200, "message": "操作成功", "data": { } }
```

| code | 含义 |
|---|---|
| 200 | 成功 |
| 400 | 参数有误（message 里会指出具体哪个字段） |
| 401 | 未登录或 token 过期（前端会自动跳登录页） |
| 403 | 已登录但无权限（如巡检员调新增设备） |
| 404 | 资源不存在 |
| 409 | 数据冲突（如设备编号重复、非法状态流转） |
| 500 | 服务器内部错误 |

> **注意**：HTTP 状态码始终是 200，业务码放在 body 的 `code` 里。这是国内管理系统的常见做法，好处是前端只需在一处判断，不必为每个接口处理不同的 HTTP 状态。

---

## 1. 认证

### POST /auth/login

```json
// 请求
{ "username": "admin", "password": "admin123" }

// 响应 data
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": 1,
  "username": "admin",
  "realName": "系统管理员",
  "role": "ADMIN",
  "expire": 43200000
}
```

---

## 2. 设备台账

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/equipments` | 登录 | 分页查询 |
| GET | `/equipments/{id}` | 登录 | 详情 |
| GET | `/equipments/categories` | 登录 | 类目列表（去重，下拉框用） |
| GET | `/equipments/{id}/records` | 登录 | 该设备的历史巡检记录 |
| POST | `/equipments` | 管理员 | 新增 |
| PUT | `/equipments/{id}` | 管理员 | 修改 |
| DELETE | `/equipments/{id}` | 管理员 | 删除（逻辑删除） |
| GET | `/equipments/export` | 登录 | **导出 Excel**（返回二进制流，非 JSON） |
| POST | `/equipments/import` | 管理员 | **从 Excel 导入**（multipart，字段名 file） |

**查询参数**（`GET /equipments`）：

| 参数 | 类型 | 说明 |
|---|---|---|
| `keyword` | string | 模糊匹配设备编号或名称 |
| `category` | string | 精确匹配类目 |
| `status` | string | `RUNNING` / `STOPPED` / `FAULT` / `MAINTENANCE` |
| `location` | string | 模糊匹配安装位置 |
| `overdueOnly` | boolean | 仅看"下次巡检时间已过期"的设备 |
| `pageNum` | int | 页码，默认 1 |
| `pageSize` | int | 每页条数，默认 10，上限 200 |

### GET /equipments/export

导出当前筛选条件下的设备台账为 `.xlsx`。

- **筛选参数与 `GET /equipments` 完全一致**（复用同一套条件构造逻辑，保证列表和导出不会对不上）
- 响应**不是 JSON**，而是二进制流：

```
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename="..."; filename*=UTF-8''...
```

- 单次最多导出 **5000 行**（`EquipmentExcelService.MAX_EXPORT_ROWS`），超出会截断并在文件末尾追加一行提示
- 14 列：设备编号、名称、类目、型号、厂商、安装位置、状态、巡检周期、上次巡检、下次巡检、投运日期、最近温度、最近压力、备注
- 数据库里的英文状态码（`RUNNING` 等）会翻译成中文再写入

> **前端调用要点**：`responseType` 必须设成 `'blob'`，否则 axios 会按文本解析二进制内容，
> 下载到的文件必然打不开。另外后端出错时返回的是 JSON，需要判断 MIME 类型再决定是下载还是抛错。

### POST /equipments/import

`multipart/form-data`，字段名 `file`，只接受 `.xlsx`，单次最多 2000 行。

响应（`data` 部分）：

```json
{
  "total": 6,
  "successCount": 2,
  "failCount": 4,
  "errors": [
    "第 4 行：设备编号已存在：EQ-1001",
    "第 5 行：设备名称不能为空",
    "第 6 行：状态取值非法：已报废（可选：运行/停机/故障/检修中）",
    "第 7 行：投运日期不是合法日期：2025-13-45"
  ]
}
```

**导入的四条规则**：

1. **按表头名定位列**，不按列序号——删掉"备注"列或调换顺序都能正确读取；
2. **必需列**：设备编号、设备名称、类目、安装位置。缺任一列整份文件报错；
3. **逐行独立**：格式正确的行会写入，错误行跳过并记录行号与原因（部分成功）；
4. **编号重复报错不覆盖**——避免静默改掉现场已有数据。

状态列中英文都接受（`运行` / `RUNNING`）；日期接受 `yyyy-MM-dd`、`yyyy/MM/dd`、`yyyy.MM.dd`。

> ⚠️ **dev 模式重启后数据会重置**：dev 用 H2 内存库，导入的数据只在本次运行期间存在。
> 要验证持久化请切 `prod` profile 连 MySQL。

**注意**：`lastInspectTime`、`nextInspectTime`、`lastTemperature`、`lastPressure` 这四个字段**不接受前端传入**——由系统在"完成任务"或"提交记录"时回写，避免被手工改乱。

---

## 3. 巡检任务

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/tasks` | 登录 | 分页查询 |
| GET | `/tasks/{id}` | 登录 | 详情 |
| POST | `/tasks` | 管理员 | 创建（自动生成任务编号 TK-yyyyMMdd-NNN） |
| PUT | `/tasks/{id}` | 管理员 | 修改（仅未结束的任务） |
| PUT | `/tasks/{id}/assign?inspectorId=` | 管理员 | 分派巡检员 |
| PUT | `/tasks/{id}/status?status=` | 登录 | 流转状态 |
| DELETE | `/tasks/{id}` | 管理员 | 删除（执行中的不能删） |

**状态流转规则**（后端强制校验，非法流转返回 409）：

```
PENDING  ──→ DOING ──→ DONE
   │           │
   └───────────┴──→ CANCELLED
```

- `DONE` 与 `CANCELLED` 是终态，不能再流转
- 推进到 `DONE` 时会自动回写设备的巡检时间
- 未分派巡检员的任务不能推进到 `DOING`

---

## 4. 巡检记录

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/records` | 登录 | 分页查询 |
| GET | `/records/{id}` | 登录 | 详情 |
| POST | `/records` | 登录 | 提交记录 |
| PUT | `/records/{id}/handle` | 登录 | 处理异常 |
| DELETE | `/records/{id}` | 登录 | 删除 |

**提交记录的请求体**：

```json
{
  "taskId": 8,
  "equipmentId": 9,
  "result": "ABNORMAL",
  "abnormalDesc": "机组振动值偏高，超出正常范围",
  "temperature": 78.5,
  "pressure": 0.9,
  "photoUrl": "/files/2026/10/xxx.jpg",
  "remark": "已通知检修班"
}
```

**跨字段校验（后端强制）**：

- `result = ABNORMAL` 时，`abnormalDesc` 必填 → 否则 400
- 提交后联动：回写设备读数与巡检时间；异常时设备置为 `FAULT`；带 `taskId` 时把任务置为 `DONE`

**处理异常的请求体**：

```json
{ "handleStatus": "HANDLED", "handleRemark": "已更换轴承，振动恢复正常" }
```

- `handleStatus` 只能是 `HANDLING` 或 `HANDLED`
- 标记为 `HANDLED` 时 `handleRemark` 必填
- 该设备所有异常都处理完毕后，设备状态自动恢复为 `RUNNING`

---

## 5. 看板

### GET /dashboard

一次返回首页所需的全部数据（避免前端并发调 6 个接口）：

```json
{
  "summary": { "equipmentTotal": 15, "equipmentFault": 1, "taskOpen": 6, "taskOverdue": 0, "abnormalUnhandled": 2 },
  "equipmentStatus": [ { "name": "运行", "value": 12 } ],
  "equipmentCategory": [ { "name": "压力表", "value": 5 } ],
  "inspectTrend": [ { "date": "2026-10-09", "total": 1, "abnormal": 0 } ],
  "unhandledAbnormal": [ { "recordId": 5, "equipmentCode": "EQ-1006", "abnormalDesc": "..." } ]
}
```

---

## 6. 文件上传

### POST /files/upload

`multipart/form-data`，字段名 `file`。

```json
// 响应 data
{ "relativeUrl": "/files/2026/10/a1b2c3d4e5f6.jpg" }
```

**限制与安全处理**：

- 仅图片格式（jpg / jpeg / png / gif / bmp / webp），**按扩展名白名单校验**，不信任 Content-Type
- 单文件不超过 10 MB
- 文件名由服务端生成（UUID），**不使用用户提供的文件名** → 防止 `../` 路径穿越
- 按 `yyyy/MM` 分目录存放

---

## 7. 用户

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/users/inspectors` | 管理员 | 在用的巡检员列表（任务分派下拉框用） |
