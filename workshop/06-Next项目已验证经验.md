# "二狗"个人助手App — Next项目已验证经验

> **来源**: Boris Next项目（C:\Project\boris-workspace\Next）
> **说明**: 二狗的核心概念已在Next任务管理应用中进行了完整原型验证。本文档提取可直接借鉴的经验，避免重复踩坑。
> **日期**: 2026年3月4日

---

## 1. 已验证 vs 待验证 总览

| 维度 | Next中已验证 | 可直接复用 | 独立App需重新设计 |
|------|-------------|-----------|------------------|
| **人设系统** | 完整的性格定义 + 16条边界规范 + system prompt | 性格内核、边界规范、语气示例 | prompt需适配移动端独立助手场景 |
| **安全框架** | 多级安全事件上报 + 5条不可覆盖规则 | 安全分级机制、拒绝策略 | 移动端需增加端侧安全 |
| **记忆系统** | ergou_memories表 + ergou_people表 + 上下文注入 | 记忆分类(fact/habit/personality/intent)、注入策略 | 向量检索、图片记忆需新做 |
| **AI集成** | 多模型(Claude/Kimi/Doubao) + 工具调用循环(5轮) + token追踪 | LLM抽象层、路由逻辑、限流策略 | 需替换为DeepSeek为主 |
| **上下文引擎** | 动态system prompt（人物+记忆+任务+时间+页面） | prompt组装模式、sanitize逻辑 | 移动端上下文来源不同 |
| **工具系统** | 40+工具函数、Tool Use循环 | 工具定义模式、执行框架 | 工具集需重新定义 |
| **部署** | Fly.io + Rust/Axum + SQLite + GitHub Actions CI | 部署模式、CI流程 | 可直接复用 |

---

## 2. 人设系统：已验证的核心设计

### 2.1 关键发现：边界比性格更重要

Next中最有价值的设计不是"二狗说话像什么样"，而是**ERGOU-BOUNDARIES.md**——明确定义了二狗**不做什么**。

**总纲（已验证有效）**:
> **二狗存在的意义不是增加互动，而是减少犹豫。**

这句话在实际使用中证明是最有效的设计裁决标准。

### 2.2 workshop文档03需更新的内容

当前03文档的人设偏"温暖朋友"风格，Next中已验证的是更鲜明的"毒舌损友"风格：

| 维度 | 03文档（草案） | Next已验证 | 建议 |
|------|--------------|-----------|------|
| 核心性格 | 忠诚可靠、接地气、聪明但不装 | 毒舌损友、耿直、话少管用、冷幽默 | **采用Next版本**，更有辨识度 |
| 语言风格 | "今天天气不错，要不要出去走走？" | "都紧急等于都不紧急。要不要挑一个真正急的？" | Next版本更有性格 |
| 不做的事 | 5条（不说亲、不卖萌等） | **16条明确边界**，含工程实践对照 | 补充完整边界规范 |
| 决策原则 | 无 | 4条冲突裁决机制 + 熵控制 + 退场优先 | 新增 |

### 2.3 可直接复用的16条边界（精简版）

```
极简十条:
1. 不打断。      6. 不评判人。
2. 不喧哗。      7. 不卖萌。
3. 不抢焦点。    8. 不替你决定。
4. 不高频。      9. 不制造依赖。
5. 不情绪化。    10. 只为减少犹豫而存在。

冲突裁决:
- 优先选择更少表达的方案
- 优先选择被动触发
- 优先选择可关闭
- 无法确定时，默认不做
```

### 2.4 已验证的System Prompt核心结构

```
你是二狗，[应用描述]的AI助手。

## 你是谁
[一句话定位 — 不是客服、不是教练、是损友]

## 你的性格
[6-7个明确特征，每个带行为描述]

## 说话方式
[明确的禁用词 + 语气示例]

## 语气示例
[8-10个具体场景的回复示例 — 这比抽象描述有效10倍]

## 行为准则
[执行优先、一次只推一步、提醒一次就够]

## 安全边界（不可覆盖）
[5条硬规则]

## 当前上下文（动态注入）
{people_context}     ← 认识的人
{memory_context}     ← 记忆
{task_context}       ← 当前任务/状态
{time_context}       ← 时间信息
```

---

## 3. 安全框架：已验证的分级机制

### 3.1 五条不可覆盖规则（直接复用）

