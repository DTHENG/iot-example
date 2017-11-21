package com.dtheng.iotexample;

import com.pi4j.io.gpio.GpioPinDigitalOutput;
import rx.Observable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Daniel Thengvall <fender5289@gmail.com>
 */
public class Utils {

    public static List<List<Boolean>> numberToBinarySequence = Arrays.asList(
            Arrays.asList(false, false, false, false),
            Arrays.asList(false, false, false, true),
            Arrays.asList(false, false, true, false),
            Arrays.asList(false, false, true, true),
            Arrays.asList(false, true, false, false),
            Arrays.asList(false, true, false, true),
            Arrays.asList(false, true, true, false),
            Arrays.asList(false, true, true, true),
            Arrays.asList(true, false, false, false),
            Arrays.asList(true, false, false, true));

    public static Observable<Integer> buildExchangeRateArray(Double rate, int numberOfLights) {
        return Observable.just(rate)
                .map(Math::round)
                .map(Double::toString)
                .map(asString -> {
                    if ( ! asString.contains("."))
                        return asString;
                    return asString.substring(0, asString.indexOf("."));
                })
                .map(string -> string.split("(?!^)"))
                .flatMap(list -> {
                    if (list.length > numberOfLights)
                        throw new RuntimeException("Too many digits!");
                    List<String> adjustedList = new ArrayList<>();
                    for (int i = 0; i < numberOfLights - list.length; i++)
                        adjustedList.add("0");
                    Collections.addAll(adjustedList, list);
                    return Observable.from(adjustedList.toArray(new String[adjustedList.size()]));
                })
                .map(Integer::valueOf);
    }

    public static Observable<Void> blink(GpioPinDigitalOutput digitalOutput, int speedInMs) {
        return Observable.just(null)
                .doOnNext(Void -> digitalOutput.setState(true))
                .delay((speedInMs / 3) * 2, TimeUnit.MILLISECONDS)
                .doOnNext(Void -> digitalOutput.setState(false))
                .delay(speedInMs / 3, TimeUnit.MILLISECONDS)
                .ignoreElements().cast(Void.class);
    }
}
