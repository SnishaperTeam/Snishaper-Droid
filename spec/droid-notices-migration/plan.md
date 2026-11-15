# Implementation Plan: droid-notices-migration

**Input**: Feature specification from `spec/droid-notices-migration/spec.md`

## Summary

把桌面版 2026-11-08 以来可迁移改动移植到 Snishaper-Droid：完整公告功能（gomobile 后端 + React 前端横幅/历史 + 磁盘缓存回退链）、默认规则完整保真迁移（226 组，保留 mitm/sni_fake/cert_verify）、snishaper-core 升 v0.8.5；在 Website 后端新增 `SnishaperDroid` 公告产品通道（双 functions 树 + AdminView 切换器 + 种子 JSON），Droid release 流水线自动发 Commit 内容发版公告（管理员凭据写入仓库 secrets）；三个仓库分别提交，默认不推送。

## Technical Context

**Language/Version**: Go 1.27（gomobile 模块）、Kotlin（Android bridge）、TypeScript/React 19 + MUI（frontend-android）、TypeScript（Cloudflare Pages Functions）
**Primary Dependencies**: snishaper-core v0.8.4→v0.8.5、gomobile bind AAR、@wailsio runtime（Android transport）、MUI 9、Cloudflare Pages Functions + D1
**State Management**: 前端 React state（NoticeCenter 内部 state + localStorage 已读键）；Go 侧 App 结构体互斥锁保护的公告缓存
**Storage**: 公告缓存 `<dataDir>/config/notices_cache.json`（桌面同款文件名）；已读公告 `localStorage['snishaper.notices.read_ids']`；公告文档 GitHub 仓库文件（`notices/SnishaperDroid.json`）
**Testing**: `go test ./...`（gomobile，含 sanitize_test 分组数断言）；前端 `npm run build`（frontend-android，tsc + vite）；Gradle 组装 APK 验证 AAR/bridge 链路
**Target Platform**: Android（minSdk 24）+ Cloudflare Pages（jetcpp.ccwu.cc）+ GitHub Actions（release workflow）
**Project Type**: mobile-app（gomobile + WebView 前端）+ web-service（Pages Functions）
**Performance Goals**: 公告请求超时 8s；缓存 TTL 10 分钟；断网回退不阻塞 UI
**Constraints**: 公告步骤失败不得阻断发版（continue-on-error）；未知 product 返回 400 `invalid_product`；不使用 AI 摘要
**Scale/Scope**: 3 仓库；Droid 新增 Go 文件 1 + 前端组件 1 + bridge 分派 1 行级改动；Web 新增通道（2 树 × 1 文件改动 + 1 种子文件 + AdminView 1 处）；桌面版仅提交已有未提交改动

## Project Structure

### Documentation (this feature)

```text
D:\c++\Contributions\Snishaper-Droid\spec\droid-notices-migration\
├── spec.md              # 需求规格（已确认）
├── plan.md              # 本文件
└── tasks.md             # 任务分解（Phase 3 产出）
```

### Source Code (repository root)

```text
# 仓库 1：Droid（D:\c++\Contributions\Snishaper-Droid）
gomobile/
├── go.mod / go.sum                        # snishaper-core v0.8.4 → v0.8.5
├── mobile/
│   ├── app.go                             # coreVersion 常量 → v0.8.5；App 增加公告缓存字段
│   ├── notices.go                         # [新增] 公告获取/缓存/回退（移植桌面 app_notices.go）
│   └── sanitize_test.go                   # 默认规则分组断言 222 → 226
├── cmd/gen-defaults/main.go               # 移除 CA-free 清洗逻辑（保留 settings 安卓覆盖）
app/src/main/assets/defaults/rules.json    # 完整保真重新生成（226 组）
app/src/main/java/com/snishaper/android/webview/EngineBridge.kt
                                           # dispatch 表增加 "GetNotices" 分派
frontend-android/src/
├── api/bindings.ts                        # 增加 GetNotices 绑定
├── components/NoticeCenter.tsx            # [新增] 公告横幅 + 历史弹窗（移植桌面组件）
├── pages/Dashboard.tsx                    # 挂载 <NoticeCenter />
├── lib/icons.tsx                          # [按需] 补 X / Info 图标导出
└── i18n/locales/{zh,en,ru}.json           # 增加 notices.* 文案键
.github/workflows/release.yml              # publish 阶段增加发版公告步骤

# 仓库 2：Web（D:\WEB\dongzheyu.github.io）
functions/_lib/notices-store.ts            # DROID_TARGET + targetFromProduct('SnishaperDroid')
build/vue-app/functions/_lib/notices-store.ts   # 同步镜像（rsync 约定）
build/vue-app/notices/SnishaperDroid.json  # [新增] 空公告文档种子
build/vue-app/src/views/AdminView.vue      # noticeProducts 增加 SnishaperDroid 选项
# 工作区已有未提交改动（requireAdmin → resolveAdmin D1 鉴权，2 文件）随本次一并提交

# 仓库 3：桌面版（D:\c++\Contributions\SniShaper-main）
# 工作区已有 3 处未提交改动直接提交：
#   app/app_notices.go（端点 + product=snishaper）
#   .github/workflows/_release_pipeline.yml（公告 POST + product=snishaper）
#   docs/portable-updater.md（product 必填参数说明）
```

