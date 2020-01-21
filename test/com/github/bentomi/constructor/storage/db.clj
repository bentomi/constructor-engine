(ns com.github.bentomi.constructor.storage.db
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.pprint :as pp]
            [com.github.bentomi.constructor.storage.jdbc :as dbstore])
  (:import (java.util Properties)
           (com.zaxxer.hikari HikariConfig HikariDataSource)))

(defonce db-spec
  (let [ps (Properties.)]
    (.put ps "jdbcUrl" "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
    {:datasource (-> ps HikariConfig. HikariDataSource.)}))

(defn db [processes]
  (dbstore/->JdbcStorage db-spec processes))

(defn db-fixture [f]
  (jdbc/execute! db-spec "drop table if exists process")
  (jdbc/execute! db-spec "create table process (process_id varchar, process_instance_id varchar primary key, phase varchar, state varchar, action_states varchar)")
  (f))

(defn show-db []
  (let [rows (jdbc/query db-spec ["select * from process"]
                         {:row-fn #'dbstore/from-db-row})]
    (pp/pprint rows)
    #_(insp/inspect-table rows)))
