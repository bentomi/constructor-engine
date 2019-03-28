(ns com.ionos.hop.constructor.storage)

(defprotocol Storage
  (store [db process-instance]
    "Store `process-instance` in `db`.
     Return the ID of the process instance")
  (refresh [db process-instance]
    "Update `process-instance` in `db`.")
  (fetch [db process-instance-id]
    "Get process instance with ID `process-instance-id` from `db`.")
  (delete [db process-instance-id]
    "Delete process instance with ID `process-instance-id` from `db`.")
  (execute [db process-instance-id operation]
    "Execute the operation `operation` on process instance with ID
     `process-instance-id` and update it in `db` to its result."))
