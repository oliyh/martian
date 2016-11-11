Ideas

- Start off with normal progression of making HTTP calls
  - Yay Clojure, so simples `(http/get "https://api.com/foo/bar" {:as :json})`
  - Oh, parameters `(http/get (str "https://api.com" (format "/foo/%s" bar)) {:as :json})` but it's still good
  - Query parameters! `(http/get (str "https://api.com" (format "/foo/%s" bar)) {:as :json})` hmm
  - Body parameters... header parameters... multiple route parameters, starting to get a bit messy here
  - End up writing adhoc methods for each route, params in different order, it all becomes a mess
  - How do tests work? Stub server?

- Show the server code - something perhaps equally messy pulling things out of the route, the query, the body and some adhoc coercing perhaps
  - Then try to refactor something from a query param to a route param, and show all the bits of code you have to change
  - Oh man and now my client test is testing code that isn't even true anymore

- Stop for a bit and talk about the difference between functional data flow - data goes into a function, data comes out - and what we're seeing here
  - Isn't a function so much easier to read and understand?
  - Why should a web handler have to look different?

- Go back to the server code
  - Why don't we try to describe the inputs and outputs like a function?
  - Slowly build up what we want to describe - the path params, query params, method, url
  - We end up with a much clear picture of what the data going in and out looks like
  - Can generate swagger from it, and get a free UI!

- Sweet Swagger UI, so why does my client code look like (show messy client code)
  - Go back to what a function looks like - ideally it could look like this
  - What do we need to know to be able to do this?
  - Looks a lot like what we just did on the server - method, route, query param etc
  - We just described the implementation of the API
  - We can imagine taking the description of the implementation and writing generic code to make the call
  - Enter martian!

- So my client code is cleaner, what else does it do?
  - The refactor we did earlier would be super painless, just move one param on the server side and done
  - Adding cross cutting behaviour - metrics, for example - is now super easy rather than before passing metrics around everywhere
  - Generative testing - what do all the responses look like? Did I consider them all? My `re-match` doesn't work on the nil body returned from a 404!

- I want to talk to other servers that don't use swagger
  - The description is just data - we can write our own data to describe it

- re-frame usage - simple, clean HTTP as a side effect
  - Describing a side effect using data makes testing much much easier
  - Martian's side effect data is clean and simple and has a wider tolerance for refactoring

- Explain the interceptors model

- By the way, interceptors are a nice way to let the user inject and extend behaviour
  - They are just data as well so you can list them, reorder them, remove or add them, attach metadata etc
  - You can provide a default stack, let the user change it up and then accept it for execution in a single argument
  - Much better than maps of arbitrary options, where you still rely on the code existing to interpret the options in the way you want
  - Better than binding dynamic vars (which can't cross thread boundaries)
  - Better than multimethods, where you are limited by where the library author thinks you might want to extend behaviour
  - Encourages simple, decoupled steps of behaviour
  - Encourages re-use
