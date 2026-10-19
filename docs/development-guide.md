# 开发指南：从零把项目跑起来

> 这份文档假设你**从来没有写过 Java 项目**。按顺序做，每一步都有验证方法。
> 遇到报错先看最后一节「常见报错」。

---

## 第 0 步：检查环境

打开 PowerShell，逐条执行：

```powershell
java -version      # 需要 17 或更高
node -v            # 需要 18 或更高
npm -v
```

**你现在的情况**（我已经在本机确认过）：

| 组件 | 状态 | 说明 |
|---|---|---|
| Java 17 (OpenJDK) | ✅ 已装 | `D:\java` |
| Node v24 + npm | ✅ 已装 | 可以直接跑前端 |
| Maven | ❌ 未装 | **不用单独装**，用 IDEA 自带的 |
| MySQL | ❌ 未装 | **不用装**，dev 模式用 H2 内存库 |
| Docker | ❌ 未装 | 可选，只是想体验编排时才装 |

### 安装 IntelliJ IDEA（必装）

去 https://www.jetbrains.com/idea/download/ 下载 **Community 版**（免费）。

安装后**不要**急着新建项目，直接进入第 2 步"打开已有项目"。

### 配置阿里云 Maven 镜像（重要，否则下载依赖会很慢）

IDEA 启动后：`File` → `Settings` → `Build, Execution, Deployment` → `Build Tools` → `Maven`

1. 找到 **User settings file**，勾上 `Override`，路径设为：
   `C:\Users\ffgxf\.m2\settings.xml`
2. 如果这个文件不存在，先创建它，内容：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <mirrors>
    <mirror>
      <id>aliyunmaven</id>
      <mirrorOf>*</mirrorOf>
      <name>阿里云公共仓库</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
```

3. 回到 Maven 设置页，勾选 **Work offline** 的**反选**（即保持联网），点 `Apply`。

---

## 第 1 步：先用命令行验证后端能跑

这一步**不用 IDEA**，先确认代码本身没问题。

```powershell
cd "D:\桌面\实习\作品集\equipment-inspection-system\backend"
```

### 1.1 生成 Maven Wrapper（一次性）

Maven Wrapper 让你不需要单独安装 Maven。用 IDEA 生成最简单：

- 用 IDEA 打开 `backend` 目录 → 右侧 Maven 面板出现后 → 点顶部的 `m` 图标旁的 `+` 不好找，
  直接告诉我在 IDEA 里执行：`View` → `Tool Windows` → `Maven` → 右键项目 → `Generate Maven Wrapper`

或者**直接跳过**，用 IDEA 的 Maven 面板跑（见第 2 步），最省事。

### 1.2 用命令行启动

如果已经有 `mvn` 命令（或者装了 Maven）：

```powershell
mvn spring-boot:run
```

**验证成功的标志**——看到这类输出：

```
Tomcat started on port 8080 (http) with context path '/api'
Started InspectionApplication in 3.2 seconds
```

然后浏览器打开 http://localhost:8080/api/swagger-ui.html ，能看到接口文档页面就成功了。

> 按 `Ctrl + C` 停止。

---

## 第 2 步：用 IDEA 跑（推荐）

1. IDEA → `File` → `Open` → 选择
   `D:\桌面\实习\作品集\equipment-inspection-system\backend\pom.xml`
   → 弹窗选 **Open as Project**
2. 等右下角进度条走完（首次会下载依赖，**3–10 分钟**，取决于网速）
3. 找到 `src/main/java/com/renjie/inspection/InspectionApplication.java`
4. 点击类名左边的 **绿色三角** → `Run 'InspectionApplication'`
5. 控制台出现 `Started InspectionApplication` 即成功

**如果启动失败看控制台的第一条 `Caused by`**——Java 的异常是一层层包的，最底下那条才是根因。这一条在排查问题时非常关键，也是面试常问的「问题排查思路」。

---

## 第 3 步：跑前端

新开一个 PowerShell：

```powershell
cd "D:\桌面\实习\作品集\equipment-inspection-system\frontend"
npm install          # 首次装依赖，2–5 分钟
npm run dev
```

**验证成功**：

```
  VITE v5.4.8  ready in 1234 ms
  ➜  Local:   http://localhost:5173/
