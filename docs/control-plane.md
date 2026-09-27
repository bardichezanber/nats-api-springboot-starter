# Bridge vNext — Agent Control Plane Architecture

> Status: Draft 1 / implementation baseline\
> Purpose: 作為後續 coding agent 的設計 source of truth。\
> 核心原則：先完成一個可靠、低 token、可遠端操作的 agent control plane，再逐步增加 autonomy；不要先做一個複雜的 autonomous swarm framework。

---

# 1. GOAL

目前的 Bridge 已經能：

- 監聽公司內部 Chat 的 Self DM。
- 收發、刪除訊息。
- 建立 Thread。
- 收發、刪除 Thread 訊息。
- 使用 reaction 當 interaction primitive。
- 每個 Self DM Room 有一個 Room Agent。
- 每個 Thread 有一個 Thread Agent。
- Agent 目前由 OpenCode + Terra 驅動。
- Herdr `chat` session 管理 Chat 相關 agents。
- Herdr `default` session 保存原本人工使用的 agents/workspaces。
- Chat Thread title 已可顯示 agent `idle / busy / closed` 狀態。
- `!tail` 可以持續更新 Herdr pane 尾端內容並透過 reaction 關閉。

下一階段的目標不是單純增加 Chat command。

目標是把 Bridge 從：

```text
Chat ↔ OpenCode/Herdr bridge
```

演進成：

```text
           Agent Control Plane
```

它應該允許使用者：

```text
手機 / Desktop Chat
        │
        ├── 建立 coding thread
        ├── 問 repo 問題
        ├── 要 agent 修改 code
        ├── 建立較長時間 task
        ├── 看 agent / task / machine 狀態
        ├── 回答 blocked agent
        ├── review / approve / reject
        └── 控制原本 default session agents

                    │

               Bridge
         Agent Control Plane

                    │
       ┌────────────┼────────────┐
       │            │            │
      Herdr       SCM/CI       LLM/Harness
       │
 ┌─────┴─────┐
Local       Dev VM
 │             │
Agents       Agents
```

Bridge 的最終價值不是「讓 agent 可以互相 prompt」。

真正價值是：

> 讓所有 agent、repo、worktree、task、machine、Chat interaction 都有 durable identity、ownership、policy 與 lifecycle。

---

# 2. HIGH-LEVEL DECISIONS

以下先視為已決定。

## D1. Bridge 升級為 Agent Control Plane

專案名稱可以暫時繼續叫 `bridge`。

但 architecture 上它不再只是 Chat bridge。

它負責：

- Agent Registry
- Repository Catalog
- Project grouping
- Thread binding
- Task / Run lifecycle
- Agent messaging
- Worktree ownership
- Machine scheduling
- Model / quota routing
- Verification pipeline
- Approval / interaction
- Chat adapter
- Web UI API
- SCM adapter
- Harness abstraction
- Restart / reconciliation

---

## D2. Herdr 是 Execution Runtime

Herdr 負責：

- terminal / pane runtime
- agent process lifecycle
- local/remote machine execution
- agent status detection
- terminal output
- prompt agent
- wait agent
- managed worktree creation
- worktree opening/removal
- worktree landing
- session/pane persistence
- native harness session resume where available

Bridge **不要重新實作 Herdr 已經有的功能**。

Bridge 決定：

> WHAT / WHERE / WHO / WHY

Herdr 執行：

> RUN IT

---

## D3. OpenCode 是 Default Harness，但不是 Control Plane

OpenCode 負責：

- reasoning
- coding
- filesystem inspection
- edit
- bash
- test/debug
- local OpenCode subagents
- context management
- model interaction

OpenCode 不應該負責：

- durable global agent registry
- repo catalog
- machine scheduler
- cross-agent identity
- task database
- Chat routing
- global quota scheduling
- authoritative worktree ownership

---

## D4. 保留 Harness abstraction

V1 主 harness：

```text
OpenCode
```

但 architecture 必須容許：

```text
OpenCode
Claude Code
Codex
other CLI agents
```

原因不是現在就要全部支援。

而是：

> Bridge 的 domain model 不應依賴 OpenCode session/model/tool implementation。

---

## D5. SQLite 繼續使用

V1 不需要：

- PostgreSQL
- Redis
- Kafka
- distributed scheduler

SQLite 足以作為 Bridge 的 durable source of truth。

需要：

- transactions
- foreign keys
- WAL
- migration system
- idempotency
- reconciliation

---

## D6. Repo 是第一級 domain entity

Bridge 必須「知道有哪些 repo」。

但：

> Catalog all, clone on demand.

不要把所有 repo 全 clone。

GitLab / Azure DevOps 是 Repository Catalog 的 discovery source。

---

## D7. Thread 預設代表 Repo Workstream

正常情況：

```text
Chat Thread
      │
      └── Thread Agent
             │
             └── one repo
```

例如：

```text
🟢 A repo
~/managed/repos/a

primary agent: Terra
```

Thread 不直接等於 worktree。

正確 abstraction 是：

```text
Thread
  = human-visible workstream

Thread Agent
  = logical repo owner

Harness Session
  = concrete LLM session

Worktree
  = concrete mutable code workspace
```

---

## D8. Thread Agent 是 Working Orchestrator

Thread Agent 不是純 manager。

它預設自己：

- read
- code
- test
- debug
- integrate

只有遇到明確優勢才：

- spawn worker
- ask peer
- request review
- use another machine
- invoke local subagent

原則：

```text
自己完成是 default。

delegate 必須有：
parallelism
isolation
specialization
existing-context ownership
其中至少一個明顯優勢。
```

---

## D9. 每個 Thread Agent 有自己的 managed worktree

V1 推薦：

```text
Repo main checkout
      │
      ├── Thread A worktree ← Thread Agent A
      ├── Thread B worktree ← Thread Agent B
      └── Worker worktree   ← temporary worker
```

Thread Agent 保持長壽命。

這可以保留：

- OpenCode conversation continuity
- repo familiarity
- 低 bootstrap token
- 低來回溝通
- 手機操作體驗

每次完成工作後：

```text
verify
→ delivery/land
→ worktree 回到 clean/synced 狀態
→ agent 繼續留著
```

---

## D10. Mutation 都應該有 Task，但可以是 implicit quick task

使用者：

```text
把 README typo 修掉
```

不需要先進 Web UI 建 task。

系統可以自動產生：

```text
Task #123
kind: quick
source: chat
```

完成：

```text
edit
→ verify
→ land/deliver
→ done
```

使用者幾乎感覺不到 Task 存在。

但後端仍然有 audit trail。

---

## D11. Verification 必須是 deterministic pipeline

不要讓 LLM 自己說：

> 看起來應該沒問題。

Final gate 應由 Bridge 執行：

```text
format
lint
typecheck
unit tests
build
repo-specific checks
```

Agent 可以自行多跑 test。

但 Task Ready 後，Bridge 再跑一次 authoritative verification。

