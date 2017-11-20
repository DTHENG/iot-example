package com.dtheng.iotexample;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import retrofit.RestAdapter;

import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertTrue;

/**
 * @author Daniel Thengvall <fender5289@gmail.com>
 */
@Slf4j
public class WyreRatesServiceTest {

    private WyreRatesService service;

    @Before
    public void setUp() throws Exception {

        RestAdapter retrofit = new RestAdapter.Builder()
                .setEndpoint("https://api.sendwyre.com/")
                .build();

        service = retrofit.create(WyreRatesService.class);
    }

    @Test
    public void testRates() throws Exception {
        Map<String, Double> rates = service.rates().toBlocking().single();

        log.info(rates.toString());

        Optional<Double> usdbtcRate = Optional.ofNullable(rates.get("USDBTC"));

        log.info(usdbtcRate.toString());

        assertTrue(usdbtcRate.isPresent());
    }
}
