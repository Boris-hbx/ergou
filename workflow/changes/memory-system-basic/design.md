# 设计: 基础长期记忆系统

> **关联提案**: [proposal.md](./proposal.md)
> **关联用例**: [usecases.md](./usecases.md)

---

## 背景

当前二狗的对话系统只有工作记忆（当前会话的消息历史），没有跨会话的长期记忆能力。每次新会话二狗都是"失忆"状态。

Next项目中已验证了记忆系统的可行性和数据模型（ergou_memories + ergou_people表），本次在Android独立App中实现同等能力。

---

## 目标 / 非目标

**目标**:
- 用户能通过对话保存记忆
- 二狗在后续对话中自动引用相关记忆
- 用户能查看和删除记忆
- 记忆数据安全加密存储

**非目标**:
- 不做自动记忆提取（LLM自动从对话中识别记忆） → V2
- 不做向量语义检索 → V2
- 不做多设备同步 → V3
- 不做人物管理（ergou_people） → 独立功能 B-012

---

## 数据模型

```sql
CREATE TABLE ergou_memories (
    id TEXT PRIMARY KEY,             -- UUID
    category TEXT NOT NULL,          -- 'fact' | 'habit' | 'personality' | 'intent'
    content TEXT NOT NULL,           -- 记忆内容（限200字）
    source TEXT DEFAULT 'user',      -- 'user'(用户主动) | 'auto'(自动提取,V2)
    created_at TEXT NOT NULL,        -- ISO8601
    last_accessed_at TEXT NOT NULL,  -- 每次注入prompt时更新
    access_count INTEGER DEFAULT 0   -- 热度指标
);

-- 索引: 按最近访问时间排序（记忆检索用）
CREATE INDEX idx_memories_accessed ON ergou_memories(last_accessed_at DESC);
```

**记忆分类说明**:

| 分类 | 含义 | 举例 |
|------|------|------|
| fact | 客观事实 | "对花生过敏"、"住在北京"、"有一只猫叫咪咪" |
| habit | 行为习惯 | "早上喜欢喝咖啡"、"周末经常跑步" |
| personality | 性格/偏好 | "不喜欢被说教"、"喜欢简洁的回答" |
| intent | 意图/计划 | "想学英语"、"打算换工作" |

> **MVP只需fact和habit**。personality和intent可以先暴露接口但不主动使用，降低prompt复杂度。

---

## API设计

### 后端API（Fly.io Rust/Axum）

```
POST /api/memories
  请求: { "category": "fact", "content": "对花生过敏" }
  响应: { "id": "uuid", "category": "fact", "content": "...", "created_at": "..." }

GET /api/memories
  查询参数: ?category=fact&limit=50
  响应: [{ "id": "...", "category": "...", "content": "...", ... }]

DELETE /api/memories/:id
  响应: 204 No Content

DELETE /api/memories
  说明: 清除全部记忆（需确认token/header）
  响应: 204 No Content
```

### Tool定义（二狗可调用的工具）

```json
{
  "name": "save_memory",
  "description": "保存一条用户的长期记忆。用户说'帮我记住'、'记一下'时调用。",
  "parameters": {
    "category": { "type": "string", "enum": ["fact", "habit", "personality", "intent"] },
    "content": { "type": "string", "description": "记忆内容，简洁准确，不超过200字" }
  }
}

{
  "name": "delete_memory",
  "description": "删除一条记忆。用户说'忘掉'、'别记了'时调用。",
  "parameters": {
    "memory_id": { "type": "string", "description": "要删除的记忆ID" }
  }
}
```

---

## 决策记录

### D1: 记忆存储位置

**决定**: 后端SQLite（Fly.io）

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| A: 后端SQLite | 和对话系统在一起，prompt注入方便 | 需要网络 | ← 选择 |
| B: 客户端本地SQLite | 离线可用，隐私更好 | prompt注入需要先上传 | 后续考虑 |

**理由**: MVP阶段对话本身就依赖云端API，记忆放后端架构更简单。V2做端侧推理时再考虑本地存储。

### D2: 记忆检索策略

**决定**: 按最近访问时间排序，取TOP 20

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| A: 时间排序 TOP 20 | 简单可靠，Next已验证 | 不能语义匹配 | ← 选择 |
| B: 向量语义检索 | 精准匹配相关记忆 | 复杂度高，需要Embedding | V2 |

**理由**: Next中验证过，时间排序在记忆条数<1000时效果已经不错。

### D3: 记忆冲突处理

**决定**: V1不做自动冲突检测，由用户主动管理

**理由**: 自动检测"花生过敏"和"花生不过敏"冲突需要额外的语义理解，MVP暂不做。用户说"帮我改一下那条"时，由二狗先query再update。

---

## 风险与权衡

| 类型 | 描述 | 应对 |
|------|------|------|
| 风险 | 记忆内容注入prompt导致prompt injection | sanitize: 截断200字+过滤控制字符+替换<> |
| 风险 | 记忆过多导致prompt过长、token成本高 | 硬性限制20条，每条200字，最多~4000字 |
| 权衡 | 不做语义检索，可能错过相关记忆 | 可接受，MVP验证产品价值优先 |

---

## AI/安全设计

### Prompt变更

新增到system prompt：
```
## 你对这个用户的记忆
以下是你记住的关于用户的信息，对话中自然融入，不要逐条复述：
- [事实] 对花生过敏 (ID:xxx)
- [习惯] 早上喜欢喝咖啡 (ID:xxx)
（记忆会自然融入对话，不要逐条复述。用户要求忘掉某条时用 delete_memory。）
```

### 输入Sanitize

所有记忆content注入prompt前：
1. 截断到200字符
2. 过滤控制字符（保留\n）
3. `<` `>` 替换为空格