---

## D12. CI 不應被 swarm 取代

Agent swarm 應該：

> 自動化 development feedback loop。

而不是重新實作：

- GitLab CI
- Azure Pipelines
- branch protection
- artifact pipeline
- production deployment controls

之後 Bridge 可以：

```text
push branch
→ 建 MR/PR
→ 看 CI
→ CI failure
→ 自動叫原 agent 修
→ 再 push
```

這才是理想方向。

---

# 3. RESPONSIBILITY BOUNDARY

| Concern                | Bridge                   | Herdr            | Harness/OpenCode          | SCM/CI            |
| ---------------------- | ------------------------ | ---------------- | ------------------------- | ----------------- |
| Chat API               | ✅                        |                  |                           |                   |
| Web UI                 | ✅                        |                  |                           |                   |
| Repo catalog           | ✅                        |                  |                           | source            |
| Repo discovery         | ✅                        |                  |                           | ✅                 |
| Clone repo             | ✅                        |                  |                           |                   |
| Fetch/update repo      | ✅                        |                  |                           |                   |
| Worktree policy        | ✅                        |                  |                           |                   |
| Worktree Git mechanics | orchestrate              | ✅                |                           |                   |
| Agent identity         | ✅                        | runtime identity | session identity          |                   |
| Agent process          | orchestrate              | ✅                | ✅                         |                   |
| Machine routing        | ✅                        | execute          |                           |                   |
| Terminal persistence   |                          | ✅                |                           |                   |
| Agent status           | normalize                | ✅                | optional                  |                   |
| Coding                 |                          |                  | ✅                         |                   |
| Reasoning              |                          |                  | ✅                         |                   |
| Local subagent         |                          |                  | ✅                         |                   |
| Cross-agent routing    | ✅                        | transport        |                           |                   |
| Task DB                | ✅                        |                  |                           |                   |
| Verification policy    | ✅                        | execute command  | can debug                 | CI authoritative  |
| Rate-limit scheduling  | ✅                        |                  | reports usage if possible |                   |
| PR/MR                  | ✅ adapter                |                  |                           | ✅                 |
| CI                     | monitor/orchestrate      |                  | agent fixes               | ✅                 |
| Production deployment  | policy/orchestrate later |                  |                           | existing pipeline |

---

# 4. DOMAIN MODEL

不要直接把：

```text
Chat Thread == OpenCode Session == Herdr Pane == Agent
```

綁死。

短期很方便，長期會限制 architecture。

應拆成以下概念。

---

## 4.1 Repository

```text
Repository
```

代表一個 Git repository。

建議欄位：

```text
id
provider
provider_instance
provider_repo_id
name
full_name
clone_url
web_url
default_branch
archived
enabled
metadata
last_discovered_at
```

Provider：

```text
gitlab
azure_devops
manual
local
```

---

## 4.2 Project

Project 是 logical grouping。

例如：

```text
Booking Platform
├── frontend-web
├── backend-api
├── notification-service
└── infra
```

不要直接假設：

```text
GitLab Group == Project
Azure Project == Bridge Project
```

可以先從 SCM metadata suggest。

最後 grouping 應由 Bridge 自己保存。

---

## 4.3 Repository Materialization

同一 repo 可以存在不同 machine。

```text
Repo A
├── workstation
│     ~/bridge/repos/a
└── dev-vm
      ~/bridge/repos/a
```

資料：

```text
repo_id
machine_id
path
remote_url
base_branch
last_fetch_at
state
managed
```

---

## 4.4 Thread

Thread 是使用者看到的 workstream。

```text
thread_id
chat_room_id
chat_thread_id
title
scope_type
scope_id
primary_agent_id
state
created_at
```

`scope_type`：

```text
repo
project
general
```

V1 主要支援：

```text
repo
```

---

## 4.5 Agent Profile

代表角色設定，而不是正在跑的 process。

例如：

```text
room
thread
flagship
general
reviewer
explore
deep-explore
```

包含：

```text
role
default_model_class
default_harness
permissions
tools
prompt
```

---

## 4.6 Agent Instance

logical durable agent。

例如：

```text
thread:abc
default:w1:p2
worker:task-123-1
```

資料：

```text
id
profile
kind
thread_id
repo_id
machine_id
state
managed
created_at
last_seen_at
```

---

## 4.7 Harness Session

真正的 OpenCode / Claude Code / Codex session。

```text
id
agent_id
harness
native_session_id
herdr_machine_id
herdr_session
herdr_pane_id
cwd
state
started_at
last_activity_at
```

一個 logical Agent 未來可以：

```text
restart
resume
change harness
new session
```

但 identity 不變。

---

## 4.8 Task

Task 是 durable user intent。

```text
id
parent_task_id
thread_id
repo_id
project_id
title
description
kind
priority
risk
state
created_by
created_at
updated_at
```

Task state：

```text
created
queued
preparing
running
blocked
verifying
reviewing
ready
delivering
done
failed
cancelled
```

---

## 4.9 Run

Task 可以執行多次。

例如：

```text
Task
 ├─ Run 1 → Terra → failed test
 └─ Run 2 → same agent → success
```

Run：

```text
id
task_id
agent_id
machine_id
workspace_id
harness_session_id
model
status
started_at
finished_at
result_summary
```

---

## 4.10 Workspace / Worktree Lease

代表：

> 某 agent 現在被允許在哪個 code workspace 寫東西。

```text
id
repo_id
machine_id
path
branch
base_revision
owner_agent_id
task_id
herdr_workspace_id
state
```

原則：

```text
one writable worktree
→ one writer
```

可以多個 reviewer read。

---

## 4.11 Interaction

手機端 interactive UI 應視為 first-class object。

例如：

```text
Agent needs input:

Use migration A or B?

🅰️
🅱️
❌
```

Interaction：

```text
id
type
task_id
agent_id
chat_message_id
state
expires_at
payload
response
```

Reaction 只是 Interaction 的其中一個 transport。

---

# 5. REPOSITORY MANAGEMENT

Bridge 必須知道 repo。

但不要要求使用者一個一個手動新增。

---

## 5.1 Repository discovery

增加：

```text
ScmProvider
```

interface：

```text
listRepositories()
getRepository()
getDefaultBranch()
getBranches()
getPullRequests()
getPipelineStatus()
```

V1：

```text
GitLabProvider
AzureDevOpsProvider
ManualProvider
```

啟動或 background refresh：

```text
GitLab
   │
Azure DevOps
   │
   ▼
Repository Catalog
   │
 SQLite
```

---

## 5.2 不 auto clone 所有 repo

Discovery：

```text
幾百/幾千 repo
```

只保存 metadata。

真正需要工作時：

```text
ensureRepository(repo, machine)
```

才 clone。

---

## 5.3 Managed repo root

建議 Chat swarm 不直接使用你日常人工 checkout。

每台 machine 都有：

```text
~/bridge/repos/
```