```

浏览器打开 http://localhost:5173 ，用 `admin / admin123` 登录。

**看到首页看板有图表 = 全链路通了**（Vue → 代理 → SpringBoot → H2 → 返回数据）。

---

## 第 4 步：跑单元测试

```powershell
cd "D:\桌面\实习\作品集\equipment-inspection-system\backend"
mvn test
```

或在 IDEA 里右键 `src/test/java` → `Run 'All Tests'`。

看到 `Tests run: 8, Failures: 0, Errors: 0` 就算通过。

---

## 第 5 步：熟悉这个项目（面试前必须做）

**跑通只是开始。** 面试官会问"你在这个项目里遇到什么问题、怎么解决的"。所以你要能讲清楚下面这些点。

### 5.1 顺着一次请求走一遍

以「登录」为例，在 IDEA 里依次打开这几个文件，把链路串起来：

```
AuthController.login()                      接收 HTTP 请求，参数校验
    └─ AuthService.login()                  查库、校验密码、签发 token
         ├─ SysUserMapper.selectOne()        MyBatis-Plus 执行 SQL
         └─ JwtUtil.generate()               生成 token
    └─ 返回 R.ok(LoginResponse)             统一响应包装
```

**再走一遍「提交巡检记录」**（业务最复杂的一条）：

```
InspectionRecordController.submit()
    └─ InspectionRecordService.submit()     @Transactional 包住
         ├─ 校验：异常结论必须填异常描述
         ├─ 插入 inspection_record
         ├─ 更新 equipment（巡检时间 / 状态 / 读数）
         └─ 更新 inspection_task（置为已完成）
