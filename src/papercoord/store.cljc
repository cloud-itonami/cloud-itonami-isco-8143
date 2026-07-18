(ns papercoord.store
  "SSoT for the ISCO-08 8143 paper products machine operators plant
  scheduling/logistics coordination actor (itonami actor pattern,
  ADR-2607121000 / CLAUDE.md Actors section; README's 'Robotics
  premise' — a plant scheduling/logistics coordination robot performs
  crew scheduling, production-run/inventory/progress-record logging
  and raw-paper/converting-materials supply-order coordination for a
  paper-converting crew under this advisor/governor pair, which never
  dispatches hardware itself, never operates converting equipment
  itself, and never finalizes a machine-operation-execution decision
  or a plant-safety-clearance decision, and never overrides a plant
  safety officer's judgment — those remain the plant safety officer's
  exclusive judgment). Modeled closely on cloud-itonami-isco-8122's
  platingcoord.store.

  Domain:

    converter — a registered paper products machine operator crew
                member who runs converting/cutting/folding equipment
                (box-making, bag-making, tissue-converting machines)
                (:converter-id, :name)
    facility  — a registered paper-converting facility/line
                {:facility-id :name :max-supply-cost number}.
                `:max-supply-cost` is an informational registered
                ceiling used only to decide whether a
                `:coordinate-supply-order` proposal escalates to human
                sign-off (the governor never blocks a
                within-threshold order outright; it only decides
                commit vs. escalate).
    record    — a committed operating record (a logged production-run/
                inventory/progress entry, a scheduled crew/shift
                operation, a flagged safety concern, or a coordinated
                raw-paper/converting-materials supply order) — written
                ONLY via commit-record!. This actor coordinates plant
                scheduling/logistics ONLY — a `record` is a
                coordination artifact, never a machine-operation-
                execution act, never a plant-safety-clearance
                decision, and never a plant safety officer's-judgment
                override.
    ledger    — append-only audit trail, commit or hold.")

(defprotocol Store
  (converter [s converter-id])
  (facility [s facility-id])
  (records-of [s converter-id])
  (ledger [s])
  (register-converter! [s converter])
  (register-facility! [s facility])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (converter [_ converter-id] (get-in @a [:converters converter-id]))
  (facility [_ facility-id] (get-in @a [:facilities facility-id]))
  (records-of [_ converter-id] (filter #(= converter-id (:converter-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-converter! [s c]
    (swap! a assoc-in [:converters (:converter-id c)] c) s)
  (register-facility! [s f]
    (swap! a assoc-in [:facilities (:facility-id f)] f) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:converters {} :facilities {} :records [] :ledger []}
                                    seed)))))
