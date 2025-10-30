package home.work.booking.repositories;

import home.work.booking.entities.ProcessedRequest;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ProcessedRequestRepository extends ReactiveCrudRepository<ProcessedRequest, String> {
    Mono<Boolean> existsByRequestId(String requestId);
}