例如：

```text
~/bridge/repos/gitlab/team/a
~/bridge/repos/azure/project/backend
```

這些是：

```text
Bridge-managed parent checkouts
```

優點：

- 不碰人工 dirty tree
- agent 不會切你人工 branch
- worktree lifecycle 可控
- restart/reconcile 容易
- remote machine layout 一致

原本 `default` session 的 repo：

```text
managed = false
```

可以被觀察與溝通。

但 Bridge 不應任意 cleanup / reset。

---

## 5.4 ensureRepository()

概念流程：

```text
ensureRepository(repo_id, machine_id)

if materialization exists:
    verify git remote
    git fetch --prune
    ensure parent branch usable
else:
    mkdir managed root
    git clone
    checkout default branch
    register materialization

return Materialization
```

clone credential：

> 不存 raw token 在 Task / Agent DB。

使用：

- machine credential helper
- SSH key
- provider credential service

---

# 6. THREAD MODEL

## 6.1 Default: one repo Thread

正常 UX：

```text
Self DM
> 幫我開 A repo

🟢 A repo
  workdir: managed/a
```

Thread 的 logical scope：

```text
repo A
```

---

## 6.2 Thread Agent 維持長壽命

Thread 建立：

```text
ensure repo
→ create/reuse thread worktree
→ start Thread Agent
→ bind Thread ↔ Agent
```

例如：

```text
repo:
~/bridge/repos/a

thread worktree:
~/bridge/repos/a/.herdr/worktrees/thread-123
```

Thread Agent 在這裡長期工作。

---

## 6.3 為什麼不是一 Task 一 Agent

你的現況最大優勢：

```text
Thread Agent 已經知道 repo
+ 對話短
+ 使用者不用重新交代
+ context locality 高
```

所以不要變成：

```text
每一句話
→ spawn fresh agent
→ reread repo
→ reread architecture
```

這會浪費 token。

---

## 6.4 Project Thread

未來可有：

```text
Project: Product A
├── frontend repo
└── backend repo
```

Project Thread Agent 不應跨兩個 repo 亂改。

應：

```text
Project Agent
     │
     ├── frontend Task → frontend repo Agent
     └── backend Task  → backend repo Agent
```

V1 先把 `Project` domain 做好。

但 UI 可以暫時主要 expose Repo Thread。

---

# 7. ROOM AGENT

Room Agent 不寫 code。

責任：

```text
repo discovery/search
create/open threads
fleet query
cross-thread task routing
machine status
task query
blocked-agent query
attach existing agent
```

範例：

```text
> A repo 有哪些 agent？

> 哪些 task 卡住？

> 幫我開 backend repo

> 把 default 裡面的 auth agent attach 到 A thread

> 在 dev VM 開一個 worker 查 flaky test
```

---

# 8. THREAD AGENT

Thread Agent 是：

```text
Repo Owner
+
Primary Coder
+
Local Orchestrator
```

責任：

```text
understand user intent
inspect repo
write code
test/debug
create Task when mutation starts
request worker when appropriate
talk to existing peer
request human interaction
mark task ready
```

Thread Agent 不負責：

```text
direct global machine scheduling
direct quota accounting
raw GitLab/Azure API
global DB mutation
arbitrary Herdr topology manipulation
```

這些透過 Bridge tools。

---

# 9. OPENCode AGENT CONFIGURATION

建議配置：

## Primary

```text
thread
  model: Terra
  role: default repo coder/orchestrator

flagship
  model: Sol
  role: difficult architecture/debug/security/high-risk work
```

可保留 OpenCode `build` 名稱，或讓 `thread` override build。

---

## Room

```text
room
  default model: Luna
```

理由：

Room 大部分是：

- intent routing
- tool calling
- fleet queries
- thread creation

不要平白吃 Terra 的 constrained TPM。

模糊複雜問題再 escalate。

---

## Subagents

```text
general
  Sonnet 5

second-opinion / reviewer
  Sonnet 5
  read-only

explore
  Haiku
  read-only

deep-explore
  Qwen 3.8 27B
  read-only / large exploration
```

---

## 不建立 generic implement subagent

不要：

```text
implement → Haiku
```

Thread Agent 自己 implement。

只有 mechanical work 未來有明確需求才新增：

```text
mechanical-worker
```

甚至更推薦由 persistent Worker Agent 做，而不是 OpenCode child subagent。

---

# 10. TWO AGENT LAYERS

必須明確區分：

## OpenCode Subagent

```text
temporary
fresh context
parent-owned
local session hierarchy
```

適合：

```text
grep/explore
research
review
isolated reasoning
```

---

## Bridge / Herdr Peer Agent

```text
persistent
addressable
durable identity
cross-session
cross-machine
human-visible
```

適合：

```text
獨立長時間 task
另一個 repo
已有 context 的 agent
parallel code work
dev VM worker
```

Prompt 應明確教 agent：

```text
OpenCode subagents are temporary local helpers.

Bridge agents are persistent peers.

Do not create a persistent worker for work that can be completed
cheaply in the current context.

Do not use a local subagent when another persistent agent already
owns the required context.
```

---

# 11. CROSS-AGENT COMMUNICATION

所有 persistent agent messaging 經 Bridge。

不要直接：

```text
agent A
→ herdr pane send-text
→ agent B
```

正常流程：

```text
agent A
   │
bridge_agent_send()
   │
   ▼
Bridge
├── persist message
├── validate routing
├── update delivery state
└── Herdr agent prompt
          │
          ▼
        agent B
```

資料：

```text
agent_messages
--------------
id
from_agent_id
to_agent_id
task_id
thread_id
body
status
created_at
delivered_at
reply_to
```

---

## agent_send 不等於建立永久 relationship

只是傳訊息。

永久/半永久 association 要明確：

```text
agent_links
-----------
source
target
relationship
task_id
expires_at
```

relationship：

```text
peer
worker
reviewer
attached
delegate
```

---

# 12. DEFAULT HERDR SESSION

原本 `default` session 繼續存在。

Bridge 啟動時 discovery：

```text
Herdr default
├── Agent X
├── Agent Y
└── Agent Z
```

將它們 register 成：

```text
kind: external
managed: false
```

Bridge 可取得：

```text
machine
session
pane
cwd
repo match
state
native harness
```

Thread Agent 可以：

```text
agent_list(repo=current)
agent_send(default:X, ...)
```

但 Bridge 不應：

- 任意殺掉 external agent
- 任意 reset 它
- 任意清理它的 worktree
- 自動 land 它的 code

除非 user explicitly adopts it。

---

# 13. WORKER AGENT

Thread Agent 可以：

```text
bridge_worker_spawn(...)
```

用途：

- isolated change
- independent investigation
- parallelizable code
- expensive build/test
- remote VM execution

Worker 是 disposable persistent peer。

---

## Worker restrictions

Worker：

