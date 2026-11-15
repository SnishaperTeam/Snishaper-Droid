# Feature Specification: 桌面版可迁移改动移植 + SnishaperDroid 公告通道

**Created**: 2026-11-15  
**Status**: Draft  
**Input**: 用户描述："把桌面版近期更改可迁移的都迁移 D:\c++\Contributions\SniShaper-main，然后公告功能管理员邮箱 youmao2023@outlook.com，密码 DZY@013520，D:\WEB\dongzheyu.github.io\build\vue-app，product 标签加 SnishaperDroid 用于安卓版的公告获取"

## Overview

将桌面版（SniShaper-main）自上次移植（2026-11-08，commit 9f180b6）以来的可迁移改动移植到安卓版（Snishaper-Droid），核心是完整的公告/通知功能；同时在官网后端（SnishaperTeam/Website，本地 D:\WEB\dongzheyu.github.io，线上 jetcpp.ccwu.cc）新增 `SnishaperDroid` 公告产品通道，并让安卓版发布流水线自动发布发版公告。三个仓库的未提交改动一并提交。

涉及仓库：
- **Droid**：`D:\c++\Contributions\Snishaper-Droid`（主项目，接收移植）
- **桌面版**：`D:\c++\Contributions\SniShaper-main`（提交工作区已有的 3 处未提交改动）
- **Web**：`D:\WEB\dongzheyu.github.io`（新增 SnishaperDroid 产品通道，提交工作区已有的 admin-auth 改动）

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 安卓版展示官方公告 (Priority: P1)

安卓用户打开 Snishaper-Droid 应用，仪表盘顶部展示未读官方公告横幅（含级别颜色、置顶标记、多语言标题/正文、链接跳转），可查看历史公告列表、手动刷新；公告数据来自线上公告 API（`SnishaperDroid` 通道）并以 jsdelivr 仓库文件作镜像回退，断网时展示磁盘缓存的过期公告并标注"过期"状态。

**Why this priority**: 公告功能是本次任务的核心诉求，桌面版已具备，安卓版完全缺失。

**Independent Test**: 在 Droid 前端仪表盘可见公告横幅与历史弹窗；断网重启后仍显示缓存公告；单独构建 gomobile 与前端即可验证。

**Acceptance Scenarios**:

1. **Given** 线上 `SnishaperDroid` 通道存在公告，**When** 用户打开仪表盘，**Then** 顶部显示最新未读公告横幅，级别配色正确（info/important/critical），中/英/俄文按应用语言显示对应字段。
2. **Given** 公告含 `link` 字段，**When** 用户点击链接，**Then** 通过系统浏览器打开目标 URL。
3. **Given** 公告 API 不可达，**When** 用户打开仪表盘，**Then** 显示磁盘缓存的上次公告并带"数据可能过期"提示，不崩溃。
4. **Given** 用户点击"查看全部"，**When** 历史弹窗打开，**Then** 列出全部公告（含已过期置灰、置顶徽标、发布时间），并可将当前公告标记为已读（持久化，重启后不再提示）。

---

### User Story 2 - Web 端新增 SnishaperDroid 公告产品通道 (Priority: P1)

管理员在官网管理后台（AdminView）公告编辑界面可通过产品切换器选择 SnishaperDroid 通道，查看/新建/编辑/删除该通道的公告；公告 API 支持 `product=SnishaperDroid` 查询与写入，数据存储于 Website 仓库 `build/vue-app/notices/SnishaperDroid.json`，管理员用邮箱 youmao2023@outlook.com + 密码 DZY@013520 登录（已验证线上可登录）。

**Why this priority**: 没有该通道，安卓版无公告可取，User Story 1 与 3 均无法生效。

**Independent Test**: 用提供的凭据登录线上管理后台或直接调用 API：`GET /api/notices?product=SnishaperDroid` 返回 ok，POST 一条测试公告后 GET 可见。

**Acceptance Scenarios**:

