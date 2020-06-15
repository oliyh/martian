# martian-vcr
`martian-vcr` is a library which allows you to easily record and play back HTTP requests made through martian.

## Usage



## Options

Options are supplied as a map, like this:

```clj
{:store {:kind :file
         :root-dir "target"
         :pprint? true}
 :on-missing-response :generate-404}
 ```

The options available are:

### Store

The `:store` option can be one of the following:

#### File store

For Clojure only:

 ```clj
{:kind :file
 :root-dir "target" ;; where the response files are written
 :pprint? ;; whether to pprint the files (uses fipp)
}
 ```

 #### Atom store

 For Clojure and Clojurescript:

 ```clj
{:kind :atom
 :store (atom {}) ;; where the reponses are assoced
}
```

### Missing response behaviour

The `:on-missing-response` allows you to choose between the following behaviours when a response is not in the store:

#### default
If you do not specify, the default behaviour is for no response key to be present in the context

#### throw-error
An error is thrown which can be handled by another interceptor

#### generate-404
A barebones 404 response is returned