```text
只能 ownership 自己的 worktree
不能自行建立另一層 persistent Worker
可以使用 OpenCode local subagents
可以 message parent
可以 request human input
```

避免：

```text
agent → agent → agent → agent
```

失控。

---

# 14. WORKTREE LIFECYCLE

正常 mutation：

```text
Task starts
    │
    ▼
ensure repo
    │
    ▼
ensure/sync worktree
    │
    ▼
agent edits
    │
    ▼
agent tests/debugs
    │
    ▼
agent reports READY
    │
    ▼
Bridge verification
    │
    ├── fail → same agent fixes
    │
    └── pass
           │
           ▼
      review policy
           │
           ▼
        delivery
```

---

## Thread worktree

Thread primary worktree 長壽命。

工作完成後：

```text
clean
verified
delivered/landed
synced with base
```

下一個 Task reuse。

---

## Worker worktree

Worker 工作結束：

```text
success
→ integrate/deliver
→ terminate worker
→ remove worktree
```

failure：

```text
preserve worktree
→ inspect / retry / abandon
```

---

# 15. HERDR WORKTREE VS BRIDGE WORKTREE RESPONSIBILITY

Bridge：

```text
decide that a worktree is needed
choose repo
choose machine
choose branch name
assign ownership
persist metadata
decide verification/delivery policy
```

Herdr：

```text
create actual git worktree
open workspace
start agent
track worktree
remove worktree
mechanically land when requested
```

不要自己另外實作：

```text
git worktree add/remove lifecycle manager
```

除非 Herdr 某項 capability 不存在。

---

# 16. DEV VM

Dev VM 應納入 Machine Registry。

```text
machines
--------
id
name
herdr_machine_ref
state
capabilities
managed_repo_root
labels
```

例如：

```text
local
  labels:
    interactive
    fast-disk

dev-vm
  labels:
    build
    docker
    background
```

---

## Scheduler

Worker spawn：

```text
machine = auto
```

Bridge 決定：

```text
repo exists?
machine online?
CPU load?
active workers?
required toolchain?
Docker?
special environment?
```

Herdr `machine` 做真正 remote execution。

---

## 重要

增加 machine：

```text
≠ 增加 LLM TPM
```

如果 model provider quota 共用：

```text
Local Terra
+
Dev VM Terra
```

仍然會一起撞 quota。

所以：

```text
Machine Scheduler
```

與：

```text
Model/Quota Scheduler
```

必須是不同模組。

---

# 17. MODEL ROUTER / QUOTA GOVERNOR

Agent 不應直接 hardcode：

```text
spawn(model="terra")
```

它應該說：

```text
spawn(
  role="worker",
  quality="normal",
  purpose="..."
)
```

Model Router 選：

```text
primary coding
→ Terra

hard/high-risk
→ Sol

independent reviewer
→ Sonnet

fast exploration
→ Haiku

large cheap exploration
→ Qwen
```

---

## Quota buckets

維護：

```text
quota_bucket
provider
model_group
rolling_usage
active_turns
backoff_until
state
```

state：

```text
available
warm
congested
rate_limited
```

---

## 初版不用做到精確 TPM accounting

先做：

```text
concurrency cap
+
rolling estimate
+
429 feedback
+
backoff
```

例如：

```text
Terra/Sonnet constrained bucket

maximum simultaneous heavy turns: 1
```

或由 configuration 決定。

---

## Context policy

Thread/flagship harness 使用目前策略：

```text
effective compact target ≈ 300K
keep recent ≈ 50K
```

目標不是榨滿 1M context。

目標是：

```text
避免單一 turn 太大
+
降低 shared TPM burst
```

---

# 18. TASK MODEL

Task 可以從：

```text
Chat
Web UI
Agent
SCM/CI event
```

建立。

Task types：

```text
quick-change
feature
bug
investigation
review
maintenance
ci-fix
```

---

## Task creation from Chat

使用者：

```text
> 幫我修一下 null pointer
```

Thread Agent 判斷需要 mutation：

```text
bridge_task_start(
  title="Fix null pointer in ...",
  kind="quick-change"
)
```

Bridge：

```text
create task
bind current thread
bind current agent
ensure writable workspace
return task_id
```

---

## Web UI Task

Web：

```text
Create Task
repo: backend
title: Add TokenReview audience validation
priority: normal
```

Bridge：

```text
task queued
```

可以：

```text
assign existing Thread Agent
```

或：

```text
create/open repo Thread
```

---

# 19. VERIFICATION PIPELINE

這是系統很重要的一層。

Agent swarm 不只是：

```text
很多 agent 寫 code
```

而是：

```text
寫 code
→ deterministic verification
→ independent review when useful
→ delivery
→ CI feedback
```

---

## Repo Verification Profile

每個 repo 可以有：

```yaml
version: 1

verify:
  - name: lint
    command: pnpm lint
    timeout: 300

  - name: typecheck
    command: pnpm typecheck
    timeout: 300

  - name: unit
    command: pnpm test
    timeout: 900

  - name: build
    command: pnpm build
    timeout: 900
```

檔名暫定：

```text
.agent-control.yaml
```

之後可改。

---

## Config discovery

如果 repo 沒 config：

Bridge 可以：

1. 看 package.json / Makefile / pyproject / Gradle 等。
2. 建立 initial suggestion。
3. 讓 Thread Agent確認。
4. 保存 repo policy。

不要每次都叫 LLM重新猜：

> 應該跑什麼 test？

---

## Verification levels

### QUICK

例如 docs/typo：

```text
format
affected lightweight checks
```

### NORMAL

```text
lint
typecheck
unit
build if reasonable
```

### HIGH

```text
full tests
build
integration
independent review
```

---

# 20. REVIEW POLICY

不要所有 code 都 spawn Sonnet review。

這會放大 token。

建議：

```text
risk=low
→ no independent LLM review

risk=medium
→ deterministic verify
→ optional reviewer

risk=high
→ deterministic verify
→ independent Sonnet review
→ human approval

security/auth/db/schema/infrastructure
→ high by default
```

---

# 21. DELIVERY

抽象：

```text
DeliveryAdapter
```

模式：

```text
local_land
remote_pr
manual
```

---

## local_land

適合：

- personal repo
- local integration
- temporary branch workflow

Herdr mechanical land。

---

## remote_pr

公司 repo 建議長期 default。

流程：

```text
verified worktree
→ commit
→ push branch
→ GitLab MR / Azure PR
→ CI
→ review/merge policy
```

Bridge monitor：

```text
CI failed
→ reopen task
→ message same Thread Agent
→ agent fix
→ push
```

---

# 22. CHAT UI

Chat 是：

```text
fast interaction surface
```

不是 observability dashboard。

---

## Thread title

沿用目前設計：

```text
🟢 idle
🟡 busy
🔴 blocked
⚪ unknown
⚫ closed
```

可以再加 task indicator：

```text
🟡 A repo · #123
```

---

## Blocked interaction

例如：

