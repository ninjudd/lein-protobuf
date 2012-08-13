(ns lein-protobuf.hooks
  (:use [leiningen.javac :only [javac]]
        [leiningen.protobuf :only [protobuf *compile-protobuf?* *compile-java?*]]
        [robert.hooke :only [add-hook]]))

(defn activate []
  (add-hook #'javac
            (fn [f & args]
              (when *compile-java?*
                (when *compile-protobuf?*
                  (protobuf (first args)))
                (apply f args)))))