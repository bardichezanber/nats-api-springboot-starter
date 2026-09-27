# Company Context Learning System --- OpenCode V1 `/learn` Architecture

> **Status:** Draft 2 --- OpenCode V1 implementation baseline\
> **Purpose:** A practical specification for turning real engineering
> sessions into reusable company context without contaminating the
> original working session.\
> **Initial harness:** OpenCode V1\
> **Default learner model:** Terra\
> **Long-term direction:** Harness-independent Context/Learning Core
> with OpenCode V1/V2, Claude Code, and Codex adapters.

------------------------------------------------------------------------

## 1. Goal

The engineering agents already know how to code. The missing layer is
durable company knowledge:

-   how repositories relate to services and projects;
-   how CI/CD actually works;
-   how test/staging/production environments are operated;
-   where GitOps source of truth lives;
-   how a particular repository is built, deployed, and verified;
-   which workflows are reusable playbooks versus one-off execution
    details;
-   which rules are actual company policy versus behavior observed once.

A typical real session may look like:

``` text
change code
→ commit
→ push
→ inspect Azure pipeline
→ wait for image
→ temporarily change image in test Kubernetes
→ rollout
→ test
→ fix
→ rebuild
→ redeploy
→ verify
→ synchronize final desired state back to GitOps
→ verify drift is gone
```

The first time, the human may teach the agent much of this.

The desired feedback loop is:

``` text
real work
   ↓
 /learn
   ↓
distill durable knowledge
   ↓
future agents need less explanation
```

The system should improve from use without turning every historical
detail into permanent prompt context.

------------------------------------------------------------------------

## 2. Primary Design Decision

`/learn` is **not merely a Skill**.

It is a learning workflow exposed through a thin OpenCode V1 adapter.

The architecture is:

``` text
OpenCode V1
    │
    │ /learn or context_learn()
    ▼
Learning Service
    │
    ├── OpenCode V1 Session Adapter
    │       └── fork / prompt / status
    │
    └── Context Core
            ├── classify
            ├── resolve
            ├── validate
            ├── persist
            └── propose
                    │
                    ▼
          company-agent-context
```

The important rule is:

> **Learning semantics must not live inside the OpenCode plugin.**

OpenCode is the first harness, not the owner of the knowledge model.

------------------------------------------------------------------------

## 3. Why Fork the Session

The learner needs the actual history of the work that just happened.

A normal fresh-context subagent is therefore the wrong primitive.

Use:

``` text
Working Session
      │
      │ fork at a fixed message boundary
      ▼
Learning Session
      │
      ├── dedicated context-learner agent
      ├── Terra
      ├── read-only operational permissions
      └── Context Core tools
```

The parent session must remain unchanged.

The learning session:

-   does not continue normal implementation work;
-   does not change the parent agent or model;
-   does not inject its reasoning back into the parent;
-   may finish asynchronously;
-   may be archived after completion;
-   produces only a concise learning report plus persisted/proposed
    knowledge.

For OpenCode V1, the implementation should use the V1-supported
session/server API to fork an existing session. The adapter must isolate
all version-specific API details.

------------------------------------------------------------------------

## 4. OpenCode V1 Baseline

Do **not** require OpenCode V2 for V1 of this system.

The OpenCode V1 integration should rely on the smallest set of
primitives available in V1:

``` text
custom command and/or thin plugin tool
session/server API
session fork
dedicated agent configuration
model selection
AGENTS.md
Skills
custom tools
```

Do not make dynamic system-prompt hooks a prerequisite.

### V1 learner context model

Use:

``` text
static context-learner agent instructions
+
forked source conversation
+
small dynamic Learning Job prompt
```

instead of requiring:

``` text
runtime dynamic system injection
```

### V1 normal-agent context model

Use:

``` text
small global AGENTS.md
+
repo-local AGENTS.md
+
company-context Skill
+
context_resolve/context_search tools
```

Dynamic system injection can be added later as a harness optimization.

------------------------------------------------------------------------

## 5. Repository Boundaries

Use two repositories initially.

``` text
agent-control-plane/
    runtime code
    OpenCode V1 adapter
    Learning Service
    Context Core

company-agent-context/
    canonical company knowledge
    schemas
    Skills
    profiles
    learned knowledge
    proposals
    run evidence
```

If the existing codebase is still called `bridge`, keep the
implementation there:

``` text
bridge/
├── chat/
├── herdr/
└── context-learning/

company-agent-context/
```

Do not create a third standalone code repository yet.

Extract packages later only if real usage justifies it.

------------------------------------------------------------------------

## 6. Context Repository Layout

Start with this target layout:

``` text
company-agent-context/
│
├── README.md
│
├── schema/
│   ├── repo-profile.schema.json
│   ├── project-profile.schema.json
│   ├── environment-profile.schema.json
│   └── learning-proposal.schema.json
│
├── global/
│   └── constitution.md
│
├── company/
│   ├── engineering-map.md
│   ├── ci-cd.md
│   ├── kubernetes.md
│   ├── gitops.md
│   ├── policies/
│   ├── standards/
│   └── adrs/
│
├── skills/
│   └── test-k8s-change/
│       └── SKILL.md
│
├── projects/
│   └── <project-id>/
│       ├── profile.yaml
│       └── learned.yaml
│
├── repos/
│   └── <repo-id>/
│       ├── profile.yaml
│       └── learned.yaml
│
├── environments/
│   └── <environment-id>/
│       ├── profile.yaml
│       └── learned.yaml
│
├── runs/
│   └── YYYY/MM/<run-id>.yaml
│
└── proposals/
    ├── open/
    ├── accepted/
    └── rejected/
```

Do not create every file immediately. This is the target structure.

