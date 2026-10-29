package com.ffl.config;

import com.ffl.client.HnairClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@ConditionalOnProperty(name = "ffl.probe", havingValue = "true")
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class ProbeRunner implements ApplicationRunner {

    private final HnairClient hnairClient;

    @Override
    public void run(ApplicationArguments args) {
        var result = hnairClient.search("TFU", "PVG", LocalDate.of(2026, 12, 13));
        log.info("Probe success={} limited={} error={} quotes={}",
                result.success(), result.riskLimited(), result.errorMessage(), result.quotes().size());
        result.quotes().stream().limit(3).forEach(quote ->
                log.info("Probe quote {} {}-{} price={} inventory={}",
                        quote.flightNo(), quote.depTime(), quote.arrTime(), quote.price(), quote.inventory()));
    }
}
