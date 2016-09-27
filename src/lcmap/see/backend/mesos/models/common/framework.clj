(ns lcmap.see.backend.mesos.models.common.framework
  "General framework functions for Mesomatic-based models."
  (:require [clojure.core.async :as async :refer [chan <! go]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojusc.twig :refer [pprint]]
            [lcmap.see.backend :as see]
            [lcmap.see.util :as util]
            [mesomatic.scheduler :as scheduler :refer [scheduler-driver]]))

(defn do-unhealthy-status
  ""
  [state-name state payload]
  (log/debug "Doing unhealthy check ...")
  (do
    (log/errorf "%s - %s"
                state-name
                (get-error-msg payload))
    (async/close! (get-channel state))
    (scheduler/stop! (get-driver state))
    (util/finish :exit-code 127)
    state))

(defn check-task-finished
  ""
  [state payload]
  (if (= (get-task-state payload) :task-finished)
    (let [task-count (inc (:launched-tasks state))
          new-state (assoc state :launched-tasks task-count)]
      (log/debug "Incremented task-count:" task-count)
      (log/info "Tasks finished:" task-count)
      (if (>= task-count (get-max-tasks state))
        (do
          (scheduler/stop! (get-driver state))
          (util/finish :exit-code 0)
          new-state)
        new-state))
    state))

(defn check-task-abort
  ""
  [state payload]
  (if (or (= (get-task-state payload) :task-lost)
          (= (get-task-state payload) :task-killed)
          (= (get-task-state payload) :task-failed))
    (let [status (:status payload)]
      (log/errorf (str "Aborting because task %s is in unexpected state %s "
                       "with reason %s from source %s with message '%s'")
                  (get-task-id payload)
                  (:state status)
                  (:reason status)
                  (:source status)
                  (:message status))
      (scheduler/abort! (get-driver state))
      (util/finish :exit-code 127)
      state)
    state))

(defn do-healthy-status
  ""
  [state payload]
  (log/debug "Doing healthy check ...")
  (-> state
      (check-task-finished payload)
      (check-task-abort payload)))

(defn wrap-handle-msg
  "Wrap the handle-msg multi-method so that exceptions can be properly caught
  and the scheduler can be given the change to perform an abort procedure.

  Anticipated use of the function is:

    (async.core/reduce (partial wrap-handle-msg handle-msg) { ... } ch)

  For use with the Mesomatic async scheduler API."
  [handler state payload]
  (try
    (handler state payload)
    (catch Exception e
      (log/error "Got error:" (.getMessage e))
      (scheduler/abort! (:driver state))
      (reduced
        (assoc state :error e)))))