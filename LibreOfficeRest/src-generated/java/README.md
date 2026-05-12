# Generated development source

`org/libreoffice/rest/XLibreOfficeRest.java` is a checked-in development mirror of the LibreOffice SDK generated UNO interface.

LibreOfficeRestAddIn implements this interface.

**This interface not needed for creating the artifact,  
but it ensures that wrong cell functions in LibreOfficeRestAddIn are found at compile-time  
and not at runtime after installing extension and opening the ods file.**



It is regenerated after each docker script call:

```bash
./LibreOfficeRest/tools/build-libreoffice-rest-docker.sh generate-sources
```

Docker/Maven uses the LibreOffice SDK to create the real `XLibreOfficeRest.class`, then CFR writes the dev mirror into this folder.   
The real `.oxt` build still uses LibreOffice SDK `javamaker` and `LibreOfficeRest.rdb`.