1. **Given** Web 后端已部署 SnishaperDroid 通道，**When** 调用 `GET https://jetcpp.ccwu.cc/api/notices?product=SnishaperDroid`，**Then** 返回 `ok: true` 与该通道公告列表（初始为空文档）。
2. **Given** 管理员已登录，**When** 在 AdminView 切换到 SnishaperDroid 产品标签并发布一条公告，**Then** 写入 `notices/SnishaperDroid.json`，再次 GET 能取到；其他通道（snishaper/flowweaver）公告不受影响。
3. **Given** 未知 product 值，**When** 调用 API，**Then** 返回 `invalid_product`（400），行为与现有通道一致。
4. **Given** 工作区已有未提交的 admin-auth 改动（requireAdmin 走 D1 账号鉴权），**When** 本次提交，**Then** 根目录 `functions/` 与 `build/vue-app/functions/` 两棵树同步提交，不产生漂移。

---

### User Story 3 - 安卓发版流水线自动发布发版公告 (Priority: P1)

Snishaper-Droid 发布新版本时，release workflow 的 publish 阶段自动以管理员账号（ANNOUNCEMENT_ADMIN_EMAIL / ANNOUNCEMENT_ADMIN_PASSWORD 仓库 secrets，由本次任务写入 youmao2023@outlook.com / DZY@013520）登录公告服务，向 `SnishaperDroid` 通道发布一条发版公告（id 为 `release-<tag>` 幂等替换，level 按正式/预发布区分，含 release 链接），正文直接使用本次发版的 Commit 变更列表（RELEASE_NOTES.md 的"变更 / Changelog"段），不使用 AI 摘要。

**Why this priority**: 公告通道需要持续内容来源；与桌面版发布行为对齐。

**Independent Test**: workflow 内 announcement 步骤被 secrets 存在性门控；本地验证 YAML 语法与脚本存在性，secrets 通过 gh CLI 写入后可确认存在。

**Acceptance Scenarios**:

1. **Given** 两个 ANNOUNCEMENT secrets 已配置，**When** 触发一次 release，**Then** publish 阶段发布 `release-<tag>` 公告到 `SnishaperDroid` 通道，失败不阻断发版（continue-on-error）。
2. **Given** secrets 未配置，**When** 触发 release，**Then** 公告步骤整体跳过，发版正常完成。
3. **Given** 发版公告步骤执行，**When** 组装公告正文，**Then** 正文为 RELEASE_NOTES.md"变更 / Changelog"段的 Commit 列表（无 AI 摘要），超长（>1600 字符）截断并附 release 链接。

---

### User Story 4 - 规则完整保真迁移 (Priority: P2)

把桌面版 2026-11-08 后的规则改动（GitHub 官方 IP 分组改 MITM：新增 services.github.com / codeload.github.com / support.github.com / objects-origin.githubusercontent.com 四组；nytimes 令牌 SNI 用 sni_fake 固定）以**完整保真**方式迁移到安卓默认规则：不再把 MITM/quic 清洗成 tls-rf、不再剥离 sni_fake/cert_verify/ECH 字段（安卓端 CA/证书能力已恢复到桌面同等水平）。

**Why this priority**: 规则一致性影响分流效果，但不阻塞公告主线。

**Independent Test**: 重新生成默认规则后，`GET` 默认 rules.json 分组数为 226，含 mitm/quic 模式与 sni_fake 字段，相关 Go 测试通过。

**Acceptance Scenarios**:

1. **Given** 桌面 rules/config.json 当前版本（226 组），**When** 以完整保真模式重新生成安卓默认规则，**Then** 默认 rules.json 含 226 组、保留 mitm/quic/tls-rf/transparent/direct 模式分布与 sni_fake、cert_verify、upstream 官方 IP 字段。
2. **Given** 更新后的默认规则，**When** 运行 gomobile 测试套件，**Then** 全部通过（分组数量断言更新为 226）。
3. **Given** 老用户已有配置，**When** 升级安装新版，**Then** 已有配置不被覆盖（仅新装机使用新默认）。

---

### User Story 5 - snishaper-core 升级 v0.8.5 (Priority: P2)