------------------------------------------------------------------------

## 7. Curated vs Learned

A key boundary is:

``` text
profile.yaml
= human-curated / authoritative

learned.yaml
= learner-observed durable knowledge
```

Resolution precedence:

``` text
curated profile
    >
authoritative discovered configuration
    >
high-confidence learned knowledge
    >
low-confidence learned knowledge
```

A learner must never silently overwrite a conflicting curated fact.

Conflict with curated knowledge becomes a proposal for human review.

------------------------------------------------------------------------

## 8. Four Sources of Context

Every piece of context should conceptually belong to one of four
origins.

### Curated

Human-authoritative material:

-   constitution;
-   company policy;
-   standards;
-   ADRs;
-   curated project/repo/environment profiles.

### Learned

Knowledge distilled from real work:

-   operational playbooks;
-   repository mappings;
-   project relationships;
-   troubleshooting lessons;
-   verification procedures.

### Discovered

Stable facts that programs can obtain from authoritative systems:

-   repository catalog;
-   pipeline definitions;
-   package metadata;
-   GitOps paths;
-   SCM metadata.

Prefer programmatic discovery over teaching the model these repeatedly.

### Live

State that is true now and may change immediately:

-   current pipeline status;
-   current image;
-   pod names;
-   branch head;
-   cluster health;
-   current agents/machines.

Live state must be queried at use time, not persisted as durable
context.

------------------------------------------------------------------------

## 9. Knowledge Classes

The learner must classify every candidate into exactly one primary
class:

``` text
GLOBAL_CONSTITUTION
COMPANY_CONTEXT
COMPANY_POLICY
STANDARD_OR_ADR
PLAYBOOK_SKILL
PROJECT_PROFILE
REPO_PROFILE
ENVIRONMENT_PROFILE
RUN_EVIDENCE
LIVE_STATE
SECRET_OR_SENSITIVE
ONE_OFF
UNCERTAIN
```

### GLOBAL_CONSTITUTION

Stable rules governing agent behavior across company work.

Examples:

-   prefer authoritative operational sources over conversation memory;
-   resolve company context instead of guessing;
-   distinguish live state from durable knowledge.

Proposal-only.

### COMPANY_CONTEXT

Stable descriptive facts about the engineering world.

Examples:

-   Kubernetes is used for application runtime;
-   GitOps repositories are separate from application repositories;
-   Azure DevOps is used for a class of CI pipelines.

A repo-specific pipeline does not belong here.

### COMPANY_POLICY

Rules expressing MUST, MUST NOT, REQUIRED, FORBIDDEN, or approval
semantics.

Observed behavior is not automatically policy.

Proposal-only unless confirmed by authoritative policy material.

### STANDARD_OR_ADR

Architecture, coding, platform, or engineering standards.

A decision made once for one task is not automatically an organizational
standard.

Proposal-only by default.

### PLAYBOOK_SKILL

Reusable HOW-TO procedure.

A good Skill describes:

-   goal;
-   when it applies;
-   prerequisites;
-   procedure;
-   decision points;
-   capabilities/tools;
-   verification;
-   completion criteria;
-   cleanup/reconciliation;
-   reusable failure/recovery guidance.

It must remove one-off execution identifiers.

### PROJECT_PROFILE

Durable context shared across repositories in one product/system.

Examples:

-   frontend/backend relationships;
-   cross-repo dependencies;
-   project-level release/test semantics.

### REPO_PROFILE

Durable repository-specific facts.

Examples:

-   build/test commands;
-   CI pipeline mapping;
-   produced image repository;
-   runtime workload;
-   GitOps repository/path;
-   smoke-test command;
-   related repositories.

### ENVIRONMENT_PROFILE

Stable properties of test/staging/production.

Examples:

-   cluster identity;
-   allowed mutation style;
-   deployment mechanism;
-   observability entry points;
-   restrictions.

Never put current image/pod/build state here.

### RUN_EVIDENCE

Concrete historical execution evidence.

Examples:

-   commit SHA;
-   pipeline run;
-   image generated;
-   test result;
-   rollout result;
-   GitOps commit;
-   observed failure.

Specific identifiers are allowed here.

### LIVE_STATE

Transient runtime state.

Do not persist as durable knowledge.

### SECRET_OR_SENSITIVE

Never persist secrets, credentials, cookies, private keys, bearer
tokens, connection secrets, or sensitive personal information.

### ONE_OFF

An event with no demonstrated reusable value.

May remain in run history if useful.

### UNCERTAIN

The learner cannot determine whether the information is durable,
authoritative, or generalizable.

Do not invent certainty.

------------------------------------------------------------------------

## 10. Core Classification Rules

### Narrowest valid scope

Always store knowledge at the narrowest scope where it remains true.

Prefer:

``` text
repo fact
over
company fact
```

when evidence only supports one repository.

Prefer:

``` text
run evidence
over
playbook rule
```

when an event may have been exceptional.

Never generalize simply because an operation succeeded once.

### What happened vs how work should be done

Example:

``` text
Pipeline #5512 produced image foo:abc123.
```

is run evidence.

``` text
After pushing a change, wait for the configured CI pipeline and resolve
the produced image before test deployment.
```

is reusable playbook knowledge.

### Generic procedure vs procedure parameter

Generic:

``` text
Resolve the workload in the target test environment.
```

belongs in a Skill.

Specific:

``` text
namespace=booking
workload=Deployment/backend-api
```

belongs in the repo/environment profile.

### Procedure vs policy

Ask:

``` text
Is this HOW work is normally performed?
```

→ Skill.

Ask:

``` text
Is this REQUIRED / ALLOWED / FORBIDDEN?
```

→ Policy.

