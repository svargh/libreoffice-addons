# LibreOfficeRest

LibreOffice Calc Add-In for REST APIs and JSONPath extraction.

## Use it in Calc

Example BTC/EUR ticker:

```calc
A1 = HTTPGETREFRESH("https://api.kraken.com/0/public/Ticker?pair=XBTEUR";RAND())
B1 = JSONNUMBER(A1;"$.result.XXBTZEUR.c[0]")
C1 = JSONVALID(A1)
D1 = LASTERROR()
```

Useful functions:

- `HTTPGET(url)` — GET URL and return response text.
- `HTTPGETREFRESH(url; refreshKey)` — GET URL and refresh when `refreshKey` changes.
- `HTTPGETHEADER(url; name; value)` — GET with one header.
- `HTTPGETHEADERS(url; headers)` — GET with headers like `Authorization=Bearer x;X-Key=abc`.
- `HTTPPOST(url; body; contentType)` — POST body.
- `JSONPATH(json; path)` — JSONPath result as text.
- `JSONNUMBER(json; path)` — JSONPath result as number.
- `JSONVALID(json)` — `1` for valid JSON, `0` for invalid JSON.
- `LASTERROR()` — last Java-side error.
- `PING()` — simple load test.

Example sheet:

```text
LibreOfficeRest/examples/example-restapi.ods
```

The VNC test container opens this file automatically after installing the built `.oxt`.

## Download binary from GitHub

- Push to `test`: build and test.
- Push to `stable`: build, test, and publish `LibreOfficeRest.oxt` to the `libreoffice-rest-latest` release.
- Download URL after publishing:
  - `https://github.com/<owner>/<repo>/releases/download/libreoffice-rest-latest/LibreOfficeRest.oxt`

Install with Extension Manager or:

```bash
unopkg add LibreOfficeRest.oxt
```

Flatpak LibreOffice:

```bash
flatpak run --command=unopkg org.libreoffice.LibreOffice add LibreOfficeRest.oxt
```

Requirements:

- Java 17+ selected in LibreOffice.
- Tested with LibreOffice 24.x and 26.x.

## Build yourself

From repo root:

```bash
./LibreOfficeRest/tools/build-libreoffice-rest-docker.sh
```

Default build:

```text
mvn -Puno-sdk-build clean verify -DskipITs=true
```

Output:

```text
LibreOfficeRest/target/LibreOfficeRest-*.oxt
```

Run Kraken integration tests too:

```bash
./LibreOfficeRest/tools/build-libreoffice-rest-docker.sh clean verify -DskipITs=false
```

The Docker build runs as your host user and uses:

```text
.m2-docker/
```

If old Docker runs left root-owned files:

```bash
./LibreOfficeRest/tools/build-libreoffice-rest-docker.sh fix-permissions
```

Quick IntelliJ/local unit tests after the dev interface exists:

```bash
cd LibreOfficeRest
mvn test -DskipITs=true
```

The checked-in dev mirror is:

```text
src-generated/java/org/libreoffice/rest/XLibreOfficeRest.java
```

## GUI/VNC test container

After building:

```bash
./LibreOfficeRest/tools/run-libreoffice-rest-gui-vnc.sh
```

Open:

```text
http://localhost:6080/vnc.html
```

The container:

- installs LibreOffice 26.2.3 from the hardcoded Document Foundation `.deb.tar.gz` URL;
- verifies the matching `.asc` signature before installing;
- runs with `-u "$(id -u):$(id -g)"`;
- mounts the built `.oxt` read-only;
- installs the extension inside the container-only LibreOffice profile;
- opens `examples/example-restapi.ods` directly.

Pass an explicit `.oxt` if needed:

```bash
./LibreOfficeRest/tools/run-libreoffice-rest-gui-vnc.sh LibreOfficeRest/target/LibreOfficeRest-1.2026.05.12.oxt
```
