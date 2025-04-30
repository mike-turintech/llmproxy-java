package com.llmproxy.service.router;

import com.llmproxy.exception.ModelError;
import com.llmproxy.model.ModelType;
import com.llmproxy.model.QueryRequest;
import com.llmproxy.model.StatusResponse;
import com.llmproxy.model.TaskType;
import com.llmproxy.service.llm.LlmClient;
import com.llmproxy.service.llm.LlmClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
@Slf4j
@RequiredArgsConstructor
public class RouterService {
    private static final int DEFAULT_AVAILABILITY_TTL = 300; // 5 minutes
    
    private final LlmClientFactory clientFactory;
    private final Map<ModelType, Boolean> availableModels = new HashMap<>();
    private final ReadWriteLock availabilityLock = new ReentrantReadWriteLock();
    private Instant lastUpdated = null;
    private final SecureRandom random = new SecureRandom();
    
    @Value("${router.availability.ttl:300}")
    private int availabilityTtl;
    
    private boolean testMode = false;
    
    public void setTestMode(boolean enabled) {
        this.testMode = enabled;
    }
    
    public void setModelAvailability(ModelType model, boolean available) {
        availabilityLock.writeLock().lock();
        try {
            availableModels.put(model, available);
        } finally {
            availabilityLock.writeLock().unlock();
        }
    }
    
    public void updateAvailability() {
        if (testMode) {
            return;
        }
        
        availabilityLock.writeLock().lock();
        try {
            if (lastUpdated != null && 
                Duration.between(lastUpdated, Instant.now()).getSeconds() < availabilityTtl) {
                log.debug("Skipping availability update due to TTL");
                return;
            }
            
            log.debug("Updating model availability");
            for (ModelType modelType : ModelType.values()) {
                try {
                    LlmClient client = clientFactory.getClient(modelType);
                    availableModels.put(modelType, client.checkAvailability());
                } catch (Exception e) {
                    availableModels.put(modelType, false);
                }
            }
            
            lastUpdated = Instant.now();
        } finally {
            availabilityLock.writeLock().unlock();
        }
    }
    
    private void ensureAvailabilityUpdated() {
        if (testMode) {
            return;
        }
        
        boolean needsUpdate = false;
        
        availabilityLock.readLock().lock();
        try {
            needsUpdate = lastUpdated == null || 
                Duration.between(lastUpdated, Instant.now()).getSeconds() >= availabilityTtl;
        } finally {
            availabilityLock.readLock().unlock();
        }
        
        if (needsUpdate) {
            updateAvailability();
        }
    }
    
    public StatusResponse getAvailability() {
        ensureAvailabilityUpdated();
        
        availabilityLock.readLock().lock();
        try {
            return StatusResponse.builder()
                .openai(availableModels.getOrDefault(ModelType.OPENAI, false))
                .gemini(availableModels.getOrDefault(ModelType.GEMINI, false))
                .mistral(availableModels.getOrDefault(ModelType.MISTRAL, false))
                .claude(availableModels.getOrDefault(ModelType.CLAUDE, false))
                .build();
        } finally {
            availabilityLock.readLock().unlock();
        }
    }
    
    public ModelType routeRequest(QueryRequest request) {
        if (request.getModel() != null) {
            if (isModelAvailable(request.getModel())) {
                log.debug("Using user-specified model: {}", request.getModel());
                return request.getModel();
            }
            log.warn("Requested model {} not available, trying alternatives", request.getModel());
        }
        
        if (request.getTaskType() != null) {
            try {
                ModelType model = routeByTaskType(request.getTaskType());
                log.debug("Routed to model {} based on task type {}", model, request.getTaskType());
                return model;
            } catch (Exception e) {
                log.warn("Failed to route by task type: {}", e.getMessage());
            }
        }
        
        ModelType model = getRandomAvailableModel();
        log.debug("Using random available model: {}", model);
        return model;
    }
    
    public ModelType fallbackOnError(ModelType originalModel, QueryRequest request, Exception error) {
        if (!(error instanceof ModelError) || !((ModelError) error).isRetryable()) {
            throw ModelError.unavailableError("all");
        }
        
        List<ModelType> availableModels = getAvailableModelsExcept(originalModel);
        if (availableModels.isEmpty()) {
            throw ModelError.unavailableError("all");
        }
        
        if (request.getModel() != null && request.getModel() != originalModel) {
            for (ModelType model : availableModels) {
                if (model == request.getModel() && isModelAvailable(model)) {
                    log.debug("Falling back to user-specified model: {}", model);
                    return model;
                }
            }
        }
        
        int fallbackIndex = random.nextInt(availableModels.size());
        ModelType fallbackModel = availableModels.get(fallbackIndex);
        
        log.debug("Falling back from {} to {}", originalModel, fallbackModel);
        return fallbackModel;
    }
    
    private boolean isModelAvailable(ModelType model) {
        ensureAvailabilityUpdated();
        
        availabilityLock.readLock().lock();
        try {
            return availableModels.getOrDefault(model, false);
        } finally {
            availabilityLock.readLock().unlock();
        }
    }
    
    
    private ModelType routeByTaskType(TaskType taskType) {
        switch (taskType) {
            case TEXT_GENERATION:
                if (isModelAvailable(ModelType.OPENAI)) {
                    return ModelType.OPENAI;
                }
                break;
            case SUMMARIZATION:
                if (isModelAvailable(ModelType.CLAUDE)) {
                    return ModelType.CLAUDE;
                }
                break;
            case SENTIMENT_ANALYSIS:
                if (isModelAvailable(ModelType.GEMINI)) {
                    return ModelType.GEMINI;
                }
                break;
            case QUESTION_ANSWERING:
                if (isModelAvailable(ModelType.MISTRAL)) {
                    return ModelType.MISTRAL;
                }
                break;
        }
        
        return getRandomAvailableModel();
    }
    
    private ModelType getRandomAvailableModel() {
        ensureAvailabilityUpdated();
        
        availabilityLock.readLock().lock();
        List<ModelType> availableModelTypes = new ArrayList<>();
        try {
            for (ModelType modelType : ModelType.values()) {
                if (availableModels.getOrDefault(modelType, false)) {
                    availableModelTypes.add(modelType);
                }
            }
        } finally {
            availabilityLock.readLock().unlock();
        }
        
        if (availableModelTypes.isEmpty()) {
            throw ModelError.unavailableError("all");
        }
        
        int randomIndex = random.nextInt(availableModelTypes.size());
        return availableModelTypes.get(randomIndex);
    }
    
    private List<ModelType> getAvailableModelsExcept(ModelType excludeModel) {
        ensureAvailabilityUpdated();
        
        availabilityLock.readLock().lock();
        List<ModelType> availableModelTypes = new ArrayList<>();
        try {
            for (ModelType modelType : ModelType.values()) {
                if (modelType != excludeModel && availableModels.getOrDefault(modelType, false)) {
                    availableModelTypes.add(modelType);
                }
            }
        } finally {
            availabilityLock.readLock().unlock();
        }
        
        return availableModelTypes;
    }
}
