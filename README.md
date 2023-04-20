# Quarkus Reactive H2 Client
<!-- ALL-CONTRIBUTORS-BADGE:START - Do not remove or modify this section -->
[![All Contributors](https://img.shields.io/badge/all_contributors-1-orange.svg?style=flat-square)](#contributors-)
<!-- ALL-CONTRIBUTORS-BADGE:END -->

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.quarkus-reactive-h2-client/quarkus-reactive-h2-client?logo=apache-maven&style=flat-square)](https://search.maven.org/artifact/io.quarkiverse.quarkus-reactive-h2-client/quarkus-reactive-h2-client)

## Welcome to Quarkiverse!

This extension enable usage of reactive client on H2 database. Cause the Vert.x community does not develop a reactive driver for H2, Vert.x JDBC client is used for H2 database access.

The purpose of develop this extension is to speed up testing with in memory H2 database on developer's workstation or build server. Even though the code base is about 90% same with quarkus reactive mysql client, it's not suggested being used in product environments.

As [Clement Escoffier](https://github.com/cescoffier) had point out in [#20471](https://github.com/quarkusio/quarkus/issues/20471#issuecomment-1386577186): The problem with the reactive API using a JDBC driver is the heavy usage of worker threads. Everything goes to a worker thread making such kind of solution expensive and slow. It also limits the concurrency and may use the worker threads for a long time (which means that the rest of the system may have to wait to be executed). Once you accept these limitations, sure, you can use that approach. But don't expect any benefits.

Please pick corresponding version with Quarkus version:

|Quarkus|Reactive H2 Client|
|---|---|
|2|0.1.x|
|3|0.2.x|

Notice: DevService not supported yet.

## Documentation

The documentation for this extension should be maintained as part of this repository and it is stored in the `docs/` directory.

The layout should follow the [Antora's Standard File and Directory Set](https://docs.antora.org/antora/2.3/standard-directories/).

Once the docs are ready to be published, please open a PR including this repository in the [Quarkiverse Docs Antora playbook](https://github.com/quarkiverse/quarkiverse-docs/blob/main/antora-playbook.yml#L7). See an example [here](https://github.com/quarkiverse/quarkiverse-docs/pull/1).

## Contributors ✨

Thanks goes to these wonderful people ([emoji key](https://allcontributors.org/docs/en/emoji-key)):

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/benstonezhang"><img src="https://avatars.githubusercontent.com/u/9375502?v=4?s=100" width="100px;" alt="Benstone Zhang"/><br /><sub><b>Benstone Zhang</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-reactive-h2-client/commits?author=benstonezhang" title="Code">💻</a> <a href="#maintenance-benstonezhang" title="Maintenance">🚧</a></td>
    </tr>
  </tbody>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

This project follows the [all-contributors](https://github.com/all-contributors/all-contributors) specification. Contributions of any kind welcome!
