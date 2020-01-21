(ns com.github.bentomi.constructor.storage.storage-test
  (:require [clojure.test :as test :refer [deftest is are]]
            [clojure.inspector :as insp]
            [clojure.tools.logging :as log]
            [medley.core :as medley]
            [com.github.bentomi.constructor.core :as core]
            [com.github.bentomi.constructor.storage :as storage]
            [com.github.bentomi.constructor.storage.memory :refer [->MemoryStorage]]
            [com.github.bentomi.constructor.view :as view]
            [com.github.bentomi.constructor.storage.db :refer [db db-fixture show-db]])
  (:import (clojure.lang ExceptionInfo)))

;;; Define processes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def processes
  (medley/map-vals
   core/extend-process
   {::test-process {::core/process-id ::test-process
                    ::core/actions #{::d ::e}
                    ::core/dependencies {::c #{::a ::b}}}

    ::reset-website {::core/process-id ::reset-website
                     ::core/dependencies {::create-website #{::unbind-domain}
                                          ::create-association #{::create-website ::create-account}
                                          ::delete-old-website #{::create-association}
                                          ::delete-old-account #{::create-association}}}}))

(comment
  (view/view (get processes ::test-process))
  (view/view (get processes ::reset-website)))

(doseq [p [::test-process ::reset-website]]
  (derive p ::process))

(doseq [a [::a ::b ::c ::d ::e
           ::unbind-domain ::create-website ::create-account ::create-association
           ::delete-old-website ::delete-old-account]]
  (derive a ::action))

(defmethod core/act [::process ::action ::core/forward]
  [{::core/keys [process-id process-state]} action]
  (log/debugf "%s: starting forward %s with %s" process-id action process-state)
  [:started (assoc process-state action "started")])

(defmethod core/act [::process ::b ::core/forward]
  [{::core/keys [process-id process-state]} action]
  (log/debugf "%s: completed %s with %s" process-id action process-state)
  [:completed (assoc process-state action "completed")])

(defmethod core/act [::process ::b ::core/rollback]
  [{::core/keys [process-id process-state]} action]
  (log/debugf "%s: compensated %s with %s" process-id action process-state)
  [:completed (assoc process-state action "compensated")])

(defmethod core/act [::process ::action ::core/rollback]
  [{::core/keys [process-id process-state]} action]
  (log/debugf "%s: starting rollback %s with %s" process-id action process-state)
  [:started (assoc process-state action "compensation started")])

(defmethod core/react [::process ::action ::core/started ::success]
  [process-instance action message]
  [:success (::core/process-state process-instance)])

(defmethod core/react [::process ::action ::core/forward-started ::failure]
  [process-instance action message]
  [:failure (::core/process-state process-instance)])

(defmethod core/react [::process ::action ::core/rollback-started ::failure]
  [process-instance action message]
  [:stop (::core/process-state process-instance)])

;;; Set up storage solutions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def mem (->MemoryStorage (atom {})))

(test/use-fixtures :each db-fixture)

(defn assert-action-states
  [storage proc-inst-id state-actions]
  (let [as (::core/action-states (storage/fetch storage proc-inst-id))]
    (doseq [[state actions] state-actions, action actions]
      (is (= state (get as action))))))

;;; Simple test ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest reset-website-test
  (let [store (db processes)
        proc-inst-id (storage/store store (core/instantiate (processes ::reset-website)))]
    (try
      (storage/execute store proc-inst-id core/step)
      (assert-action-states store proc-inst-id
                            {::core/forward-started [::unbind-domain ::create-account]})

      (doseq [action [::create-account ::unbind-domain]]
        (storage/execute store proc-inst-id
                         #(core/deliver-message % action {::core/message-id ::success})))
      (assert-action-states store proc-inst-id
                            {::core/forward-completed [::unbind-domain ::create-account]
                             ::core/forward-started [::create-website]})

      (let [action ::create-website]
        (storage/execute store proc-inst-id
                         #(core/deliver-message % action {::core/message-id ::success})))
      (assert-action-states store proc-inst-id
                            {::core/forward-completed [::unbind-domain
                                                       ::create-account ::create-website]
                             ::core/forward-started [::create-association]})

      #_(show-db)
      #_(view/view (storage/fetch store proc-inst-id))

      (let [action ::create-association]
        (storage/execute store proc-inst-id
                         #(core/deliver-message % action {::core/message-id ::success})))
      (assert-action-states store proc-inst-id
                            {::core/forward-completed [::unbind-domain ::create-website
                                                       ::create-account ::create-association]
                             ::core/forward-started [::delete-old-website ::delete-old-account]})

      (doseq [action [::delete-old-website ::delete-old-account]]
        (storage/execute store proc-inst-id
                         #(core/deliver-message % action {::core/message-id ::success})))
      (assert-action-states store proc-inst-id
                            {::core/forward-completed [::unbind-domain
                                                       ::create-website ::create-account
                                                       ::create-association
                                                       ::delete-old-website ::delete-old-account]})

      (is (= ::core/completed (::core/phase (storage/fetch store proc-inst-id))))

      (finally
        (storage/delete store proc-inst-id)))
    (is (nil? (storage/fetch store proc-inst-id)))))

;;; Rollback and sync action test ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest rollback-test
  (let [store mem
        proc-inst-id (storage/store store (core/instantiate (processes ::test-process)))]
    (try
      (storage/execute store proc-inst-id core/step)
      (assert-action-states store proc-inst-id {::core/forward-started [::a ::d ::e]
                                                ::core/forward-completed [::b]})

      (doseq [action [::a ::d]]
        (storage/execute store proc-inst-id
                         #(core/deliver-message % action {::core/message-id ::success})))
      (assert-action-states store proc-inst-id {::core/forward-completed [::a ::b ::d]
                                                ::core/forward-started [::c ::e]})

      (storage/execute store proc-inst-id
                       #(core/deliver-message % ::c {::core/message-id ::failure}))
      (assert-action-states store proc-inst-id {::core/rollback-started [::a ::d]
                                                ::core/rollback-completed [::b ::c]})

      (doseq [action [::d ::a]]
        (storage/execute store proc-inst-id
                         #(core/deliver-message % action {::core/message-id ::success})))
      (assert-action-states store proc-inst-id {::core/rollback-completed [::a ::b ::c ::d]
                                                ::core/forward-started [::e]})

      #_(view/view (storage/fetch store proc-inst-id))

      (storage/execute store proc-inst-id
                       #(core/deliver-message % ::e {::core/message-id ::success}))
      (assert-action-states store proc-inst-id {::core/rollback-completed [::a ::b ::c ::d]
                                                ::core/rollback-started [::e]})

      #_(view/view (storage/fetch store proc-inst-id))

      (storage/execute store proc-inst-id
                       #(core/deliver-message % ::e {::core/message-id ::success}))
      (assert-action-states store proc-inst-id {::core/rollback-completed [::a ::b ::c ::d ::e]})

      (is (= ::core/compensated (::core/phase (storage/fetch store proc-inst-id))))

      (finally
        (storage/delete store proc-inst-id)))
    (is (nil? (storage/fetch store proc-inst-id)))))