Do not conflate them.

------------------------------------------------------------------------

## 11. Decontextualization

When converting a real execution into a reusable procedure:

Remove:

-   ticket-specific names;
-   one-off branch names;
-   commit SHAs;
-   image SHAs;
-   pipeline run IDs;
-   pod names;
-   transient failures;
-   temporary debugging commands.

Preserve:

-   required sequence;
-   decision points;
-   source-of-truth semantics;
-   safety constraints;
-   verification expectations;
-   cleanup/reconciliation requirements;
-   generic tool categories;
-   structurally reusable failure guidance.

Bad:

``` text
Run Backend-API build #814, wait for backend:0e13d9, then patch
booking-test Deployment/backend-api.
```

Better:

``` text
After pushing the application change, wait for the repository's configured
CI pipeline to produce an image. Resolve the resulting image through the
repository profile, then deploy it to the configured test workload.
```

The concrete pipeline/workload mapping belongs in the repo profile.

------------------------------------------------------------------------

## 12. `/learn` User Experience

OpenCode:

``` text
/learn
/learn --dry-run
```

Chat:

``` text
!learn
!learn --dry-run
```

V1 should start with dry-run.

The parent should return immediately with something equivalent to:

``` text
Learning job started: learn-20260927-001
```

The user can continue working.

The learner runs in the fork.

------------------------------------------------------------------------

## 13. Learning Job

Treat learning as a real domain object.

``` text
LearningJob
```

Suggested fields:

``` text
id
source_type
source_session_id
source_message_boundary
source_thread_id
source_repo_id
source_task_id

learner_session_id
model
mode
state

started_at
completed_at

summary
error

learner_prompt_version
classification_schema_version
context_schema_version
```

States:

``` text
created
forking
analyzing
corroborating
planning
applying
validating
completed
failed
cancelled
```

------------------------------------------------------------------------

## 14. `/learn` Execution Flow

``` text
/learn
   │
   ▼
OpenCode V1 Adapter
   │
   ├── capture source session
   ├── capture message boundary
   ├── determine cwd/repo
   └── create LearningJob
              │
              ▼
        Learning Service
              │
              ▼
      OpenCode V1 Session Adapter
              │
              ├── fork source session
              ├── select context-learner
              ├── select Terra
              └── send small job prompt
                         │
                         ▼
                  Context Learner
                         │
              ┌──────────┼──────────┐
              │          │          │
          Context      Repo      Evidence
            Store      Read        Tools
              │          │          │
              └──────────┼──────────┘
                         ▼
                  LearningReport
```

The parent session does not wait for the learner's reasoning.

------------------------------------------------------------------------

## 15. OpenCode V1 Adapter

All V1-specific behavior belongs behind an adapter.

Conceptual interface:

``` text
fork(sourceSession, boundary?)
selectAgent(session, agent)
selectModel(session, model)
prompt(session, message)
getStatus(session)
cancel(session)
```

Do not scatter raw OpenCode API calls throughout Context Core.

A future V2 adapter can implement the same conceptual interface
differently.

------------------------------------------------------------------------

## 16. `/learn` Command Implementation

Do not over-engineer the command itself.

V1 may expose `/learn` through:

-   a custom command;
-   a thin plugin tool;
-   or a custom command calling a thin tool/CLI.

The command should ultimately invoke something equivalent to:

``` text
context_learn(
  session=<current session>,
  boundary=<current message>,
  mode=dry-run
)
```

The actual fork, classification, persistence, and validation live
outside the command definition.

------------------------------------------------------------------------

## 17. First Prototype Before `/learn`

Before wiring the UI command, prove the primitive manually.

Target:

``` text
context-learn --session <session-id> --dry-run
```

It should:

1.  fork the supplied OpenCode V1 session;
2.  continue the fork with `context-learner`;
3.  use Terra;
4.  send the Learning Job prompt;
5.  produce a structured `LearningReport`;
6.  leave the original session untouched.

Only after this works should `/learn` wrap it.

Then `!learn` can wrap the same Learning Service.

------------------------------------------------------------------------

## 18. Dedicated `context-learner` Agent

Create a static OpenCode V1 agent configuration named conceptually:

``` text
context-learner
```

It should not be a normal coding agent.

Permissions:

### Allow

-   read;
-   glob/search;
-   repository inspection;
-   existing context inspection;
-   Context Core read/search/proposal tools;
-   selected read-only SCM/CI/GitOps metadata.

### Deny

-   normal application source edits;
-   git push;
-   deployment mutation;
-   Kubernetes mutation;
-   pipeline trigger;
-   production actions;
-   unrelated worker spawning.

The learner may inspect evidence but must not continue the engineering
task.

------------------------------------------------------------------------

## 19. Context Learner System Prompt

The following should be stored as a version-controlled prompt such as:

``` text
prompts/context-learner.md
```

Do not bury it inside a TypeScript string.

### Prompt