```text
🔴 A repo needs input

Migration strategy:

A. backward compatible
B. breaking change

[🅰️] [🅱️] [❌]
```

reaction：

```text
→ Interaction resolved
→ agent receives answer
→ resumes
```

---

## Tail

保留：

```text
!tail
```

但定位成：

```text
debug / escape hatch
```

不是正常 observability。

正常使用者應看到：

```text
Working:
Running integration tests

Duration:
8m

Task:
#123 auth validation
```

而不是必須看 terminal。

---

# 23. WEB UI

Web UI 不需要取代 Chat。

分工：

```text
Chat
→ interaction

Web
→ observability / task management
```

---

## Web UI V1

Pages：

```text
Dashboard
Agents
Tasks
Repositories
Machines
Worktrees
```

---

## Dashboard

顯示：

```text
Active Agents
Blocked Agents
Running Tasks
Failed Verification
Ready for Review
Quota Pressure
Machine Health
```

---

## Task Detail

```text
Task #123

Repo
Thread
Agent
Machine
Model
State

Timeline

Workspace
Branch

Verification
Diff summary

Messages

Actions:
Pause
Send Message
Retry
Review
Land/Deliver
Cancel
```

---

## Web UI 初期可以 read-heavy

先把 observability 做好。

不要一開始就把 terminal、IDE、diff editor 都搬進 Web。

---

# 24. TOOLS EXPOSED TO AGENTS

核心原則：

> Tool 負責 structured action。\
> Skill 負責教 agent policy / semantics。

---

## Room Agent Tools

```text
bridge_repo_search
bridge_repo_get

bridge_thread_create
bridge_thread_list
bridge_thread_get

bridge_agent_list
bridge_agent_get
bridge_agent_send

bridge_task_create
bridge_task_list
bridge_task_get

bridge_machine_list
bridge_capacity_get

bridge_interaction_create
```

---

## Thread Agent Tools

```text
bridge_context

bridge_task_start
bridge_task_get
bridge_task_ready
bridge_task_fail

bridge_worker_spawn
bridge_worker_get
bridge_worker_stop

bridge_agent_list
bridge_agent_send

bridge_capacity_get

bridge_interaction_request
```

---

## Worker Tools

```text
bridge_task_get
bridge_task_report
bridge_task_ready

bridge_agent_send_parent

bridge_interaction_request
```

Worker 不給：

```text
global worker_spawn
global thread creation
machine admin
repo admin
```

---

# 25. TOOL IMPLEMENTATION STRATEGY

不要只做 OpenCode-specific TypeScript tools。

Canonical integration 應該是：

```text
Bridge API
   │
   ├── bridgectl CLI
   │
   └── OpenCode custom tools
```

---

## Why bridgectl

任何 harness 都能：

```bash
bridgectl agent list
bridgectl task get ...
bridgectl worker spawn ...
```

所以：

```text
Claude Code
Codex
future harness
```

即使沒有 OpenCode custom-tool system，也能工作。

---

## OpenCode custom tool

OpenCode 版可以包裝成：

```text
bridge_agent_list()
```

讓 agent 得到：

- schema
- validated arguments
- structured result

而不是自己 parse CLI output。

---

## Future

如果真的需要：

```text
MCP server
```

可以包在同一 Bridge API 上。

但 V1 不必優先做 MCP。

---

# 26. SKILLS

## bridge-control skill

所有 managed agent 應有。

內容包括：

- persistent peer vs local subagent
- task lifecycle
- worker rules
- messaging semantics
- blocked interaction
- verification
- delivery
- quota awareness

---

## herdr skill

不要作為 managed agent 的主要 orchestration interface。

因為：

```text
Agent direct Herdr mutation
→ Bridge DB 不知道
→ state drift
```

建議：

```text
herdr-debug
```

只作：

- inspection
- debugging
- emergency/manual recovery

正常 lifecycle 必須透過 Bridge tools。

---

# 27. HARNESS ABSTRACTION

定義：

```text
HarnessAdapter
```

大致能力：

```text
start()
resume()
prompt()
abort()
reset()
status()
read()
close()

capabilities()
nativeSessionRef()
```

optional capability：

```text
structuredMessages
permissionResponse
diff
tokenUsage
sessionFork
```

---

## Routing principle

一般 runtime control：

```text
Bridge
→ Herdr
→ Harness
```

如果 harness 有 structured native API：

```text
Bridge
→ OpenCode Adapter
```

可用於：

- session metadata
- diff
- permission response
- token usage
- richer events

但 domain logic 不應依賴它。

---

# 28. DIRECT LLM API

V1：

```text
不要讓 Bridge 自己大量直接 call LLM。
```

主要 reasoning 繼續由 harness agent。

---

## 未來適合 direct API 的工作

stateless micro-task：

```text
classify message
generate title
summarize logs
risk classification
choose repo among candidates
summarize CI failure
```

要求：

```text
small context
structured output
cheap model
strict token cap
no filesystem mutation
```

它應該是：

```text
ReasoningService
```

跟 HarnessAdapter 分開。

---

# 29. MACHINE + HARNESS + MODEL 是三個獨立維度

Agent Run 不應只保存：

```text
model=terra
```

而應是：

```text
machine = dev-vm
harness = opencode
model_class = primary
resolved_model = terra
```

未來可以：

```text
machine=local
harness=codex
model=...
```

完全不影響 Task domain。

---

# 30. DETAILED FLOW — CREATE REPO THREAD

```text
User
 │
 │ "幫我開 A repo"
 ▼
Room Agent
 │
 │ bridge_repo_search("A")
 ▼
Bridge
 │
 ├─ search Repository Catalog
 └─ return repo
 │
 ▼
Room Agent
 │
 │ bridge_thread_create(repo=A)
 ▼
Bridge
 │
 ├─ create Chat Thread
 ├─ create Thread DB row
 ├─ ensure repo materialization
 ├─ ask Herdr create/reuse worktree
 ├─ start Thread Agent
 ├─ bind agent
 └─ update thread title
 │
 ▼
Chat

🟢 A repo
  workdir: ...
```

---

# 31. DETAILED FLOW — READ-ONLY QUESTION

```text
User
 │
 │ "這個 auth flow 怎麼走？"
 ▼
Thread Agent
 │
 ├─ inspect code
 ├─ optional explore subagent
 └─ answer
```

不建立 Task 也可以。

不建立 worker。

---

# 32. DETAILED FLOW — QUICK CODE CHANGE

```text
User
 │
 │ "把這個 null check 修掉"
 ▼
Thread Agent
 │
 ├─ bridge_task_start(kind=quick)
 ├─ edit
 ├─ test/debug
 └─ bridge_task_ready()
           │
           ▼
         Bridge
           │
           ├─ run verification
           │
           ├─ risk policy
           │
           ├─ delivery
           │
           └─ task done
```

Chat：

```text
✅ Fixed null check

Verification:
✓ lint
✓ unit

Task #123 complete
```

