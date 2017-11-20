package com.dtheng.iotexample;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.RaspiPin;
import lombok.extern.slf4j.Slf4j;
import retrofit.RestAdapter;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author Daniel Thengvall <fender5289@gmail.com>
 */
@Slf4j
public class BitcoinLedExample {

    private static final int numberOfLights = 5;

    private static final GpioController controller = GpioFactory.getInstance();
    private static final GpioPinDigitalOutput redLightOne = controller.provisionDigitalOutputPin(RaspiPin.GPIO_00);
    private static final GpioPinDigitalOutput redLightTwo = controller.provisionDigitalOutputPin(RaspiPin.GPIO_01);
    private static final GpioPinDigitalOutput redLightThree = controller.provisionDigitalOutputPin(RaspiPin.GPIO_02);
    private static final GpioPinDigitalOutput redLightFour = controller.provisionDigitalOutputPin(RaspiPin.GPIO_03);
    private static final GpioPinDigitalOutput redLightFive = controller.provisionDigitalOutputPin(RaspiPin.GPIO_04);

    private static final GpioPinDigitalOutput yellowLightOne = controller.provisionDigitalOutputPin(RaspiPin.GPIO_05);

    private static final RestAdapter retrofit = new RestAdapter.Builder()
            .setEndpoint("https://api.sendwyre.com/")
            .build();

    private static final WyreRatesService wyreRatesService = retrofit.create(WyreRatesService.class);

    public static void main(String[] args) {

        log.info("Starting...");

        // Reset lights
        reset();

        // Creates a new stream every 15 minutes
        Observable.interval(0, 15, TimeUnit.MINUTES, Schedulers.trampoline())

                // Turn on yellow light, indicating network request
                .doOnNext(Void -> yellowLightOne.setState(true))

                // Get rates from api
                .flatMap(Void -> wyreRatesService.rates())

                // Disable yellow light
                .doOnNext(Void -> yellowLightOne.setState(false))

                // Get the USD to BTC exchange rate
                .map(node -> Optional.ofNullable(node.get("USDBTC")))

                // Make sure the exchange rate is present
                .filter(Optional::isPresent)

                // Unwrap the value
                .map(Optional::get)

                .flatMap(rate -> buildExchangeRateArray(rate)
                        .toList())

                .flatMap(exchangeRateArray -> {

                    log.info("USDBTC {}", exchangeRateArray);

                    // List of observables configured to trigger a light to blink a certain number of times
                    List<Observable<Void>> oBlinks = new ArrayList<>();

                    oBlinks.add(blink(redLightOne).repeat(exchangeRateArray.get(0)).ignoreElements().cast(Void.class));
                    oBlinks.add(blink(redLightTwo).repeat(exchangeRateArray.get(1)).ignoreElements().cast(Void.class));
                    oBlinks.add(blink(redLightThree).repeat(exchangeRateArray.get(2)).ignoreElements().cast(Void.class));
                    oBlinks.add(blink(redLightFour).repeat(exchangeRateArray.get(3)).ignoreElements().cast(Void.class));
                    oBlinks.add(blink(redLightFive).repeat(exchangeRateArray.get(4)).ignoreElements().cast(Void.class));

                    // Run observables in order one after the other
                    return Observable.concat(oBlinks);
                })
                .subscribe(Void -> {},
                        throwable -> log.error("subscribe error: "+ throwable.toString()),
                        () -> {
                            log.info("subscribe onComplete");
                            controller.shutdown();
                        });
    }

    private static Observable<Integer> buildExchangeRateArray(Double rate) {
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

    private static Observable<Void> blink(GpioPinDigitalOutput digitalOutput) {
        return Observable.just(null)
                .doOnNext(Void -> digitalOutput.setState(true))
                .delay(1, TimeUnit.SECONDS)
                .doOnNext(Void -> digitalOutput.setState(false))
                .delay(500, TimeUnit.MILLISECONDS)
                .ignoreElements().cast(Void.class);
    }

    private static void reset() {
        redLightOne.setState(false);
        redLightTwo.setState(false);
        redLightThree.setState(false);
        redLightFour.setState(false);
        redLightFive.setState(false);

        yellowLightOne.setState(false);
    }
}

