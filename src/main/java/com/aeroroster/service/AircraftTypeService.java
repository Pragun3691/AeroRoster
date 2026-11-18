package com.aeroroster.service;

import com.aeroroster.persistence.repository.AircraftTypeRepository;
import com.aeroroster.service.dto.AircraftTypeView;
import com.aeroroster.service.exception.ResourceNotFoundException;
import com.aeroroster.service.validation.ServiceValidation;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AircraftTypeService {
    private final AircraftTypeRepository types;
    private final ServiceValidation validation;
    private final ServiceTransactions transactions;

    public AircraftTypeService(AircraftTypeRepository types, ServiceValidation validation, ServiceTransactions transactions) {
        this.types = types;
        this.validation = validation;
        this.transactions = transactions;
    }

    public AircraftTypeView get(Long id) {
        validation.id(id);
        return transactions.read(() -> ServiceViews.aircraftType(types.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aircraft type was not found"))));
    }

    public List<AircraftTypeView> list() {
        return transactions.read(() -> types.findAll(Sort.by("model", "id"))
                .stream().map(ServiceViews::aircraftType).toList());
    }
}
