(ns papercoord.governor
  "PaperCoordGovernor — the independent safety/scope layer gating
  every plant scheduling/logistics proposal an advisor may make for a
  paper products machine operator crew. The governor never dispatches
  hardware itself, never operates converting equipment itself, and
  never finalizes a machine-operation-execution decision (e.g.
  deciding to proceed with a specific converting/cutting/folding run)
  or a plant-safety-clearance decision (e.g. declaring the plant
  safety cleared), and never overrides a plant safety officer's
  judgment — those are permanently out of this actor's scope and
  remain a plant safety officer's exclusive judgment (README's
  'Robotics premise': this actor coordinates PLANT SCHEDULING/
  LOGISTICS ONLY — it never operates converting equipment itself).
  Modeled closely on cloud-itonami-isco-8122's platingcoord.governor.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. converter provenance    — the crew member must be independently
                                verified/registered before any action.
    2. facility provenance   — the paper-converting facility/line must
                                be independently verified/registered
                                before any action.
    3. no-actuation           — proposal :effect must be :propose (the
                                governor never dispatches hardware and
                                never operates converting equipment
                                itself; it only gates what the advisor
                                may coordinate).
    4. closed op-allowlist    — only :log-work-record,
                                :schedule-crew-operation,
                                :flag-safety-concern and
                                :coordinate-supply-order may ever be
                                proposed; anything else is refused.
    5. scope-excluded action  — any proposal to directly finalize a
                                machine-operation-execution decision
                                (e.g. deciding to proceed with a
                                specific converting/cutting/folding
                                run), or a plant-safety-clearance
                                decision (e.g. declaring the plant
                                safety cleared), or to override a
                                plant safety officer's judgment, is a
                                hard, permanent block (checked both
                                against the proposed :op and,
                                defense-in-depth, against the
                                proposal's :rationale text — matched as
                                full finalization/execution ACTION
                                phrases such as \"finalize the
                                converting operation\" / \"declare the
                                plant safety cleared\" / \"override the
                                plant safety officer's judgment\",
                                never as bare nouns like \"paper\",
                                \"blade\", \"roller\" or
                                \"converting\", so the check can never
                                self-trip on the advisor's own routine
                                rationale text, e.g. \"logged work
                                record for converter …\" or \"scheduled
                                crew operation for converting task …\"
                                or \"…routed for plant safety officer
                                review\" — all three legitimately
                                contain bare domain nouns but none is a
                                finalization action, and all are
                                exercised by
                                `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off
  regardless of confidence):
    6. :op :flag-safety-concern (a roller-entanglement / blade-injury /
                                crush-hazard / equipment-condition
                                concern always escalates to a human,
                                never auto-commits).
    7. :op :coordinate-supply-order above `supply-cost-threshold`.
    8. low confidence (< `confidence-floor`).

  This actor coordinates plant scheduling/logistics ONLY — it never
  operates converting equipment itself, and it never makes a
  plant-safety-clearance decision itself; those decisions always route
  to a human plant safety officer, either via a hard permanent block
  on the op-allowlist (rules 4/5 above) or via a mandatory escalation
  (rule 6 above)."
  (:require [clojure.string :as str]
            [papercoord.store :as store]))

(def confidence-floor 0.6)
(def supply-cost-threshold 2000)

(def allowed-ops
  #{:log-work-record :schedule-crew-operation
    :flag-safety-concern :coordinate-supply-order})

;; Defense-in-depth: none of these ops are ever in `allowed-ops`
;; above, so they are already refused by the closed-allowlist check
;; below; they are named again here — as explicit finalization/
;; execution ACTIONS, never bare nouns — so a future allowlist edit
;; cannot silently re-open this specific out-of-scope path without
;; also touching this list.
(def ^:private scope-excluded-ops
  #{:finalize-converting-operation :finalize-machine-operation-decision
    :authorize-converting-run
    :proceed-with-converting-run
    :finalize-plant-safety-clearance
    :declare-plant-safety-cleared
    :declare-plant-safety-clearance
    :override-plant-safety-officer-judgment
    :override-safety-officer-judgment})

;; Full finalization/execution ACTION phrases only — never bare nouns
;; ("paper", "blade", "roller", "converting", "cutting", "folding",
;; "plant", "safety", "officer") — so this can never match inside the
;; mock advisor's own default rationale text (which legitimately
;; contains those bare nouns, e.g. "converting task" / "plant safety
;; officer review"). See
;; `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`.
(def ^:private scope-excluded-phrases
  ["proceed with the converting run" "proceed with the cutting run"
   "proceed with the folding run"
   "authorize the converting run" "authorize the cutting run"
   "finalize the converting decision" "finalize the converting operation"
   "finalize the machine operation decision"
   "declare the plant safety cleared" "declare plant safety cleared"
   "finalize the plant safety clearance" "finalize the plant-safety clearance"
   "declare the plant safety clearance"
   "override the plant safety officer's judgment"
   "override the safety officer's judgment"
   "override plant safety officer judgment"])

(defn- contains-excluded-phrase? [s]
  (let [s (str/lower-case (or s ""))]
    (boolean (some #(str/includes? s %) scope-excluded-phrases))))

(defn- hard-violations [proposal converter-record facility-record]
  (let [{:keys [op rationale]} proposal]
    (cond-> []
      (nil? converter-record)
      (conj {:rule :no-converter
             :detail "未登録 converter への提案は不可（converter record は独立して検証・登録済みでなければならない）"})

      (nil? facility-record)
      (conj {:rule :no-facility
             :detail "未登録 facility への提案は不可（facility record は独立して検証・登録済みでなければならない）"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation
             :detail "effect は :propose のみ許可（governor は紙製品加工・変換作業を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op
             :detail (str op " は closed op-allowlist に無い — 提案不可")})

      (or (contains? scope-excluded-ops op) (contains-excluded-phrase? rationale))
      (conj {:rule :scope-excluded-action
             :detail "機械操作実行判断・plant safety clearance 判断の確定、および plant safety officer の判断の上書きは、この actor の権限外 — 常に永続ブロック"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `papercoord.store/Store`. Pure — never mutates
  the store, never dispatches a converting operation."
  [request _context proposal store]
  (let [converter-record (store/converter store (:converter-id request))
        facility-record (some->> (:facility-id proposal) (store/facility store))
        hard (hard-violations proposal converter-record facility-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        supply-order-over-threshold?
        (and (= :coordinate-supply-order (:op proposal))
             (number? (:cost proposal))
             (> (:cost proposal) supply-cost-threshold))
        always-risky? (or (= :flag-safety-concern (:op proposal))
                           supply-order-over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
