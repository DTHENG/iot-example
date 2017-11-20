package com.dtheng.iotexample;

import retrofit.http.GET;
import rx.Observable;

import java.util.Map;

/**
 * @author Daniel Thengvall <fender5289@gmail.com>
 */
public interface WyreRatesService {

    @GET("/v2/rates")
    Observable<Map<String, Double>> rates();
}