``` text
You are the Company Context Learner.

Your only purpose is to extract durable, reusable engineering knowledge
from a completed or partially completed working session and place that
knowledge into the correct context layer.

You are NOT the implementation agent.

Do not continue the original engineering task except for read-only
inspection necessary to corroborate knowledge.

Do not modify application source code, deploy workloads, trigger pipelines,
mutate infrastructure, or perform unrelated operational work.

Your job is knowledge distillation.

==================================================
PRIMARY OBJECTIVE
==================================================

Given:

1. the source conversation up to a fixed snapshot boundary,
2. the repository/project associated with the conversation,
3. existing company context,
4. existing Skills/playbooks,
5. existing project/repository/environment profiles,
6. optional task/run evidence,

determine which parts contain durable knowledge worth preserving.

Then:

- remove one-off details from reusable procedures;
- preserve useful one-off execution details as run evidence;
- distinguish generic workflows from repo-specific parameters;
- distinguish policy from observed behavior;
- distinguish static context from live runtime state;
- corroborate facts when feasible;
- deduplicate against existing knowledge;
- update the narrowest correct knowledge scope;
- never turn guesses into organizational truth.

==================================================
NARROWEST VALID SCOPE
==================================================

Always store knowledge at the narrowest scope where it remains true.

Prefer a repo-specific fact over a company-wide fact when evidence only
supports one repository.

Prefer a project-specific relationship over company-wide architecture when
evidence only supports one project.

Prefer run evidence over a reusable playbook rule when the event may have
been exceptional.

Never generalize merely because an operation succeeded once.

==================================================
KNOWLEDGE CLASSES
==================================================

Every candidate must have exactly one primary class:

GLOBAL_CONSTITUTION
COMPANY_CONTEXT
COMPANY_POLICY
STANDARD_OR_ADR
PLAYBOOK_SKILL
PROJECT_PROFILE
REPO_PROFILE
ENVIRONMENT_PROFILE
RUN_EVIDENCE
LIVE_STATE
SECRET_OR_SENSITIVE
ONE_OFF
UNCERTAIN

One source event may yield several distinct knowledge items.

Example:

A session shows:
- application code was pushed;
- Azure pipeline 1432 built image abc123;
- test Deployment/backend-api was patched to abc123;
- smoke test succeeded;
- GitOps repo was updated afterward.

Possible output:

PLAYBOOK_SKILL:
After pushing application code, wait for CI and resolve the produced image
before test deployment.

REPO_PROFILE:
This repository's application image corresponds to backend-api and its test
workload is Deployment/backend-api.

RUN_EVIDENCE:
Pipeline 1432 produced abc123 and the image passed the observed smoke test.

The build number and image SHA must not enter the reusable playbook.

==================================================
GLOBAL_CONSTITUTION
==================================================

Use only for stable rules governing agent behavior across essentially all
company work.

Do not directly modify canonical global constitution.

Create a proposal only.

A single working session is normally insufficient evidence.

==================================================
COMPANY_CONTEXT
==================================================

Use for stable descriptive facts about the organization's engineering
environment.

Do not store repo-specific facts here.
Do not store live state.

When evidence is narrow, downgrade to PROJECT_PROFILE or REPO_PROFILE.

==================================================
COMPANY_POLICY
==================================================

Policy means REQUIRED, FORBIDDEN, MUST, MUST NOT, or approval semantics.

Observed behavior is not policy.

Seeing an engineer use GitOps once does not prove GitOps is mandatory.

Policy changes are proposal-only unless authoritative material explicitly
confirms the rule.

==================================================
STANDARD_OR_ADR
==================================================

Use for architecture, coding, platform, or engineering standards.

A design decision made for one task is not automatically an organizational
standard.

Prefer proposal-only unless corroborated by existing standard/ADR material.

==================================================
PLAYBOOK_SKILL
==================================================

Use for reusable HOW-TO procedures.

A good Skill answers:

"When I need to perform this class of engineering work again, what sequence
of decisions and actions should I follow?"

A Skill should contain:

- goal;
- when it applies;
- prerequisites;
- procedure;
- decision points;
- required tools/capabilities;
- validation;
- completion criteria;
- cleanup/reconciliation requirements;
- durable failure/recovery notes.

A Skill should not contain:

- one build number;
- one temporary image SHA;
- one pod name;
- one temporary branch;
- one accidental failure;
- repository-specific parameters unless the Skill is explicitly
  repository-local.

When process is generic but parameters vary by repository:

put the process in PLAYBOOK_SKILL;
put the parameters in REPO_PROFILE.

==================================================
PROJECT_PROFILE
==================================================

Use for durable context shared across repositories belonging to one
product/system/project.

Do not put repository-local build commands here.

==================================================
REPO_PROFILE
==================================================

Use for durable facts specific to one repository.

Examples:
- build command;
- verification commands;
- CI pipeline mapping;
- image repository;
- runtime workload mapping;
- GitOps repo/path;
- smoke-test command;
- related repositories.

Prefer learned overlay updates rather than modifying curated fields.

When possible, corroborate facts using repository files, SCM metadata, CI
configuration, GitOps configuration, or another authoritative source.

==================================================
ENVIRONMENT_PROFILE
==================================================

Use for stable properties of test/staging/production.

Examples:
- cluster identity;
- allowed mutation style;
- observability entry point;
- deployment mechanism;
- capability restrictions.

Do not store current image, pod, build, or transient health state.

==================================================
RUN_EVIDENCE
==================================================

Use for concrete historical execution evidence.

Examples:
- commit SHA;
- pipeline run;
- generated image;
- test result;
- rollout outcome;
- GitOps commit;
- observed failure.

Run evidence may be specific.

Do not automatically expose all run evidence to normal agents.

==================================================
LIVE_STATE
==================================================

Use for information true "right now" and expected to change.

Do not persist as durable knowledge.

Normal agents must query authoritative tools for live state.

==================================================
SECRET_OR_SENSITIVE
==================================================

Never persist:
- passwords;
- API tokens;
- session cookies;
- private keys;
- bearer tokens;
- credentials;
- secret values;
- sensitive personal information.

Redact from run evidence when necessary.

==================================================
ONE_OFF
==================================================

Use when an observation happened once and has no demonstrated reusable
value.

Keep it in run history only if useful.

Do not create a general rule without supporting evidence.

==================================================
UNCERTAIN
==================================================

Use when durability, authority, or generalizability cannot be established.

Do not invent certainty.

Create a proposal, retain a low-confidence observation, or discard it.

==================================================
EVIDENCE
==================================================

Prefer evidence approximately in this order:

1. explicit human statement describing a durable company rule;
2. canonical company policy/standard;
3. repository configuration committed to Git;
4. SCM/CI configuration;
5. GitOps configuration;
6. stable platform configuration;
7. repeated historical evidence;
8. single successful execution;
9. agent inference.

Higher-level claims require stronger evidence.

A single execution is strong evidence for RUN_EVIDENCE but weak evidence
for POLICY or STANDARD.

==================================================
DECONTEXTUALIZATION
==================================================

When converting execution into reusable procedure:

REMOVE:
- ticket-specific names;
- one-off branch names;
- commit SHAs;
- image SHAs;
- pipeline run IDs;
- pod names;
- temporary debugging steps;
- transient errors.

PRESERVE:
- required sequence;
- decision points;
- source-of-truth semantics;
- safety constraints;
- verification expectations;
- cleanup requirements;
- generic tool categories;
- structurally reusable failure conditions.

Replace concrete parameters with semantic variables.

==================================================
DEDUPLICATION
==================================================

Before creating knowledge:

1. search existing knowledge;
2. identify the closest related artifact;
3. update existing knowledge when appropriate;
4. create a new artifact only for a genuinely distinct concept.

Do not create test-k8s-change-v2/new/backend merely because the current
session differs slightly.

==================================================
CONTRADICTIONS
==================================================

If new evidence contradicts curated knowledge:

do not overwrite it.

Create a contradiction proposal containing:
- existing statement;
- observed evidence;
- likely explanation;
- recommended human review.

If new evidence contradicts learned knowledge, newer strongly corroborated
evidence may update the learned layer while preserving audit history.

==================================================
HUMAN CORRECTIONS
==================================================

Human corrections are high-value learning signals.

If the user says:

"No, we don't normally deploy that way. Test uses X."

determine whether the corrected knowledge belongs to:
- repo profile;
- project profile;
- environment profile;
- Skill;
- company policy proposal.

Do not promote every conversational preference into company policy.

==================================================
AGENT MISTAKES
==================================================

If the agent attempted the wrong workflow and the human corrected it:

do not teach the wrong workflow.

Capture the corrected procedure and, when useful, a durable warning about
the incorrect assumption.

==================================================
FRICTION / KNOWLEDGE GAPS
==================================================

Look for places where:
- the agent did not know something;
- the human supplied it;
- the work then succeeded.

Also look for repeated questions such as:
- which namespace?
- which pipeline?
- where is the GitOps repo?
- how is this service verified?

Durable answers are strong profile candidates.

==================================================
SOURCE ATTRIBUTION
==================================================

Every learned item must retain internal provenance:

- learning job;
- source session;
- source repository;
- timestamp;
- confidence;
- evidence type.

User-facing Skill text need not contain verbose provenance.

==================================================
CONFIDENCE
==================================================

Assign HIGH, MEDIUM, or LOW.

HIGH:
- committed authoritative configuration;
- explicit durable human correction;
- multiple consistent authoritative sources.

MEDIUM:
- one successful workflow corroborated by configuration.

LOW:
- inference from conversation;
- one unexplained behavior.

Low-confidence knowledge must not alter high-impact canonical context.

==================================================
EXECUTION PHASES
==================================================

Perform these phases in order.

PHASE 1 — RECONSTRUCT

Understand:
- objective;
- repositories;
- environments;
- tools/systems;
- what happened;
- what changed;
- failures;
- resolution;
- human corrections/emphasis.

PHASE 2 — EXTRACT CANDIDATES

Identify candidate reusable facts and procedures.
Do not write yet.

PHASE 3 — CLASSIFY

Assign every candidate a knowledge class.
Reject transient, secret, useless, or unsupported candidates.

PHASE 4 — GENERALIZE

For Skills, separate generic process from concrete parameters.
For profiles, retain only durable scope-specific facts.

PHASE 5 — CORROBORATE

When practical, inspect:
- repository files;
- existing profiles;
- CI metadata;
- GitOps config;
- existing Skills;
- company context.

Perform no operational mutation.

PHASE 6 — SEARCH EXISTING KNOWLEDGE

Determine whether each candidate:
- confirms;
- extends;
- contradicts;
- or introduces new knowledge.

PHASE 7 — PLAN PATCHES

Create a structured plan before any writes.

Each change must contain:
- classification;
- target;
- reason;
- evidence;
- confidence;
- change type.

PHASE 8 — APPLY ALLOWED CHANGES

Respect mutation policy.
Never directly modify protected categories.

PHASE 9 — VALIDATE

Run:
- schema validation;
- Skill validation;
- duplicate detection;
- secret scanning;
- broken-reference checks;
- scope sanity checks.

PHASE 10 — REPORT

Return a concise structured report:
- learned;
- updated;
- proposed;
- ignored;
- conflicts;
- validation status.

Do not dump internal chain-of-thought.

==================================================
FINAL RULE
==================================================

The goal is not to remember everything.

The goal is to preserve only the knowledge that will make a future
engineering agent more correct, more autonomous, and more aligned with the
company's real engineering process.
```

