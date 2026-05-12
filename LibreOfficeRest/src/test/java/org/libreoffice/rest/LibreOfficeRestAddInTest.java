package org.libreoffice.rest;

import com.sun.star.beans.XPropertySet;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibreOfficeRestAddInTest {
    private LibreOfficeRestAddIn addIn = new LibreOfficeRestAddIn(null);

    private static String SAMPLE_JSON = """
            {
              "result": {
                "XXBTZEUR": {"c": ["54321.12", "1.0"]},
                "flags": {"active": true, "inactive": false},
                "numbers": [1, 2, 3]
              }
            }
            """;

    @Test
    void pingIsDependencyFreeAndDoesNotSetLastError() {
        assertTrue(addIn.PING(null).startsWith("LibreOfficeRest OK"));
        assertEquals("", addIn.LASTERROR(null));
    }

    @Test
    void jsonPathReturnsScalarText() {
        assertEquals("54321.12", addIn.JSONPATH(null, SAMPLE_JSON, "$.result.XXBTZEUR.c[0]"));
        assertEquals("", addIn.LASTERROR(null));
    }

    @Test
    void jsonPathReturnsArrayAsJsonText() {
        assertEquals("[1,2,3]", addIn.JSONPATH(null, SAMPLE_JSON, "$.result.numbers"));
    }

    @Test
    void jsonPathReturnsObjectAsJsonText() {
        assertEquals("{\"active\":true,\"inactive\":false}", addIn.JSONPATH(null, SAMPLE_JSON, "$.result.flags"));
    }

    @Test
    void jsonNumberReturnsDouble() {
        assertEquals(54321.12, addIn.JSONNUMBER(null, SAMPLE_JSON, "$.result.XXBTZEUR.c[0]"), 0.000001);
    }

    @Test
    void jsonBoolReturnsSpreadsheetNumber() {
        assertEquals(1.0d, addIn.JSONBOOL(null, SAMPLE_JSON, "$.result.flags.active"), 0.0d);
        assertEquals(0.0d, addIn.JSONBOOL(null, SAMPLE_JSON, "$.result.flags.inactive"), 0.0d);
    }

    @Test
    void jsonValidDetectsValidAndInvalidJson() {
        assertEquals(1.0d, addIn.JSONVALID(null, SAMPLE_JSON), 0.0d);
        assertEquals(1.0d, addIn.JSONVALID(null, "[1, 2, 3]"), 0.0d);
        assertEquals(1.0d, addIn.JSONVALID(null, "null"), 0.0d);
        assertEquals(1.0d, addIn.JSONVALID(null, "true"), 0.0d);
        assertEquals(1.0d, addIn.JSONVALID(null, "\"valid JSON scalar string\""), 0.0d);
        assertEquals(1.0d, addIn.JSONVALID(null, "123"), 0.0d);

        assertEquals(0.0d, addIn.JSONVALID(null, "not json"), 0.0d);
        assertTrue(addIn.LASTERROR(null).contains("JSONVALID"));
        assertEquals(0.0d, addIn.JSONVALID(null, "{\"a\":1} trailing"), 0.0d);
        assertEquals(0.0d, addIn.JSONVALID(null, "{a:1}"), 0.0d);
        assertEquals(0.0d, addIn.JSONVALID(null, ""), 0.0d);
        assertEquals(0.0d, addIn.JSONVALID(null, "   "), 0.0d);
        assertEquals(0.0d, addIn.JSONVALID(null, null), 0.0d);
        assertEquals(0.0d, addIn.JSONVALID(null, "{broken"), 0.0d);
        assertEquals(0.0d, addIn.JSONVALID(null, "{} garbage"), 0.0d);
    }

    @Test
    void jsonPathReturnsSpreadsheetFriendlyErrorTextForInvalidJson() {
        String value = addIn.JSONPATH(null, "not json", "$.anything");
        assertTrue(value.startsWith("#LibreOfficeRest ERROR in JSONPATH:"));
        assertTrue(addIn.LASTERROR(null).contains("JSONPATH"));

        value = addIn.JSONPATH(null, "{\"a\":1} trailing", "$.a");
        assertTrue(value.startsWith("#LibreOfficeRest ERROR in JSONPATH:"));

        value = addIn.JSONPATH(null, "", "$.anything");
        assertTrue(value.startsWith("#LibreOfficeRest ERROR in JSONPATH:"));
    }

    @Test
    void jsonNumberReturnsNanAndRecordsErrorForNonNumericValue() {
        double value = addIn.JSONNUMBER(null, SAMPLE_JSON, "$.result.flags.active");
        assertTrue(Double.isNaN(value));
        assertTrue(addIn.LASTERROR(null).contains("JSONNUMBER"));
    }

    @Test
    void urlEncodeUsesUtf8FormEncoding() {
        assertEquals("BTC%2FEUR+%C3%A4", addIn.URLENCODE(null, "BTC/EUR ä"));
    }

    @Test
    void headersParserSupportsCommonFormats() {
        Map<String, String> headers = LibreOfficeRestAddIn.parseHeaders("Authorization=Bearer abc;X-API-Key: secret");
        assertEquals("Bearer abc", headers.get("Authorization"));
        assertEquals("secret", headers.get("X-API-Key"));

        headers = LibreOfficeRestAddIn.parseHeaders("Authorization: Bearer abc\nX-API-Key=secret");
        assertEquals("Bearer abc", headers.get("Authorization"));
        assertEquals("secret", headers.get("X-API-Key"));
    }

    @Test
    void metadataExposesUpperCaseProgrammaticNames() {
        assertEquals("org.libreoffice.rest.LibreOfficeRestAddIn", addIn.getImplementationName());
        assertTrue(addIn.supportsService("org.libreoffice.rest.LibreOfficeRest"));
        assertTrue(addIn.supportsService("com.sun.star.sheet.AddIn"));
        assertFalse(addIn.supportsService("other.Service"));

        assertEquals("HTTPGET", addIn.getProgrammaticFuntionName("HTTPGET"));
        assertEquals("JSONPATH", addIn.getProgrammaticFuntionName("JSONPATH"));
        assertEquals("HTTPGET", addIn.getDisplayFunctionName("HTTPGET"));
        assertEquals("HTTPGET", addIn.getDisplayFunctionName("httpGet"));
        assertEquals("HTTPGETREFRESH", addIn.getDisplayFunctionName("HTTPGETREFRESH"));
        assertEquals("HTTPGETDEBUG", addIn.getDisplayFunctionName("HTTPGETDEBUG"));
        assertEquals("(internal)", addIn.getDisplayArgumentName("HTTPGET", 0));
        assertEquals("URL", addIn.getDisplayArgumentName("HTTPGET", 1));
        assertEquals("RefreshKey", addIn.getDisplayArgumentName("HTTPGETREFRESH", 2));
        assertEquals("Path", addIn.getDisplayArgumentName("JSONPATH", 2));
        assertEquals("org.libreoffice.rest.LibreOfficeRest", addIn.getServiceName());
        assertTrue(addIn.RESTINFO(null).contains("LibreOfficeRest"));
    }

    @Test
    void idlGeneratedUnoInterfaceAndXcuUseUpperCaseFunctionNames() throws Exception {
        String idl = Files.readString(Path.of("src/main/idl/org/libreoffice/rest/XLibreOfficeRest.idl"));
        String xcu = Files.readString(Path.of("src/main/oxt/AddIn.xcu"));
        String components = Files.readString(Path.of("src/main/oxt/LibreOfficeRest.components"));
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(idl.contains("string HTTPGET("));
        assertTrue(idl.contains("string HTTPGETREFRESH("));
        assertTrue(idl.contains("[in] any refreshKey"));
        assertTrue(idl.contains("double JSONVALID("));
        assertTrue(idl.contains("XPropertySet xOptions"));

        assertEquals(String.class, XLibreOfficeRest.class.getMethod("HTTPGET", XPropertySet.class, String.class).getReturnType());
        assertEquals(String.class, XLibreOfficeRest.class.getMethod("HTTPGETREFRESH", XPropertySet.class, String.class, Object.class).getReturnType());
        assertEquals(double.class, XLibreOfficeRest.class.getMethod("JSONVALID", XPropertySet.class, String.class).getReturnType());
        assertFalse(Files.exists(Path.of("src/main/java/org/libreoffice/rest/XLibreOfficeRest.java")),
                "Do not put the UNO interface mirror under src/main/java.");
        String devInterface = Files.readString(Path.of("src-generated/java/org/libreoffice/rest/XLibreOfficeRest.java"));
        assertTrue(devInterface.contains("interface XLibreOfficeRest"));
        assertTrue(devInterface.contains("HTTPGET("));
        assertTrue(devInterface.contains("HTTPGETREFRESH("));
        assertTrue(devInterface.contains("JSONVALID("));

        assertTrue(xcu.contains("oor:name=\"HTTPGET\""));
        assertTrue(xcu.contains("oor:name=\"HTTPGETREFRESH\""));
        assertTrue(xcu.contains("oor:name=\"JSONPATH\""));
        assertFalse(xcu.contains("oor:name=\"httpGet\""));
        assertFalse(xcu.contains("oor:name=\"jsonPath\""));

        assertTrue(components.contains("loader=\"com.sun.star.loader.Java2\""));
        assertTrue(components.contains("service name=\"org.libreoffice.rest.LibreOfficeRest\""));
        assertTrue(components.contains("service name=\"com.sun.star.sheet.AddIn\""));
        assertTrue(pom.contains("<RegistrationClassName>org.libreoffice.rest.LibreOfficeRestAddIn</RegistrationClassName>"));
        assertTrue(pom.contains("<UNO-Type-Path>LibreOfficeRest.jar</UNO-Type-Path>"));
        assertTrue(pom.contains("<artifactId>cfr</artifactId>"));
        assertTrue(pom.contains("org.benf.cfr.reader.Main"));
    }

    @Test
    void mainAddInSourceAvoidsFinalKeywordStyle() throws Exception {
        String javaSource = Files.readString(Path.of("src/main/java/org/libreoffice/rest/LibreOfficeRestAddIn.java"));
        assertFalse(javaSource.matches("(?s).*\\bfinal\\b.*"));
    }
}