**Structure Decision**: 三个仓库均为既有项目，完全遵循各自现有架构与目录约定，不引入新结构。Droid 遵循现有 gomobile `XxxJSON()` 字符串返回约定、EngineBridge dispatch 表路由、React 页面/组件分层；Web 遵循根 `functions/` 与 `build/vue-app/functions/` 双树镜像约定。

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| 跨 3 仓库改动 | 用户明确要求迁移 + 通道 + 提交三处 | 单仓库无法打通"发版→公告→安卓展示"链路 |
| 完整保真规则迁移（去除 CA-free 清洗） | 用户明确选择；安卓 CA 能力已恢复桌面同级，清洗逻辑会丢 mitm/sni_fake/cert_verify | 保持 CA-free 约定被用户否决 |

## Research & Decisions

- **Decision**: product 标签用字面值 `SnishaperDroid`（精确匹配），通道文件 `build/vue-app/notices/SnishaperDroid.json`
  **Rationale**: 用户明确选择字面值而非小写约定；API 精确匹配保持与 snishaper/flowweaver 实现一致
  **Alternatives considered**: 小写 `snishaperdroid` + 大小写不敏感匹配（被用户否决）
- **Decision**: Droid 公告 Go 方法命名为 `GetNoticesJSON(force bool) (out string)`，Kotlin dispatch 为 `"GetNotices" -> parse(app.getNoticesJSON(a.bool(0)))`
  **Rationale**: 遵循 Droid 现有 gomobile `XxxJSON()` 字符串返回约定与 EngineBridge JSON passthrough 模式（已核实 Args.bool 助手存在）
  **Alternatives considered**: 直接绑定结构体（gomobile bind 支持，但 Droid 全部走 JSON 字符串，保持一致）
- **Decision**: 安卓公告回退链 = 线上 API（直连）→ jsdelivr 镜像 → 内存缓存 → 磁盘缓存；不做 system-proxy 源
  **Rationale**: 桌面的 system-proxy 源依赖 Windows sysproxy 包，安卓无对应物；其余回退链与桌面一致
- **Decision**: 公告正文 = RELEASE_NOTES.md"变更 / Changelog"段的 Commit 列表，超 1600 字符截断；不移植 AI 三语摘要脚本
  **Rationale**: 用户明确要求"公告不用 AI 总结，就写 Commit 内容"；Droid release.yml 已生成该段（`git log --pretty=format:"- %s by @%an in ..."`），直接提取复用
  **Alternatives considered**: 移植桌面 generate-notice-summaries.ps1（被用户否决）
- **Decision**: gen-defaults 保留 settings 的安卓覆盖（migration 关闭、auto-routing 为空、Cloudflare autoUpdate 关闭、语言/主题默认），仅移除 site_groups 的 CA-free 清洗
  **Rationale**: settings 覆盖是安卓平台差异（非 CA 相关），与保真迁移无关，保留现状
- **Decision**: release.yml 公告步骤置于 GitHub Release 创建之后，continue-on-error + secrets 门控（`ANNOUNCEMENT_READY`），登录 `POST /api/admin/session` 取 token 后 `POST /api/notices?product=SnishaperDroid`
  **Rationale**: 与桌面 publish 阶段行为对齐（桌面已验证该流程线上可用）；id=`release-<tag>` 幂等替换
- **Decision**: 管理员凭据通过 `gh secret set` 写入 SnishaperTeam/snishaper-droid（gh 已以 dongle-china 认证，具 admin 权限 scope）
  **Rationale**: 用户明确要求由本次任务设置；secrets 不落仓库文件
- **Decision**: Web 双树（根 `functions/` 与 `build/vue-app/functions/`）同步修改 notices-store.ts，随工作区未提交 admin-auth 改动一并提交
  **Rationale**: 仓库 184322c 提交已确立"单一源 + 镜像"约定，live 部署取 `build/vue-app/functions`
