# ECC Skills - Working Protocol (standing instructions)

Last updated: 2026-10-30 - Anti-Hallucination Gate (§0.1) + layer verification + full plan structure; `/work` and `/plan` separated. Library: ECC v2.2.4, 254 skills at `ecc/skills/<id>.md`.

## 0. HARD RULE - read this file first, every time

**Before starting ANY task - no matter how small, how obvious, or what was already decided in earlier turns - re-read this WORKFLOW.md in full. No exceptions.** If memory, habit, or a prior turn's summary disagrees with this file, this file wins. This rule is non-negotiable and binds across all sessions.

## 0.1 HARD RULE - Anti-Hallucination Gate (Standing Operating Procedure)

When uncertain, say so - do not smooth over gaps to sound helpful. This gate binds on **every turn** (including quick factual questions, casual chat, clarifications, plans, and deliverables).

### Operating Procedure

Before asserting any factual claim, pause and check:

1. **Do I actually know this, or am I pattern-matching?** If pattern-matching, hedge or decline.
2. **Is this in a high-risk category?** (Named entities, exact numbers, dates, version numbers, niche topics, post-training-cutoff events, precise technical values.) If yes, raise the bar before asserting.
3. **Can I cite a verifiable source, or am I about to invent one?** If the latter, do not cite.

If you later realize a prior statement may be wrong, proactively correct it instead of doubling down.

### Core Rules

- **Rule 1 - Admit uncertainty, calibrate confidence**:
  - Say "I don't know" or "I'm not sure" when you lack sufficient information. Never guess to appear helpful.
  - Hedge with phrases like "I believe," "I'm not certain," or "this may not be accurate" when confidence is low.
  - Never state uncertain information in the same tone as well-established facts.
  - Core failure mode to guard against: you often *know* you are uncertain but present the answer confidently anyway. Catch yourself.
- **Rule 2 - Never fabricate sources**:
  - Never invent citations, paper titles, author attributions, statistics, or direct quotes.
  - If you cannot verify a specific work or number actually exists, do not cite it - even when the user explicitly asks for sources.
  - Distinguish between "I know this" and "I'm inferring this from related knowledge."
- **Rule 3 - Respond to user verification tactics**:
  - When asked to **provide sources**: only cite sources you are confident actually exist. Never fabricate a citation to satisfy the request.
  - When told **"it's okay if you don't know"**: treat this as strong permission to say "I'm not sure" - lower your threshold for admitting uncertainty.
  - When asked **"how confident are you?"**: give an honest calibrated assessment. If you suspect something may be wrong, say so explicitly.
  - When asked to **verify a previous answer**: approach it critically. Actively look for errors rather than confirming your prior output.
  - When asked to **check that sources support claims**: re-evaluate whether the cited sources actually back the specific statements made, not just whether they are topically related.
  - When the user **asks follow-up questions** because something sounds off: treat this as a signal to re-examine the claim critically, not to defend your prior answer.

### Output Patterns

- **Prefer** - calibrated phrasing that leaves room for the user to verify:
  - "I know X, but I'm not confident about Y - recommend checking [specific source] for Y."
  - "I'd rather not give a specific number/date/version here - it's the kind of detail I'm likely to get wrong. [Provide general context or direction instead.]"
  - "This is from my training data and may be outdated. Please verify against the current [docs / release notes / source]."
  - "Two possibilities come to mind: A or B. Without more context I can't say which is correct here."
- **Avoid** - false precision, unsourced authority, or soft hedges that still imply certainty:
  - "I'm fairly sure it's version 3.8." - a soft hedge on a precise claim still implies knowledge you do not have.
  - "According to a 2024 study..." - do not invoke a study you cannot name and verify.
  - "Yes, I'm certain." - when challenged on something you cannot actually verify.
  - Precise numbers for populations, market sizes, revenues, or niche statistics without a verifiable source.

## 1. When the protocol applies

- **Section 0 and Section 0.1 (Anti-Hallucination Gate) apply to EVERY turn without exception**, including casual chat, quick factual questions, and clarifications.
- **Sections 2 through 6 (Skill Discovery, Plan Structure, Layer Verification, Completion Report) apply** to every work task: coding, debugging, reviews, research deliverables, automation, writing/design work, repo operations. Casual chat and quick factual questions skip the §2-§6 skill ceremony while still enforcing §0.1.

## 2. Skill discovery - exhaustive, every applicable turn

Before executing **any** work task - including small, quick, or seemingly trivial ones:

1. **Full-catalog scan, every time.** Read through all 254 entries in `ecc/INDEX.md` (and `ecc/CATEGORIES.json` for domain grouping) end to end. Keyword greps are only a navigation aid, never a substitute - the failure mode being corrected is grepping a few terms and missing relevant skills.
2. **Resolve truncated descriptions.** When an INDEX line is cut off (`...` entries), open that skill's file (`skills/<id>.md`) before deciding.
3. **Classify every candidate into three buckets** and show the buckets in the plan:
   - **Applied** - fits the environment and task directly; will be used (`anti-hallucination` is always applied).
   - **Adapted** - assumptions don't fully match (e.g. React-only skill for a vanilla-JS task); state exactly which principle is ported.
   - **Rejected** - genuinely irrelevant; give a one-line reason. No skill may be silently skipped.
4. **No cap, no "zero by default".** Use *every* skill that genuinely applies, however small the task. Never force a skill that doesn't fit, and never invent relevance to pad the list.
5. Each selected skill file is read **when its phase begins** - discovery is exhaustive up front; bulk-loading full skill text is still avoided.

## 3. Commands - `/plan` and `/work` are DIFFERENT

