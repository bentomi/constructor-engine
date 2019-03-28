(ns com.ionos.hop.constructor.storage.memory
  (:require [com.ionos.hop.constructor.core :as core]
            [com.ionos.hop.constructor.storage])
  (:import (clojure.lang ExceptionInfo)
           (com.ionos.hop.constructor.storage Storage)))

(defrecord MemoryStorage [db-atom]
  Storage
  (store [_ process-instance]
    (let [proc-inst-id (::core/process-instance-id process-instance)]
      (swap! db-atom assoc proc-inst-id process-instance)
      proc-inst-id))
  (fetch [_ proc-inst-id]
    (get @db-atom proc-inst-id))
  (delete [_ proc-inst-id]
    (swap! db-atom dissoc proc-inst-id))
  (execute [_ proc-inst-id operation]
    (locking proc-inst-id
      (try
        (swap! db-atom update proc-inst-id (comp core/step operation))
        (catch ExceptionInfo e
          (when-let [process-instance' (:process-instance e)]
            (swap! db-atom assoc proc-inst-id process-instance'))
          (throw e))))))
