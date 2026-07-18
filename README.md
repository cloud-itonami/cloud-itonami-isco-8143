# cloud-itonami-isco-8143

Open Occupation Blueprint for **ISCO-08 8143**: Paper Products Machine
Operators.

This repository designs a forkable OSS business for a paper-converting
plant scheduling and logistics coordination practice: a plant scheduling
and supply-coordination robot manages crew/task records under a
governor-gated actor, so a paper products machine operator crew keeps its
own operating records instead of renting a closed workforce-management
SaaS.

**Maturity: `:implemented`.** `src/papercoord/` implements the
`PaperCoordActor` as a `langgraph.graph/state-graph` (`papercoord.actor`)
wired to a `Paper Products Plant Scheduling Coordination Advisor`
(`papercoord.advisor`) and an independent `PaperCoordGovernor`
(`papercoord.governor`), following the itonami actor pattern
(ADR-2607121000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok? true) +-> :request-approval (:escalate? true, human-in-the-loop
interrupt) +-> :hold (:hard? true)`. HARD invariants (always hold, never
overridable): converter provenance, facility provenance, no-actuation
(`:effect` must be `:propose`), a closed op-allowlist (`:log-work-record`,
`:schedule-crew-operation`, `:flag-safety-concern`,
`:coordinate-supply-order` — nothing else may ever be proposed), and a
permanent, unconditional block on any proposal that would directly finalize
a machine-operation-execution decision (e.g. deciding to proceed with a
specific converting/cutting/folding run) or a plant-safety-clearance
decision (e.g. declaring the plant safety cleared), or that would override
a plant safety officer's judgment. Always-escalate paths (human sign-off
regardless of confidence, mapping this repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)): `:flag-safety-concern`
(always) and `:coordinate-supply-order` above the registered cost
threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a plant scheduling/logistics coordination
robot performs crew scheduling, production-run/inventory/progress-record
logging and raw-paper/converting-materials supply-order coordination for a
paper products machine operator crew, under an actor that proposes actions
and an independent **Paper Products Plant Scheduling Coordination
Governor** that gates them. The governor never dispatches hardware itself,
never operates converting equipment on the plant floor, and never finalizes
a machine-operation-execution decision or a plant-safety-clearance
decision, and never overrides a plant safety officer's judgment;
`:high`/`:safety-critical` actions (such as a flagged roller-entanglement/
blade-injury/crush-hazard/equipment-condition concern, or an
above-threshold supply order) require human sign-off. **This actor
coordinates PLANT SCHEDULING/LOGISTICS ONLY — it never operates converting
equipment itself, and it never makes a plant-safety-clearance decision
itself.**

Paper Products Machine Operators run converting/cutting/folding equipment
(box-making, bag-making, tissue-converting machines) — a real heavy-
machinery hazard domain (crush, entanglement and cutting-blade injury from
rollers and blades). This actor never operates that equipment and never
clears it as safe — it only schedules and logs around it, and always
routes machinery-hazard/safety concerns to a human plant safety officer.

## Core Contract

```text
crew roster + facility registration + safety-reporting policy
        |
        v
Paper Products Plant Scheduling Coordination Advisor -> PaperCoordGovernor -> log/schedule/coordinate, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses,
finalize a machine-operation-execution decision, finalize a
plant-safety-clearance decision, override a plant safety officer's
judgment, suppress an operating record, or disclose sensitive data without
governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `8143`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
