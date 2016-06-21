(ns martian.protocols)

(defprotocol Martian
  (url-for
    [this route-name]
    [this route-name params])

  (request-for
    [this route-name]
    [this route-name params]))
