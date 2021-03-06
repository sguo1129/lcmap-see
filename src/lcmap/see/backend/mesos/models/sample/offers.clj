(ns lcmap.see.backend.mesos.models.sample.offers
  ""
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [clojusc.twig :refer [pprint]]
            [lcmap.see.backend.mesos.models.common.resources :as resources]
            [lcmap.see.backend.mesos.models.sample.task :as task]
            [lcmap.see.backend.mesos.util :as util]))

(defn process-one
  ""
  [state data status index offer]
  (let [task-info (task/make-map state data index offer)]
    (log/trace "Got task info:" (pprint task-info))
    task-info))

(defn hit-limits?
  ""
  [limits status]
  (if (or (< (:remaining-cpus status) (:cpus-per-task limits))
          (< (:remaining-mem status) (:mem-per-task limits)))
    (do
      (log/debug "Hit resource limit.")
      true)
    false))

(defn quit-loop?
  ""
  [limits status offers]
  (log/debugf "quit-loop check:\nlimits=\n%s \nstatus=%s \noffer-count=%s"
             limits status (count offers))
  (or (hit-limits? limits status)
      (empty? offers)))

(defn update-status
  ""
  [status rsrcs]
  (log/debug "Updating status with rsrcs:" rsrcs)
  (-> status
      (update :remaining-cpus (partial - (:cpus rsrcs)))
      (update :remaining-mem (partial - (:mem rsrcs)))))

(defn loop-offers
  ""
  [state data offers]
  (let [status {:remaining-cpus 0
                :remaining-mem 0}
        index 1
        [offer & remaining-offers] offers
        tasks [(process-one state data status index offer)]]
    ; (let [rsrcs (resources/sum offer)
    ;       status (update-status status rsrcs)]
    ;   (if (quit-loop? (:limits state) status remaining-offers)
    ;     (do
    ;       (log/debug "Quitting loop ...")
    ;       tasks)
    ;     (do
    ;       (log/debug "Iterating offers ...")
    ;       (recur
    ;         status
    ;         (inc index)
    ;         remaining-offers
    ;         (conj tasks
    ;               (process-one state data status index offer))))))))
    tasks))

(defn process-all
  ""
  [state data offers]
  (vec (loop-offers state data offers)))

(defn get-ids
  ""
  [offers]
  (map :id offers))
