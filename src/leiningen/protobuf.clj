(ns leiningen.protobuf
  (:use [clojure.string :only [join]]
        [leiningen.javac :only [javac]]
        [leinjacker.eval :only [in-project]]
        [leiningen.core.user :only [leiningen-home]]
        [leiningen.core.main :only [abort]])
  (:require [clojure.java.io :as io]
            [fs.core :as fs]
            [fs.compression :as fs-zip]
            [conch.core :as sh]))

(def cache (io/file (leiningen-home) "cache" "lein-protobuf"))
(def default-version "2.6.1")

(defn version [project]
  (or (:protobuf-version project) default-version))

(defn zipfile [project]
  (io/file cache (format "protobuf-%s.zip" (version project))))

(defn srcdir [project]
  (io/file cache (str "protobuf-" (version project))))

(defn srcprotoc [project]
  (io/file (srcdir project) "src" "protoc"))

(defn url [project]
  (java.net.URL.
   (format "https://github.com/google/protobuf/releases/download/v%s/protobuf-%s.zip"
     (version project)
     (version project))))

(defn proto-path [project]
  (io/file (get project :proto-path "resources/proto")))

(def ^{:dynamic true} *compile-protobuf?* true)

(defn target [project]
  (doto (io/file (:target-path project))
    .mkdirs))

(defn extract-dependencies
  "Extract all files proto depends on into dest."
  [project proto-path protos dest]
  (in-project (dissoc project :prep-tasks)
    [proto-path (.getPath proto-path)
     dest (.getPath dest)
     protos protos]
    (ns (:require [clojure.java.io :as io]))
    (letfn [(dependencies [proto-file]
              (when (.exists proto-file)
                (for [line (line-seq (io/reader proto-file))
                      :when (.startsWith line "import")]
                  (second (re-matches #".*\"(.*)\".*" line)))))]
      (loop [deps (mapcat #(dependencies (io/file proto-path %)) protos)]
        (when-let [[dep & deps] (seq deps)]
          (let [proto-file (io/file dest dep)]
            (if (or (.exists (io/file proto-path dep))
                    (.exists proto-file))
              (recur deps)
              (do (.mkdirs (.getParentFile proto-file))
                  (when-let [resource (io/resource (str "proto/" dep))]
                    (io/copy (io/reader resource) proto-file))
                  (recur (concat deps (dependencies proto-file)))))))))))

(defn modtime [f]
  (let [files (if (fs/directory? f)
                (->> f io/file file-seq rest)
                [f])]
    (if (empty? files)
      0
      (apply max (map fs/mod-time files)))))

(defn proto-file? [file]
  (let [name (.getName file)]
    (and (.endsWith name ".proto")
         (not (.startsWith name ".")))))

(defn proto-files [dir]
  (for [file (rest (file-seq dir)) :when (proto-file? file)]
    (.substring (.getPath file) (inc (count (.getPath dir))))))

(defn fetch
  "Fetch protocol-buffer source and unzip it."
  [project]
  (let [zipfile (zipfile project)
        srcdir  (srcdir project)]
    (when-not (.exists zipfile)
      (.mkdirs cache)
      (println (format "Downloading %s to %s" (.getName zipfile) zipfile))
      (with-open [stream (.openStream (url project))]
        (io/copy stream zipfile)))
    (when-not (.exists srcdir)
      (println (format "Unzipping %s to %s" zipfile srcdir))
      (fs-zip/unzip zipfile cache))))

(defn build-protoc
  "Compile protoc from source."
  [project]
  (let [srcdir (srcdir project)
        protoc (srcprotoc project)]
    (when-not (.exists protoc)
      (fetch project)
      (fs/chmod "+x" (io/file srcdir "autogen.sh"))
      (sh/stream-to-out (sh/proc "./autogen.sh" :dir srcdir) :out)
      (println "Configuring protoc")
      (fs/chmod "+x" (io/file srcdir "configure"))
      (sh/stream-to-out (sh/proc "./configure" :dir srcdir) :out)
      (println "Running 'make'")
      (sh/stream-to-out (sh/proc "make" :dir srcdir) :out))))

(defn protoc
  "Get protoc from
   1. :protoc argument from project.
   2. $PATH enviroment"
  [project]
  (or (:protoc project)
      (let [abs-protoc-path (sh/stream-to-string (sh/proc "command" "-v" "protoc") :out)]
        (when-not (= abs-protoc-path "")
          (.trim abs-protoc-path)))))

(defn compile-protobuf
  "Create .java and .class files from the provided .proto files."
  ([project protos]
     (compile-protobuf project protos (io/file (target project) "protosrc")))
  ([project protos dest]
     (let [target     (target project)
           class-dest (io/file target "classes")
           proto-dest (io/file target "proto")
           proto-path (proto-path project)
           protoc (or (protoc project) proto-path)]
       (when (or (> (modtime proto-path) (modtime dest))
                 (> (modtime proto-path) (modtime class-dest)))
         (binding [*compile-protobuf?* false]
           (fs/mkdirs target)
           (fs/mkdirs class-dest)
           (fs/mkdirs proto-dest)
           (.mkdirs dest)
           (extract-dependencies project proto-path protos proto-dest)
           (doseq [proto protos]
             (let [args (into [protoc proto
                               (str "--java_out=" (.getAbsoluteFile dest)) "-I."]
                              (map #(str "-I" (.getAbsoluteFile %))
                                   [proto-dest proto-path]))]
               (println " > " (join " " args))
               (let [result (apply sh/proc (concat args [:dir proto-path]))]
                 (when-not (= (sh/exit-code result) 0)
                   (abort "ERROR:" (sh/stream-to-string result :err))))))
           (javac (-> project
                      (update-in [:java-source-paths] concat [(.getPath dest)])
                      (update-in [:javac-options] concat ["-Xlint:none"]))))))))

(defn compile-google-protobuf
  "Compile com.google.protobuf.*"
  [project]
  (fetch project)
  (let [srcdir (srcdir project)
        descriptor "google/protobuf/descriptor.proto"
        src (io/file srcdir "src" descriptor)
        dest (io/file (proto-path project) descriptor)]
    (.mkdirs (.getParentFile dest))
    (when (> (modtime src) (modtime dest))
      (io/copy src dest))
    (compile-protobuf project [descriptor]
                      (io/file srcdir "java" "src" "main" "java"))))

(defn protobuf
  "Task for compiling protobuf libraries."
  [project & files]
  (let [files (or (seq files)
                  (proto-files (proto-path project)))
        protoc (protoc project)]
    (when-not protoc
      (build-protoc project))
    (when (= "protobuf" (:name project))
      (compile-google-protobuf project))
    (compile-protobuf project files)))
