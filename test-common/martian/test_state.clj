(ns martian.test-state
  (:import (java.util.concurrent.atomic AtomicInteger)))

(defonce all-pets (atom {}))

(defonce last-pet-id (AtomicInteger.))

(defn get-last-pet-id []
  (AtomicInteger/.get last-pet-id))

(defn next-pet-id []
  (AtomicInteger/.incrementAndGet last-pet-id))
