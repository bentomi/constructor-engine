(ns com.ionos.hop.constructor.view
  (:require [clojure.java.shell :refer [sh]]))

(def ^:private action-state->style
  #:com.ionos.hop.constructor.core
  {:forward-started "color=green, style=dotted"
   :forward-completed "color=green"
   :rollback-started "color=red, style=dotted"
   :rollback-completed "color=red"})

(defn- append-action
  [sb action action-state]
  (.append sb "  \"")
  (.append sb (name action))
  (.append sb "\"")
  (when-let [style (action-state->style action-state)]
    (.append sb " [")
    (.append sb style)
    (.append sb "]"))
  (.append sb ";\n"))

(defn generate-dot
  [{:com.ionos.hop.constructor.core/keys [actions action-states dependencies process-id]}]
  (let [label (name process-id)
        sb (StringBuilder.)]
    (.append sb "digraph \"")
    (.append sb label)
    (.append sb "\" {\n  label = \"")
    (.append sb label)
    (.append sb "\";\n")
    (doseq [a actions]
      (append-action sb a (get action-states a)))
    (doseq [[target sources] dependencies, source sources]
      (.append sb "  \"")
      (.append sb (name source))
      (.append sb "\" -> \"")
      (.append sb (name target))
      (.append sb "\";\n"))
    (.append sb "}\n")
    (str sb)))

(defn- create-image
  [process-instance]
  (:out (sh "dot" "-Tpng" :in (generate-dot process-instance) :out-enc :bytes)))

(defn- as-file
  [prefix suffix ^bytes byte-data]
  (let [tmp (java.io.File/createTempFile prefix suffix)]
    (with-open [w (java.io.FileOutputStream. tmp)]
      (.write w byte-data))
    (.deleteOnExit tmp)
    tmp))

(defn view
  [process-instance]
  (sh "xdg-open" (str (as-file "process-image-" ".png" (create-image process-instance)))))
