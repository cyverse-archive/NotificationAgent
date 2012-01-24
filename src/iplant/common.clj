(ns iplant.common)

(defn resp
  "Returns a value that Ring can use to generate a response.

   Parameters:
     status - the HTTP status code to return.
     msg    - the message to return."
  [status msg]
  {:status status
   :body msg})