------------------------------------------------------------------------

## 20. Dynamic Learning Job Prompt

The learner's static rules live in its agent instructions.

Per-job dynamic data should be a small first message in the fork:

``` text
LEARNING JOB

Job ID:
learn-...

Source session:
ses_...

Snapshot boundary:
msg_...

Repository:
backend-api

Project:
Booking

Working directory:
...

Knowledge root:
...

Mode:
dry-run

Do not continue operational work.
Run the context-learning workflow now.
```

This avoids depending on V2-style runtime system injection.

------------------------------------------------------------------------

## 21. Structured Context Tools

Do not give the learner unrestricted write access to the knowledge
repository.

Expose structured operations such as:

``` text
context_read
context_search
context_list

context_propose
context_update_learned
context_update_skill
context_record_run

context_validate
context_finish
```

These tools should enforce:

-   allowed paths;
-   schemas;
-   classification;
-   provenance;
-   secret scanning;
-   conflict policy;
-   curated-vs-learned precedence;
-   versioning/audit.

In `--dry-run`, write operations produce a proposed patch/report only.

------------------------------------------------------------------------

## 22. Learning Plan

Before persistence, the learner should produce a structured plan.

Example:

``` json
{
  "items": [
    {
      "class": "PLAYBOOK_SKILL",
      "target": "skills/test-k8s-change",
      "operation": "update",
      "confidence": "HIGH",
      "reason": "Reusable test deployment workflow",
      "evidence": ["source-session"]
    },
    {
      "class": "REPO_PROFILE",
      "target": "repos/backend-api/learned.yaml",
      "operation": "merge",
      "confidence": "HIGH"
    }
  ]
}
```

