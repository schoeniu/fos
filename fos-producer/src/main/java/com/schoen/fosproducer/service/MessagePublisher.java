package com.schoen.fosproducer.service;


import com.schoen.fosproducer.config.KafkaProducerConfiguration;
import com.schoen.fosproducer.model.FosEventInput;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;

@Service
public class MessagePublisher {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducerConfiguration.class);
    private final KafkaTemplate<String, Message<?>> kafkaTemplate;
    private final FosEventReader fosEventReader;

    private long timeThreshold;

    @Value("${PRODUCER_DATA_SEC_PER_REAL_SEC}")
    private int incrementValue;

    @Value("${PRODUCER_STARTUP_DELAY_IN_SEC}")
    private int delay;

    @Value("${PRINT_PRODUCER_LOGS}")
    private boolean isPrintProducerLogs;

    public MessagePublisher(KafkaTemplate<String, Message<?>> kafkaTemplate, FosEventReader fosEventReader) {
        this.kafkaTemplate = kafkaTemplate;
        this.fosEventReader = fosEventReader;
    }

    @PostConstruct
    void publishRawEvents() throws InterruptedException {
        LOG.info("Start publishing raw events...");
        FosEventInput nextFosEvent = fosEventReader.nextInput();

        final long startTime = nextFosEvent.getEventTime().toInstant(ZoneOffset.UTC).toEpochMilli();
        final long delaySeconds = delay * incrementValue * 1000L;
        this.timeThreshold = startTime-delaySeconds-1;
        startTimer();

        while (nextFosEvent != null){
            final long nextEventTime = nextFosEvent.getEventTime().toInstant(ZoneOffset.UTC).toEpochMilli();
            while(nextEventTime>timeThreshold){
                Thread.sleep(1000);
                if(startTime>timeThreshold){
                    LOG.info(((startTime-timeThreshold)/1000)/incrementValue + " seconds until event production starts.");
                }
            }
            sendFosEventInputMessage(nextFosEvent.getInput());
            nextFosEvent = fosEventReader.nextInput();
        }
        LOG.info("End of file. Publishing events finished.");
    }

    private void startTimer(){
        int period = 1000;
        int incrementValue = this.incrementValue*1000;
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask()
        {
            public void run(){
                timeThreshold += incrementValue;
            }
        }, 0, period);
    }

    private void sendFosEventInputMessage(final String fosEventInput) {
        final Message<String> message = new GenericMessage<>(fosEventInput, Collections.singletonMap(KafkaHeaders.TOPIC,"producedEvents"));
        kafkaTemplate.send(message);
        if(isPrintProducerLogs){
            LOG.info("Sent message: "+ message);
        }
    }

}