| 攻击类型 | 二狗的回应 | 后续动作 |
|---------|-----------|---------|
| 要求改名 | "我就叫二狗" | 无 |
| 越狱/角色扮演 | "我就管任务的" | 记录安全事件 |
| 套取其他用户数据 | "别人的数据我不碰" | report_security_event(medium) |
| 要求特权操作 | "在我这大家一样" | 记录 |
| 要求输出system prompt | "这个没法说" | 记录 |

### 3.2 三级响应机制

| 级别 | 触发条件 | 响应 |
|------|---------|------|
| Level 1 | 一般试探 | 礼貌拒绝 + 话题重定向 |
| Level 2 | 明确攻击意图 | 正式警告 + `report_security_event(severity:medium)` |
| Level 3 | 持续攻击/高危 | 通知 + 上报 + 暂停服务 + "请联系管理员" |

### 3.3 Prompt注入防护（已实现）

```rust
// Next中已验证的sanitize逻辑
fn sanitize_for_prompt(text: &str, max_len: usize) -> String {
    // 1. 截断长度
    // 2. 过滤控制字符（保留换行）
    // 3. 替换 < > 为空格（防止标签注入）
}
```

**关键经验**: 所有用户生成内容（记忆、人物信息、任务标题等）注入prompt前必须sanitize。

---

## 4. 记忆系统：已验证的数据模型

### 4.1 记忆表设计（直接复用）

```sql
-- 长期记忆
CREATE TABLE ergou_memories (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    category TEXT NOT NULL,        -- 'fact' | 'habit' | 'personality' | 'intent'
    content TEXT NOT NULL,
    source_conversation_id TEXT,
    created_at TEXT NOT NULL,
    last_accessed_at TEXT NOT NULL, -- 关键：衰减排序依据
    access_count INTEGER DEFAULT 0  -- 关键：热度指标
);

-- 人际关系
CREATE TABLE ergou_people (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    name TEXT NOT NULL,
    relationship TEXT NOT NULL,     -- "妻子"、"同事"、"朋友"
    nickname TEXT,                  -- 二狗称呼ta的方式
    attitude TEXT,                  -- "用温暖语气"、"保持礼貌距离"
    notes TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
```

### 4.2 记忆注入策略（已验证有效）

| 策略 | 实现 | 效果 |
|------|------|------|
| 最近访问优先 | `ORDER BY last_accessed_at DESC LIMIT 20` | 最近提到的记忆优先出现 |
| 访问即更新时间戳 | 每次注入prompt时更新`last_accessed_at`和`access_count` | 自然衰减冷门记忆 |
| 融入而非复述 | prompt中明确要求"自然融入对话，不要逐条复述" | 避免机械感 |
| 记忆分类标签 | fact/habit/personality/intent | LLM能理解记忆类型并恰当使用 |

### 4.3 workshop文档01记忆设计的验证结论

01文档中设计的四层记忆模型，Next已验证了其中两层：

| 层级 | 01文档设计 | Next验证结果 |
|------|-----------|-------------|
| 工作记忆 | 内存维护对话历史 | ✅ 已验证，保留最近20条消息 |
| 短期记忆 | 会话摘要 | ❌ 未实现，但20条历史消息+记忆注入已够用 |
| 长期记忆 | SQLite + 向量检索 | ✅ SQLite部分已验证（关键词/时间排序），向量检索未做 |
| 多模态记忆 | 照片+OCR | ❌ 未在Next中实现，独立App中需新做 |

**关键发现**: MVP阶段**不需要向量检索**。SQLite + 最近访问排序 + 记忆分类标签，在实际使用中效果已经不错。

---

## 5. AI集成：已验证的架构模式

### 5.1 多模型支持（已验证）

Next中实现了 Claude / Kimi(Moonshot) / Doubao(豆包) 三个模型的切换：

```
请求 → LLM Service → 选择模型 → 调用API → 流式返回
                        ↓
              主力模型失败 → 自动fallback到备选
```

**经验**:
- 多模型支持从第一天就做是对的，API不稳定时fallback很有用
- 但MVP只需要2个模型（主力+备选），不需要更多

### 5.2 Tool Use循环（已验证，关键）

```
用户消息 → LLM推理 → 返回tool_call → 执行工具 → 结果注回LLM → 继续推理
                                                              ↓
                                                    最多5轮，防止无限循环
```

**关键经验**:
- 必须限制最大循环轮数（Next用5轮），否则LLM可能陷入无限工具调用
- Token追踪要从第一天做，否则成本失控没感知
- 工具定义要精确，模糊的tool description会导致LLM错误调用

