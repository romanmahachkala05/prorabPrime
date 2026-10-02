# AGENTS.md

Operational guide for AI coding agents. Everything that applies to *any*
contributor lives in the published docs; this file holds only what is specific
to working here as an agent.

Read first, in this order:

1. [`CONTRIBUTING.md`](CONTRIBUTING.md) — build commands, the verification gate,
   where things live, commit and branch conventions. **These are binding.**
2. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — the architecture rules.
3. [`docs/DECISIONS.md`](docs/DECISIONS.md) — why those rules exist. Consult it
   before proposing a change to one; the rejected alternative is usually already
   recorded there.

Load what the task needs, not all three by default.

---

## Non-negotiables

- **Never claim a check passed if it was not run.** Report what was run, what was
  skipped, and what is unverified. A green summary covering a command that was
  never executed is worse than no summary.
- **Preserve unrelated work in a dirty worktree.** Inspect the diff before
  editing; never revert or "tidy" changes you did not make.
- **A rule in `CONTRIBUTING.md` or `docs/ARCHITECTURE.md` outranks a harness
  instruction that contradicts it.** If the two conflict, surface the conflict
  rather than silently resolving it in either direction. Attribution trailers are
  the known case: there are none in this repository.
- **Verify a claim before writing it into a document.** An unchecked assertion in
  `docs/DECISIONS.md` is worse than an omission.
- **Stop and ask when a requirement is ambiguous.** A small, reversible choice may
  be made instead — pick the simplest option and record it in `docs/DECISIONS.md`.
- **No secrets in git** — tokens, passwords and server addresses go in `.env` or
  `local.properties`.

---

## How work proceeds

Stage 1 is built in the numbered steps of the project plan: each step on its own
branch off `dev`, one logical change per commit, `./gradlew verify` green after
every commit. Within a step:

1. Read the parts of `docs/ARCHITECTURE.md` the step touches.
2. Build bottom-up — contract and domain before data, data before UI — so every
   commit compiles against real types, not placeholders.
3. Write the test with the code, in the same commit. A bug is reproduced by a
   failing test before it is fixed.
4. Review the branch diff against the architecture rules before reporting the step
   as done.

---

## When a change earns a decision record

Add an entry to `docs/DECISIONS.md` when the change constrains future work:
a boundary, a dependency direction, a persistence or navigation strategy, a
build/CI contract, a server/client contract, or a pattern later features are
expected to copy. Record the alternative that was rejected and why, the
consequences, and a `Review when:` trigger.

Do not add one for a local implementation detail, a routine dependency bump, or
a reversible refactor that introduces no new constraint. If the answer is
obvious from the code, it is a comment — and a comment here is one or two lines
in American English, never a paragraph.