---

# 33. DETAILED FLOW — PARALLEL WORKER

```text
Thread Agent
 │
 │ "需要同時查 flaky test"
 │
 └─ bridge_worker_spawn(
       purpose="Investigate flaky test"
    )
            │
            ▼
          Bridge
            │
            ├─ choose machine
            ├─ choose model class
            ├─ ensure repo
            ├─ create worktree
            ├─ start Herdr agent
            └─ register Worker
                      │
                      ▼
                   Worker
                      │
                      ├─ investigate
                      └─ report result
                            │
                            ▼
                      Thread Agent
```

---

# 34. DETAILED FLOW — DEV VM

```text
Thread Agent
 │
 │ spawn integration worker
 ▼
Bridge Scheduler
 │
 ├─ local machine busy
 ├─ dev-vm healthy
 └─ choose dev-vm
 │
 ▼
ensureRepository(repo, dev-vm)
 │
 ▼
Herdr --machine dev-vm
 │
 ├─ worktree
 └─ start harness
```

Machine placement 與 model quota placement 分開。

---

# 35. DETAILED FLOW — EXISTING DEFAULT AGENT

```text
Thread Agent
 │
 │ bridge_agent_list(repo=A)
 ▼
Bridge

returns:

default:auth
  external
  busy
  cwd=A
```

Thread Agent：

```text
bridge_agent_send(
  default:auth,
  "你目前 auth refactor 有處理 audience 嗎？"
)
```

Bridge：

```text
persist message
→ Herdr default session
→ prompt agent
→ result routed back
```

---

# 36. DETAILED FLOW — BLOCKED AGENT

```text
Herdr
 │
 │ state = blocked
 ▼
Bridge event subscriber
 │
 ├─ lookup agent
 ├─ lookup task
 ├─ inspect recent output
 └─ create Interaction
 │
 ▼
Chat

🔴 backend needs input
...
```

User reaction：

```text
🅰️
```

Bridge：

```text
resolve Interaction
→ deliver answer
→ mark busy
```

---

# 37. DETAILED FLOW — VERIFICATION FAIL

```text
Agent READY
 │
 ▼
Bridge Verify
 │
 ├─ lint ✓
 ├─ typecheck ✓
 └─ test ✗
       │
       ▼
Task → running

Bridge
 │
 └─ prompt same Thread Agent:

"Final verification failed:
 test X ...
 Fix and mark ready again."
```

不要馬上 spawn 新 agent。

---

# 38. DETAILED FLOW — REMOTE CI FAILURE

未來：

```text
Task
→ PR
→ CI
→ failure
     │
     ▼
SCM Webhook
     │
     ▼
Bridge
     │
     ├─ map PR → Task
     ├─ summarize failure
     └─ notify same Thread Agent
            │
            ▼
         fix/push
```

這才是 swarm 對 SDLC 最大的價值之一：

> 自動維持 feedback loop continuity。

---

# 39. DETAILED FLOW — MULTI-REPO PROJECT

```text
Project Task
"backend API + frontend UI"
       │
       ▼
Project coordinator
       │
       ├─ Child Task A
       │    repo=backend
       │
       └─ Child Task B
            repo=frontend
```

各自：

```text
repo-specific worktree
repo-specific agent
repo-specific verification
```

parent Task：

```text
wait children
→ integration verification
→ done
```

V1 不必先實作 dependency DAG。

先支援：

```text
parent_task_id
```

就夠。

---

# 40. DETAILED FLOW — BRIDGE RESTART

Bridge 必須可 crash/restart。

啟動：

```text
load SQLite
   │
   ├─ query Herdr local snapshot
   ├─ query Herdr machine snapshots
   ├─ inspect managed repos/worktrees
   └─ reconcile
```

處理：

```text
DB says running
Herdr missing
→ agent lost

Herdr has managed agent
DB missing
→ discovered orphan

DB says worktree active
git path missing
→ broken workspace

agent session changed
→ update HarnessSession
```

不要依賴 memory-only state。

---

# 41. DATABASE TABLES — V1

至少：

```text
repositories
projects
project_repositories

repo_materializations

chat_rooms
chat_threads

agent_profiles
agents
harness_sessions
agent_links
agent_messages

tasks
runs

workspaces

machines

interactions

verification_runs
verification_steps

events
```

---

# 42. EVENT MODEL

建議有 append-only event log：

```text
events
------
id
type
entity_type
entity_id
payload
created_at
```

例如：

```text
chat.message_received
thread.created

agent.started
agent.state_changed
agent.blocked
agent.closed

task.created
task.started
task.ready
task.failed
task.completed

worktree.created
worktree.landed
worktree.removed

verification.started
verification.failed
verification.passed

interaction.created
interaction.resolved

machine.online
machine.offline
```

好處：

- debugging
- Web UI timeline
- restart recovery
- audit trail

---

# 43. STATE RECONCILIATION

Herdr 是 runtime truth。

SQLite 是 domain truth。

Git 是 workspace truth。

SCM 是 remote repo truth。

Bridge 必須 reconcile，而不是假設任何一方永遠同步。

例如：

```text
Herdr: agent idle
SQLite: busy

→ update busy → idle
```

但：

```text
Herdr: agent gone
Task: running

→ Task 不直接 done
→ mark run lost
→ retry/recover
```

---

# 44. LOCKS / LEASES

需要簡單 application-level leases。

至少：

```text
one Thread primary agent
one writer per worktree
one active land operation per target branch
one ensure-clone operation per repo/machine
```

避免兩個 worker 同時：

```text
git fetch/reset parent
```

或同時 land。

---

# 45. STATUS MAPPING

Herdr runtime：

```text
working
blocked
done
idle
unknown
```

Bridge normalize：

```text
busy
blocked
idle
closed
unknown
```

Chat：

```text
🟡 busy
🔴 blocked
🟢 idle
⚫ closed
⚪ unknown
```

Task status 與 Agent status 不要混成一個欄位。

---

# 46. SECURITY / SAFETY

Agent 不應取得：

```text
GitLab access token
Azure token
Chat API token
Bridge DB credentials
```

由 Bridge service 做 privileged API。

---

## Destructive actions

例如：

```text
delete worktree
force reset
push force
merge protected branch
deploy
```

需要 policy。

---

## Audit

保存：

```text
who requested
which agent
which task
which repo
which machine
what happened
when
```

---

# 47. BRIDGE SHOULD NOT LET AGENTS BYPASS CONTROL PLANE

正常 managed Agent 不應：

```bash
herdr agent start ...
herdr worktree remove ...
```

來管理 persistent swarm。

因為 Bridge DB 會 drift。

如果 agent 需要：

```text
spawn peer
```

必須：

```text
bridge_worker_spawn()
```

如果需要：

```text
talk peer
```

必須：

```text
bridge_agent_send()
```

---

# 48. DIRECT HERDR ACCESS

仍然允許：

