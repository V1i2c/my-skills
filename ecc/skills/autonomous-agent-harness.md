---
name: autonomous-agent-harness
description: Design a fully autonomous agent system with persistent memory, scheduled operations, computer use, and task queuing — an alternative to standalone agent frameworks (Hermes, AutoGPT). Use when the user wants continuous autonomous operation, scheduled tasks, or a self-directing agent loop.
metadata:
  origin: ECC
---

# Autonomous Agent Harness

Combine the agent runtime's session tools with separately configured scheduling, memory, and computer-use integrations. This is a setup pattern, not a bundled always-on runtime.

## Consent and Safety Boundaries

Autonomous operation must be explicitly requested and scoped by the user. Do not create schedules, dispatch remote agents, write persistent memory, use computer control, post externally, modify third-party resources, or act on private communications unless the user has approved that capability and the target workspace for the current setup.

Prefer dry-run plans and local queue files before enabling recurring or event-driven actions. Keep credentials, private workspace exports, personal datasets, and account-specific automations out of reusable ECC artifacts.

## When to Activate

- User wants an agent that runs continuously or on a schedule
- Setting up automated workflows that trigger periodically
- Building a personal AI assistant that remembers context across sessions
- User says "run this every day", "check on this regularly", "keep monitoring"
- Wants to replicate functionality from Hermes, AutoGPT, or similar autonomous agent frameworks
- Needs computer use combined with scheduled execution

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    Agent Runtime                        │
│                                                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────────┐ │
│  │  Crons   │  │ Dispatch │  │ Memory   │  │ Computer    │ │
│  │ Schedule │  │ Remote   │  │ Store    │  │ Use         │ │
│  │ Tasks    │  │ Agents   │  │          │  │             │ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └──────┬──────┘ │
│       │              │             │                │        │
│       ▼              ▼             ▼                ▼        │
│  ┌──────────────────────────────────────────────────────┐    │
│  │              ECC Skill + Agent Layer                  │    │
│  │                                                      │    │
│  │  skills/     agents/     commands/     hooks/        │    │
│  └──────────────────────────────────────────────────────┘    │
│       │              │             │                │        │
│       ▼              ▼             ▼                ▼        │
│  ┌──────────────────────────────────────────────────────┐    │
│  │              tool integration Server Layer                        │    │
│  │                                                      │    │
│  │  memory    github    exa    supabase    browser-use  │    │
│  └──────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. Persistent Memory

Combine built-in file memory with a structured memory server for entity/relation data.

**Built-in file memory** (per-project `memory/` directories):
- User preferences, feedback, project context
- Stored as markdown files with frontmatter
- Automatically loaded at session start

**Structured memory server** (knowledge graph):
- Entities, relations, observations
- Queryable graph structure
- Cross-session persistence

**Memory patterns:**

```
# Short-term: current session context
In-session todo tracking

# Medium-term: project memory files
Write to per-project memory/ directories for cross-session recall

# Long-term: structured knowledge graph
Create entities for permanent structured data
Create relations for relationship mapping
Add observations for new facts about known entities
```

### 2. Scheduled Operations (Crons)

Use the runtime's native scheduled tasks for recurring prompts within an interactive session. These tasks are session-scoped; an external scheduler is required for work that must run independently of an open session.

**Pattern:**

```
# Recurring, inside a live session
every 30m -> "Review open PRs in this repository and summarize CI failures."

# One-shot, from a shell (any headless agent CLI)
cd "/path/to/repo" && agent-cli -p "Review open PRs and summarize"
```

Use an OS scheduler or CI schedule to invoke that command repeatedly when no interactive session is running. Configure the runner's authentication and tool permissions separately.

**Useful cron patterns:**

| Pattern | Schedule | Use Case |
|---------|----------|----------|
| Daily standup | `0 9 * * 1-5` | Review PRs, issues, deploy status |
| Weekly review | `0 10 * * 1` | Code quality metrics, test coverage |
| Hourly monitor | `0 * * * *` | Production health, error rate checks |
| Nightly build | `0 2 * * *` | Run full test suite, security scan |
| Pre-meeting | `*/30 * * * *` | Prepare context for upcoming meetings |

