(ns com.github.bentomi.constructor.storage.wordpress1-test
  (:require [clojure.test :as test :refer [deftest is are]]
            [clojure.inspector :as insp]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async :refer [<! >!]]
            [com.github.bentomi.constructor.core :as core]
            [com.github.bentomi.constructor.storage :as storage]
            [com.github.bentomi.constructor.view :as view]
            [com.github.bentomi.constructor.storage.db :refer [db db-fixture show-db]])
  (:import (clojure.lang ExceptionInfo)))

;;; Define processes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def processes
  {::create-wordpress1
   (core/extend-process
    {::core/process-id ::create-wordpress1
     ::core/dependencies {::create-database #{::create-stack-instance}
                          ::create-webspace #{::create-stack-instance}
                          ::connect-domain #{::create-webspace}
                          ::create-wordpress #{::create-database
                                               ::connect-domain}}})})

(comment
  (view/view (get processes ::create-wordpress1)))

;;; Define environment ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn outcome
  [success-rate]
  (if (< (Math/random) success-rate)
    ::success
    ::failure))

(defn server-proc
  [command-chan response-chan {:keys [success-rate] :or {success-rate 1.0}}]
  (async/go-loop []
    (when-let [command (<! command-chan)]
      (println (str "command: " command))
      (let [result (outcome success-rate)]
        (>! response-chan (assoc command :result result))
        (when (and (= ::delete (:verb command))
                   (= ::failure result))
          (>! response-chan (assoc command :result ::success))))
      (recur))))

(defn response-processor
  [store response-chan]
  (async/go-loop []
    (when-let [{:keys [proc-inst-id action result] :as response} (<! response-chan)]
      (println (str "event:   " response))
      (storage/execute store proc-inst-id
                       #(core/deliver-message % action (assoc response ::core/message-id result)))
      (recur))))

;;; create stack instance actions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod core/act [::create-wordpress1 ::create-stack-instance ::core/forward]
  [{:keys [::core/process-instance-id ::core/process-state ::stack-instance-chan]
    :as proc-inst} action]
  (async/put! stack-instance-chan {:verb ::create, :action action
                                   :proc-inst-id process-instance-id})
  [:started (assoc process-state action :started)])

(defmethod core/react [::create-wordpress1 ::create-stack-instance ::core/forward-started ::success]
  [{:keys [::core/process-state]} action _message]
  [:success (assoc process-state action :completed)])

(defmethod core/react [::create-wordpress1 ::create-stack-instance ::core/forward-started ::failure]
  [{:keys [::core/process-state]} action _message]
  [:failure (assoc process-state action :failed)])

(defmethod core/act [::create-wordpress1 ::create-stack-instance ::core/rollback]
  [{:keys [::core/process-instance-id ::core/process-state ::stack-instance-chan]} action]
  (async/put! stack-instance-chan {:verb ::delete, :action action
                                   :proc-inst-id process-instance-id})
  [:started (assoc process-state action :compensation-started)])

(defmethod core/react [::create-wordpress1 ::create-stack-instance ::core/rollback-started ::success]
  [{:keys [::core/process-state]} action _message]
  [:success (assoc process-state action :compensated)])

(defmethod core/react [::create-wordpress1 ::create-stack-instance ::core/rollback-started ::failure]
  [{:keys [::core/process-state]} action _message]
  [:stop (assoc process-state action :compensation-failed)])

;;; create database actions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod core/act [::create-wordpress1 ::create-database ::core/forward]
  [{:keys [::core/process-instance-id ::core/process-state ::database-chan]} action]
  (async/put! database-chan {:verb ::create, :action action
                             :proc-inst-id process-instance-id})
  [:started (assoc process-state action :started)])

(defmethod core/react [::create-wordpress1 ::create-database ::core/forward-started ::success]
  [{:keys [::core/process-state]} action _message]
  [:success (assoc process-state action :completed)])

(defmethod core/react [::create-wordpress1 ::create-database ::core/forward-started ::failure]
  [{:keys [::core/process-state]} action _message]
  [:failure (assoc process-state action :failed)])

(defmethod core/act [::create-wordpress1 ::create-database ::core/rollback]
  [{:keys [::core/process-instance-id ::core/process-state ::database-chan]} action]
  (async/put! database-chan {:verb ::delete, :action action
                             :proc-inst-id process-instance-id})
  [:started (assoc process-state action :compensation-started)])

(defmethod core/react [::create-wordpress1 ::create-database ::core/rollback-started ::success]
  [{:keys [::core/process-state]} action _message]
  [:success (assoc process-state action :compensated)])

(defmethod core/react [::create-wordpress1 ::create-database ::core/rollback-started ::failure]
  [{:keys [::core/process-state]} action _message]
  [:stop (assoc process-state action :compensation-failed)])

;;; create webspace actions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod core/act [::create-wordpress1 ::create-webspace ::core/forward]
  [{:keys [::core/process-instance-id ::core/process-state ::webspace-chan]} action]
  (async/put! webspace-chan {:verb ::create, :action action
                             :proc-inst-id process-instance-id})
  [:started (assoc process-state action :started)])

(defmethod core/react [::create-wordpress1 ::create-webspace ::core/forward-started ::success]
  [{:keys [::core/process-state]} action _message]
  [:success (assoc process-state action :completed)])

(defmethod core/react [::create-wordpress1 ::create-webspace ::core/forward-started ::failure]
  [{:keys [::core/process-state]} action _message]
  [:failure (assoc process-state action :failed)])

(defmethod core/act [::create-wordpress1 ::create-webspace ::core/rollback]
  [{:keys [::core/process-instance-id ::core/process-state ::webspace-chan]} action]
  (async/put! webspace-chan {:verb ::delete, :action action
                             :proc-inst-id process-instance-id})
  [:started (assoc process-state action :compensation-started)])

