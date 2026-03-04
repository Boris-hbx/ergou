# 二狗开发工作流 (Ergou Dev Workflow)

> 基于 openspec 模式，为一人开发 + AI助手产品定制。
> 每个功能/变更都经过 **提案 → 用例 → 设计 → 规格 → 任务 → 验收** 的完整流程，避免遗漏。

---

## 核心理念

1. **写清楚再动手** — 30分钟写文档能省3小时返工
2. **用例驱动** — 不是"做什么功能"，而是"用户怎么用"
3. **打钩才算完** — 每个任务都是checkbox，完成才打钩，不打钩不算交付
4. **安全内建** — AI产品的安全审查不是事后补，是流程中的必经环节

---

## 流程总览

```
  提出想法
     │
     ▼
┌──────────┐    不值得做 → 记录到 backlog，关闭
│ 1.提案    │───────────────────────────────────→ (归档)
│ proposal  │
└────┬─────┘    值得做 ↓
     │
     ▼
┌──────────┐
│ 2.用例    │    "用户会怎么用？" — 强制思考场景
│ usecases  │
└────┬─────┘
     │
     ▼
┌──────────┐
│ 3.设计    │    "怎么做？选什么方案？" — 架构决策
│ design    │
└────┬─────┘
     │
     ▼
┌──────────┐
│ 4.规格    │    "做到什么标准？" — 可测试的WHEN/THEN
│ specs     │
└────┬─────┘
     │
     ▼
┌──────────┐
│ 5.任务    │    "具体写什么代码？" — checkbox清单
│ tasks     │
└────┬─────┘
     │
     ▼
┌──────────┐
│ 6.验收    │    "安全吗？好用吗？" — 安全+体验检查
│ review    │
└────┬─────┘
     │
     ▼
  归档到 archive/
```

---

## 目录结构

```
workflow/
├── README.md              ← 你正在看的这个
├── templates/             ← 模板文件
│   ├── proposal.md
│   ├── usecases.md
│   ├── design.md
│   ├── spec.md
│   ├── tasks.md
│   └── review.md
├── backlog.md             ← 想法池（未启动的）
└── changes/               ← 进行中和已完成的变更
    ├── [feature-name]/    ← 当前进行中
    │   ├── .meta.yaml
    │   ├── proposal.md
    │   ├── usecases.md
    │   ├── design.md
    │   ├── specs/
    │   │   └── [cap-name]/spec.md
    │   ├── tasks.md
    │   └── review.md
    └── archive/           ← 已完成的
        └── YYYY-MM-DD-[feature-name]/
```

---

## 各阶段详细说明

### 阶段1: 提案 (proposal.md)

**回答的问题**: 为什么要做？做什么？影响多大？

**何时写**: 有一个想法/需求时，先花10分钟写提案。
**何时跳过**: 纯bug修复（<30分钟的修改）可以跳过整个流程，直接修。

**关键内容**:
- Why — 为什么要做，解决什么问题
- What — 做什么变更，涉及哪些模块
- 影响范围 — 后端/前端/数据库/API
- 安全考量 — AI产品必填：是否涉及用户数据、prompt修改、新工具暴露

### 阶段2: 用例 (usecases.md) ⭐ 必做

**回答的问题**: 用户怎么用？正常流程是什么？异常情况怎么办？

**这是最重要的一步**。没有用例的功能容易做偏。

**关键内容**:
- 主角是谁（用户？管理员？系统？）
- 前置条件
- 正常流程（Main Success Scenario）— 编号步骤
- 异常分支（Extensions）— 如 "3a. 网络断开时..."
- 成功标准

### 阶段3: 设计 (design.md)

**回答的问题**: 技术上怎么实现？为什么选这个方案？

**关键内容**:
- 方案选择（Decision）+ 被否决的方案及原因
- 数据模型变更
- API变更
- 风险和权衡
- 不做什么（Non-Goals）— 防止范围蔓延

### 阶段4: 规格 (specs/[name]/spec.md)

**回答的问题**: 做到什么标准算完？怎么验证？

