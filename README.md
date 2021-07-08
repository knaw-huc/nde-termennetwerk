# NDE - Termennetwerk (pilot)

Pilot implementation for the [NDE Termennetwerk](https://docs.google.com/document/d/11CLVYri6B1h4tHShhEmYYJB-y-bmS5cm3E5e7hZJLiQ/edit?usp=sharing) created for the [CLARIAH Techdag November 30, 2018](https://www.clariah.nl/evenementen/tech-dag-2-2018).

Got a question about this pilot? Please [contact](mailto:tech@netwerkdigitaalerfgoed.nl) us!

## Build & Start

```sh
$ mvn build
$ mvn "-Dexec.args=-Dnde.config=`pwd`/conf/termennetwerk.xml -classpath %classpath nl.knaw.huc.di.nde.Main" -Dexec.executable=java org.codehaus.mojo:exec-maven-plugin:1.5.0:exec
```

or via docker:

```sh
$ docker build -t nde-termennetwerk .
$ docker run --rm -it -p 8080:8080 nde-termennetwerk
```

## Endpoints

1. [GraphQL](https://graphql.org/) endpoint: http://localhost:8080/nde/graphql
2. [GraphiQL](https://github.com/graphql/graphiql) endpoint: http://localhost:8080/static/graphiql/index.html

## Queries

The GraphiQL endpoint is hardwired to the NDE Termennetwerk GraphQL endpoint and supports autocomplete.

Example queries:

```graphql
query { terms(match:"*Dutch*",dataset:["clavas"]) { dataset terms {uri, prefLabel} } }
```

```graphql
# Example: Gemeenschappelijke Thesaurus Audiovisuele Archieven (GTAA) en Wikidata
query {
  terms(match:"*fietsen*" dataset:["gtaa","wikidata"]) { dataset terms {uri prefLabel altLabel} }
}
```

```graphql
# Example: Nederlandse Thesaurus van Auteursnamen (NTA)
query {
  terms(match:"wolkers" dataset:["nta"] ) {
    dataset label terms { uri prefLabel altLabel definition scopeNote }
  }
}
```

```graphql
# Example: Cultuurhistorische Thesaurus (CHT) en Volkenkundige Thesaurus (SVCN)
query {
  terms(match:"amerika" dataset:["cht","svcn"]) {
    dataset terms {uri prefLabel altLabel scopeNote}
  }
}
```
```graphql
# Example: WO2 Thesaurus
query {
  terms(match:"bezetting" dataset:["wo2"] ) {
    dataset label terms { uri prefLabel altLabel definition scopeNote }
  }
}
```
```graphql
# Example: AAT Thesaurus
query {
  terms(match:"wiel*" dataset:["aat"] ) {
    dataset label terms { uri prefLabel altLabel definition scopeNote }
  }
}
```
```graphql
# Example: Stichting Omroep Muziek Termen
query {
  terms(match:"cello" dataset:["som"] ) {
    dataset label terms { uri prefLabel scopeNote }
  }
}
```
```graphql
# Example: Stichting Omroep Muziek Termen
query {
  terms(match:"cello" dataset:["som"] ) {
    dataset label terms { uri prefLabel scopeNote }
  }
}
```
```graphql
# Example queries for the Erfgeo connector (default type hg:Place)

query {
  terms(match:"Hoorn" dataset:[ "erfgeo"] ) {
    dataset
    label
    terms { uri prefLabel altLabel related }
  } 
}

query {
  terms(match:"Zardam" dataset:[ "erfgeo"] ) {
    dataset
    label
    terms { uri prefLabel altLabel related }
  } 
}

# And additional support for the hg:Street type 
# using 'erfgeo:street' as dataset name
# add defintions in /conf/termennetwerk.xml to add other types
query {
  terms(match:"Begijnhof" dataset:[ "erfgeo:street"] ) {
    dataset
    label
    terms { uri prefLabel altLabel related }
  } 
}

query {
  terms(match:"dam, middelburg" dataset:[ "erfgeo:street"] ) {
    dataset
    label
    terms { uri prefLabel altLabel related }
  } 
}

```
or via curl:

```sh
$ curl -XPOST -H 'Content-Type:application/graphql'  -d 'query { terms(match:"Abkhazian",dataset:["clavas"]) { dataset terms {uri, altLabel} } }' http://localhost:8080/nde/graphql
```

## TODO

* [x] load dataset recipe
* [x] example dataset recipe
* [x] docker setup
* [ ] keep the languages
* [x] query multiple datasets and merge the results
* [ ] fuller support for the NDE API design
* [ ] how to deal with different response times
* [ ] use Dropwizard
* [ ] support literal SPARQL queries
* [ ] ...