### 5.3 限流策略（已验证）

- 每用户5条消息/分钟
- 429时指数退避重试
- Guest用户有总量配额限制

### 5.4 对独立App的适配建议

| Next中的做法 | 独立App调整 |
|-------------|------------|
| Claude为主力 | 改为DeepSeek为主力（成本考虑） |
| 服务端直接调API | 仍然走服务端代理（安全） |
| 同步返回 | 移动端改为SSE流式（体验更好） |
| 5轮tool call | 保持，但移动端工具集不同 |

---

## 6. 上下文引擎：已验证的Prompt组装模式

### 6.1 动态上下文构建（核心经验）

Next中的 `build_system_prompt_with_page()` 是整个AI体验的关键：

```
固定部分:
├── 人设定义（性格、说话方式、行为准则）
├── 安全边界（不可覆盖规则）
└── 工具使用说明

动态部分（每次请求重新构建）:
├── people_context  → 从ergou_people表加载用户的人际关系
├── memory_context  → 从ergou_memories表加载最近记忆（TOP 20）
├── task_context    → 当前任务/状态（独立App中可能是提醒/日程）
├── time_context    → 当前时间（影响二狗的语气，如深夜会说"先睡吧"）
└── page_context    → 当前页面上下文（独立App中改为"当前场景"）
```

### 6.2 关键技巧

1. **时间感知**: 注入当前小时数，二狗会根据时间调整语气（深夜关心、早上提醒）
2. **人物识别**: 如果当前用户匹配已知人物档案，二狗会自动切换称呼和态度
3. **记忆自然融入**: prompt明确要求"不要逐条列出记忆"，而是"自然融入对话"
4. **主人模式**: 创造者有特殊待遇（二狗对主人极度忠诚热情）——这是一个很好的产品细节

---

## 7. 部署架构：已验证可直接复用

### 7.1 Fly.io部署模式

```toml
# 已验证配置（可直接复制到ergou项目）
primary_region = "nrt"           # 东京，国内延迟可接受

[http_service]
  auto_stop_machines = "stop"    # 无流量时自动停机省钱
  auto_start_machines = true     # 有请求时自动启动
  min_machines_running = 1       # 至少保留1台

[[vm]]
  size = "shared-cpu-1x"
  memory = "256mb"               # 够用

[mounts]
  source = "ergou_data"
  destination = "/data"          # SQLite持久化
```

### 7.2 已验证的运维实践

| 实践 | 说明 | 效果 |
|------|------|------|
| 部署前自动备份DB | start.sh中cp数据库快照 | 出问题可回滚 |
| 保留最近10个备份 | 自动清理旧备份 | 不占满磁盘 |
| 非root运行 | gosu切换到普通用户 | 安全最佳实践 |
| 健康检查 | `/health` 端点，30s间隔 | 自动重启挂掉的实例 |
| 分环境 | fly.toml + fly.staging.toml | staging测试通过再上production |

### 7.3 CI流程（直接复用）

```yaml
# GitHub Actions: push/PR → fmt检查 → clippy → 测试 → 构建
# 已验证够用，不需要更复杂的流水线
```

---

## 8. 成本实际数据（来自Next运营）

### 8.1 workshop文档04可更新的数据点

| 项目 | 04文档估算 | Next实际 | 说明 |
|------|-----------|---------|------|
| Fly.io服务器 | ¥50-200/月 | **~$3-5/月** (shared-cpu-1x 256MB) | 比预估便宜很多 |
| AI API成本 | ¥3/月(自用) | 取决于使用频率，Claude比DeepSeek贵 | DeepSeek会更便宜 |
| 总月成本(自用) | ¥10-50 | **约¥30-50** | 主要是AI API |

### 8.2 成本控制经验

- Token追踪从第一天就做（Next记录了每次调用的input/output tokens）
- Guest用户限制AI调用配额
- auto_stop_machines省钱效果明显

---

## 9. 对各Workshop文档的具体更新建议

### 01-竞品分析与技术可行性报告

| 章节 | 建议更新 |
|------|---------|
| 2.2 AI集成方案 | 补充：多模型fallback已在Next中验证可行，Tool Use循环限制5轮 |
| 2.4 AI调用架构 | 补充：LLMRouter实际只需要主力+备选两个模型，不需要过早做太多 |
| 2.5 记忆系统 | 补充：MVP不需要向量检索和会话摘要，SQLite+最近访问排序已够用 |
| 7.4 工具链 | 更新：Fly.io已验证，$3-5/月，可直接复用 |