### 3. Dispatch / Remote Agents

Have an authenticated CI job or webhook receiver invoke the agent CLI in a workspace it owns, in programmatic (headless) mode — never via a public dispatch endpoint.

**Dispatch patterns:**

```bash
# Run inside the CI workspace
cd "/path/to/repo" && agent-cli -p "Build failed on main. Diagnose the failure."

# Trigger from webhook
# GitHub webhook -> authenticated CI runner -> agent-cli -p -> reviewable result

# Trigger from another agent
agent-cli -p "Analyze the output of the security scan and create issues for findings"
```

### 4. Computer Use

Computer control needs a separately configured integration. Anthropic's computer-use tool and reference environment require an application to execute tool calls in an isolated desktop environment. Adding an tool integration package name does not supply that environment.

**Capabilities:**
- Browser automation (navigate, click, fill forms, screenshot)
- Desktop control (open apps, type, mouse control)
- File system operations beyond CLI

**Use cases within the harness:**
- Automated testing of web UIs
- Form filling and data entry
- Screenshot-based monitoring
- Multi-app workflows

### 5. Task Queue

Manage a persistent queue of tasks that survive session boundaries.

**Implementation:**

```
# Task persistence via memory
Write task queue to the project's memory/task-queue.md

# Task format
---
name: task-queue
type: project
description: Persistent task queue for autonomous operation
---

## Active Tasks
- [ ] PR #123: Review and approve if CI green
- [ ] Monitor deploy: check /health every 30 min for 2 hours
- [ ] Research: Find 5 leads in AI tooling space

## Completed
- [x] Daily standup: reviewed 3 PRs, 2 issues
```

## Replacing Hermes

| Hermes Component | ECC Equivalent | How |
|------------------|---------------|-----|
| Gateway/Router | CLI + external scheduler | An authenticated runner starts agent sessions |
| Memory System | File memory + structured memory server | Built-in persistence + knowledge graph |
| Tool Registry | tool integrations | Dynamically loaded tool providers |
| Orchestration | ECC skills + agents | Skill definitions direct agent behavior |
| Computer Use | Separately configured integration | Browser or desktop control in an isolated environment |
| Context Manager | Session management + memory | ECC 2.0 session lifecycle |
| Task Queue | Memory-persisted task list | In-session todos + memory files |

## Safety Notes

- Verify the exact package, publisher, and version before registering any memory-server package. A plain `npx -y` executes whatever is later published under that name — never register guessed or unpublished package names.
- Requesting a schedule from the CLI does not provision an always-on scheduler. Choose the schedule, workspace, and allowed actions explicitly in the external scheduler before enabling it.

## Example Workflows

### Autonomous PR Reviewer
```
Cron: every 30 min during work hours
1. Check for new PRs on watched repos
2. For each new PR:
   - Pull branch locally
   - Run tests
   - Review changes with code-reviewer agent
   - Post review comments via the GitHub integration
3. Update memory with review status
```

### Personal Research Agent
```
Cron: daily at 6 AM
1. Check saved search queries in memory
2. Run web searches for each query
3. Summarize new findings
4. Compare against yesterday's results
5. Write digest to memory
6. Flag high-priority items for morning review
```

### Meeting Prep Agent
```
Trigger: 30 min before each calendar event
1. Read calendar event details
2. Search memory for context on attendees
3. Pull recent email/Slack threads with attendees
4. Prepare talking points and agenda suggestions
5. Write prep doc to memory
```

## Constraints

- Native scheduled prompts share their interactive session. External scheduler invocations start separate sessions unless explicitly resumed.
- Computer use requires explicit permission grants. Don't assume access.
- CLI automation still consumes model usage and is subject to the configured provider's limits. Choose appropriate scheduler intervals.
- Memory files should be kept concise. Archive old data rather than letting files grow unbounded.
- Always verify that scheduled tasks completed successfully. Add error handling to cron prompts.
