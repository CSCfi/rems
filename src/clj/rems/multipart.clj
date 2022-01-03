(ns rems.multipart
  "Namespace for handling multipart requests."
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.test :refer [deftest is testing]]
            [medley.core :refer [assoc-some]]
            [ring.middleware.multipart-params.temp-file])
  (:import [java.io ByteArrayOutputStream InputStream OutputStream]))

(defn size-checking-copy
  "Basically what `io/copy` does but with size checking."
  [^InputStream input ^OutputStream output opts]
  (let [buffer (make-array Byte/TYPE (:buffer-size opts 1024))
        max-size (:max-size opts)]
    (loop [read-so-far 0]
      (let [size (.read input buffer)]
        (when (pos? size)
          (if (and max-size (> (+ read-so-far size) max-size))
            :too-large
            (do
              (.write output buffer 0 size)
              (recur (+ read-so-far size)))))))))

(deftest test-size-checking-copy
  (let [data [0x0 0x1 0x2 0x4 0x2 0xF]
        copy (fn [opts]
               (let [input (io/input-stream (byte-array data))
                     output (ByteArrayOutputStream. (count data))
                     maybe-error (size-checking-copy input output opts)]
                 [maybe-error (seq (.toByteArray output))]))]
    (testing "default parameters, essentially infinite"
      (is (= [nil data] (copy {}))))

    (testing "small buffer size is no problem"
      (is (= [nil data] (copy {:buffer-size 3}))))

    (testing "too large, chunked behavior"
      (is (= [:too-large [0 1]] (copy {:buffer-size 2 :max-size 3}))
          "copies in chunks, already 2 x 2 > 3"))

    (testing "too large, byte at a time"
      (is (= [:too-large [0 1 2]] (copy {:buffer-size 1 :max-size 3}))))))

(defn size-limiting-temp-file-store
  "Drop-in replacement for `ring.middleware.multipart-params.temp-file/temp-file-store` that
  respects a maximum size limit for a file. We want to stop as soon as we have read
  enough of the stream to determine this is too big.

  NB: The size limit is only approximately true, a buffer more bytes may be transfered
  since the streaming is chunky.

  `:max-size`  maximum size of file in bytes

  See also `ring.middleware.multipart-params.temp-file/temp-file-store`"
  {:arglists '([] [options])}
  ([] (size-limiting-temp-file-store {:expires-in 3600 :max-size nil}))
  ([{:keys [expires-in max-size] :as options}]
   (let [file-set (atom #{})
         clean-up (delay (#'ring.middleware.multipart-params.temp-file/start-clean-up file-set expires-in))]
     (#'ring.middleware.multipart-params.temp-file/ensure-shutdown-clean-up file-set)
     (fn [item]
       (force clean-up)
       (let [temp-file (#'ring.middleware.multipart-params.temp-file/make-temp-file file-set)
             maybe-error (with-open [os (io/output-stream temp-file)]
                           (size-checking-copy (:stream item) os options))]
         (when maybe-error
           (log/error "Upload is too large, filename" (:filename item) "max size" max-size))
         (-> (select-keys item [:filename :content-type])
             (assoc-some :tempfile (when-not maybe-error temp-file)
                         :error maybe-error
                         :size (if maybe-error
                                 -1
                                 (.length temp-file)))))))))