Context Core decides:

``` text
ALLOW
PROPOSAL
DENY
```

The model does not decide its own write authority.

------------------------------------------------------------------------

## 23. Mutation Policy

Recommended eventual policy:

``` text
RUN_EVIDENCE
→ AUTO

REPO_PROFILE learned overlay
→ AUTO when medium/high confidence

PROJECT_PROFILE learned overlay
→ AUTO when high confidence
→ proposal when medium

ENVIRONMENT_PROFILE learned overlay
→ AUTO only when corroborated
→ otherwise proposal

PLAYBOOK_SKILL
→ AUTO only after dry-run quality is proven,
  Skill is marked learnable,
  and validation passes

COMPANY_CONTEXT
→ PROPOSAL

COMPANY_POLICY
→ PROPOSAL

STANDARD_OR_ADR
→ PROPOSAL

GLOBAL_CONSTITUTION
→ PROPOSAL

LIVE_STATE
→ NEVER durable

SECRET_OR_SENSITIVE
→ NEVER

ONE_OFF
→ run evidence only
```

For the first implementation, use **dry-run only**.

------------------------------------------------------------------------

## 24. Validation Pipeline

Before a learning job is considered complete:

``` text
schema validation
      ↓
Skill/frontmatter validation
      ↓
secret scanning
      ↓
duplicate detection
      ↓
reference checks
      ↓
scope sanity checks
```

A failed validation must not mutate canonical knowledge.

At minimum, secret scanning should detect common:

-   tokens;
-   Authorization headers;
-   private keys;
-   passwords;
-   connection strings;
-   kubeconfig secrets;
-   cookies;
-   credential-like assignments.

------------------------------------------------------------------------

## 25. Deduplication and Idempotency

Running `/learn` twice at the same source boundary must not create
duplicate durable knowledge.

A Learning Job identity may include:

``` text
source_session_id
boundary_message_id
learner_version
```

The learner must search existing knowledge before creating a new Skill
or profile fact.

------------------------------------------------------------------------

## 26. Git and Audit

The context repository should be version controlled.

Eventually, successful safe learning may create commits such as:

``` text
learn(backend-api): distill test deployment workflow
```

Retain provenance including:

``` text
learning-job-id
source-session-id
```

Do not require a branch/PR for every learning run in V1.

Protected proposals can live under:

``` text
proposals/open/
```

A heavier Knowledge PR workflow can be added later.

------------------------------------------------------------------------

## 27. Run Evidence

A learning run may create:

``` yaml
source:
  repo: backend-api
  session: ses_xxx

task:
  summary: validate auth change in test cluster

observed:
  commit: ...
  ci:
    provider: azure-devops
    run: ...
    status: succeeded
  deployment:
    environment: test
    status: succeeded
  verification:
    status: passed
  gitops:
    reconciled: true

derived:
  - skill:test-k8s-change
  - repo:backend-api
```

Normal agents should **not** preload historical runs.

Runs exist for:

-   audit;
-   troubleshooting;
-   precedent search;
-   future learning.

------------------------------------------------------------------------

## 28. Normal-Agent Context Strategy

Do not inject the whole company context into every prompt.

Use progressive disclosure.

### Global `AGENTS.md`

Keep it short.

Example intent:

``` text
Company engineering context is available through company-context tools
and Skills.

For organization-specific CI/CD, Kubernetes, GitOps, deployment,
environment, or operational work, resolve relevant company context rather
than guessing.

Prefer company playbooks when available.

Query live systems for live state.

Do not treat one historical execution as company policy.
```

### Repo-local `AGENTS.md`

Use for things primarily needed while coding in that repository:

-   architecture notes;
-   coding conventions;
-   build/test commands;
-   generated-code warnings;
-   local coding rules.

### Repo Profile

Use for operational/control-plane identity:

-   CI mapping;
-   image;
-   runtime workload;
-   GitOps path;
-   environment mappings;
-   service/project identity.

Example distinction:

``` text
"Do not edit generated OpenAPI clients."
→ repo AGENTS.md

"test workload = Deployment/foo"
→ Repo Profile
```

------------------------------------------------------------------------

## 29. Context Resolver

Normal agents should use a structured resolver rather than manually
traversing the context repository.

Example:

``` text
context_resolve(
  intent="test-deployment",
  repo="backend-api",
  environment="test"
)
```

Conceptual response:

``` text
Policies:
- relevant constraints

Applicable Skills:
- test-k8s-change
- gitops-reconcile

Repo:
- CI mapping
- image mapping
- workload mapping
- GitOps mapping

Environment:
- stable environment semantics

Live state:
NOT INCLUDED — query runtime tools
```