- **Decision**: 三仓库只 commit 不 push
  **Rationale**: 推送会触发线上部署与 CI；由用户事后确认推送时机

## Data Model

### Notice（公告记录，与桌面/线上现有 schema 完全一致）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 唯一 id；发版公告为 `release-<tag>` |
| title / title_en / title_ru | string | 三语标题（en/ru 可选） |
| body / body_en / body_ru | string | 三语正文（en/ru 可选） |
| level | string | `info` / `important` / `critical` |
| published_at | string | ISO 8601 |
| expires_at | string? | 过期时间（可选，过期默认过滤） |
| link / link_text | string? | 外链与文案（可选） |
| pinned | bool | 置顶 |

### NoticesPayload（Droid gomobile 返回，桌面同构）

| 字段 | 类型 | 说明 |
|------|------|------|
| version | int | 文档版本 |
| updated_at | string | 文档更新时间 |
| notices | []Notice | 公告列表 |
| fetched_at | int64 | 本次抓取 unix 时间 |
| source | string | `api` / `mirror` / `disk-cache` / `memory` |
| cached / stale | bool | 缓存/过期标记 |
| error | string? | 回退时的错误信息 |

### StoreTarget（Web 产品通道）

| 字段 | SnishaperDroid 通道取值 |
|------|------------------------|
| repo | `SnishaperTeam/Website` |
| file | `build/vue-app/notices/SnishaperDroid.json` |
| branch | `main` |
| cacheKey | `https://notices.jetcpp.ccwu.cc/api/notices?product=SnishaperDroid` |

### 默认规则集（完整保真后）

site_groups 226 组（mitm 78 / tls-rf 118 / quic 9 / transparent 20 / direct 1），字段含 mode、sni_fake、cert_verify、upstream（官方 IP）、ECH 系列；upstreams / dns_nodes / ech_profiles / nat64_profiles 与桌面当前版本一致；config_version 1 不变。

## Contracts & Interfaces

### 公告 API（线上 jetcpp.ccwu.cc，已有实现，新通道复用）

- `GET /api/notices?product=SnishaperDroid[&all=1][&limit=n]` → `{ok, version, updated_at, count, total, source, notices[]}`；未知 product → 400 `invalid_product`
- `GET /api/notices/:id?product=SnishaperDroid` → `{ok, version, notice}`
- `POST /api/notices?product=SnishaperDroid`（Bearer 管理员 token）→ 发布/幂等替换公告
- `PATCH/DELETE /api/notices/:id?product=SnishaperDroid`（Bearer）
- `POST /api/admin/session` body `{email, password}` → `{ok, admin, token, user}`（已验证凭据可登录）

### Droid JS 桥接（新增）

- `GetNotices(force: boolean) => Promise<NoticesPayload>`（前端 bindings → EngineBridge `"GetNotices"` → gomobile `GetNoticesJSON`）
- `OpenURL(url)`：已有实现（`__snishaperOpenURL` hook），公告外链复用

### Droid release workflow（新增步骤契约）

- 门控：`ANNOUNCEMENT_READY: secrets.ANNOUNCEMENT_ADMIN_EMAIL != '' && secrets.ANNOUNCEMENT_ADMIN_PASSWORD != ''`
- 正文：RELEASE_NOTES.md"## 变更 / Changelog"段原文（Commit 列表），>1600 字符截断加 `...`
- 公告对象：`id=release-<tag>`、`title=Snishaper-Droid <version> 发布`、`level=正式版 important / 预发布 info`、`link=https://github.com/SnishaperTeam/snishaper-droid/releases/tag/<tag>`、`pinned=false`
- 失败语义：continue-on-error，绝不阻断发版

### gh secrets（执行一次）

- `gh secret set ANNOUNCEMENT_ADMIN_EMAIL --body "youmao2023@outlook.com" --repo SnishaperTeam/snishaper-droid`
- `gh secret set ANNOUNCEMENT_ADMIN_PASSWORD --body "DZY@013520" --repo SnishaperTeam/snishaper-droid`

### 提交契约（conventional commits，各仓库风格）

- Droid：`feat(android): port the desktop notices feature and add the SnishaperDroid announcement channel`（规则/版本随同或按逻辑拆分多条）
- Web：`feat(notices): add SnishaperDroid product channel`（含 admin-auth 工作区改动）
- 桌面版：`feat(notices): scope the announcement endpoints to the snishaper product`（3 文件）