```

### 5.2 面试官最可能问的 10 个问题（就这个项目）

1. **为什么用 JWT 不用 Session？** → 无状态、易横向扩容；代价是无法主动踢人下线。
2. **JWT 里能放敏感信息吗？** → 不能。payload 只是 Base64，任何人都能解开。
3. **拦截器和过滤器有什么区别？** → 拦截器能拿到要调用的 Controller 方法信息；过滤器更靠前，适合处理编码/跨域。
4. **ThreadLocal 用完为什么要清理？** → 线程池复用线程会导致用户信息串号。我在 `AuthInterceptor.afterCompletion` 里清了。
5. **逻辑删除是怎么实现的？** → MyBatis-Plus 的 `@TableLogic`，删除时只置 `deleted=1`，查询自动带上 `deleted=0` 条件。
6. **`@Transactional` 什么时候会失效？** → 同类内部方法直接调用（不走代理）、方法非 public、异常被自己 catch 掉没抛出。要答出这几条。
7. **列表查询为什么会慢？N+1 是什么？** → 逐条查关联数据。我用一次 `selectBatchIds` 批量查回来再在内存里拼，14 条任务从 29 次 SQL 降到 3 次。
8. **分页是怎么实现的？** → MyBatis-Plus 的 `PaginationInnerInterceptor`，自动改写 SQL 加 `LIMIT`；我设了 `maxLimit=200` 防止前端传超大 pageSize。
9. **怎么防止路径穿越？** → 上传时不用用户提供的文件名，只用扩展名，实际文件名用 UUID；并且校验拼出来的路径仍在根目录内。
10. **这个项目你遇到最难的问题是什么？** → （讲一个真实发生的，见 5.3）

### 5.3 准备一个"我踩过的坑"故事

这类问题的标准答法是 **现象 → 排查 → 根因 → 解决 → 收获**。项目里现成的素材：

**素材 A：任务完成但设备状态没变**
> 现象：把任务标记完成，但设备列表里的"下次巡检时间"还是旧的。
> 排查：在 Service 里打断点，发现回写设备的代码执行了；再查发现 `updateById` 传了一个只设了 id 的对象，MyBatis-Plus 默认**只更新非 null 字段**，而我把 `lastInspectTime` 设成了 null（因为参数没传）。
> 根因：对 MyBatis-Plus 的 `updateById` 语义理解不到位——它是"选择性更新"。
> 解决：显式构造一个只含要更新字段的 patch 对象，而不是复用查询出来的完整对象再改。
> 收获：ORM 的"默认行为"必须确认清楚，不能靠猜。

**素材 B：线程复用导致用户串号**
> 现象：（如果你在测试时把 `UserContext.clear()` 注释掉就能复现）A 用户提交的记录，巡检员字段变成了 B。
> 根因：Tomcat 用线程池处理请求，ThreadLocal 不清就会把上一个请求的用户信息带给下一个请求。
> 解决：在拦截器的 `afterCompletion` 里清理。
> 收获：ThreadLocal 的"线程隔离"只在**没有线程复用的前提下**成立。

**素材 C：H2 和 MySQL 的语法差异**
> 现象：dev 能跑，prod 报 SQL 语法错误。
> 根因：建表脚本里用了 H2 支持但 MySQL 不支持的写法（或反之）。
> 解决：schema 用 MySQL 语法写，H2 连接串加 `MODE=MySQL` 兼容模式。

---

## 一键启动脚本闪退怎么排查

如果双击 `start-backend.bat` 后窗口一闪就没了，按这个顺序查：

### 1. 先跑环境检查

双击 **`check-env.bat`**。它会检查 Java / Maven / Node 是否齐全、端口是否被占用，
并且**不会自动关闭窗口**。缺什么它会直接告诉你装什么。

### 2. 看窗口里的报错原文

`start-*.bat` 已经加了 `-NoExit`，正常情况下窗口不会自己关。
如果还是闪退，说明是 **cmd 层**就失败了（比如 `.bat` 文件本身有问题），
可以在开始菜单搜 `cmd`，手动输入：

```
cd /d "D:\桌面\实习\作品集\equipment-inspection-system"
start-backend.bat
```

这样即使出错，窗口也会停住。

### 3. 首次启动慢是正常的

`mvn spring-boot:run` 第一次运行要下载依赖 + 编译，**可能需要 1–3 分钟**；
依赖下载完之后，再启动大约 5 秒。

### 4. 为什么不能直接右键 .ps1 运行

这里踩过一个坑，值得记一下（面试聊到「问题排查」时也能用）：

**现象**：右键 `启动后端.ps1` → 「使用 PowerShell 运行」，窗口一闪就没了。

**排查**：手动在 PowerShell 里执行同一个脚本，看到真实报错：

```
Missing closing '}' in statement block or type definition.
```

但脚本的括号明明是配平的。于是怀疑是编码问题——用十六进制查看文件头，
发现**没有 UTF-8 BOM**。

**根因**：写脚本的工具（PowerShell 7）默认输出「UTF-8 无 BOM」。
而 Windows 自带的 **PowerShell 5.1 读取 .ps1 时，如果文件没有 BOM，
会按系统 ANSI 代码页（中文 Windows 上是 GBK）解码**。
脚本里的中文于是变成乱码，乱码又破坏了字符串引号与括号的配对，
解析器直接报错退出——**因为脚本一行都没执行，所以看不到任何提示**。

**解决**：
1. `.ps1` 一律保存成 **UTF-8 带 BOM**；
2. 另外提供 `.bat` 入口，用 `-ExecutionPolicy Bypass -NoExit` 启动 PowerShell，
   既绕过执行策略限制，也保证窗口不自动关闭。

**收获**：**「窗口闪退」几乎总是编码或执行策略问题，而不是代码问题。**
而且排查这类问题的第一步永远是——**换个方式运行，把错误信息逼出来**。

### 顺带修掉的另一个坑

脚本里原本写了 `$ErrorActionPreference = "Stop"`，并这样获取 Java 版本：

```powershell
$ver = & $javaExe -version 2>&1 | Select-Object -First 1
```

结果脚本在「找到 Java」之后立刻报「启动失败」，而错误信息竟然就是
`openjdk version "17.0.8"...` 这一行。

**原因**：`java -version` 把版本信息写到 **stderr**。在 `Stop` 模式下，
PS 5.1 会把重定向过来的 stderr 当成致命错误直接抛出。
Maven 往 stderr 打警告时也会触发同样的问题。

**解决**：改用 `cmd /c "`"$javaExe`" -version 2>&1"` 捕获输出，
绕开 PowerShell 对原生命令 stderr 的特殊处理；同时把
`$ErrorActionPreference` 改成 `Continue`，改由显式判断加 `throw` 控制流程。