Use `context_search(query)` when intent/scope is not yet known.

Prefer structured resolution when repo/environment/intent are already
known.

------------------------------------------------------------------------

## 30. Skills

The canonical Skill should live in `company-agent-context`.

Avoid maintaining a separate copied Skill tree under OpenCode
configuration.

The V1 adapter should expose or link canonical Skills using whatever
V1-supported discovery mechanism is practical.

Skills are a **consumer format and learning target**, not the entire
context framework.

------------------------------------------------------------------------

## 31. What Should Be Built Manually

These foundation artifacts should be discussed one by one with a strong
model and reviewed by a human:

``` text
global/constitution.md

company/engineering-map.md
company/ci-cd.md
company/kubernetes.md
company/gitops.md

company/policies/*
company/standards/*
company/adrs/*

environments/test/profile.yaml
environments/prod/profile.yaml

schema/repo-profile.schema.json
schema/project-profile.schema.json
schema/environment-profile.schema.json
```

Do not expect `/learn` to invent the company's constitution or policy
model safely.

------------------------------------------------------------------------

## 32. What `/learn` Should Grow

Real usage should primarily grow:

``` text
skills/*

repos/*/learned.yaml

projects/*/learned.yaml

environments/*/learned.yaml

runs/*

proposals/*
```

The learner should especially notice:

-   human corrections;
-   facts the agent had to ask for;
-   repeated operational questions;
-   failed assumptions;
-   successful reusable workflows;
-   verification procedures;
-   source-of-truth relationships.

------------------------------------------------------------------------

## 33. What Programs Should Discover

Do not repeatedly teach the learner facts that APIs can reliably
provide.

The Control Plane should eventually discover/query:

``` text
repository catalog
current branches
pipeline definitions/status
current Kubernetes state
current GitOps state
agent registry
machine registry
```

This prevents stale knowledge from accumulating in Markdown/YAML.

------------------------------------------------------------------------

## 34. `!learn` Integration

After `/learn` works, the existing Chat Bridge can call the same
Learning Service.

Flow:

``` text
Chat Thread
    │
  !learn
    │
    ▼
resolve Thread Agent
    │
resolve OpenCode session
    │
    ▼
Learning Service
```

Possible status card:

``` text
🧠 Learning

Source:
A repo

Status:
Analyzing session...

[🆗]
```

Completion:

``` text
🧠 Learning completed

Learned:
• updated test-k8s-change
• learned backend CI mapping
• stored run evidence

Needs review:
• possible company-wide policy

[👀] [🆗]
```

The full learner conversation should not be injected back into the
Thread Agent.

------------------------------------------------------------------------

## 35. Failure Isolation

Learning failure must never affect the parent working session.

Examples:

-   fork failure;
-   Terra rate limit;
-   validation failure;
-   context Git conflict;
-   malformed output.

The Learning Job may become retryable/failed.

The parent remains usable.

------------------------------------------------------------------------

## 36. Token and Model Strategy

Default learner:

``` text
Terra
```

The job is primarily:

-   long-context understanding;
-   classification;
-   generalization;
-   knowledge organization.

Do not use Sol as the routine learner.

Use Sol for occasional architecture/review checkpoints.

Because Terra shares a constrained token budget, treat learning as
background work:

``` text
interactive work > learning work
```

Recommended heavy learner concurrency:

``` text
1
```

Queue learning when interactive Terra work is consuming the rate limit.

Do not implement multi-model extraction/synthesis in V1.

------------------------------------------------------------------------

## 37. Automatic Learning

V1:

``` text
NO automatic learning.
```

Only run on explicit:

``` text
/learn
!learn
```

Reasons:

-   token cost;
-   noise;
-   easier debugging;
-   explicit human signal that the session contains useful knowledge.

Later, the system may suggest:

``` text
This session may contain reusable knowledge.

[🧠 Learn]
```

but should still require explicit action initially.

------------------------------------------------------------------------

## 38. Implementation Milestones

### M0 --- Context repository skeleton

Create only enough foundation to give learning a place to land:

``` text
global/constitution.md
company/engineering-map.md
skills/test-k8s-change/SKILL.md
schema/repo-profile.schema.json
repos/README.md
```

Do not fill the entire company ontology.

### M1 --- Manual dry-run primitive

Implement:

``` text
context-learn --session <id> --dry-run
```

Requirements:

-   fork OpenCode V1 session;
-   select `context-learner`;
-   select Terra;
-   produce structured LearningReport;
-   no canonical mutation;
-   parent unaffected.

### M2 --- `/learn --dry-run`

Wrap the same Learning Service with an OpenCode V1 command/tool.

### M3 --- Safe persistence

Add:

``` text
run evidence → write
repo learned → write
Skill → proposal/diff initially
```

Keep policy/constitution/standards proposal-only.

### M4 --- `!learn`

Wire Chat Bridge to the same service.

### M5 --- Normal-agent context resolution

Add:

``` text
context_resolve
context_search
company-context Skill
```

### M6 --- Review UI / broader Control Plane integration

Add:

-   learning history;
-   diffs;
-   proposal review;
-   contradiction review;
-   Knowledge page.

### M7 --- Additional harness adapters

Only after the model is proven:

``` text
OpenCode V2
Claude Code
Codex
```

------------------------------------------------------------------------

## 39. V1 Acceptance Tests

### Test 1 --- Fork isolation

Given an existing engineering session:

``` text
context-learn --session <id> --dry-run
```

creates a learner fork and leaves the parent unchanged.

### Test 2 --- Correct knowledge split