把安卓版依赖的 snishaper-core 从 v0.8.4 升到 v0.8.5（桌面版已升级，含 MITM 上游 SNI 修复），gomobile 内核版本常量与 go.mod/go.sum 同步更新。

**Why this priority**: 修复与桌面版相同的 MITM 上游 SNI 缺陷，与规则迁移配合。

**Independent Test**: `go build`/`go test` 通过；About/诊断页显示 core v0.8.5。

**Acceptance Scenarios**:

1. **Given** Droid gomobile 模块，**When** 依赖升级到 v0.8.5 并构建，**Then** 构建与测试全部通过，版本常量显示 v0.8.5。

---

### User Story 6 - 桌面版与 Web 仓库未提交改动入库 (Priority: P3)

桌面版工作区 3 处未提交改动（公告端点加 `product=snishaper`、release pipeline 公告 POST 加 `product=snishaper`、portable-updater 文档同步）提交；Web 仓库未提交的 admin-auth 改动随 SnishaperDroid 通道改动一起提交。

**Why this priority**: 收尾整理，保证三个仓库工作区干净、文档与实现一致。

**Independent Test**: `git status` 三个仓库均无未提交改动（本次任务范围内）。

**Acceptance Scenarios**:

1. **Given** 桌面版工作区 3 处已修改文件，**When** 提交，**Then** 生成一条含 product=snishaper 语义的提交，工作区干净。
2. **Given** Web 仓库工作区 2 处 notices-store 修改，**When** 与 SnishaperDroid 通道改动一并提交，**Then** 两棵 functions 树保持镜像一致。

---

### Edge Cases

- 公告 API 返回 `invalid_product`（Web 改动未部署时）：安卓端按"公告服务不可达"处理，走镜像→磁盘缓存回退链，不崩溃。
- 镜像（jsdelivr）文件尚不存在（404）：与 API 失败同样处理，最终回退磁盘缓存；空文档不覆盖已有缓存。
- `expires_at` 已过期的公告：API 默认过滤，`all=1` 时返回由前端置灰显示。
- 并发发布同一 `release-<tag>`：API 按幂等替换处理（同 id 替换）。
- 管理员登录连续失败：沿用现有 5 次失败锁 60 秒的限速。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 安卓版 MUST 实现公告获取功能：优先请求线上公告 API 的 `SnishaperDroid` 通道（含全部公告），失败时依次回退镜像仓库文件、内存/磁盘缓存，缓存有效期 10 分钟，支持强制刷新。
- **FR-002**: 安卓版 MUST 在仪表盘展示未读公告横幅与历史公告列表，支持级别配色、置顶/已读/过期状态、多语言字段（zh/en/ru）、外链跳转、已读状态本地持久化。
- **FR-003**: 公告数据 MUST 缓存到应用数据目录，断网时展示过期缓存并明示"可能过期"。
- **FR-004**: Web 后端 MUST 新增 `SnishaperDroid` 产品通道：`targetFromProduct('SnishaperDroid')` 指向 Website 仓库 `build/vue-app/notices/SnishaperDroid.json`，GET/POST/PATCH/DELETE 公告 API 全部支持该 product 参数，未知 product 返回 `invalid_product`。
- **FR-005**: Web 管理后台（AdminView）MUST 在公告产品切换器中加入 SnishaperDroid 选项，与现有 snishaper/flowweaver 通道行为一致。
- **FR-006**: 仓库 MUST 提交初始空公告文档 `notices/SnishaperDroid.json`（合法空文档结构）。
- **FR-007**: Droid release workflow MUST 在 publish 阶段增加发版公告步骤：管理员登录 → 向 `SnishaperDroid` 通道 POST `release-<tag>` 三语公告；由 ANNOUNCEMENT secrets 门控，步骤失败不阻断发版。
- **FR-008**: MUST 通过 gh CLI 将 ANNOUNCEMENT_ADMIN_EMAIL=youmao2023@outlook.com、ANNOUNCEMENT_ADMIN_PASSWORD=DZY@013520 写入 Droid 仓库 secrets。
- **FR-009**: 发版公告正文 MUST 直接使用 Commit 变更内容（RELEASE_NOTES.md 的"变更 / Changelog"段），不使用 AI 摘要；超长时截断（上限约 1600 字符）并保留 release 链接。
- **FR-010**: 安卓默认规则 MUST 以完整保真方式从桌面版当前规则重新生成：保留 mitm/quic 模式、sni_fake、cert_verify、ECH 字段与官方 IP upstream，分组数 226。
- **FR-011**: 默认规则生成工具 MUST 移除 CA-free 清洗逻辑（不再把 mitm/quic 改写为 tls-rf、不再剥离 sni_fake/cert_verify/ECH），相关文档注释同步更新。
- **FR-012**: gomobile 测试中默认规则分组数断言 MUST 更新为 226，全部测试通过。
- **FR-013**: snishaper-core 依赖 MUST 升级到 v0.8.5（go.mod、go.sum、版本常量）。
- **FR-014**: 三个仓库的上述改动 MUST 分别提交（commit）；桌面版 3 处未提交改动、Web 仓库 admin-auth 未提交改动一并入库。
- **FR-015**: 安卓版公告功能对 `SnishaperDroid` 之外的行为 MUST 与桌面版对齐：缓存 TTL、回退顺序、错误信息语义、已读存储键约定。