```text
human/admin
debug tool
emergency recovery
```

Bridge 之後透過 reconciliation 修正狀態。

---

# 49. SOURCE OF TRUTH HIERARCHY

非常重要：

```text
User intent / Task ownership
→ Bridge DB

Agent process/runtime
→ Herdr

Code state
→ Git

Remote source / PR / CI
→ GitLab/Azure

Reasoning/context
→ Harness
```

不要互相侵犯 ownership。

---

# 50. ROOM / THREAD / WORKER PERMISSIONS

## Room

```text
Can:
repo search
thread create
task create
agent query
agent message
machine query

Cannot:
edit repo
arbitrary shell
direct worktree mutation
```

## Thread

```text
Can:
read/write repo
bash
local subagents
task lifecycle
spawn worker
message agents
request interaction

Cannot:
global admin
secret access
unrestricted worker recursion
```

## Worker

```text
Can:
edit own worktree
bash
local subagents
report task
contact parent

Cannot:
spawn persistent workers
modify global registry
land directly without policy
```

---

# 51. PROJECT STRUCTURE SUGGESTION

Conceptual modules：

```text
src/
  chat/
    adapter
    reactions
    thread-ui

  control/
    agent-registry
    messaging
    interactions

  repo/
    catalog
    materializer
    projects

  scm/
    gitlab
    azure
    manual

  task/
    service
    state-machine
    runs

  workspace/
    manager
    leases
    delivery

  verify/
    profiles
    runner
    policy

  herdr/
    client
    event-subscriber
    reconciler

  harness/
    adapter
    opencode
    claude-code
    codex

  machine/
    registry
    scheduler

  model/
    router
    quota-governor

  web/
    api
    ui

  db/
    migrations
    repositories

  cli/
    bridgectl
```

實際 language/framework 可依現有 Bridge stack。

---

# 52. IMPLEMENTATION PHASES

## Phase 1 — Control Plane Foundation

先完成：

```text
Agent Registry
Thread binding
Herdr adapter
Herdr event sync
agent_list/get/send
SQLite migrations
default/chat session discovery
```

成功條件：

```text
所有 local Herdr agent
→ Bridge 都看得到
→ 可以用 stable ID address
→ restart 後恢復
```

---

## Phase 2 — Repo Catalog

完成：

```text
GitLab discovery
Azure discovery
Repository Catalog
repo search
repo materialization
managed clone root
```

成功：

```text
Room:
"開 A repo"

不用知道 path。
```

---

## Phase 3 — Managed Thread Worktree

完成：

```text
Thread create
→ managed repo
→ managed worktree
→ Thread Agent
```

成功：

```text
兩個同 repo Thread
不會共享 writable tree。
```

---

## Phase 4 — Task + Verification

完成：

```text
quick task
normal task
task state
verification profile
verification runner
task-ready flow
```

成功：

```text
Chat 小修改
→ automatic Task
→ code
→ verify
→ done
```

---

## Phase 5 — Worker / Swarm

完成：

```text
worker_spawn
worker lifecycle
agent messaging
parent/worker relation
worker worktree
worker cleanup
```

成功：

```text
Thread Agent 可以安全 parallelize。
```

---

## Phase 6 — Remote Machine

完成：

```text
Machine Registry
Herdr machine adapter
remote repo materialization
remote workers
```

成功：

```text
worker 可以透明跑 local 或 dev-vm。
```

---

## Phase 7 — Quota / Model Router

完成：

```text
model class
model routing
quota bucket
concurrency guard
rate-limit backoff
capacity tool
```

成功：

```text
swarm 不會因為同時啟動很多 Terra/Sonnet
立刻撞 shared TPM。
```

---

## Phase 8 — Web UI

先做：

```text
Dashboard
Agents
Tasks
Repos
Machines
Worktrees
```

先 read-heavy。

---

## Phase 9 — SCM Delivery / CI Loop

完成：

```text
push
MR/PR
pipeline events
CI retry loop
```

---

## Phase 10 — Alternate Harnesses

確認：

```text
Claude Code
Codex
```

至少可以透過：

```text
Herdr
+
bridgectl
+
bridge skill
```

完成相同 Task lifecycle。

---

# 53. ACCEPTANCE SCENARIOS

Coding agent 實作時至少用以下 scenarios 驗證 architecture。

---

## Scenario A

```text
User:
"幫我開 backend repo"
```

應：

```text
repo search
→ on-demand clone
→ managed worktree
→ Terra Thread Agent
→ Chat Thread
```

---

## Scenario B

同 repo 開第二個 Thread。

應：

```text
different worktree
different agent
same managed parent repo
```

不能互相污染。

---

## Scenario C

Thread：

```text
"幫我解釋 TokenReview"
```

應：

```text
不建立 worker
不建立 Task
直接 answer
```

---

## Scenario D

Thread：

```text
"幫我把 audience validation 加上"
```

應：

```text
implicit Task
→ edit
→ deterministic verify
→ ready
```

---

## Scenario E

驗證失敗。

不能：

```text
Task done
```

必須：

```text
same agent receives failure
→ fixes
```

---

## Scenario F

Thread Agent spawn Worker。

Worker：

```text
own worktree
own Run
parent link
```

完成後 parent 收 result。

---

## Scenario G

Dev VM。

Bridge：

```text
ensure repo on VM
→ Herdr machine
→ worker
```

Chat usage 不應看出 machine implementation detail。

---

## Scenario H

Terra quota congested。

系統不能無限制再 spawn Terra。

應：

```text
queue
fallback
or reject delegation
```

依 policy。

---

## Scenario I

Bridge process restart。

existing agents/tasks/worktrees 不能全部消失。

應 reconcile。

---

## Scenario J

default session existing agent。

Thread Agent 可：

```text
discover
message
receive reply
```

但 Bridge 不擅自 delete/land。

---

## Scenario K

切換 Thread harness：

```text
OpenCode → Claude Code
```

Task/Repo/Thread schema 不需修改。

---

## Scenario L

Blocked agent。

Chat 收到 structured interaction。

Reaction 可以恢復 agent。

---

# 54. NON-GOALS — V1

V1 不做：

```text
fully autonomous task decomposition
recursive swarm
distributed consensus
agent voting
agent marketplace
complex DAG scheduler
automatic production deployment
replace GitLab/Azure CI
custom terminal runtime
own SSH multiplexing
own git-worktree implementation
one AI model deciding every infrastructure event
```

---

# 55. IMPORTANT DESIGN PRINCIPLE

不是：

```text
LLM everywhere
```

而是：

```text
program when deterministic
LLM when judgment is needed
```

例如：

```text
agent state
→ program

repo clone
→ program

worktree lifecycle
→ program

rate limit
→ program

test pipeline
→ program

reaction
→ program

"這個 bug 可能在哪？"
→ LLM

"怎麼實作？"
→ LLM

"是否需要 parallel investigation？"
→ LLM + policy
```

---

