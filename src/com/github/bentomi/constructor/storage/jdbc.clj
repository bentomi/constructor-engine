(ns com.github.bentomi.constructor.storage.jdbc
  (:require [clojure.set :as set]
            [clojure.edn :as edn]
            [clojure.java.jdbc :as jdbc]
            [medley.core :as medley]
            [com.github.bentomi.constructor.core :as core]
            [com.github.bentomi.constructor.storage])
  (:import (clojure.lang ExceptionInfo)
           (com.github.bentomi.constructor.storage Storage)))

(defn- to-db-row
  [process-instance]
  (when process-instance
    (-> process-instance
        (select-keys [::core/process-id ::core/phase ::core/process-state ::core/action-states])
        (set/rename-keys {::core/process-id :process_id
                          ::core/phase :phase
                          ::core/process-state :state
                          ::core/action-states :action_states})
        (->> (medley/map-vals pr-str))
        (assoc :process_instance_id (::core/process-instance-id process-instance)))))

(defn- from-db-row
  [row]
  (when row
    (-> row
        (select-keys [:process_id :phase :state :action_states])
        (set/rename-keys {:process_id ::core/process-id
                          :phase ::core/phase
                          :state ::core/process-state
                          :action_states ::core/action-states})
        (->> (medley/map-vals edn/read-string))
        (assoc ::core/process-instance-id (:process_instance_id row)))))

(defn db-refresh
  [db process-instance]
  (let [row (to-db-row process-instance)
        sets (select-keys row [:phase :state :action_states])
        condition ["process_instance_id = ?" (:process_instance_id row)]]
    (jdbc/update! db :process sets condition)))

(let [query "select * from process where process_instance_id = ?"
      lock-query (str query " for update")]
  (defn db-fetch
    [db processes proc-inst-id & {:keys [lock?]}]
    (let [row (-> (jdbc/query db [(if lock? lock-query query) proc-inst-id])
                  first
                  from-db-row)]
      (merge row (processes (::core/process-id row))))))

(defrecord JdbcStorage [db-spec processes]
  Storage
  (store [_ process-instance]
    (jdbc/insert! db-spec :process (to-db-row process-instance))
    (::core/process-instance-id process-instance))
  (refresh [_ process-instance]
    (db-refresh db-spec process-instance))
  (fetch [_ proc-inst-id]
    (db-fetch db-spec processes proc-inst-id))
  (delete [_ proc-inst-id]
    (jdbc/delete! db-spec :process ["process_instance_id = ?" proc-inst-id]))
  (execute [_ proc-inst-id operation]
    (jdbc/with-db-transaction [t-con db-spec]
      (let [proc-inst (db-fetch t-con processes proc-inst-id :lock? true)]
      (try
        (db-refresh t-con (-> proc-inst operation core/step))
        (catch ExceptionInfo e
          (some->> (:process-instance e) (db-refresh t-con))
          (throw e)))))))
