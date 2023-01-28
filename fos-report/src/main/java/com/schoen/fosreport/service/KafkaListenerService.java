package com.schoen.fosreport.service;


import com.schoen.fosreport.dao.EventMetricsRepository;
import com.schoen.fosreport.model.EventMetrics;
import com.schoen.fosreport.model.EventWindow;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@EnableKafka
@Service
public class KafkaListenerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaListenerService.class);

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    @Autowired
    private EventMetricsRepository eventMetricsRepository;

    @KafkaListener(topics = "availableDBEntries")
    public void eventListener(final ConsumerRecord<UUID, EventWindow> consumerRecord) {
        final Optional<EventMetrics> eventMetrics = eventMetricsRepository.findById(consumerRecord.value());
        if(eventMetrics.isPresent()){
            printPretty(eventMetrics.get());

        }else {
            LOGGER.warn(String.format("Metrics for events from %s to %s could not be found! May be messages from prior docker run."
                                        ,dateFormat.format(consumerRecord.value().getStart_event_time())
                                        ,dateFormat.format(consumerRecord.value().getEnd_event_time())));
        }

    }

    private void printPretty(final EventMetrics eventMetrics){
        final  DecimalFormat decimalFormat =  new DecimalFormat();
        decimalFormat.setMaximumFractionDigits(2);
        decimalFormat.setMinimumFractionDigits(2);
        LOGGER.info(String.format(
                """
                    
                    Metrics for events from %s to %s:
                    Number of events                    : %d
                    Number of items viewed              : %d
                    Number of items put in cart         : %d
                    Number of items sold                : %d
                    Value of items sold                 : %s
                    Number of distinct users active     : %d
                    Number of distinct categories viewed: %d
                    Number of distinct brands viewed    : %d
                """
                ,dateFormat.format(eventMetrics.getEventWindow().getStart_event_time())
                ,dateFormat.format(eventMetrics.getEventWindow().getEnd_event_time())
                ,eventMetrics.getEventWindow().getNr_of_events()
                ,eventMetrics.getNrItemsViewed()
                ,eventMetrics.getNrItemsPutInCart()
                ,eventMetrics.getNrItemsSold()
                ,decimalFormat.format(eventMetrics.getValueItemsSoldInCent()/100)
                ,eventMetrics.getNrUsersActive()
                ,eventMetrics.getNrCategoriesViewed()
                ,eventMetrics.getNrBrandsViewed()

        ));
    }
}
