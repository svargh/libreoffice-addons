package org.libreoffice.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.star.beans.XPropertySet;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.sun.star.lang.Locale;
import com.sun.star.lang.XServiceInfo;
import com.sun.star.lang.XServiceName;
import com.sun.star.lang.XMultiServiceFactory;
import com.sun.star.lang.XSingleServiceFactory;
import com.sun.star.lang.XSingleComponentFactory;
import com.sun.star.lib.uno.helper.Factory;
import com.sun.star.comp.loader.FactoryHelper;
import com.sun.star.lib.uno.helper.WeakBase;
import com.sun.star.registry.XRegistryKey;
import com.sun.star.sheet.XAddIn;
import com.sun.star.uno.XComponentContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * LibreOffice Calc Add-In implementation.
 *
 * The UNO/Calc function methods are intentionally upper-case, matching the
 * function names in the IDL and AddIn.xcu files. Helper methods stay normal
 * lower camelCase Java.
 */
public class LibreOfficeRestAddIn extends WeakBase
        implements XServiceInfo, XServiceName, XAddIn, XLibreOfficeRest {

    static String IMPLEMENTATION_NAME = LibreOfficeRestAddIn.class.getName();
    static String ADDIN_SERVICE_NAME = "com.sun.star.sheet.AddIn";
    static String REST_SERVICE_NAME = "org.libreoffice.rest.LibreOfficeRest";
    static String[] SERVICE_NAMES = {REST_SERVICE_NAME, ADDIN_SERVICE_NAME};

    static String VERSION = "0.1.0-SNAPSHOT";
    static Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    static int MAX_IN_MEMORY_BYTES = 16 * 1024 * 1024;

    static Map<String, FunctionInfo> FUNCTIONS = buildFunctionInfo();
    static volatile String lastError = "";

    @SuppressWarnings("unused")
    private XComponentContext context;
    private Locale locale = new Locale("en", "", "");

    public LibreOfficeRestAddIn() {
        this(null);
    }

    public LibreOfficeRestAddIn(XComponentContext context) {
        this.context = context;
        debugLog("constructor " + IMPLEMENTATION_NAME);
    }

    /**
     * Entry point used by LibreOffice's Java loader for Java UNO components.
     *
     * The official LibreOffice CalcAddins Java example still exposes this
     * legacy factory method. Keeping it here avoids the classic situation
     * where AddIn.xcu is installed and autocomplete works, but CreateUnoService
     * and Calc formulas fail with "illegal object given" / #VALUE!.
     */
    public static XSingleServiceFactory __getServiceFactory(String implementationName,
                                                             XMultiServiceFactory serviceManager,
                                                             XRegistryKey registryKey) {
        if (IMPLEMENTATION_NAME.equals(implementationName)) {
            debugLog("__getServiceFactory " + implementationName);
            return FactoryHelper.getServiceFactory(LibreOfficeRestAddIn.class, REST_SERVICE_NAME, serviceManager, registryKey);
        }
        return null;
    }

    public static XSingleComponentFactory __getComponentFactory(String implementationName) {
        if (IMPLEMENTATION_NAME.equals(implementationName)) {
            return Factory.createComponentFactory(LibreOfficeRestAddIn.class, SERVICE_NAMES);
        }
        return null;
    }

    public static boolean __writeRegistryServiceInfo(XRegistryKey registryKey) {
        return Factory.writeRegistryServiceInfo(IMPLEMENTATION_NAME, SERVICE_NAMES, registryKey);
    }

    // ---- Calc functions exposed through XLibreOfficeRest -------------------

    @Override
    public String PING(XPropertySet xOptions) {
        debugLog("PING called");
        clearLastError();
        return "LibreOfficeRest OK";
    }

    @Override
    public String RESTINFO(XPropertySet xOptions) {
        clearLastError();
        return "LibreOfficeRest " + VERSION
                + "; implementation=" + IMPLEMENTATION_NAME
                + "; service=" + REST_SERVICE_NAME
                + "; java=" + javaRuntimeVersion()
                + "; debugLog=" + nullToEmpty(System.getenv("LIBREOFFICE_REST_DEBUG_LOG"))
                + "; classLoader=" + safeClassLoaderName();
    }

    @Override
    public String JAVAVERSION(XPropertySet xOptions) {
        clearLastError();
        return javaRuntimeVersion();
    }

    @Override
    public String LASTERROR(XPropertySet xOptions) {
        return lastError == null ? "" : lastError;
    }

    @Override
    public String HTTPGET(XPropertySet xOptions, String url) {
        return callString("HTTPGET", () -> doHttpGet(url, Collections.emptyMap()));
    }

    @Override
    public String HTTPGETREFRESH(XPropertySet xOptions, String url, Object refreshKey) {
        debugLog("HTTPGETREFRESH refreshKey=" + nullToEmpty(String.valueOf(refreshKey)));
        return callString("HTTPGETREFRESH", () -> doHttpGet(url, Collections.emptyMap()));
    }

    @Override
    public String HTTPGETDEBUG(XPropertySet xOptions, String url) {
        try {
            String body = withExtensionClassLoader(() -> doHttpGet(url, Collections.emptyMap()));
            clearLastError();
            String preview = abbreviate(body == null ? "" : body, 240);
            return "OK length=" + (body == null ? 0 : body.length())
                    + "; java=" + javaRuntimeVersion()
                    + "; preview=" + preview;
        } catch (Throwable ex) {
            String message = recordError("HTTPGETDEBUG", ex);
            return "#LibreOfficeRest ERROR in HTTPGETDEBUG: " + message
                    + "; java=" + javaRuntimeVersion()
                    + "; classLoader=" + safeClassLoaderName();
        }
    }

    @Override
    public String HTTPGETHEADER(XPropertySet xOptions, String url, String headerName, String headerValue) {
        return callString("HTTPGETHEADER", () -> doHttpGet(url, singletonHeader(headerName, headerValue)));
    }

    @Override
    public String HTTPGETHEADERS(XPropertySet xOptions, String url, String headers) {
        return callString("HTTPGETHEADERS", () -> doHttpGet(url, parseHeaders(headers)));
    }

    @Override
    public String HTTPPOST(XPropertySet xOptions, String url, String body, String contentType) {
        return callString("HTTPPOST", () -> doHttpPost(url, body, contentType, Collections.emptyMap()));
    }

    @Override
    public String HTTPPOSTHEADER(XPropertySet xOptions, String url, String body, String contentType,
                                 String headerName, String headerValue) {
        return callString("HTTPPOSTHEADER",
                () -> doHttpPost(url, body, contentType, singletonHeader(headerName, headerValue)));
    }

    @Override
    public String JSONPATH(XPropertySet xOptions, String json, String path) {
        return callString("JSONPATH", () -> valueToString(readJsonPath(json, path)));
    }

    @Override
    public double JSONNUMBER(XPropertySet xOptions, String json, String path) {
        return callDouble("JSONNUMBER", () -> valueToDouble(readJsonPath(json, path)));
    }

    @Override
    public double JSONBOOL(XPropertySet xOptions, String json, String path) {
        return callDouble("JSONBOOL", () -> valueToBoolean(readJsonPath(json, path)) ? 1.0d : 0.0d);
    }

    @Override
    public double JSONVALID(XPropertySet xOptions, String json) {
        debugLog("JSONVALID called length=" + (json == null ? 0 : json.length()));
        try {
            withExtensionClassLoader(() -> {
                parseStrictJson(json);
                return Boolean.TRUE;
            });
            clearLastError();
            return 1.0d;
        } catch (Throwable ex) {
            recordError("JSONVALID", ex);
            return 0.0d;
        }
    }

    @Override
    public String URLENCODE(XPropertySet xOptions, String text) {
        return callString("URLENCODE", () -> URLEncoder.encode(nullToEmpty(text), StandardCharsets.UTF_8));
    }

    // ---- HTTP ---------------------------------------------------------------

    private static String doHttpGet(String url, Map<String, String> headers) {
        URI uri = checkedHttpUri(url);
        String response = HttpHolder.WEB_CLIENT.get()
                .uri(uri)
                .headers(httpHeaders -> applyHeaders(httpHeaders, headers))
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(responseBody -> new IllegalArgumentException("HTTP " + clientResponse.statusCode().value()
                                + " from " + uri + (responseBody.isBlank() ? "" : ": " + abbreviate(responseBody, 500)))))
                .bodyToMono(String.class)
                .block(REQUEST_TIMEOUT);
        return response == null ? "" : response;
    }

    private static String doHttpPost(String url, String body, String contentType, Map<String, String> headers) {
        URI uri = checkedHttpUri(url);
        MediaType mediaType = isBlank(contentType)
                ? MediaType.APPLICATION_JSON
                : MediaType.parseMediaType(contentType.trim());

        String response = HttpHolder.WEB_CLIENT.post()
                .uri(uri)
                .headers(httpHeaders -> applyHeaders(httpHeaders, headers))
                .contentType(mediaType)
                .bodyValue(nullToEmpty(body))
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(responseBody -> new IllegalArgumentException("HTTP " + clientResponse.statusCode().value()
                                + " from " + uri + (responseBody.isBlank() ? "" : ": " + abbreviate(responseBody, 500)))))
                .bodyToMono(String.class)
                .block(REQUEST_TIMEOUT);
        return response == null ? "" : response;
    }

    private static URI checkedHttpUri(String url) {
        if (isBlank(url)) {
            throw new IllegalArgumentException("URL is empty");
        }
        URI uri = URI.create(url.trim());
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Only http:// and https:// URLs are supported: " + url);
        }
        return uri;
    }

    private static Map<String, String> singletonHeader(String headerName, String headerValue) {
        if (isBlank(headerName)) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(headerName.trim(), nullToEmpty(headerValue));
        return headers;
    }

    static Map<String, String> parseHeaders(String headersText) {
        if (isBlank(headersText)) {
            return Collections.emptyMap();
        }

        String normalized = headersText.replace("\r\n", "\n").replace('\r', '\n');
        String[] entries = normalized.contains("\n") ? normalized.split("\n") : normalized.split(";");
        Map<String, String> headers = new LinkedHashMap<>();

        for (String rawEntry : entries) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                continue;
            }

            int equals = entry.indexOf('=');
            int colon = entry.indexOf(':');
            int separator;
            if (equals < 0) {
                separator = colon;
            } else if (colon < 0) {
                separator = equals;
            } else {
                separator = Math.min(equals, colon);
            }

            if (separator <= 0) {
                throw new IllegalArgumentException("Invalid header entry: " + entry);
            }

            String name = entry.substring(0, separator).trim();
            String value = entry.substring(separator + 1).trim();
            if (!name.isEmpty()) {
                headers.put(name, value);
            }
        }
        return headers;
    }

    private static void applyHeaders(HttpHeaders httpHeaders, Map<String, String> headers) {
        Objects.requireNonNull(httpHeaders, "httpHeaders");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (!isBlank(entry.getKey())) {
                httpHeaders.set(entry.getKey().trim(), nullToEmpty(entry.getValue()));
            }
        }
    }

    private static class HttpHolder {
        static WebClient WEB_CLIENT = WebClient.builder()
                .clientConnector(new JdkClientHttpConnector(
                        HttpClient.newBuilder()
                                .connectTimeout(CONNECT_TIMEOUT)
                                .build()))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();

        private HttpHolder() {
        }
    }

    // ---- JSON ---------------------------------------------------------------

    private static Object readJsonPath(String json, String path) {
        if (isBlank(path)) {
            throw new IllegalArgumentException("JSONPath is empty");
        }

        Object parsedJson = parseStrictJson(json);
        return JsonPath.using(JsonHolder.JSON_CONFIG).parse(parsedJson).read(path);
    }

    private static Object parseStrictJson(String json) {
        String jsonText = requireJsonText(json);
        try {
            return JsonHolder.OBJECT_MAPPER.readValue(jsonText, Object.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid JSON text", ex);
        }
    }

    private static String requireJsonText(String json) {
        if (isBlank(json)) {
            throw new IllegalArgumentException("JSON text is empty");
        }
        return json.trim();
    }

    private static String valueToString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        try {
            return JsonHolder.OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Cannot serialize JSONPath value", ex);
        }
    }

    private static double valueToDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            return Double.parseDouble(text.trim());
        }
        throw new IllegalArgumentException("JSONPath value is not numeric: " + valueToString(value));
    }

    private static boolean valueToBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase(java.util.Locale.ROOT);
            if ("true".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized)) {
                return false;
            }
        }
        throw new IllegalArgumentException("JSONPath value is not boolean: " + valueToString(value));
    }

    private static class JsonHolder {
        static Configuration JSON_CONFIG = Configuration.defaultConfiguration();
        static ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

        private JsonHolder() {
        }
    }

    // ---- Error handling -----------------------------------------------------

    private static String callString(String functionName, Supplier<String> supplier) {
        debugLog(functionName + " called");
        try {
            String value = withExtensionClassLoader(supplier);
            clearLastError();
            return value == null ? "" : value;
        } catch (Throwable ex) {
            String message = recordError(functionName, ex);
            return "#LibreOfficeRest ERROR in " + functionName + ": " + message;
        }
    }

    private static double callDouble(String functionName, Supplier<Double> supplier) {
        debugLog(functionName + " called");
        try {
            Double value = withExtensionClassLoader(supplier);
            clearLastError();
            return value == null ? Double.NaN : value;
        } catch (Throwable ex) {
            recordError(functionName, ex);
            return Double.NaN;
        }
    }

    private static <T> T withExtensionClassLoader(Supplier<T> supplier) {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(LibreOfficeRestAddIn.class.getClassLoader());
        try {
            return supplier.get();
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private static String recordError(String functionName, Throwable throwable) {
        Throwable root = rootCause(throwable);
        String message = root.getClass().getName();
        if (!isBlank(root.getMessage())) {
            message += ": " + root.getMessage();
        }
        lastError = functionName + ": " + message;
        debugLog("ERROR " + lastError);
        return message;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable == null ? new NullPointerException("throwable") : throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static void clearLastError() {
        lastError = "";
    }

    // ---- XAddIn metadata ----------------------------------------------------

    @Override
    public String getProgrammaticFuntionName(String displayName) {
        FunctionInfo info = findFunction(displayName);
        return info == null ? normalizeFunctionName(displayName) : info.programmaticName;
    }

    @Override
    public String getDisplayFunctionName(String programmaticName) {
        FunctionInfo info = findFunction(programmaticName);
        return info == null ? programmaticName : info.displayName;
    }

    @Override
    public String getFunctionDescription(String programmaticName) {
        FunctionInfo info = findFunction(programmaticName);
        return info == null ? "" : info.description;
    }

    @Override
    public String getDisplayArgumentName(String programmaticFunctionName, int argument) {
        FunctionInfo info = findFunction(programmaticFunctionName);
        if (info == null || argument < 0) {
            return "";
        }
        if (argument == 0) {
            return "(internal)";
        }
        int visibleArgument = argument - 1;
        if (visibleArgument >= info.argumentNames.length) {
            return "";
        }
        return info.argumentNames[visibleArgument];
    }

    @Override
    public String getArgumentDescription(String programmaticFunctionName, int argument) {
        FunctionInfo info = findFunction(programmaticFunctionName);
        if (info == null || argument < 0) {
            return "";
        }
        if (argument == 0) {
            return "Internal Calc document options supplied by LibreOffice.";
        }
        int visibleArgument = argument - 1;
        if (visibleArgument >= info.argumentDescriptions.length) {
            return "";
        }
        return info.argumentDescriptions[visibleArgument];
    }

    @Override
    public String getProgrammaticCategoryName(String programmaticFunctionName) {
        return "Add-In";
    }

    @Override
    public String getDisplayCategoryName(String programmaticFunctionName) {
        return "LibreOfficeRest";
    }

    @Override
    public void setLocale(Locale locale) {
        this.locale = locale == null ? new Locale("en", "", "") : locale;
    }

    @Override
    public Locale getLocale() {
        return locale;
    }

    // ---- XServiceName -------------------------------------------------------

    @Override
    public String getServiceName() {
        return REST_SERVICE_NAME;
    }

    // ---- XServiceInfo -------------------------------------------------------

    @Override
    public String getImplementationName() {
        return IMPLEMENTATION_NAME;
    }

    @Override
    public boolean supportsService(String serviceName) {
        for (String service : SERVICE_NAMES) {
            if (service.equals(serviceName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String[] getSupportedServiceNames() {
        return SERVICE_NAMES.clone();
    }

    private static FunctionInfo findFunction(String name) {
        return FUNCTIONS.get(normalizeFunctionName(name));
    }

    private static String normalizeFunctionName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        int lastDot = trimmed.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < trimmed.length()) {
            trimmed = trimmed.substring(lastDot + 1);
        }
        return trimmed.replace("_", "").toUpperCase(java.util.Locale.ROOT);
    }

    private static Map<String, FunctionInfo> buildFunctionInfo() {
        Map<String, FunctionInfo> functions = new LinkedHashMap<>();
        add(functions, "PING",
                "Returns a simple diagnostic string if the LibreOfficeRest Java add-in can be loaded and called.",
                new String[]{},
                new String[]{});
        add(functions, "RESTINFO",
                "Returns diagnostic information without touching Spring WebClient or JSON dependencies.",
                new String[]{},
                new String[]{});
        add(functions, "JAVAVERSION",
                "Returns the Java runtime version used by LibreOffice for this add-in.",
                new String[]{},
                new String[]{});
        add(functions, "LASTERROR",
                "Returns the last LibreOfficeRest error message captured by a function call.",
                new String[]{},
                new String[]{});
        add(functions, "HTTPGET",
                "Performs an HTTP GET request and returns the response body as text.",
                new String[]{"URL"},
                new String[]{"HTTP or HTTPS URL to request."});
        add(functions, "HTTPGETREFRESH",
                "Performs an HTTP GET request and includes a dummy refresh key argument so Calc can force immediate recalculation.",
                new String[]{"URL", "RefreshKey"},
                new String[]{"HTTP or HTTPS URL to request.", "Any value used only as a dependency trigger, for example NOW(), RAND(), or a counter cell."});
        add(functions, "HTTPGETDEBUG",
                "Performs an HTTP GET request and returns diagnostic status plus a response preview.",
                new String[]{"URL"},
                new String[]{"HTTP or HTTPS URL to request."});
        add(functions, "HTTPGETHEADER",
                "Performs an HTTP GET request with one custom header.",
                new String[]{"URL", "HeaderName", "HeaderValue"},
                new String[]{"HTTP or HTTPS URL to request.", "Header name, for example Authorization.", "Header value, for example Bearer token."});
        add(functions, "HTTPGETHEADERS",
                "Performs an HTTP GET request with headers given as name=value lines or semicolon-separated entries.",
                new String[]{"URL", "Headers"},
                new String[]{"HTTP or HTTPS URL to request.", "Headers as Name=Value lines, Name: Value lines, or semicolon-separated entries."});
        add(functions, "HTTPPOST",
                "Performs an HTTP POST request and returns the response body as text.",
                new String[]{"URL", "Body", "ContentType"},
                new String[]{"HTTP or HTTPS URL to request.", "Request body text.", "Content type, for example application/json."});
        add(functions, "HTTPPOSTHEADER",
                "Performs an HTTP POST request with one custom header.",
                new String[]{"URL", "Body", "ContentType", "HeaderName", "HeaderValue"},
                new String[]{"HTTP or HTTPS URL to request.", "Request body text.", "Content type, for example application/json.", "Header name.", "Header value."});
        add(functions, "JSONPATH",
                "Evaluates a JSONPath expression and returns the result as text.",
                new String[]{"JSON", "Path"},
                new String[]{"JSON text.", "JSONPath expression, for example $.result.XXBTZEUR.c[0]."});
        add(functions, "JSONNUMBER",
                "Evaluates a JSONPath expression and returns the result as a number.",
                new String[]{"JSON", "Path"},
                new String[]{"JSON text.", "JSONPath expression whose result is numeric."});
        add(functions, "JSONBOOL",
                "Evaluates a JSONPath expression and returns 1 for true or 0 for false.",
                new String[]{"JSON", "Path"},
                new String[]{"JSON text.", "JSONPath expression whose result is boolean-like."});
        add(functions, "JSONVALID",
                "Returns 1 if the text is valid JSON and 0 otherwise.",
                new String[]{"JSON"},
                new String[]{"JSON text to validate."});
        add(functions, "URLENCODE",
                "URL-encodes text using UTF-8.",
                new String[]{"Text"},
                new String[]{"Text to encode for URL query parameters."});
        return Collections.unmodifiableMap(functions);
    }

    private static void add(Map<String, FunctionInfo> functions, String functionName,
                            String description, String[] argumentNames, String[] argumentDescriptions) {
        FunctionInfo info = new FunctionInfo(functionName, functionName, description, argumentNames, argumentDescriptions);
        functions.put(normalizeFunctionName(functionName), info);
    }

    private record FunctionInfo(String programmaticName,
                                String displayName,
                                String description,
                                String[] argumentNames,
                                String[] argumentDescriptions) {
    }

    // ---- Optional file debug -------------------------------------------------

    private static void debugLog(String message) {
        String logPath = System.getenv("LIBREOFFICE_REST_DEBUG_LOG");
        if (isBlank(logPath)) {
            return;
        }
        try {
            String line = Instant.now() + " " + nullToEmpty(message) + System.lineSeparator();
            Files.writeString(Path.of(logPath), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
            // Diagnostics must not break a Calc formula.
        }
    }

    // ---- Small helpers ------------------------------------------------------

    private static String javaRuntimeVersion() {
        return System.getProperty("java.runtime.version", System.getProperty("java.version", "unknown"));
    }

    private static String safeClassLoaderName() {
        ClassLoader loader = LibreOfficeRestAddIn.class.getClassLoader();
        return loader == null ? "bootstrap" : loader.getClass().getName();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String abbreviate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String compact = text.replace('\n', ' ').replace('\r', ' ');
        return compact.length() <= maxLength ? compact : compact.substring(0, maxLength) + "...";
    }
}
