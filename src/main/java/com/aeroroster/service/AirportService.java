package com.aeroroster.service;

import com.aeroroster.persistence.repository.AirportRepository;
import com.aeroroster.service.dto.AirportView;
import com.aeroroster.service.exception.ResourceNotFoundException;
import com.aeroroster.service.validation.InputValues;
import com.aeroroster.service.validation.ServiceValidation;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AirportService {
    private final AirportRepository airports;
    private final ServiceValidation validation;
    private final ServiceTransactions transactions;

    public AirportService(AirportRepository airports, ServiceValidation validation, ServiceTransactions transactions) {
        this.airports = airports;
        this.validation = validation;
        this.transactions = transactions;
    }

    public AirportView get(Long id) {
        validation.id(id);
        return transactions.read(() -> ServiceViews.airport(airports.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airport was not found"))));
    }

    public List<AirportView> search(String query) {
        return transactions.read(() -> airports.search(InputValues.searchPattern(query))
                .stream().map(ServiceViews::airport).toList());
    }
}