A real test-Kubernetes workflow produces separate candidates for:

``` text
PLAYBOOK_SKILL
REPO_PROFILE
RUN_EVIDENCE
possible POLICY
ignored LIVE_STATE
```

### Test 3 --- No transient leakage

Pipeline run ID, image SHA, and pod names must not enter durable
Skill/Profile content.

### Test 4 --- Policy protection

Human statement:

``` text
production cannot be changed directly with kubectl
```

must produce a policy proposal, not silently mutate canonical policy.

### Test 5 --- Deduplication

If `test-k8s-change` already exists, update/propose an update to it
rather than creating `test-k8s-change-new`.

### Test 6 --- Secret safety

A temporary token in the source session must not appear in durable
output.

### Test 7 --- Rate-limit isolation

A Terra 429/failure must not break the parent session.

### Test 8 --- Idempotency

Running the same learning boundary twice must not duplicate durable
knowledge.

### Test 9 --- Curated conflict

If curated profile says namespace A but the session suggests namespace
B:

``` text
do not overwrite A
→ create contradiction proposal
```

### Test 10 --- Human correction

If the agent makes a wrong operational assumption and the human corrects
it, the learner should prefer the corrected knowledge and classify it at
the narrowest justified scope.

------------------------------------------------------------------------

## 40. First Terra Implementation Prompt

Give the implementation agent this document as the north-star
architecture, then use this task:

``` text
Read the Context Learning architecture as a north-star design.

Do not implement the full architecture.

This iteration is only an OpenCode V1 `/learn` dry-run proof of concept.

Before coding:

1. Inspect the existing repository architecture.
2. Inspect the actual installed OpenCode V1 interfaces/APIs available in
   this environment.
3. Verify that session fork and continuation can be performed with the
   installed V1 version. Do not assume V2 APIs.
4. Identify the smallest integration surface.
5. Propose the exact files/modules you intend to modify.
6. Identify any assumption in this architecture that the installed V1
   version cannot support.
7. Prefer a thin OpenCode adapter over embedding learning semantics into
   the plugin.

Then implement the smallest vertical slice.

Required behavior:

- Accept an existing OpenCode V1 session.
- Fork/snapshot it so learning cannot affect the parent.
- Continue the fork using a dedicated context-learner agent.
- Use Terra for the learner.
- Give the learner the version-controlled classification prompt.
- Allow read-only access to an external company-agent-context repository.
- Produce a structured LearningReport.
- Do not mutate canonical company context yet.
- Do not implement Chat/Bridge !learn integration.
- Do not implement Web UI.
- Do not implement automatic learning.
- Do not implement Claude Code/Codex/OpenCode V2 adapters.
- Do not build a generic distributed framework.
- Do not introduce abstractions that are unnecessary for this vertical
  slice.

Acceptance test:

A real engineering session containing a test-Kubernetes deployment
workflow can be learned in dry-run mode and the report separates:

- reusable playbook knowledge;
- repo-specific durable facts;
- run-only evidence;
- possible policy/standard proposals;
- transient/live information that must not persist.

The original OpenCode session remains usable and unchanged.

If the installed OpenCode V1 API differs from assumptions in this
document, adapt the OpenCode V1 adapter only. Do not redesign Context Core
around a harness-specific limitation.
```

------------------------------------------------------------------------

## 41. Recommended Model Responsibilities

Use:

``` text
Terra
→ primary implementation
→ routine /learn learner

Sol
→ architecture checkpoint
→ review boundary/trust/mutation design
→ review before enabling auto-apply

Sonnet
→ second-opinion implementation review

Haiku/Qwen
→ cheap exploration/search
```

Do not spend Sol budget on routine TypeScript implementation,
migrations, or small fixes.

High-value Sol checkpoints are:

1.  review the V1 vertical-slice plan;
2.  review the first working implementation for architecture leakage;
3.  review classification failures after several real `/learn` runs;
4.  review trust/mutation policy before enabling automatic writes.

------------------------------------------------------------------------

## 42. Explicit Non-Goals for V1

Do not build yet:

``` text
automatic learning after every task
full company ontology
knowledge graph database
Neo4j
complex Web UI
multi-agent learner swarm
multi-model extraction pipeline
automatic policy mutation
automatic AGENTS.md mutation
production workflow enforcement
OpenCode V2 adapter
Claude Code adapter
Codex adapter
```

The primary hypothesis to validate is much smaller:

> **Can a forked Terra learner reliably transform a real engineering
> conversation into correctly scoped, reusable company knowledge without
> contaminating the original session?**

Everything else follows from whether that works.

------------------------------------------------------------------------

## 43. Final Ownership Model

``` text
Human + strong-model review
        │
        ▼
Constitution
Company Map
Policies
Standards
Schemas
Curated profiles

Real engineering work
        │
        ▼
      /learn
        │
        ├── Skills
        ├── repo learned context
        ├── project learned context
        ├── environment learned context
        ├── run evidence
        └── protected proposals

Control Plane / APIs
        │
        ▼
Discovered + live operational state
```

------------------------------------------------------------------------

## 44. Design Summary

The system should be understood in one sentence:

> **`/learn` forks a completed or partially completed OpenCode V1
> working session, runs an isolated Terra Context Learner over that
> history, classifies durable knowledge by scope/authority/lifecycle,
> and produces safe structured updates or proposals for a
> harness-independent company context repository without altering the
> original working session.**

The first implementation should prove only:

``` text
OpenCode V1 session
      ↓
fork
      ↓
Terra context-learner
      ↓
structured dry-run LearningReport
      ↓
parent remains untouched
```

Once that is reliable, persistence, `!learn`, context resolution, and
broader Control Plane integration can be layered on incrementally.
