package org.libreoffice.rest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real network integration test. It calls Kraken's public ticker endpoint.
 *
 * Run with:
 *   mvn verify
 *
 * Skip with:
 *   mvn verify -DskipITs=true
 */
class AddinTestIT {
    private LibreOfficeRestAddIn addIn = new LibreOfficeRestAddIn(null);

    @Test
    void connectsToKrakenTickerAndReadsBtcEurLastPrice() {
        String url = "https://api.kraken.com/0/public/Ticker?pair=XBTEUR";

        String json = addIn.HTTPGET(null, url);
        assertFalse(json.startsWith("#LibreOfficeRest ERROR"), addIn.LASTERROR(null));

        String refreshedJson = addIn.HTTPGETREFRESH(null, url, System.currentTimeMillis());
        assertFalse(refreshedJson.startsWith("#LibreOfficeRest ERROR"), addIn.LASTERROR(null));
        assertTrue(refreshedJson.contains("\"result\""), refreshedJson);
        assertTrue(json.contains("\"result\""), json);
        assertEquals(1.0d, addIn.JSONVALID(null, json), 0.0d, addIn.LASTERROR(null));

        String lastPriceText = addIn.JSONPATH(null, json, "$.result.XXBTZEUR.c[0]");
        assertFalse(lastPriceText.startsWith("#LibreOfficeRest ERROR"), addIn.LASTERROR(null));

        double lastPrice = addIn.JSONNUMBER(null, json, "$.result.XXBTZEUR.c[0]");
        assertTrue(lastPrice > 0.0d, "Expected positive Kraken BTC/EUR last price but got " + lastPrice);
    }
}
