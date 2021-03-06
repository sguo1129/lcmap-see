(ns lcmap.see.util
  ""
  (:require [clojure.core.memoize :as memo]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [digest]
            [lcmap.client.status-codes :as status]
            [leiningen.core.main :as lein]))

(defn finish
  ""
  [& {:keys [exit-code]}]
  (lein/exit exit-code)
  exit-code)

(defn serialize [args]
  (cond (list? args)
          (string/join (sort (map #'str args)))
        (map? args)
          (str (into (sorted-map) args))
        :else
          (str args)))

(defn get-args-hash [model-name & args]
  (->> args
       (serialize)
       (str model-name)
       (digest/md5)))

(defn add-shutdown-handler [func]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. func)))

(defn in?
  "This function returns true if the provided seqenuce contains the given
  elment."
  [seq elm]
  (some #(= elm %) seq))

(defn make-bool
  ""
  [input]
  (case input
    0 false
    "0" false
    false false
    "false" false
    :false false
    nil false
    "nil" false
    :nil false
    true))

(defn make-flag
  "There are three cases we want to handle for command line options:
  * a flag that takes a value
  * a flag which should be passed, since a value was given
  * a flag which should not be passed, since no value was given"
  [flag value & {:keys [unary?] :or {unary? false}}]
  (cond
    unary? (when (make-bool value) flag)
    (nil? value) nil
    :else (format "%s %s" flag value)))

(defn get-local-ip
  ""
  []
  (.getHostAddress (java.net.InetAddress/getLocalHost)))

(defn get-metas
  ""
  [an-ns]
  (->> an-ns
       (ns-publics)
       (map (fn [[k v]] [k (meta v)]))
       (into {})))

(defn get-meta
  "Takes the same form as the general `get-in` function:

      (get-meta 'my.name.space ['my-func :doc])"
  [an-ns rest]
  (-> an-ns
      (get-metas)
      (get-in rest)))

(defn get-docstring
  ""
  [an-ns fn-name]
  (get-meta an-ns [fn-name :doc]))

(def penultimate
  (comp second reverse))
