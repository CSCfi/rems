# Resource Entitlement Management System

... prototype in clojure

## Getting started

### Running interactively

```
$ lein repl
rems.standalone=> (start-app [])
```

Point your browser to <http://localhost:3000>

### Running via uberjar

```
$ lein uberjar
$ java -jar target/uberjar/rems.jar
```

Point your browser to <http://localhost:3000>

### Running via docker

```
$ lein uberjar
$ docker build . -t rems
$ sudo docker run -p 127.0.0.1:3000:3000 -d rems
```

Point your browser to <http://localhost:3000>