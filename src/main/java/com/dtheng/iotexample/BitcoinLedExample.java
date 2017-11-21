package com.dtheng.iotexample;

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import lombok.extern.slf4j.Slf4j;
import retrofit.RestAdapter;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Daniel Thengvall <fender5289@gmail.com>
 */
@Slf4j
public class BitcoinLedExample {

    private static final int numberOfLights = 5;
    private static final int defaultSpeedMs = 750;

    private static final GpioController controller = GpioFactory.getInstance();

    private static final List<GpioPinDigitalOutput> redLights = Arrays.asList(
            controller.provisionDigitalOutputPin(RaspiPin.GPIO_00),
            controller.provisionDigitalOutputPin(RaspiPin.GPIO_01),
            controller.provisionDigitalOutputPin(RaspiPin.GPIO_02),
            controller.provisionDigitalOutputPin(RaspiPin.GPIO_03),
            controller.provisionDigitalOutputPin(RaspiPin.GPIO_04));

    private static final GpioPinDigitalOutput yellowLightOne = controller.provisionDigitalOutputPin(RaspiPin.GPIO_05);

    private static final GpioPinDigitalInput startButton = controller.provisionDigitalInputPin(RaspiPin.GPIO_29);
    private static final GpioPinDigitalInput modeButton = controller.provisionDigitalInputPin(RaspiPin.GPIO_28);

    private static final RestAdapter retrofit = new RestAdapter.Builder().setEndpoint("https://api.sendwyre.com/").build();

    private static final WyreRatesService wyreRatesService = retrofit.create(WyreRatesService.class);

    private static boolean isDisplayingRate = false;
    private static Mode mode = Mode.DEFAULT;
    private static int speedMs = 750;

    public static void main(String[] args) {

        log.info("Starting Bitcoin Led Example...");

        // Reset lights
        reset()
            .subscribe(Void -> {},
                throwable -> log.error("subscribe error: {}", throwable.toString()));

        // Setup button listeners
        startButton.addListener(new Listener.StartButton());
        modeButton.addListener(new Listener.ModeButton());

        // Creates a new stream every 10 minutes
        Observable.interval(0, 10, TimeUnit.MINUTES, Schedulers.trampoline())
            .flatMap(Void -> displayExchangeRate())
            .subscribe(

                // onNext, do nothing
                Void -> {},

                // onError, log error
                throwable -> log.error("subscribe error: {}", throwable.toString()),

                // onCompleted, shutdown IO controller
                controller::shutdown);
    }

    private static Observable<Void> changeMode() {
        if (isDisplayingRate)
            return Observable.empty();
        return reset()
            .defaultIfEmpty(null)
            .doOnNext(Void -> {
                switch (mode) {
                    case DEFAULT:
                        mode = Mode.BINARY;
                        speedMs = defaultSpeedMs;
                        break;
                    case BINARY:
                        mode = Mode.FAST;
                        speedMs = defaultSpeedMs / 2;
                        break;
                    case FAST:
                        mode = Mode.SLOW;
                        speedMs = defaultSpeedMs * 2;
                        break;
                    case SLOW:
                        mode = Mode.DEFAULT;
                        speedMs = defaultSpeedMs;
                        break;
                }
            })
            .doOnNext(Void -> redLights.get(mode.ordinal()).setState(true))
            .delay(1, TimeUnit.SECONDS)
            .flatMap(Void -> reset());
    }

    private static Observable<Void> displayExchangeRate() {
        return Observable.defer(() -> {

            if (isDisplayingRate)
                return Observable.empty();

            isDisplayingRate = true;

            // Get rates from api
            return getRates()

                    // Get the USD to BTC exchange rate
                    .map(node -> Optional.ofNullable(node.get("USDBTC")))

                    // Make sure the exchange rate is present
                    .filter(Optional::isPresent)

                    // Unwrap the value
                    .map(Optional::get)

                    .flatMap(rate -> {
                        switch (mode) {
                            case DEFAULT:
                            case FAST:
                            case SLOW:
                                return defaultModeHandler(rate);
                            case BINARY:
                                return binaryModeHandler(rate);
                            default:
                                throw new RuntimeException("Unknown mode: "+ mode.name());
                        }
                    })
                    .defaultIfEmpty(null)
                    .doOnNext(Void -> isDisplayingRate = false)
                    .doOnError(Void -> isDisplayingRate = false);
        });
    }

    private static Observable<Map<String, Double>> getRates() {

        // Turn on yellow light, indicating network request
        yellowLightOne.setState(true);

        return wyreRatesService.rates()

                // Disable yellow light
                .doOnNext(Void -> yellowLightOne.setState(false))

                .onErrorResumeNext(BitcoinLedExample::onRatesError);
    }

    private static Observable<Map<String, Double>> onRatesError(Throwable throwable) {
        return Observable.defer(() -> {

                // Disable yellow light
                yellowLightOne.setState(false);

                // Blink red lights 3 times
                List<Observable<Void>> oErrorBlinks = new ArrayList<>();
                for (int i = 0; i < redLights.size(); i++)
                    oErrorBlinks.add(Utils.blink(redLights.get(i), 500).repeat(3));
                return Observable.merge(oErrorBlinks)
                        .defaultIfEmpty(null)
                        .toList();
            })
            .flatMap(Void -> Observable.error(throwable));
    }

    private static Observable<Void> defaultModeHandler(Double rate) {
        return Utils.buildExchangeRateArray(rate, numberOfLights)
                .toList()
                .flatMap(exchangeRateArray -> {

                    // List of observables configured to trigger a light to blink a certain number of times
                    List<Observable<Void>> oBlinks = new ArrayList<>();

                    for (int i = 0; i < exchangeRateArray.size(); i++)
                        oBlinks.add(Utils.blink(redLights.get(i), speedMs).repeat(exchangeRateArray.get(i)));

                    // Run observables in order one after the other
                    return Observable.concat(oBlinks);
                })
                .toList()
                .ignoreElements().cast(Void.class);
    }

    private static Observable<Void> binaryModeHandler(Double rate) {
        return Utils.buildExchangeRateArray(rate, numberOfLights)
                .zipWith(Observable.interval(speedMs, TimeUnit.MILLISECONDS), (number, time) -> {
                    List<Boolean> binaryValue = Utils.numberToBinarySequence.get(number);
                    List<Observable<Void>> blinks = new ArrayList<>();
                    for (int i = 0; i < binaryValue.size(); i++)
                        if (binaryValue.get(i))
                            blinks.add(Utils.blink(redLights.get(i), speedMs));
                    return Observable.merge(blinks);
                })
                .flatMap(o -> o);
    }

    private static Observable<Void> reset() {
        return Observable.defer(() -> {
            yellowLightOne.setState(false);
            return Observable.from(redLights)
                    .doOnNext(light -> light.setState(false));
        })
        .ignoreElements().cast(Void.class);
    }

    private static class Listener {

        static class StartButton implements GpioPinListenerDigital {

            @Override
            public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
                if (event.getState() == PinState.HIGH)
                    displayExchangeRate()
                        .subscribe(Void -> {},
                            throwable -> log.error("subscribe error: {}", throwable.toString()));
            }
        }

        static class ModeButton implements GpioPinListenerDigital {

            @Override
            public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
                if (event.getState() == PinState.HIGH)
                    changeMode()
                        .subscribe(Void -> {},
                            throwable -> log.error("subscribe error: {}", throwable.toString()));
            }
        }
    }
}

