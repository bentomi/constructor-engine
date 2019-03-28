(ns com.ionos.hop.constructor.core
  (:import (java.util UUID)
           (clojure.lang ExceptionInfo)))

(defmulti act (fn [{::keys [process-id phase action-states]} action]
                [process-id action phase]))

(defmulti react (fn [{::keys [process-id action-states]} action message]
                  [process-id action (get action-states action) (::message-id message)]))

(defmulti complete ::process-id)

(defmethod act :default
  [process-instance _action]
  [:completed (::process-state process-instance)])

(defmethod react :default
  [process-instance _action _message]
  [:stop (::process-state process-instance)])

(defmethod complete :default [process-instance]
  (::process-state process-instance))

(defn instantiate
  ([process] (instantiate process {}))
  ([process state]
   (assoc process
          ::process-instance-id (str (UUID/randomUUID))
          ::phase ::forward
          ::process-state state
          ::action-states {})))

(derive ::forward-started ::started)
(derive ::rollback-started ::started)
(derive ::forward-completed ::completed)
(derive ::rollback-completed ::completed)
(derive ::started ::action-states)
(derive ::completed ::action-states)

(defn- forward-candidates
  [{::keys [actions dependencies action-states]}]
  (filter (fn [action]
            (and (nil? (action-states action))
                 (every? #(= ::forward-completed (action-states %))
                         (dependencies action))))
          actions))

(defn- reverse-dependencies
  [dependencies action]
  (filter #(contains? (dependencies %) action) (keys dependencies)))

(defn- rollback-candidates
  [{::keys [actions dependencies action-states]}]
  (filter (fn [action]
            (and (= ::forward-completed (action-states action))
                 (every? #(= ::rollback-completed
                             (action-states % ::rollback-completed))
                         (reverse-dependencies dependencies action))))
          actions))

(def phase->act-state
  {[::forward :started] ::forward-started
   [::rollback :started] ::rollback-started
   [::forward :completed] ::forward-completed
   [::rollback :completed] ::rollback-completed})

(def started->completed-state
  {::forward-started ::forward-completed
   ::rollback-started ::rollback-completed})

(defn- throw-interrupted
  ([process-instance message cause]
   (throw-interrupted process-instance message nil cause))
  ([process-instance message data cause]
   (let [ex-map (merge {:process-instance process-instance} data)]
     (throw (ex-info message ex-map cause)))))

(defn- execute
  [process-instance action]
  (try
    (let [[outcome state'] (act process-instance action)]
      (-> process-instance
          (assoc ::process-state state')
          (assoc-in [::action-states action]
                    (phase->act-state [(::phase process-instance) outcome]))))
    (catch Exception e
      (let [message (str "Exception in executing action " action)]
        (throw-interrupted process-instance message {:action action} e)))))

(defn- step-forward
  [process-instance]
  (reduce execute process-instance (forward-candidates process-instance)))

(defn- step-rollback
  [process-instance]
  (reduce execute process-instance (rollback-candidates process-instance)))

(defn- completable?
  [{::keys [phase actions action-states]}]
  (and (= ::forward phase)
       (every? #(= ::forward-completed (action-states %)) actions)))

(defn- compensatable?
  [{::keys [phase actions action-states]}]
  (and (= ::rollback phase)
       (every? #(= ::rollback-completed (action-states % ::rollback-completed))
               actions)))

(defn- finish
  [process-instance new-phase]
  (let [process-instance' (assoc process-instance ::phase new-phase)]
    (try
      (assoc process-instance' ::process-state (complete process-instance'))
      (catch Exception e
        (let [message (str "Exception in completing process "
                           (::process-id process-instance'))]
          (throw-interrupted process-instance' message e))))))

(defn step
  [process-instance]
  (let [process-instance' (case (::phase process-instance)
                            ::forward (step-forward process-instance)
                            ::rollback (step-rollback process-instance)
                            process-instance)]
    (cond
      (completable? process-instance') (finish process-instance' ::completed)
      (compensatable? process-instance') (finish process-instance' ::compensated)
      :else process-instance')))

(defn- completed
  [process-instance action]
  (update-in process-instance [::action-states action]
             #(started->completed-state % %)))

(defn- failed
  [process-instance action]
  (-> process-instance
      (assoc ::phase ::rollback)
      (assoc-in [::action-states action] ::rollback-completed)))

(defn deliver-message
  [process-instance action message]
  (let [[outcome state] (react process-instance action message)
        process-instance' (assoc process-instance ::process-state state)]
    (case outcome
      :success (completed process-instance' action)
      :failure (failed process-instance' action)
      process-instance')))

(defn extend-process
  [{deps ::dependencies :as process}]
  (update process ::actions (fnil into #{})
          (apply concat (keys deps) (vals deps))))
