    @Test
    @Tag("performance")
    void performanceBenchmark_lookupSpeed() {
        CacheService cacheService = cacheServiceRef.get();
        
        // Warm-up phase
        int warmupItems = 20;
        List<QueryRequest> warmupRequests = generateUniqueRequests(warmupItems);
        for (int i = 0; i < warmupItems; i++) {
            QueryResponse resp = QueryResponse.builder()
                    .response("Warmup response " + i)
                    .model(ModelType.OPENAI)
                    .build();
            cacheService.set(warmupRequests.get(i), resp);
            cacheService.get(warmupRequests.get(i)); // Warm up get operation
        }
        
        // Prepare a pre-populated cache
        int numItems = 100;
        List<QueryRequest> requests = new ArrayList<>();
        
        // Generate and cache requests
        for (int i = 0; i < numItems; i++) {
            QueryRequest req = QueryRequest.builder()
                    .query("Performance test query " + i)
                    .model(ModelType.OPENAI)
                    .taskType(TaskType.TEXT_GENERATION)
                    .requestId(UUID.randomUUID().toString())
                    .build();
                    
            QueryResponse resp = QueryResponse.builder()
                    .response("Test response " + i)
                    .model(ModelType.OPENAI)
                    .build();
                    
            cacheService.set(req, resp);
            requests.add(req);
        }
        
        // Benchmark lookups with multiple iterations for more accurate results
        int iterations = 5;
        double[] iterationTimes = new double[iterations];
        
        for (int iter = 0; iter < iterations; iter++) {
            long startTime = System.nanoTime();
            for (QueryRequest req : requests) {
                QueryResponse resp = cacheService.get(req);
                assertNotNull(resp);
            }
            long endTime = System.nanoTime();
            
            iterationTimes[iter] = (endTime - startTime) / (double)(numItems * 1_000_000);
        }
        
        // Calculate median time (more stable than average)
        java.util.Arrays.sort(iterationTimes);
        double medianLookupTimeMs = iterationTimes[iterations / 2];
        double avgLookupTimeMs = java.util.Arrays.stream(iterationTimes).average().orElse(0);
        
        logger.info("Cache lookup performance: Median={} ms, Avg={} ms", 
                String.format("%.3f", medianLookupTimeMs),
                String.format("%.3f", avgLookupTimeMs));
    }
    
    @Test
    @Tag("performance")
    void performanceBenchmark_insertionSpeed() {
        CacheService cacheService = cacheServiceRef.get();
        int numItems = 1000;
        List<QueryRequest> requests = generateUniqueRequests(numItems);
        
        // Warm-up phase
        int warmupItems = 50;
        for (int i = 0; i < warmupItems; i++) {
            QueryResponse resp = QueryResponse.builder()
                    .response("Warmup response " + i)
                    .model(ModelType.OPENAI)
                    .build();
            cacheService.set(requests.get(i), resp);
        }
        
        // Benchmark insertions with multiple iterations
        int iterations = 5;
        double[] iterationTimes = new double[iterations];
        
        for (int iter = 0; iter < iterations; iter++) {
            // Create a fresh cache for each iteration to avoid measuring cache size impact
            CacheService freshCache = new CacheService(true, 300, 1000, new ObjectMapper());
            
            long startTime = System.nanoTime();
            for (int i = 0; i < numItems; i++) {
                QueryResponse resp = QueryResponse.builder()
                        .response("Performance test response " + i)
                        .model(ModelType.OPENAI)
                        .build();
                        
                freshCache.set(requests.get(i), resp);
            }
            long endTime = System.nanoTime();
            
            iterationTimes[iter] = (endTime - startTime) / (double)(numItems * 1_000_000);
        }
        
        // Calculate median time
        java.util.Arrays.sort(iterationTimes);
        double medianInsertTimeMs = iterationTimes[iterations / 2];
        double avgInsertTimeMs = java.util.Arrays.stream(iterationTimes).average().orElse(0);
        
        logger.info("Cache insertion performance: Median={} ms, Avg={} ms", 
                String.format("%.3f", medianInsertTimeMs),
                String.format("%.3f", avgInsertTimeMs));
    }
    
    @Test
    @Tag("performance")
    void performanceBenchmark_scalingWithSize() {
        // Test with different cache sizes
        int[] cacheSizes = {100, 1000, 5000};
        
        for (int size : cacheSizes) {
            CacheService sizedCache = new CacheService(true, 300, size, new ObjectMapper());
            List<QueryRequest> requests = generateUniqueRequests(size);
            
            // Warmup phase
            int warmupItems = Math.min(size / 10, 100);
            for (int i = 0; i < warmupItems; i++) {
                QueryResponse resp = QueryResponse.builder()
                        .response("Warmup response " + i)
                        .model(ModelType.OPENAI)
                        .build();
                        
                sizedCache.set(requests.get(i), resp);
                sizedCache.get(requests.get(i)); // Warm up get operation
            }
            
            // Measure insertion speed
            long insertStart = System.nanoTime();
            for (int i = 0; i < size; i++) {
                QueryResponse resp = QueryResponse.builder()
                        .response("Scaling test response " + i)
                        .model(ModelType.OPENAI)
                        .build();
                        
                sizedCache.set(requests.get(i), resp);
            }
            long insertEnd = System.nanoTime();
            
            // Measure lookup speed (random access)
            int lookupCount = Math.min(size, 1000);
            long[] lookupTimes = new long[lookupCount];
            
            for (int i = 0; i < lookupCount; i++) {
                int index = (int)(Math.random() * size);
                long start = System.nanoTime();
                sizedCache.get(requests.get(index));
                lookupTimes[i] = System.nanoTime() - start;
            }
            
            double totalInsertTimeMs = (insertEnd - insertStart) / 1_000_000.0;
            double avgInsertTimeMs = totalInsertTimeMs / size;
            double avgLookupTimeNs = java.util.Arrays.stream(lookupTimes).average().orElse(0);
            double avgLookupTimeMs = avgLookupTimeNs / 1_000_000.0;
            double p95LookupTimeMs = calculatePercentile(lookupTimes, 95) / 1_000_000.0;
            
            logger.info("Cache size {}: Avg insert={} ms, Avg lookup={} ms, P95 lookup={} ms",
                    size, 
                    String.format("%.3f", avgInsertTimeMs),
                    String.format("%.3f", avgLookupTimeMs),
                    String.format("%.3f", p95LookupTimeMs));
        }
    }
    
    private double calculatePercentile(long[] times, double percentile) {
        java.util.Arrays.sort(times);
        int index = (int) Math.ceil(percentile / 100.0 * times.length) - 1;
        return times[index];
    }
    
    // Helper method to generate unique requests for performance testing
    private List<QueryRequest> generateUniqueRequests(int count) {
        List<QueryRequest> requests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            QueryRequest req = QueryRequest.builder()
                    .query("Generated query " + i)
                    .model(i % 2 == 0 ? ModelType.OPENAI : ModelType.GEMINI)
                    .taskType(i % 4 == 0 ? TaskType.TEXT_GENERATION : 
                             i % 4 == 1 ? TaskType.SUMMARIZATION : 
                             i % 4 == 2 ? TaskType.SENTIMENT_ANALYSIS :
                             TaskType.QUESTION_ANSWERING)
                    .requestId(UUID.randomUUID().toString())
                    .build();
            requests.add(req);
        }
        return requests;
    }