**格式**: WHEN / THEN 场景式，每个场景都是一个可测试的验收条件。

```
### Requirement: 对话记忆注入
系统 SHALL 在每次对话请求时注入最近20条相关记忆。

#### Scenario: 正常注入
- WHEN 用户发送消息
- THEN system prompt 中包含按 last_accessed_at 排序的记忆
- AND 记忆总数不超过20条
- AND 每条记忆内容不超过200字符

#### Scenario: 无记忆时
- WHEN 新用户首次对话
- THEN system prompt 中不包含记忆段落（不显示空的"## 记忆"标题）
```

### 阶段5: 任务 (tasks.md) ⭐ 必做

**回答的问题**: 具体要写哪些代码？

**格式**: 分组 + 编号 + checkbox。做完一个打一个勾。

```
## 1. 数据层
- [ ] 1.1 创建 ergou_memories 表 (db.rs)
- [ ] 1.2 实现 insert_memory / query_memories / delete_memory

## 2. 服务层
- [ ] 2.1 实现 build_memory_context()
- [ ] 2.2 集成到 build_system_prompt()

## 3. API层
- [ ] 3.1 POST /api/memory — 保存记忆
- [ ] 3.2 GET /api/memories — 查询记忆
- [ ] 3.3 DELETE /api/memory/:id — 删除记忆

## 4. 测试
- [ ] 4.1 单元测试: memory CRUD
- [ ] 4.2 集成测试: 记忆注入到prompt
- [ ] 4.3 手动测试: 保存→对话→验证记忆被引用
```

### 阶段6: 验收 (review.md) ⭐ AI产品必做

**回答的问题**: 安全吗？好用吗？没有遗漏吗？

**三个检查清单**:
1. **安全检查** — prompt注入？数据泄露？权限越界？
2. **体验检查** — 符合二狗人设？不违反16条边界？
3. **工程检查** — 测试通过？代码质量？性能OK？

---

## 快速启动一个新功能

```bash
# 1. 复制模板
cp -r workflow/templates workflow/changes/[feature-name]

# 2. 创建元信息
echo "schema: ergou-workflow\ncreated: $(date +%Y-%m-%d)\nstatus: active" > workflow/changes/[feature-name]/.meta.yaml

# 3. 按顺序填写文档
# proposal.md → usecases.md → design.md → specs/ → tasks.md

# 4. 开始写代码（按tasks.md打钩）

# 5. 完成后填写 review.md

# 6. 全部通过 → 移到 archive/
mv workflow/changes/[feature-name] workflow/changes/archive/$(date +%Y-%m-%d)-[feature-name]
```

---

## 简化规则（一人开发的现实）

| 变更大小 | 需要哪些文档 | 说明 |
|---------|-------------|------|
| **Bug修复 (<30min)** | 无 | 直接修，commit message说清楚 |
| **小改动 (半天)** | tasks.md | 只列任务清单，做完打钩 |
| **中等功能 (1-3天)** | usecases + tasks + review | 用例想清楚，任务列清楚，安全检查 |
| **大功能 (>3天)** | 全套6个文档 | 完整流程，不省步骤 |

> **底线**: 不管功能大小，**usecases** 和 **tasks** 至少要有一个。想清楚用户怎么用，列清楚要做什么。

---

## 状态追踪

| 状态 | 含义 | 位置 |
|------|------|------|
| backlog | 想法池，未启动 | `workflow/backlog.md` |
| active | 正在进行 | `workflow/changes/[name]/` |
| review | 开发完成，验收中 | `workflow/changes/[name]/` (.meta.yaml status: review) |
| done | 已完成归档 | `workflow/changes/archive/YYYY-MM-DD-[name]/` |

---

## 与 Git 的配合

```
feature分支命名: feat/[change-name]
commit message引用: "feat(memory): implement memory CRUD — ref workflow/changes/memory-system"

典型流程:
1. 创建 workflow/changes/memory-system/ 文档
2. git checkout -b feat/memory-system
3. 按 tasks.md 逐项开发，每完成一组提交一次
4. review.md 检查通过
5. 合入 main
6. 归档文档到 archive/
```