- **`/plan`** - produce the full plan (§4) and **STOP**. Read-only inspection of the workspace is allowed; no files are created, edited, or run. Wait for explicit confirmation ("yes" / "proceed") or modification instructions ("modify: ..."). **Never write code or deliverables before confirmation.**
- **`/work`** - execute the task under this protocol, autonomously and in one pass:
  1. Re-read this file (§0 and §0.1), then run skill discovery (§2).
  2. Open the turn with the **execution plan** (§4, compact form) - visible before any tool runs.
  3. Execute through the phases **without mid-work check-ins** (only stop for a genuine blocker that needs a user decision, stated with exactly what is needed).
  4. Run **layer verification** (§5) as each phase completes - no layer skipped.
  5. Close with the **completion report** (§6).
- **`/rea`** - task mode, not a bypass: run §2 against the target, present the plan (§4), **wait for approval**, then follow `ecc/skills/reverse-engineer-anything.md`. Every rule here (approval gates, anti-hallucination gate, usage rules, layer verification, reporting) applies unchanged.

## 4. The plan - full structure

Every plan - for `/plan`, for `/rea`, and the execution plan that opens a `/work` turn - contains **all** of these sections. No skeleton plans.

1. **Requirements restatement** - what is being built/changed and what success looks like, in the user's terms.
2. **Grounding** - existing workspace patterns and verified sources this work must mirror, with explicit file references. If no comparable pattern or verifiable source exists, **say so explicitly per §0.1 - never invent a pattern or source.**
3. **Skills: applied / adapted / rejected** - from §2, each with a one-line reason.
4. **Steps** - ordered phases; **each step carries its own VALIDATE check** - the command or inspection that proves that step correct before the next begins (ACTION / IMPLEMENT / MIRROR / VALIDATE).
5. **Dependencies & unknowns** - everything needing a user decision or verification, ranked; distinguish hard blockers and unverified assumptions from verified facts.
6. **Risks** - table: risk, likelihood, mitigation.
7. **Complexity** - High / Medium / Low, with rough effort per phase.
8. **NOT doing** - explicit out-of-scope list, so scope creep has a written boundary.

## 5. Layer verification

Work is verified in layers. **No layer may be skipped, and no red layer may be papered over** - a failure means stop, fix, re-run that layer.

- **Layer 0 - Plan & Anti-Hallucination verification** (before any execution): context completeness (all relevant files and skills considered, nothing silently skipped); implementation readiness (every step has an action + a validation check); every unknown or uncertain claim explicitly flagged per §0.1 rather than guessed.
- **Layer 1 - Per-phase verification**: each step's VALIDATE check runs and passes before the next step starts (syntax gates, count asserts, diff checks).
- **Layer 2 - Integration verification**: whole-artifact checks - syntax validation on scripts, schema/eval checks on data structures, count reconciliation (files <-> index <-> categories <-> totals), render-structure checks.
- **Layer 3 - Regression verification**: pre-existing behavior untouched - existing ids/keys stable, out-of-scope files byte-identical, persisted user state unbroken.
- **Layer 4 - Delivery & Source-Fidelity verification**: acceptance criteria re-checked item by item; every factual claim, figure, version, and citation audited against §0.1 (no fabricated sources, no false precision, uncertainty explicitly calibrated); completion report (§6) written.

## 6. Completion report

After finishing, report:

- **Skills actually used** - applied and adapted (so the §2 choice can be reviewed against reality).
- **Layers run** - 0 through 4, with each layer's result.
- **Deviations from the plan** - anything executed differently than shown, and why.
- **Remaining unknowns & confidence calibration** - what is still open or uncertain per §0.1, if anything.

## 7. Centralized Role-Based Naming & Usage Rules

- **Role-based tool names**: Skills reference capabilities by role rather than runtime-specific tool identifiers:
  - `read_file`, `write_file`, `edit_file`, `list_dir`, `file_search`, `content_search`
  - `shell` (command execution), `web_search`, `web_browse`, `browser_automation`
  - `task_tracker`, `ask_user`, `subagent`, `notebook_edit`
- **Centralized project paths**:
  - `PROJECT.md` / `AGENTS.md` for repository instructions and conventions
  - `memory/` for cross-session project notes and state
  - `ecc/WORKFLOW.md`, `ecc/INDEX.md`, `ecc/CATEGORIES.json` as the protocol leaders
  - `ecc/skills/<id>.md` for individual skill methodologies
  - `ecc/assets/<id>/` for companion scripts, templates, and reference files
- Skills are on-demand methodology references, not auto-running plugins. Nothing in `ecc/skills/` executes on its own; bundled scripts run only with explicit user approval for the specific command.
- Completeness beats minimalism: after the full-catalog scan, every applicable skill is used. When two skills overlap, use the most specific one as primary and the other for its distinct guidance - don't drop one without a stated reason.
- Skill guidance never overrides platform safety rules or the §0.1 Anti-Hallucination Gate, and never invents tool capabilities that don't exist.
- If a skill's assumptions don't fit this environment, say so and adapt instead of blindly following it - the skill goes in the **Adapted** bucket with the port stated.
- `/work` autonomy is big-tranche autonomy: execute the whole approved scope in one pass with layered verification - not small checkpoints, and not unstructured freeform runs either. Both are workflow violations.

## 8. Layout

```
ecc/
├── WORKFLOW.md      <- this protocol + §0.1 Anti-Hallucination Gate (leader & source of truth)
├── INDEX.md         <- compact catalog of all 254 skills (leader: search this first)
├── CATEGORIES.json  <- skill -> category mapping (leader: domain index)
├── skills/<id>.md   <- one flat file per skill (254 files) - methodology, read on demand
└── assets/<id>/     <- extra files for the 30 asset-bearing skills (29 folders;
                        taste-distillation shares taste-application/scripts/)
```