# 56. WHY THIS FEELS LIKE A SWARM

不是因為畫面有 10 個 agent。

而是因為：

```text
一個 Thread Agent
可以知道：

- 我在哪個 repo
- 我擁有哪個 worktree
- 我現在是哪個 Task
- 還有哪些 agent
- 哪些 agent 有相關 context
- 哪台 machine 可用
- 哪些 model quota busy
- 我能安全 spawn 哪種 worker
- worker 完成後結果會回來
- verify / review / delivery 會自動繼續
```

Human：

```text
只需要介入 decisions。
```

---

# 57. V1 CONCRETE DEFAULTS

先不要全部 configurable。

使用以下 defaults：

```text
Database:
SQLite

Runtime:
Herdr

Primary harness:
OpenCode

Room model:
Luna

Thread model:
Terra

Flagship:
Sol

General / reviewer:
Sonnet 5

Explore:
Haiku

Deep Explore:
Qwen 3.8 27B

Implement subagent:
None

Thread scope:
Repo

Thread workspace:
Long-lived Herdr managed worktree

Worker workspace:
Temporary Herdr managed worktree

Repo cloning:
Bridge-managed on demand

Remote execution:
Herdr machine

Persistent messaging:
Bridge DB + Herdr transport

Task verification:
Bridge deterministic pipeline

Final company delivery:
eventually PR/MR + CI

Direct LLM from Bridge:
not required for V1
```

---

# 58. OPEN QUESTIONS — WORTH DISCUSSING LATER

這些不要阻塞 Phase 1。

## Q1. Thread worktree 要 persistent 還是 per-task？

目前決策：

```text
persistent per Thread
```

理由：context reuse / token efficiency。

之後量測：

- dirty-state problem
- branch drift
- stale context
- land complexity

再決定是否改 per-task。

---

## Q2. Company repo 最終 delivery

需要決定：

```text
local land
vs
push branch + MR/PR
```

推薦最終：

```text
MR/PR
```

---

## Q3. Room Agent 是否 Luna 足夠

推薦先 Luna。

如果 tool routing accuracy 不夠，再升 Terra。

---

## Q4. Direct LLM API

先不做。

等發現：

```text
大量簡單 classification
一直啟動 full OpenCode agent 很浪費
```

再增加。

---

## Q5. Project Thread

Domain schema 先支援 Project。

真正 cross-repo Project Agent 後做。

---

## Q6. Task auto-review threshold

需要使用一陣子收集：

```text
verification failure rate
review findings
token usage
```

再 tune。

---

## Q7. Quota accounting

第一版 soft governor。

未來如果能取得可靠 provider token telemetry：

```text
upgrade to reservation-based scheduler
```

---

# 59. QUESTIONS FOR FUTURE “GRILL ME” SESSION

之後需要明確回答：

1. Managed repo 的 canonical parent checkout 是否允許人直接修改？
2. Thread worktree land 後 agent session 是否保留，還是 `/new`？
3. 一個 repo 是否允許多個長壽命 Threads？
4. Thread branch naming convention？
5. Agent commit policy？
6. Task 完成前是否強制 clean git status？
7. protected branch delivery policy？
8. 哪些 verification 可以跳過？
9. 哪些 path 自動標 high risk？
10. migration / infra change 是否必須 human approval？
11. Sonnet reviewer 使用 threshold？
12. Terra shared TPM 的 concurrency 上限？
13. dev-vm 是否與 local 共用 credential？
14. dev-vm 是否允許 background service 長跑？
15. remote agent blocked 如何 notification？
16. Bridge down 時 agents 是否允許繼續？
17. Agent 完成但 Bridge down 時如何重新 reconcile？
18. external/default agent adoption 的 ownership semantics？
19. Chat message delete 是否影響 Task history？
20. Web UI authentication？
21. SCM webhook 還是 polling？
22. Task 是否需要 priority queue？
23. 是否要 deadline？
24. Project Task 是否需要 dependency DAG？
25. 是否需要 agent cost accounting？
26. model/provider 429 如何 feedback scheduler？
27. 是否自動 compact/reset long-lived Thread Agent？
28. long-lived agent 多久 idle 後 suspend？
29. archived Thread 是否 terminate agent/worktree？
30. Task history 要保存多久？

---

# 60. CODING AGENT INSTRUCTION

接手實作者請遵循以下原則：

> 不要先實作 autonomous planner。
>
> 不要先做 complex swarm scheduler。
>
> 不要重新實作 Herdr。
>
> 不要讓 OpenCode session 成為 domain identity。
>
> 不要讓 Chat Thread ID 成為 agent identity。
>
> 不要 auto clone 所有 repo。
>
> 不要讓 worker 無限制 recursive spawn。
>
> 不要讓 LLM 決定 deterministic infrastructure state。
>
> 不要為未來多機器需求導入 distributed database。

優先完成：

```text
durable identity
→ state reconciliation
→ repo ownership
→ worktree ownership
→ agent messaging
→ task lifecycle
→ deterministic verification
→ worker lifecycle
→ remote machine
→ quota routing
→ Web UI
```

當以上 primitives 穩定之後：

```text
autonomous orchestration
```

會自然變成非常薄的一層，而不是整個系統最複雜的一層。

---

# 61. FINAL MENTAL MODEL

整套系統最後應該能用下面這張圖理解：

```text
                         HUMAN
                           │
             ┌─────────────┴──────────────┐
             │                            │
        Company Chat                    Web UI
             │                            │
             └─────────────┬──────────────┘
                           │
                  ┌────────▼────────┐
                  │     BRIDGE      │
                  │ Agent Control   │
                  │     Plane       │
                  └────────┬────────┘
                           │
      ┌────────────────────┼─────────────────────┐
      │                    │                     │
 Repository Catalog    Task / Agent DB      Policy / Quota
      │                    │                     │
      └────────────────────┼─────────────────────┘
                           │
                        HERDR
                  Execution Runtime
                  ┌────────┴─────────┐
                  │                  │
              workstation          dev-vm
                  │                  │
           ┌──────┼──────┐      ┌────┼────┐
           │      │      │      │    │    │
        Thread  Worker External Worker ...
         Agent   Agent   Agent
           │
           │ OpenCode / Claude Code / Codex
           │
           ├── local subagents
           ├── edit
           ├── debug
           └── reasoning
                  │
                  ▼
               Git Repo
                  │
                  ▼
             Verification
                  │
                  ▼
             GitLab/Azure
                  │
             PR/MR + CI
                  │
                  ▼
                 DONE
```

Bridge 的核心角色：

> **知道誰在做什麼、在哪裡做、為什麼做、做到哪裡、接下來應該發生什麼。**

Herdr：

> **讓那些 agent 真正活著並能執行。**

OpenCode / CC / Codex：

> **負責思考與完成工程工作。**

SCM / CI：

> **負責真正的 code collaboration 與最終驗證。**

這就是 V1 的 architecture boundary。