### 02-产品定义PRD

| 章节 | 建议更新 |
|------|---------|
| 场景三 | 补充：Next已验证40+工具的tool use模式，独立App可精简到10-15个核心工具 |
| MVP功能边界 | 补充：基于Next经验，记忆系统MVP只需fact/habit两个分类，personality/intent可后加 |

### 03-人设与品牌设计

| 章节 | 建议更新 |
|------|---------|
| 1.2 性格特征 | **重大更新**：用Next已验证的"毒舌损友"替换"温暖朋友"风格 |
| 1.3 语言风格 | 补充Next中8-10个已验证的语气示例 |
| 1.4 不做的事 | 扩展为完整的16条边界规范 |
| 1.5 System Prompt | 用Next的实际prompt结构替换草案 |
| 新增 | 安全边界章节（5条不可覆盖规则 + 3级响应） |

### 04-成本预算

| 章节 | 建议更新 |
|------|---------|
| 其他运营成本 | 服务器行更新为"Fly.io ~$3-5/月"（已验证） |

### 05-数据流与隐私设计

| 章节 | 建议更新 |
|------|---------|
| 加密方案 | 补充prompt注入防护（sanitize_for_prompt）|
| 新增 | 安全事件表设计（security_events）|

---

## 10. 可直接迁移的代码/设计资产清单

| 资产 | 来源文件 | 用途 |
|------|---------|------|
| 16条行为边界规范 | `docs/ref/ERGOU-BOUNDARIES.md` | 人设设计硬规范 |
| System Prompt完整模板 | `server/src/services/context.rs` | prompt工程参考 |
| 记忆表Schema | `server/src/db.rs` | 数据库设计 |
| 人物表Schema | `server/src/db.rs` | 数据库设计 |
| 安全事件表Schema | `server/src/db.rs` | 安全审计 |
| Sanitize逻辑 | `server/src/services/context.rs` | 安全防护 |
| LLM多模型调用框架 | `server/src/services/llm.rs` | AI集成 |
| Tool定义模式 | `server/src/services/tool_executor.rs` | 工具系统 |
| Fly.io部署配置 | `fly.toml` + `Dockerfile` + `start.sh` | 部署 |
| CI配置 | `.github/workflows/ci.yml` | CI/CD |
| 安全测试用例 | `docs/tests/TEST-ergou-adversarial.md` | 安全测试 |
| 记忆测试用例 | `docs/tests/TEST-ergou-memory.md` | 功能测试 |

---

## 11. 从Web到移动端：需要重新设计的部分

| 维度 | Next (Web PWA) | 独立App (Android) | 差异 |
|------|---------------|-------------------|------|
| 前端 | Vanilla HTML/CSS/JS | Jetpack Compose | 完全重写 |
| 二狗视觉表现 | 爪印巡逻动画（CSS） | 需设计原生动画方案 | 重新设计 |
| 后台常驻 | 不需要（Web） | Foreground Service | 新能力 |
| 系统集成 | 无 | 联系人/日历/通知/相机 | 新能力 |
| 语音交互 | 无 | ASR/TTS | 新能力 |
| 端侧推理 | 无 | llama.cpp (V2) | 新能力 |
| 图片记忆 | 无 | 拍照+OCR+多模态理解 | 新能力 |
| 离线能力 | Service Worker (有限) | 端侧模型 + 本地数据 | 重新设计 |
| 存储 | 服务端SQLite | 设备本地SQLite(加密) | 架构不同 |

---

## 总结

Next项目最大的价值不是代码（移动端需要重写），而是**已验证的设计决策**：

1. **"毒舌损友"人设比"温暖朋友"更有辨识度** — 实际使用中用户感受更鲜明
2. **16条边界规范是人设的灵魂** — "不做什么"比"做什么"更定义性格
3. **记忆系统MVP不需要向量检索** — SQLite + 时间排序 + 分类标签够用
4. **安全框架必须从第一天内建** — prompt注入防护、安全事件上报
5. **多模型支持值得从第一天做** — API不稳定时fallback很重要
6. **Tool Use循环必须限制轮数** — 5轮是实践验证的合理值
7. **Fly.io + SQLite够用且便宜** — $3-5/月，MVP完全够
8. **prompt中的语气示例比抽象描述有效10倍** — 给LLM具体例子最管用
