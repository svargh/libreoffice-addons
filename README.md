# libreoffice-addons

Small AI generated LibreOffice add-ons collected in one repository.


## Quick download

- Latest stable LibreOfficeRest binary:
  - `https://github.com/<owner>/<repo>/releases/download/libreoffice-rest-latest/LibreOfficeRest.oxt`
- Replace `<owner>/<repo>` with your GitHub repository path.
- Pushes to `stable` update the `libreoffice-rest-latest` release.

# Info
Currently included:

- `LibreOfficeRest/` — Calc Java Add-In for REST calls using SpringWebclient and JSONPath extraction.  
  Generated using ChatGPT Pro - Extended Thinking

More add-ons or non-Maven tools can be added as sibling folders later.

Build the current add-on:

```bash
./LibreOfficeRest/tools/build-libreoffice-rest-docker.sh
```

The root directory intentionally has no Maven `pom.xml` and no parent/module setup.
