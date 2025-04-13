package com.projjwal.bookingservice.service;

import com.projjwal.bookingservice.client.InventoryServiceClient;
import com.projjwal.bookingservice.entity.Customer;
import com.projjwal.bookingservice.event.BookingEvent;
import com.projjwal.bookingservice.repository.CustomerRepository;
import com.projjwal.bookingservice.request.BookingRequest;
import com.projjwal.bookingservice.response.BookingResponse;
import com.projjwal.bookingservice.response.InventoryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class BookingService {

    private final CustomerRepository customerRepository;
    private final InventoryServiceClient inventoryServiceClient;
    private final KafkaTemplate<String, BookingEvent> kafkaTemplate;

    @Autowired
    public BookingService(final CustomerRepository customerRepository,
                          InventoryServiceClient inventoryServiceClient,
                          KafkaTemplate<String,BookingEvent> kafkaTemplate)
    {
        this.customerRepository = customerRepository;
        this.inventoryServiceClient = inventoryServiceClient;
        this.kafkaTemplate = kafkaTemplate;

    }

    public BookingResponse createBooking(final BookingRequest bookingRequest)
    {
        //checking if user exist
        final Customer customer = customerRepository.findById(bookingRequest.getUserId()).orElse(null);
        if(null==customer)
        {
            throw new RuntimeException("User Not Found");
        }

        //checking if there is enough inventory
        final InventoryResponse inventoryResponse = inventoryServiceClient.getInventory(bookingRequest.getEventId());
        log.info("Inventory Response {}", inventoryResponse);
        if(inventoryResponse.getCapacity()<bookingRequest.getTicketCount())
        {
            throw new RuntimeException("Not enough inventory");
        }

        //creating booking
        final BookingEvent bookingEvent = createBookingEvent(bookingRequest, customer, inventoryResponse);
        kafkaTemplate.send("booking", bookingEvent);
        log.info("Booking info sent to Kafka: {}",bookingEvent);
        return BookingResponse.builder()
                .userId(bookingEvent.getUserId())
                .eventId(bookingEvent.getEventId())
                .ticketCount(bookingEvent.getTicketCount())
                .totalPrice(bookingEvent.getTotalPrice())
                .build();

    }

    private BookingEvent createBookingEvent(final BookingRequest request,
                                            final Customer customer,
                                            final InventoryResponse inventoryResponse) {
        return BookingEvent.builder()
                .userId(customer.getId())
                .eventId(request.getEventId())
                .ticketCount(request.getTicketCount())
                .totalPrice(inventoryResponse.getTicketPrice().multiply(BigDecimal.valueOf(request.getTicketCount())))
                .build();
    }

}