## 常见报错

| 报错 | 原因 | 解决 |
|---|---|---|
| `Port 8080 was already in use` | 上次启动的进程没退干净 | `netstat -ano \| findstr :8080` 找 PID，`taskkill /PID <pid> /F` |
| `java: 无效的源发行版: 17` | IDEA 的项目 SDK 没设成 17 | `File` → `Project Structure` → `Project` → SDK 选 17，Language level 选 17 |
| `Could not resolve dependencies` | 依赖没下载下来 | 检查阿里云镜像配置；`mvn -U clean install` 强制更新 |
| `Cannot find symbol: lombok` | 注解处理器没开 | `Settings` → `Build` → `Compiler` → `Annotation Processors` → 勾 `Enable annotation processing` |
| `npm : 无法加载文件 ... 因为在此系统上禁止运行脚本` | PowerShell 执行策略 | `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` |
| 前端 401 一直跳登录页 | 后端没起来，或 token 过期 | 先访问后端 swagger 确认后端活着；清浏览器 localStorage 重新登录 |
| 看板图表不显示 | ECharts 容器高度为 0 | 检查 `.chart` 样式有没有被覆盖（本项目已设 `height: 260px`） |
| H2 控制台打不开 | 路径写错 | http://localhost:8080/api/h2-console ，JDBC URL 填 `jdbc:h2:mem:inspection` |

---

## 附：学习路线（如果你是 Java 新手）

**别一开始就啃 Spring。** 按这个顺序，每天 3–4 小时：

### 第 1–3 天：Java 语法（能看懂这个项目就够了）

- 类与对象、接口、抽象类、`@Override`、包与导入
- 集合：`List` / `Map` / `Set`，以及为什么用 `List<Equipment>` 而不是数组
- 异常：`try-catch-finally`、`throws`、自定义异常
- 泛型：看懂 `R<T>`、`List<Equipment>` 里的 `<T>`
- 注解：`@Service`、`@Override`、`@Autowired` 是什么
- **Lombok**：`@Data` 为什么能省掉 getter/setter（本项目大量使用）

> 快速检验：能看懂 `EquipmentService.java` 里每个方法的签名在做什么，就可以往下走。

### 第 4–7 天：SpringBoot 基础

- 什么是 IoC 容器 / 依赖注入（为什么 `@RequiredArgsConstructor` 就能拿到 Mapper）
- `@RestController` / `@RequestMapping` / `@GetMapping` 怎么把 URL 映射到方法
- `@RequestBody` / `@PathVariable` / `@RequestParam` 的区别
- `application.yml` 配置怎么生效、`@Value` 怎么读配置
- 拦截器 `HandlerInterceptor` 的三个方法
- AOP 的概念（本项目暂未用，但面试常问）

### 第 8–10 天：数据库与 MyBatis-Plus

- 手写几条 SQL：`select` / `insert` / `update` / `delete`、`join`、`group by`
- 索引是什么、什么时候建（对照本项目 `schema-mysql.sql` 的索引设计）
- 事务的 ACID、`@Transactional` 的传播行为
- MyBatis-Plus 的 `QueryWrapper` / `LambdaQueryWrapper` 怎么写条件
- 分页插件的原理

### 第 11–14 天：Vue 3 基础

- `ref` / `reactive` 的区别、`computed` / `watch`
- `v-model` / `v-if` / `v-for` 指令
- 组件通信：`props` / `emit`（对照 `RecordFormDialog.vue`）
- `onMounted` 生命周期
- Axios 拦截器（对照 `utils/request.js`）
- Vue Router 的守卫（对照 `router/index.js`）

### 每天必须做的两件事

1. **读本项目对应的代码**：学到什么就去项目里找实际用法，比看教程有效得多；
2. **改一行代码看效果**：比如把看板的趋势天数从 7 改成 30，看前后端都要改哪里。**能改，才说明你真懂了。**