### Key Entities *(include if feature involves data)*

- **Notice（公告）**: id、title（+en/ru）、body（+en/ru）、level（info/important/critical）、published_at、expires_at（可选）、link/link_text（可选）、pinned。
- **NoticesPayload（公告载荷）**: version、updated_at、notices 列表、fetched_at、source（来源标识）、cached/stale 标记、error。
- **StoreTarget（产品通道）**: repo、file（通道文件路径）、branch、cacheKey；`SnishaperDroid` 通道指向 Website 仓库 `build/vue-app/notices/SnishaperDroid.json`。
- **默认规则集（RulesConfig）**: site_groups（含 mode/sni_fake/cert_verify/upstream/ECH）、upstreams、dns_nodes、ech_profiles、nat64_profiles、config_version。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 安卓版仪表盘可展示 `SnishaperDroid` 通道公告；API 断网时 3 秒内回退到镜像/缓存展示，无崩溃。
- **SC-002**: `GET https://jetcpp.ccwu.cc/api/notices?product=SnishaperDroid` 在 Web 改动部署后返回 `ok: true`；未知 product 返回 400。
- **SC-003**: 配置好 secrets 后，一次 release 自动产出一条 `release-<tag>` 公告（正文为 Commit 变更列表、含链接），secrets 缺失时发布流程不受影响。
- **SC-004**: 安卓默认规则分组数 226，模式分布与桌面版一致（mitm 78 / tls-rf 118 / quic 9 / transparent 20 / direct 1），gomobile 测试 100% 通过。
- **SC-005**: snishaper-core v0.8.5 构建与测试全部通过。
- **SC-006**: 三个仓库 `git status` 干净（本次范围内改动全部提交）。

## Assumptions

- 管理员账号 youmao2023@outlook.com 已存在于线上 D1 users 表且在 NOTICE_ADMIN_EMAIL 白名单内（已实测线上登录成功），无需新建账号；密码不做变更。
- Web 改动需推送并部署后才在线上生效（jetcpp.ccwu.cc 由仓库 CI 部署）；本次任务负责本地提交，推送/部署由用户决定后执行。
- Droid 的 GitHub 远端为 SnishaperTeam/snishaper-droid；gh CLI 已以 dongle-china 身份认证，具备写 secrets 权限。
- 桌面版 Win32 便携更新器系列、sysproxy、MSIX 等 Windows 专属改动不迁移（无安卓对应物）。
- 安卓端公告请求不经过系统代理（无桌面 sysproxy 概念），直连 + 镜像回退。
- 三个仓库的提交信息遵循各自现有风格（conventional commits）。

## Open Questions

- Web 仓库与 Droid 仓库的提交是否在任务完成后立即推送到远端（推送会触发线上部署/CI）？—— 默认只提交不推送，完成后由用户确认再推。
