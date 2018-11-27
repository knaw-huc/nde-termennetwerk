# NDE - Termennetwerk (pilot)

Pilot implementation for the NDE Termennetwerk created for the CLARIAH Techdag November 30, 2018.

## Build & Start

```sh
$ mvn build
$ mvn "-Dexec.args=-Dexec.args=-Dnde.config=`pwd`/conf/termbases.xml -classpath %classpath nl.knaw.huc.di.nde.Main" -Dexec.executable=java org.codehaus.mojo:exec-maven-plugin:1.5.0:exec
```

## Endpoints

1. [GraphQL](https://graphql.org/) endpoint: http://localhost:8080/nde/graphql
2. [GraphiQL](https://github.com/graphql/graphiql) endpoint: http://localhost:8080/static/graphiql/index.html

## Queries

The GraphIQL endpoint is hardwired to the NDE Termennetwerk GraphQL endpoint and supports autocomplete.

Example query:

```graphql
query { terms(match:"dutch",dataset:"clavas") {uri} }
```

```sh
$ curl -XPOST -H 'Content-Type:application/graphql'  -d 'query { terms(match:"dutch",dataset:"clavas") {uri} }' http://localhost:8080/nde/graphql
```

## TODO

* [x] dataset plugins
* [ ] query multiple datasets and merge the results
* [ ] ...