(defmethod core/react [::create-wordpress1 ::create-webspace ::core/rollback-started ::success]
  [{:keys [::core/process-state]} action _message]
  [:success (assoc process-state action :compensated)])

(defmethod core/react [::create-wordpress1 ::create-webspace ::core/rollback-started ::failure]
  [{:keys [::core/process-state]} action _message]
  [:stop (assoc process-state action :compensation-failed)])

;;; connect domains actions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod core/act [::create-wordpress1 ::connect-domain ::core/forward]
  [{:keys [::core/process-instance-id ::core/process-state ::domain-chan]} action]
  (async/put! domain-chan {:verb ::create, :action action
                           :proc-inst-id process-instance-id})
  [:started (assoc process-state action :started)])

(defmethod core/react [::create-wordpress1 ::connect-domain ::core/forward-started ::success]
  [{:keys [::core/process-state]} action _message]
  [:success (assoc process-state action :completed)])

(defmethod core/react [::create-wordpress1 ::connect-domain ::core/forward-started ::failure]
  [{:keys [::core/process-state]} action _message]
  [:failure (assoc process-state action :failed)])

(defmethod core/act [::create-wordpress1 ::connect-domain ::core/rollback]
  [{:keys [::core/process-instance-id ::core/process-state ::domain-chan]} action]
  (async/put! domain-chan {:verb ::delete, :action action
                           :proc-inst-id process-instance-id})
  [:started (assoc process-state action :compensation-started)])

(defmethod core/react [::create-wordpress1 ::connect-domain ::core/rollback-started ::success]
  [{:keys [::core/process-state]} action _message]
  [:success (assoc process-state action :compensated)])

(defmethod core/react [::create-wordpress1 ::connect-domain ::core/rollback-started ::failure]
  [{:keys [::core/process-state]} action _message]
  [:stop (assoc process-state action :compensation-failed)])

;;; create wordpress actions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod core/act [::create-wordpress1 ::create-wordpress ::core/forward]
  [{:keys [::core/process-instance-id ::core/process-state ::wordpress-chan]} action]
  (async/put! wordpress-chan {:verb ::create, :action action
                              :proc-inst-id process-instance-id})
  [:started (assoc process-state action :started)])

(defmethod core/react [::create-wordpress1 ::create-wordpress ::core/forward-started ::success]
  [{:keys [::core/process-state]} action _message]
  [:success (assoc process-state action :completed)])

(defmethod core/react [::create-wordpress1 ::create-wordpress ::core/forward-started ::failure]
  [{:keys [::core/process-state]} action _message]
  [:failure (assoc process-state action :failed)])

(defmethod core/act [::create-wordpress1 ::create-wordpress ::core/rollback]
  [{:keys [::core/process-instance-id ::core/process-state ::wordpress-chan]} action]
  (async/put! wordpress-chan {:verb ::delete, :action action
                              :proc-inst-id process-instance-id})
  [:started (assoc process-state action :compensation-started)])

(defmethod core/react [::create-wordpress1 ::create-wordpress ::core/rollback-started ::success]
  [{:keys [::core/process-state]} action _message]
  [:success (assoc process-state action :compensated)])

(defmethod core/react [::create-wordpress1 ::create-wordpress ::core/rollback-started ::failure]
  [{:keys [::core/process-state]} action _message]
  [:stop (assoc process-state action :compensation-failed)])


;;; Completion ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod core/complete ::create-wordpress1
  [{:keys [::core/process-instance-id ::core/process-state ::completed-chan]}]
  (async/put! completed-chan process-instance-id)
  process-state)

;;; Set up storage solutions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(test/use-fixtures :each db-fixture)

;;; Concurrent test ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start-processes
  [store
   {::keys [stack-instance-chan stack-instance-response-chan
            database-chan database-response-chan
            webspace-chan webspace-response-chan
            domain-chan domain-response-chan
            wordpress-chan wordpress-response-chan
            completed-chan]}]
  (server-proc stack-instance-chan stack-instance-response-chan {:success-rate 0.9})
  (response-processor store stack-instance-response-chan)
  (server-proc database-chan database-response-chan {:success-rate 0.9})
  (response-processor store database-response-chan)
  (server-proc webspace-chan webspace-response-chan {:success-rate 0.9})
  (response-processor store webspace-response-chan)
  (server-proc domain-chan domain-response-chan {:success-rate 0.9})
  (response-processor store domain-response-chan)
  (server-proc wordpress-chan wordpress-response-chan {:success-rate 0.9})
  (response-processor store wordpress-response-chan))

(deftest create-wordpress1
  (let [chans {::stack-instance-chan (async/chan)
               ::stack-instance-response-chan (async/chan)
               ::database-chan (async/chan)
               ::database-response-chan (async/chan)
               ::webspace-chan (async/chan)
               ::webspace-response-chan (async/chan)
               ::domain-chan (async/chan)
               ::domain-response-chan (async/chan)
               ::wordpress-chan (async/chan)
               ::wordpress-response-chan (async/chan)
               ::completed-chan (async/chan)}
        get-process #(merge (processes %) chans)
        store (db get-process)
        proc-inst-id (storage/store store (core/instantiate (get-process ::create-wordpress1)))]
    (start-processes store chans)
    (try
      (storage/execute store proc-inst-id core/step)
      (let [[val port] (async/alts!! [(chans ::completed-chan) (async/timeout 200)])]
        (is (= proc-inst-id val)))

      #_(show-db)
      #_(view/view (storage/fetch store proc-inst-id))

      (finally
        (storage/delete store proc-inst-id)
        (run! async/close! (vals chans))))))
