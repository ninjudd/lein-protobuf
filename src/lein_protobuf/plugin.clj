(ns lein-protobuf.plugin
  (:use [leiningen.javac :only [javac]]
        [leiningen.protobuf :only [protobuf *compile-protobuf?*]]
        [robert.hooke :only [add-hook]]))

(defn hooks []
  (add-hook #'javac
            (fn [f & args]
              (when *compile-protobuf?*
                (protobuf (first args)))
              (apply f args))))