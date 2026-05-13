# Notices

LibreOfficeRest bundles third-party dependencies into the extension JAR at build time.

Main dependencies:

- Spring Framework WebFlux / WebClient, Apache License 2.0
- Jayway JsonPath, Apache License 2.0
- Jackson Databind/Core/Annotations, Apache License 2.0
- SLF4J NOP provider, MIT License

LibreOffice UNO Java classes are compile-time `provided` dependencies and are not bundled into the shaded extension JAR.
